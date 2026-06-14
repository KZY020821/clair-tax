import json

import structlog

from app.clients.deepseek_client import DeepSeekClient, DeepSeekError
from app.config import get_settings
from app.models.chat import (
    ChatConfirmRequest,
    ChatConfirmResponse,
    ChatMessage,
    ChatProcessRequest,
    ChatProcessResponse,
    PendingAction,
)
from app.services.chat_tools import CHAT_TOOLS, READ_TOOLS, WRITE_TOOLS
from app.services.intent_classifier import classify_intent
from app.services.tool_executor import ToolExecutionError, ToolExecutor

logger = structlog.get_logger(__name__)

_SYSTEM_PROMPT = """You are a helpful Malaysian personal income tax assistant for Clair Tax.
Help users understand LHDN tax reliefs, manage their Year of Assessment workspaces, and review receipts.
Be concise and accurate. Reference amounts in MYR. When you need to retrieve or update data, use the available tools.
For profile updates and receipt assignments, always describe what you intend to do — the system will ask the user to confirm before acting.
Do not invent tax rules — use get_relief_categories to fetch accurate data for the relevant year.

Tool usage rules:
- Only call tools by their EXACT names as listed. Never invent or guess tool names.
- When the user has attached a receipt file and asks to add, save, or file it for a specific year, call process_receipt_attachment IMMEDIATELY — do not call any other tool first.
- Only call get_user_year_summary when the user explicitly asks about their current claimed amounts or workspace summary, not as a precondition for adding a receipt.
- Only call get_relief_categories when the user asks what reliefs exist or their caps — never as a precondition for adding a receipt.
- When you call process_receipt_attachment, include relief_category_hint if the user mentioned a category (e.g. "medical", "sports", "education", "lifestyle"). Omit attachment_s3_key — it is injected automatically."""


class ChatService:
    def __init__(self) -> None:
        settings = get_settings()
        self._deepseek = DeepSeekClient(settings)
        self._executor = ToolExecutor(settings)
        self._window_pairs = settings.CHAT_SLIDING_WINDOW_PAIRS

    def _build_messages(
        self,
        history: list[ChatMessage],
        new_message: str,
        attachment_urls: list[str] | None = None,
    ) -> list[dict]:
        """Apply sliding window and prepend system prompt. Builds multimodal content for messages with attachments."""
        system = [{"role": "system", "content": _SYSTEM_PROMPT}]
        window = history[-(self._window_pairs * 2) :]
        windowed = [
            {"role": m.role, "content": _build_content(m.content, m.attachment_urls)}
            for m in window
        ]
        windowed.append({"role": "user", "content": _build_content(new_message, attachment_urls or [])})
        return system + windowed

    async def process_message(self, request: ChatProcessRequest) -> ChatProcessResponse:
        bound = logger.bind(user_id=request.user_id)
        intent = classify_intent(request.message)
        bound.info("chat_intent_classified", intent=intent.intent, confidence=intent.confidence)

        settings = get_settings()
        if (
            intent.intent == "general"
            and intent.confidence >= settings.INTENT_CLASSIFIER_OFF_TOPIC_THRESHOLD
        ):
            bound.info("chat_off_topic_blocked", confidence=intent.confidence)
            return ChatProcessResponse(
                reply=(
                    "I'm sorry, it seems like the question is not relevant to tax. "
                    "Could you ask a question related to income tax?"
                )
            )

        messages = self._build_messages(request.history, request.message, request.attachment_urls)

        try:
            llm_response = await self._deepseek.chat_completion(messages, tools=CHAT_TOOLS)
        except DeepSeekError as exc:
            bound.error("deepseek_error", detail=str(exc))
            return ChatProcessResponse(
                reply="I'm having trouble connecting right now. Please try again in a moment."
            )

        tool_calls = llm_response.get("tool_calls", [])
        content: str = llm_response.get("content") or ""

        if not tool_calls:
            return ChatProcessResponse(reply=content)

        # Process the first tool call
        tool_call = tool_calls[0]
        tool_name: str = tool_call["name"]
        tool_call_id: str = tool_call["id"]

        try:
            tool_args: dict = json.loads(tool_call["arguments"])
        except (json.JSONDecodeError, KeyError):
            bound.warning("tool_args_parse_failed", raw=str(tool_call))
            return ChatProcessResponse(
                reply=content or "I encountered an issue processing your request."
            )

        # Auto-inject S3 key for receipt attachment processing tool
        if tool_name == "process_receipt_attachment":
            s3_keys = request.attachment_s3_keys or []
            if "attachment_s3_key" not in tool_args and s3_keys:
                tool_args["attachment_s3_key"] = s3_keys[0]

        if tool_name in WRITE_TOOLS:
            pending = PendingAction(
                tool_name=tool_name,
                tool_args=tool_args,
                description=_describe_action(tool_name, tool_args),
            )
            confirmation_prompt = content or f"I'd like to {pending.description}. Can you confirm?"
            return ChatProcessResponse(
                reply=confirmation_prompt,
                pending_action=pending,
                requires_confirmation=True,
            )

        if tool_name in READ_TOOLS:
            try:
                tool_result = await self._executor.execute(tool_name, tool_args, request.user_id)
            except ToolExecutionError as exc:
                bound.warning("tool_execution_failed", tool=tool_name, detail=str(exc))
                return ChatProcessResponse(
                    reply="I couldn't retrieve that information right now. Please try again."
                )

            follow_up_messages = messages + [
                {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [
                        {
                            "id": tool_call_id,
                            "type": "function",
                            "function": {
                                "name": tool_name,
                                "arguments": tool_call["arguments"],
                            },
                        }
                    ],
                },
                {
                    "role": "tool",
                    "tool_call_id": tool_call_id,
                    "content": json.dumps(tool_result),
                },
            ]
            try:
                final_response = await self._deepseek.chat_completion(follow_up_messages)
                return ChatProcessResponse(reply=final_response.get("content") or "")
            except DeepSeekError:
                return ChatProcessResponse(reply=str(tool_result))

        bound.warning("unknown_tool_name", tool=tool_name)
        return ChatProcessResponse(reply=content or "")

    async def confirm_action(self, request: ChatConfirmRequest) -> ChatConfirmResponse:
        bound = logger.bind(user_id=request.user_id, tool=request.pending_action.tool_name)
        try:
            result = await self._executor.execute(
                request.pending_action.tool_name,
                request.pending_action.tool_args,
                request.user_id,
            )
            bound.info("tool_confirmed_and_executed")
            reply = _describe_result(request.pending_action.tool_name, result)
            return ChatConfirmResponse(reply=reply, success=True)
        except ToolExecutionError as exc:
            bound.error("tool_execution_failed_on_confirm", detail=str(exc))
            return ChatConfirmResponse(
                reply="Something went wrong completing that action. Please try again.",
                success=False,
                error=str(exc),
            )
        except Exception as exc:  # noqa: BLE001
            bound.error("tool_confirm_unexpected_error", detail=str(exc))
            return ChatConfirmResponse(
                reply="Something went wrong completing that action. Please try again.",
                success=False,
                error=str(exc),
            )


