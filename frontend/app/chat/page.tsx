"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useQueryClient } from "@tanstack/react-query";
import {
  ALLOWED_ATTACHMENT_TYPES,
  CHAT_STORAGE_KEY,
  ChatApiError,
  MAX_ATTACHMENTS,
  MAX_ATTACHMENT_BYTES,
  type ChatAttachment,
  type ChatMessage,
  type PendingAction,
  confirmChatAction,
  sendChatMessage,
  uploadChatAttachment,
} from "../lib/chat-api";
import { subscribeToAuthEvents } from "../lib/auth";

const TOOLS_INVALIDATING_PROFILE = new Set(["update_profile"]);
const TOOLS_INVALIDATING_RECEIPTS = new Set(["assign_receipt_to_year"]);

type PendingFile = {
  id: string;
  file: File;
  status: "uploading" | "ready" | "error";
  url?: string;
  errorMsg?: string;
};

function errorMessage(err: unknown): string {
  if (err instanceof ChatApiError) {
    if (err.status === 401) {
      return "Your session has expired. Please sign out and sign in again to continue.";
    }
    if (err.status === 0) {
      return "Could not reach the server. Make sure the backend is running on port 8080.";
    }
    return `Something went wrong (${err.status}). Please try again.`;
  }
  return "Something went wrong. Please try again.";
}

const SUGGESTED_PROMPTS = [
  "What tax reliefs am I eligible for?",
  "How much can I claim for books and education?",
  "Show me a summary of my receipts",
  "What is my estimated tax for this year?",
];

function SendIcon() {
  return (
    <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
      />
    </svg>
  );
}

function PaperclipIcon() {
  return (
    <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"
      />
    </svg>
  );
}

function UserAvatar() {
  return (
    <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-brand-blue text-[11px] font-bold text-white">
      You
    </span>
  );
}

function AssistantAvatar() {
  return (
    <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-brand-blue-dark text-[11px] font-bold text-white">
      AI
    </span>
  );
}

