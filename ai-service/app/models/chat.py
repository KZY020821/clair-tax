from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class _CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str
    attachment_urls: list[str] = []


class PendingAction(_CamelModel):
    tool_name: str
    tool_args: dict[str, Any]
    description: str


class ChatProcessRequest(_CamelModel):
    user_id: str
    message: str
    history: list[ChatMessage] = []
    attachment_urls: list[str] | None = None
    attachment_s3_keys: list[str] | None = None


class ChatProcessResponse(_CamelModel):
    reply: str
    pending_action: PendingAction | None = None
    requires_confirmation: bool = False


class ChatConfirmRequest(_CamelModel):
    user_id: str
    pending_action: PendingAction


class ChatConfirmResponse(BaseModel):
    reply: str
    success: bool
    error: str | None = None
