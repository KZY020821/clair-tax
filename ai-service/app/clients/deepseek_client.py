import json
import re
import uuid as _uuid

import structlog
from openai import AsyncOpenAI, OpenAIError

from app.config import Settings

logger = structlog.get_logger(__name__)

# ---------------------------------------------------------------------------
# DeepSeek sometimes leaks its internal DSML tool-call markup into the
# `content` field instead of (or in addition to) populating `tool_calls`.
# We strip it from displayed content and optionally parse it as a fallback.
# The DSML delimiters use U+FF5C FULLWIDTH VERTICAL LINE (｜).
# ---------------------------------------------------------------------------
_DSML_BLOCK_RE = re.compile(
    r"<｜｜DSML｜｜tool_calls>.*?</｜｜DSML｜｜tool_calls>",
    re.DOTALL,
)
_DSML_INVOKE_RE = re.compile(
    r'<｜｜DSML｜｜invoke\s+name="([^"]+)">(.*?)</｜｜DSML｜｜invoke>',
    re.DOTALL,
)
_DSML_PARAM_RE = re.compile(
    r'<｜｜DSML｜｜parameter\s+name="([^"]+)"\s+string="([^"]+)">(.*?)</｜｜DSML｜｜parameter>',
    re.DOTALL,
)


def _parse_dsml_tool_calls(content: str) -> list[dict]:
    """
    Parse DSML tool-call markup leaked into content.
    Returns tool-call dicts in the same shape as our standard format.
    """
    calls: list[dict] = []
    for block in _DSML_BLOCK_RE.finditer(content):
        for invoke in _DSML_INVOKE_RE.finditer(block.group(0)):
            fn_name = invoke.group(1)
            args: dict = {}
            for param in _DSML_PARAM_RE.finditer(invoke.group(2)):
                p_name = param.group(1)
                p_is_str = param.group(2).strip().lower() == "true"
                p_value = param.group(3).strip()
                if p_is_str:
                    args[p_name] = p_value
                else:
                    try:
                        args[p_name] = json.loads(p_value)
                    except (json.JSONDecodeError, ValueError):
                        args[p_name] = p_value
            calls.append(
                {
                    "id": f"dsml-{_uuid.uuid4().hex[:8]}",
                    "name": fn_name,
                    "arguments": json.dumps(args),
                }
            )
    return calls


class DeepSeekError(Exception):
    pass


class DeepSeekClient:
    def __init__(self, settings: Settings) -> None:
        self._client = AsyncOpenAI(
            api_key=settings.DEEPSEEK_API_KEY,
            base_url=settings.DEEPSEEK_API_BASE_URL,
        )
        self._model = settings.DEEPSEEK_CHAT_MODEL
        self._max_tokens = settings.DEEPSEEK_MAX_TOKENS
        self._temperature = settings.DEEPSEEK_TEMPERATURE

    async def chat_completion(
        self,
        messages: list[dict],
        tools: list[dict] | None = None,
    ) -> dict:
        """
        Returns a dict with keys: content (str|None), tool_calls (list[dict]).
        Each tool_call has: id, name, arguments (raw JSON string).
        Raises DeepSeekError on any failure.
        """
        try:
            kwargs: dict = dict(
                model=self._model,
                messages=messages,
                max_tokens=self._max_tokens,
                temperature=self._temperature,
            )
            if tools:
                kwargs["tools"] = tools
                kwargs["tool_choice"] = "auto"

            response = await self._client.chat.completions.create(**kwargs)
            choice = response.choices[0]

            # Parse structured tool calls from SDK response
            tool_calls = []
            for tc in choice.message.tool_calls or []:
                tool_calls.append(
                    {
                        "id": tc.id,
                        "name": tc.function.name,
                        "arguments": tc.function.arguments,
                    }
                )

            raw_content: str = choice.message.content or ""

            # Fallback: if DeepSeek emitted DSML in content instead of tool_calls, parse it
            if not tool_calls and _DSML_BLOCK_RE.search(raw_content):
                parsed = _parse_dsml_tool_calls(raw_content)
                if parsed:
                    tool_calls = parsed
                    logger.debug("dsml_tool_calls_parsed_from_content", count=len(parsed))

            # Always strip DSML markup so it never reaches the user
            clean_content: str | None = _DSML_BLOCK_RE.sub("", raw_content).strip() or None

            return {
                "content": clean_content,
                "tool_calls": tool_calls,
            }
        except OpenAIError as exc:
            logger.error("deepseek_api_error", detail=str(exc))
            raise DeepSeekError(f"DeepSeek API error: {exc}") from exc