def _build_content(text: str, urls: list[str]) -> str | list[dict]:
    """Return a multimodal content list when image URLs are present, otherwise a plain string."""
    image_urls = [u for u in urls if _is_image_url(u)]
    pdf_urls = [u for u in urls if not _is_image_url(u)]
    if not image_urls and not pdf_urls:
        return text
    body = text
    if pdf_urls:
        refs = "\n".join(f"[Document: {u}]" for u in pdf_urls)
        body = f"{text}\n\n{refs}"
    parts: list[dict] = [{"type": "text", "text": body}]
    for url in image_urls:
        parts.append({"type": "image_url", "image_url": {"url": url}})
    return parts


def _is_image_url(url: str) -> bool:
    if url.startswith("local://"):
        return False
    lower = url.lower().split("?")[0]
    return any(lower.endswith(ext) for ext in (".jpg", ".jpeg", ".png", ".gif", ".webp"))


def _describe_action(tool_name: str, args: dict) -> str:
    if tool_name == "update_profile":
        parts = []
        if "marital_status" in args:
            parts.append(f"set your marital status to {args['marital_status']}")
        if args.get("is_disabled") is not None:
            parts.append(f"set disability to {args['is_disabled']}")
        return "update your profile: " + (", ".join(parts) if parts else str(args))
    if tool_name == "assign_receipt_to_year":
        return f"assign receipt to year {args.get('year', '')}"
    if tool_name == "process_receipt_attachment":
        hint = args.get("relief_category_hint", "")
        cat = f" under {hint}" if hint else ""
        return f"process your receipt attachment and add it to year {args.get('year', '')}{cat}"
    return tool_name.replace("_", " ")


def _describe_result(tool_name: str, result: dict) -> str:
    if tool_name == "update_profile":
        return "Done! Your profile has been updated."
    if tool_name == "assign_receipt_to_year":
        return "Done! The receipt has been assigned to the year workspace."
    if tool_name == "process_receipt_attachment":
        merchant = result.get("merchantName") or "the receipt"
        amount = result.get("amount")
        date = result.get("receiptDate")
        year = result.get("policyYear")
        category = result.get("reliefCategoryName")
        msg = f"Done! Added {merchant}"
        if amount:
            msg += f" (RM {amount})"
        if date:
            msg += f" dated {date}"
        if year:
            msg += f" to your {year}"
        if category:
            msg += f" {category}"
        return msg + " workspace."
    return "The action completed successfully."