function FileChip({
  pf,
  onRemove,
}: {
  pf: PendingFile;
  onRemove: (id: string) => void;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium ${
        pf.status === "error"
          ? "border-red-200 bg-red-50 text-red-700"
          : pf.status === "uploading"
            ? "border-brand-line bg-brand-ice text-brand-muted"
            : "border-brand-blue/30 bg-brand-blue/10 text-brand-blue"
      }`}
      title={pf.errorMsg}
    >
      {pf.status === "uploading" && (
        <span className="h-3 w-3 animate-spin rounded-full border border-brand-muted border-t-transparent" />
      )}
      {pf.status === "ready" && (
        <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
        </svg>
      )}
      {pf.status === "error" && (
        <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
        </svg>
      )}
      <span className="max-w-[140px] truncate">{pf.file.name}</span>
      <button
        type="button"
        onClick={() => onRemove(pf.id)}
        className="ml-0.5 rounded-full p-0.5 transition hover:bg-black/10"
        aria-label={`Remove ${pf.file.name}`}
      >
        <svg className="h-2.5 w-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </span>
  );
}

function AttachmentChips({ urls }: { urls: string[] }) {
  if (urls.length === 0) return null;
  return (
    <div className="mt-1.5 flex flex-wrap gap-1.5">
      {urls.map((url) => {
        const name = decodeURIComponent(url.split("?")[0].split("/").pop() ?? "file").replace(/^[^-]+-/, "");
        const isImage = /\.(jpg|jpeg|png|gif|webp)$/i.test(url.split("?")[0]);
        return (
          <a
            key={url}
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 rounded-full border border-brand-line bg-brand-ice px-2.5 py-0.5 text-[11px] text-brand-muted transition hover:text-brand-blue"
          >
            {isImage ? (
              <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            ) : (
              <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            )}
            <span className="max-w-[100px] truncate">{name}</span>
          </a>
        );
      })}
    </div>
  );
}

export default function ChatPage() {
  const queryClient = useQueryClient();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<PendingFile[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(CHAT_STORAGE_KEY);
      if (stored) setMessages(JSON.parse(stored) as ChatMessage[]);
    } catch {
      // ignore
    }
  }, []);

  const persistMessages = useCallback((next: ChatMessage[]) => {
    try {
      sessionStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // ignore
    }
    setMessages(next);
  }, []);

  useEffect(() => {
    return subscribeToAuthEvents((event) => {
      if (event.type === "signed-out") {
        sessionStorage.removeItem(CHAT_STORAGE_KEY);
        setMessages([]);
        setPendingAction(null);
        setPendingFiles([]);
      }
    });
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [input]);

  const handleFiles = (files: FileList | null) => {
    if (!files) return;
    const existing = pendingFiles.length;
    const allowed = MAX_ATTACHMENTS - existing;
    if (allowed <= 0) return;

    const toAdd = Array.from(files).slice(0, allowed);
    const newPending: PendingFile[] = toAdd.map((f) => ({
      id: crypto.randomUUID(),
      file: f,
      status: "uploading" as const,
    }));

    // Validate client-side before adding
    const valid: PendingFile[] = [];
    const invalid: PendingFile[] = [];
    for (const pf of newPending) {
      if (pf.file.size > MAX_ATTACHMENT_BYTES) {
        invalid.push({ ...pf, status: "error", errorMsg: "Exceeds 20 MB limit" });
      } else if (!ALLOWED_ATTACHMENT_TYPES.has(pf.file.type)) {
        invalid.push({ ...pf, status: "error", errorMsg: "Unsupported file type" });
      } else {
        valid.push(pf);
      }
    }

    setPendingFiles((prev) => [...prev, ...valid, ...invalid]);

    // Upload valid files in parallel
    for (const pf of valid) {
      void uploadChatAttachment(pf.file)
        .then((result: ChatAttachment) => {
          setPendingFiles((prev) =>
            prev.map((p) => (p.id === pf.id ? { ...p, status: "ready", url: result.url } : p)),
          );
        })
        .catch(() => {
          setPendingFiles((prev) =>
            prev.map((p) =>
              p.id === pf.id ? { ...p, status: "error", errorMsg: "Upload failed" } : p,
            ),
          );
        });
    }
  };

  const removeFile = (id: string) => {
    setPendingFiles((prev) => prev.filter((p) => p.id !== id));
  };

  const isAnyUploading = pendingFiles.some((p) => p.status === "uploading");

  const handleSend = async (text?: string) => {
    const trimmed = (text ?? input).trim();
    if (!trimmed || isLoading || isAnyUploading) return;

    const readyUrls = pendingFiles.filter((p) => p.status === "ready").map((p) => p.url!);

    const userMessage: ChatMessage = {
      role: "user",
      content: trimmed,
      ...(readyUrls.length > 0 ? { attachmentUrls: readyUrls } : {}),
    };
    const nextMessages = [...messages, userMessage];
    persistMessages(nextMessages);
    setInput("");
    setPendingFiles([]);
    setIsLoading(true);
    setPendingAction(null);

    try {
      const response = await sendChatMessage(trimmed, messages, readyUrls.length > 0 ? readyUrls : undefined);
      persistMessages([...nextMessages, { role: "assistant", content: response.reply }]);
      if (response.requiresConfirmation && response.pendingAction) {
        setPendingAction(response.pendingAction);
      }
    } catch (err) {
      const msg = errorMessage(err);
      persistMessages([...nextMessages, { role: "assistant", content: msg }]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirm = async () => {
    if (!pendingAction || isLoading) return;
    setIsLoading(true);
    const action = pendingAction;
    setPendingAction(null);

    try {
      const result = await confirmChatAction(action);
      persistMessages([...messages, { role: "assistant", content: result.reply }]);

      if (TOOLS_INVALIDATING_PROFILE.has(action.toolName)) {
        void queryClient.invalidateQueries({ queryKey: ["profile"] });
        void queryClient.invalidateQueries({ queryKey: ["user-year-workspace"] });
      }
      if (TOOLS_INVALIDATING_RECEIPTS.has(action.toolName)) {
        const year = action.toolArgs["year"] as number | undefined;
        void queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] });
        void queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] });
        void queryClient.invalidateQueries({ queryKey: ["user-years"] });
      }
    } catch (err) {
      persistMessages([
        ...messages,
        { role: "assistant", content: errorMessage(err) },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeny = () => {
    setPendingAction(null);
    persistMessages([
      ...messages,
      { role: "assistant", content: "Got it, I've cancelled that action." },
    ]);
  };

  const isEmpty = messages.length === 0;

  return (
    <div className="flex h-[calc(100vh-8rem)] flex-col">
      {/* Messages area */}
      <div className="flex-1 overflow-y-auto">
        {isEmpty ? (
          <div className="flex h-full flex-col items-center justify-center gap-6 px-4 py-12">
            <div className="text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-blue text-white">
                <svg className="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.8}
                    d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-3 3-3-3z"
                  />
                </svg>
              </div>
              <h1 className="text-xl font-semibold text-brand-black">Clair Tax Assistant</h1>
              <p className="mt-1.5 text-sm text-brand-muted">
                Ask me anything about your taxes, reliefs, or receipts.
              </p>
            </div>

            <div className="grid w-full max-w-xl grid-cols-1 gap-2 sm:grid-cols-2">
              {SUGGESTED_PROMPTS.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  onClick={() => void handleSend(prompt)}
                  className="rounded-card border border-brand-line bg-brand-white px-4 py-3 text-left text-sm text-brand-black transition hover:border-brand-blue hover:bg-brand-ice"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="mx-auto max-w-2xl space-y-6 px-4 py-6">
            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex items-end gap-3 ${msg.role === "user" ? "justify-end" : "justify-start"}`}
              >
                {msg.role === "assistant" && <AssistantAvatar />}
                <div
                  className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-6 ${
                    msg.role === "user"
                      ? "rounded-br-sm bg-brand-blue text-white"
                      : "rounded-bl-sm border border-brand-line bg-brand-white text-brand-black"
                  }`}
                >
                  {msg.role === "assistant" ? (
                    <div className="overflow-x-auto">
                      <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        components={{
                          p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                          strong: ({ children }) => (
                            <strong className="font-semibold">{children}</strong>
                          ),
                          ul: ({ children }) => (
                            <ul className="mb-2 ml-4 list-disc space-y-1">{children}</ul>
                          ),
                          ol: ({ children }) => (
                            <ol className="mb-2 ml-4 list-decimal space-y-1">{children}</ol>
                          ),
                          li: ({ children }) => <li>{children}</li>,
                          code: ({ children }) => (
                            <code className="rounded bg-brand-ice px-1 py-0.5 font-mono text-xs">
                              {children}
                            </code>
                          ),
                          table: ({ children }) => (
                            <table className="my-2 w-full border-collapse text-sm">{children}</table>
                          ),
                          thead: ({ children }) => <thead className="bg-brand-ice">{children}</thead>,
                          tbody: ({ children }) => <tbody>{children}</tbody>,
                          tr: ({ children }) => (
                            <tr className="border-b border-brand-line last:border-0">{children}</tr>
                          ),
                          th: ({ children }) => (
                            <th className="px-3 py-1.5 text-left font-semibold text-brand-black">{children}</th>
                          ),
                          td: ({ children }) => (
                            <td className="px-3 py-1.5 text-brand-black">{children}</td>
                          ),
                          h1: ({ children }) => <p className="mb-2 font-semibold text-brand-black">{children}</p>,
                          h2: ({ children }) => <p className="mb-2 font-semibold text-brand-black">{children}</p>,
                          h3: ({ children }) => <p className="mb-2 font-semibold text-brand-black">{children}</p>,
                        }}
                      >
                        {msg.content}
                      </ReactMarkdown>
                    </div>
                  ) : (
                    <>
                      {msg.content}
                      {msg.attachmentUrls && <AttachmentChips urls={msg.attachmentUrls} />}
                    </>
                  )}
                </div>
                {msg.role === "user" && <UserAvatar />}
              </div>
            ))}

            {isLoading && (
              <div className="flex items-end gap-3 justify-start">
                <AssistantAvatar />
                <div className="rounded-2xl rounded-tl-sm border border-brand-line bg-brand-white px-4 py-3">
                  <span className="flex gap-1">
                    <span className="h-2 w-2 animate-bounce rounded-full bg-brand-muted [animation-delay:-0.3s]" />
                    <span className="h-2 w-2 animate-bounce rounded-full bg-brand-muted [animation-delay:-0.15s]" />
                    <span className="h-2 w-2 animate-bounce rounded-full bg-brand-muted" />
                  </span>
                </div>
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Confirmation banner */}
      {pendingAction && !isLoading && (
        <div className="border-t border-brand-line bg-brand-ice px-4 py-3">
          <div className="mx-auto max-w-2xl space-y-2">
            <p className="text-sm font-medium text-brand-black">
              Confirm: {pendingAction.description}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => void handleConfirm()}
                className="app-button-primary py-1.5 text-xs"
              >
                Confirm
              </button>
              <button
                onClick={handleDeny}
                className="rounded-field border border-brand-line bg-white px-4 py-1.5 text-xs font-medium text-brand-muted transition hover:text-brand-black"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Input bar */}
      <div className="border-t border-brand-line bg-brand-white px-4 py-4">
        <div className="mx-auto max-w-2xl">
          {/* Pending file chips */}
          {pendingFiles.length > 0 && (
            <div className="mb-2 flex flex-wrap gap-1.5">
              {pendingFiles.map((pf) => (
                <FileChip key={pf.id} pf={pf} onRemove={removeFile} />
              ))}
            </div>
          )}

          <div className="flex items-end gap-2">
            {/* Hidden file input */}
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/jpeg,image/png,image/gif,image/webp,application/pdf"
              className="hidden"
              onChange={(e) => {
                handleFiles(e.target.files);
                e.target.value = "";
              }}
            />

            {/* Paperclip button */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={isLoading || pendingFiles.length >= MAX_ATTACHMENTS}
              title={
                pendingFiles.length >= MAX_ATTACHMENTS
                  ? `Maximum ${MAX_ATTACHMENTS} files`
                  : "Attach files (images or PDF, max 20 MB each)"
              }
              className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl border border-brand-line bg-brand-ice text-brand-muted transition hover:border-brand-blue hover:text-brand-blue disabled:cursor-not-allowed disabled:opacity-40"
            >
              <PaperclipIcon />
            </button>

            <textarea
              ref={textareaRef}
              rows={1}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  void handleSend();
                }
              }}
              placeholder="Ask about your taxes… (Shift+Enter for new line)"
              disabled={isLoading}
              className="flex-1 resize-none rounded-xl border border-brand-line bg-brand-ice px-4 py-3 text-sm text-brand-black placeholder:text-brand-muted focus:border-brand-blue focus:outline-none focus:ring-2 focus:ring-brand-blue/20 disabled:opacity-50"
            />
            <button
              onClick={() => void handleSend()}
              disabled={isLoading || isAnyUploading || !input.trim()}
              className="app-button-primary flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl p-0 disabled:opacity-40"
              aria-label="Send message"
            >
              <SendIcon />
            </button>
          </div>
          <p className="mt-2 text-center text-[11px] text-brand-muted">
            Clair Tax Assistant can make mistakes. Verify important tax information.
          </p>
        </div>
      </div>
    </div>
  );
}
