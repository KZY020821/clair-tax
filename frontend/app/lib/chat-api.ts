import { z } from "zod";
import { backendFetch } from "./backend-api";

const chatMessageSchema = z.object({
  role: z.enum(["user", "assistant"]),
  content: z.string(),
  attachmentUrls: z.array(z.string()).optional(),
});

const chatAttachmentSchema = z.object({
  url: z.string(),
  fileName: z.string(),
  mimeType: z.string(),
  fileSizeBytes: z.number(),
  s3Key: z.string(),
});

const pendingActionSchema = z.object({
  toolName: z.string(),
  toolArgs: z.record(z.string(), z.unknown()),
  description: z.string(),
});

const chatMessageResponseSchema = z.object({
  reply: z.string(),
  pendingAction: pendingActionSchema.nullable().optional(),
  requiresConfirmation: z.boolean(),
});

const chatConfirmResponseSchema = z.object({
  reply: z.string(),
  success: z.boolean(),
  error: z.string().nullable().optional(),
});

export type ChatMessage = z.infer<typeof chatMessageSchema>;
export type PendingAction = z.infer<typeof pendingActionSchema>;
export type ChatMessageResponse = z.infer<typeof chatMessageResponseSchema>;
export type ChatConfirmResponse = z.infer<typeof chatConfirmResponseSchema>;
export type ChatAttachment = z.infer<typeof chatAttachmentSchema>;

export const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024;
export const MAX_ATTACHMENTS = 5;
export const ALLOWED_ATTACHMENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "application/pdf",
]);

export const CHAT_STORAGE_KEY = "clair-tax-chat-history";

export class ChatApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ChatApiError";
  }
}

export async function uploadChatAttachment(file: File): Promise<ChatAttachment> {
  const form = new FormData();
  form.append("file", file);
  let response: Response;
  try {
    response = await backendFetch("/api/chat/attachments", { method: "POST", body: form });
  } catch {
    throw new ChatApiError("Network error uploading file", 0);
  }
  if (!response.ok) {
    throw new ChatApiError("File upload failed", response.status);
  }
  try {
    return chatAttachmentSchema.parse(await response.json());
  } catch {
    throw new ChatApiError("Unexpected response from server", response.status);
  }
}

export async function sendChatMessage(
  content: string,
  history: ChatMessage[],
  attachmentUrls?: string[],
  attachmentS3Keys?: string[],
): Promise<ChatMessageResponse> {
  let response: Response;
  try {
    response = await backendFetch("/api/chat/message", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        content,
        history: history.slice(-20),
        ...(attachmentUrls && attachmentUrls.length > 0 ? { attachmentUrls } : {}),
        ...(attachmentS3Keys && attachmentS3Keys.length > 0 ? { attachmentS3Keys } : {}),
      }),
    });
  } catch (err) {
    throw new ChatApiError("Network error — backend unreachable", 0);
  }

  if (!response.ok) {
    throw new ChatApiError(`Chat request failed`, response.status);
  }

  try {
    return chatMessageResponseSchema.parse(await response.json());
  } catch {
    throw new ChatApiError("Unexpected response format from server", response.status);
  }
}

export async function confirmChatAction(
  pendingAction: PendingAction,
): Promise<ChatConfirmResponse> {
  let response: Response;
  try {
    response = await backendFetch("/api/chat/confirm", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ pendingAction }),
    });
  } catch {
    throw new ChatApiError("Network error — backend unreachable", 0);
  }

  if (!response.ok) {
    throw new ChatApiError(`Confirm request failed`, response.status);
  }

  try {
    return chatConfirmResponseSchema.parse(await response.json());
  } catch {
    throw new ChatApiError("Unexpected response format from server", response.status);
  }
}
