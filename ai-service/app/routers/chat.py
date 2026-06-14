import structlog
from fastapi import APIRouter, HTTPException

from app.models.chat import (
    ChatConfirmRequest,
    ChatConfirmResponse,
    ChatProcessRequest,
    ChatProcessResponse,
)
from app.services.chat_service import ChatService

router = APIRouter(prefix="/internal/chat", tags=["chat"])
logger = structlog.get_logger(__name__)

_chat_service: ChatService | None = None


def get_chat_service() -> ChatService:
    global _chat_service
    if _chat_service is None:
        _chat_service = ChatService()
    return _chat_service


@router.post("/process", response_model=ChatProcessResponse, response_model_by_alias=True)
async def process_message(request: ChatProcessRequest) -> ChatProcessResponse:
    """Called by Spring Boot ChatController with user_id already injected."""
    try:
        return await get_chat_service().process_message(request)
    except Exception as exc:
        logger.error("chat_process_unexpected", detail=str(exc))
        raise HTTPException(status_code=500, detail="Chat processing failed")


@router.post("/confirm", response_model=ChatConfirmResponse, response_model_by_alias=True)
async def confirm_action(request: ChatConfirmRequest) -> ChatConfirmResponse:
    """Execute a previously proposed write tool after user confirmation."""
    try:
        return await get_chat_service().confirm_action(request)
    except Exception as exc:
        logger.error("chat_confirm_unexpected", detail=str(exc))
        raise HTTPException(status_code=500, detail="Confirmation failed")
