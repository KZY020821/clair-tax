"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useQueryClient } from "@tanstack/react-query";
import {
  ALLOWED_ATTACHMENT_TYPES,
  CHAT_STORAGE_KEY,
  ChatAttachment,
  ChatMessage,
  MAX_ATTACHMENT_BYTES,
  PendingAction,
  confirmChatAction,
  sendChatMessage,
  uploadChatAttachment,
} from "../lib/chat-api";
import { subscribeToAuthEvents } from "../lib/auth";

const TOOLS_INVALIDATING_PROFILE = new Set(["update_profile"]);
const TOOLS_INVALIDATING_RECEIPTS = new Set(["assign_receipt_to_year", "process_receipt_attachment"]);

export default function ChatWidget() {
  const queryClient = useQueryClient();
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [pendingAttachment, setPendingAttachment] = useState<ChatAttachment | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [attachmentError, setAttachmentError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Restore chat from sessionStorage on mount
  useEffect(() => {
    try {
      const stored = sessionStorage.getItem(CHAT_STORAGE_KEY);
      if (stored) {
        setMessages(JSON.parse(stored) as ChatMessage[]);
      }
    } catch {
      // Ignore errors (e.g. private mode)
    }
  }, []);

  // Persist to sessionStorage whenever messages change
  const persistMessages = useCallback((next: ChatMessage[]) => {
    try {
      sessionStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Ignore storage quota errors
    }
    setMessages(next);
  }, []);

  // Clear chat on signed-out event so a new login starts fresh
  useEffect(() => {
    return subscribeToAuthEvents((event) => {
      if (event.type === "signed-out") {
        sessionStorage.removeItem(CHAT_STORAGE_KEY);
        setMessages([]);
        setPendingAction(null);
        setPendingAttachment(null);
        setIsOpen(false);
      }
    });
  }, []);

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!e.target.files) return;
    // Reset input so the same file can be re-selected after removal
    e.target.value = "";
    if (!file) return;

    if (!ALLOWED_ATTACHMENT_TYPES.has(file.type)) {
      setAttachmentError("Only PDF, JPG, and PNG files are accepted.");
      return;
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      setAttachmentError("File must be smaller than 20 MB.");
      return;
    }

    setAttachmentError(null);
    setIsUploading(true);
    try {
      const attachment = await uploadChatAttachment(file);
      setPendingAttachment(attachment);
    } catch {
      setAttachmentError("File upload failed. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  // Auto-scroll to latest message
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || isLoading || isUploading) return;

    const attachment = pendingAttachment;
    const userMessage: ChatMessage = { role: "user", content: trimmed };
    const nextMessages = [...messages, userMessage];
    persistMessages(nextMessages);
    setInput("");
    setPendingAttachment(null);
    setAttachmentError(null);
    setIsLoading(true);
    setPendingAction(null);

    try {
      const attachmentUrls = attachment ? [attachment.url] : undefined;
      const attachmentS3Keys = attachment ? [attachment.s3Key] : undefined;
      const response = await sendChatMessage(trimmed, messages, attachmentUrls, attachmentS3Keys);
      const assistantMessage: ChatMessage = {
        role: "assistant",
        content: response.reply,
      };
      persistMessages([...nextMessages, assistantMessage]);
      if (response.requiresConfirmation && response.pendingAction) {
        setPendingAction(response.pendingAction);
      }
    } catch {
      persistMessages([
        ...nextMessages,
        { role: "assistant", content: "Sorry, I couldn't process that. Please try again." },
      ]);
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
      persistMessages([
        ...messages,
        { role: "assistant", content: result.reply },
      ]);

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
    } catch {
      persistMessages([
        ...messages,
        { role: "assistant", content: "The action could not be completed. Please try again." },
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

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end gap-3">
      {isOpen && (
        <div className="app-panel flex h-[32rem] w-80 flex-col overflow-hidden shadow-accent sm:w-96">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-brand-line px-4 py-3">
            <span className="text-sm font-semibold text-brand-black">
              Clair Tax Assistant
            </span>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="text-brand-muted transition hover:text-brand-black"
              aria-label="Close chat"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Messages */}
          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
            {messages.length === 0 && (
              <p className="mt-4 text-center text-xs text-brand-muted">
                Ask me about your tax reliefs, receipts, or profile.
              </p>
            )}
            {messages.map((msg, i) => (
              <div
                key={i}
                className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[80%] rounded-card px-3 py-2 text-sm leading-6 ${
                    msg.role === "user"
                      ? "bg-brand-blue text-white"
                      : "border border-brand-line bg-brand-ice text-brand-black"
                  }`}
                >
                  {msg.role === "assistant" ? (
                    <div className="overflow-x-auto">
                      <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        components={{
                          p: ({ children }) => <p className="mb-1 last:mb-0">{children}</p>,
                          strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
                          ul: ({ children }) => <ul className="mb-1 ml-4 list-disc space-y-0.5">{children}</ul>,
                          ol: ({ children }) => <ol className="mb-1 ml-4 list-decimal space-y-0.5">{children}</ol>,
                          li: ({ children }) => <li>{children}</li>,
                          code: ({ children }) => (
                            <code className="rounded bg-white/60 px-1 py-0.5 font-mono text-xs">{children}</code>
                          ),
                          table: ({ children }) => (
                            <table className="my-2 w-full border-collapse text-xs">{children}</table>
                          ),
                          thead: ({ children }) => <thead className="bg-brand-ice">{children}</thead>,
                          tbody: ({ children }) => <tbody>{children}</tbody>,
                          tr: ({ children }) => (
                            <tr className="border-b border-brand-line last:border-0">{children}</tr>
                          ),
                          th: ({ children }) => (
                            <th className="px-2 py-1 text-left font-semibold text-brand-black">{children}</th>
                          ),
                          td: ({ children }) => (
                            <td className="px-2 py-1 text-brand-black">{children}</td>
                          ),
                          h1: ({ children }) => <p className="mb-1 font-semibold text-brand-black">{children}</p>,
                          h2: ({ children }) => <p className="mb-1 font-semibold text-brand-black">{children}</p>,
                          h3: ({ children }) => <p className="mb-1 font-semibold text-brand-black">{children}</p>,
                        }}
                      >
                        {msg.content}
                      </ReactMarkdown>
                    </div>
                  ) : (
                    msg.content
                  )}
                </div>
              </div>
            ))}
            {isLoading && (
              <div className="flex justify-start">
                <div className="rounded-card border border-brand-line bg-brand-ice px-3 py-2 text-sm text-brand-muted">
                  Thinking…
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Confirmation banner */}
          {pendingAction && !isLoading && (
            <div className="space-y-2 border-t border-brand-line bg-brand-ice px-4 py-3">
              <p className="text-xs font-medium text-brand-black">
                Confirm: {pendingAction.description}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => void handleConfirm()}
                  className="app-button-primary flex-1 py-1.5 text-xs"
                >
                  Confirm
                </button>
                <button
                  onClick={handleDeny}
                  className="flex-1 rounded-field border border-brand-line bg-white py-1.5 text-xs font-medium text-brand-muted transition hover:text-brand-black"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Input */}
          <div className="border-t border-brand-line px-3 py-3 space-y-2">
            {/* Hidden file input */}
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              className="hidden"
              onChange={(e) => void handleFileSelect(e)}
            />

            {/* Attachment chip */}
            {pendingAttachment && (
              <div className="flex items-center gap-1.5 rounded-field border border-brand-line bg-brand-ice px-2 py-1 text-xs text-brand-black w-fit max-w-full">
                <svg className="h-3 w-3 shrink-0 text-brand-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
                </svg>
                <span className="truncate max-w-[180px]">{pendingAttachment.fileName}</span>
                <button
                  type="button"
                  onClick={() => setPendingAttachment(null)}
                  className="ml-0.5 shrink-0 text-brand-muted hover:text-brand-black"
                  aria-label="Remove attachment"
                >
                  <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            )}

            {/* Attachment error */}
            {attachmentError && (
              <p className="text-xs text-red-500">{attachmentError}</p>
            )}

            <div className="flex gap-2">
              {/* Attachment button */}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isLoading || isUploading || !!pendingAttachment}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-field border border-brand-line bg-white text-brand-muted transition hover:text-brand-black disabled:opacity-40"
                aria-label="Attach receipt"
              >
                {isUploading ? (
                  <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                ) : (
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
                  </svg>
                )}
              </button>

              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    void handleSend();
                  }
                }}
                placeholder="Ask about your taxes…"
                disabled={isLoading}
                className="flex-1 rounded-field border border-brand-line bg-white px-3 py-2 text-sm text-brand-black placeholder:text-brand-muted focus:border-brand-blue focus:outline-none disabled:opacity-50"
              />
              <button
                onClick={() => void handleSend()}
                disabled={isLoading || isUploading || !input.trim()}
                className="app-button-primary px-3 py-2 disabled:opacity-40"
                aria-label="Send message"
              >
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
                  />
                </svg>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toggle button */}
      <button
        type="button"
        onClick={() => setIsOpen((v) => !v)}
        className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-blue text-white shadow-accent transition hover:bg-brand-blue-dark"
        aria-label={isOpen ? "Close tax assistant" : "Open tax assistant"}
      >
        {isOpen ? (
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-3 3-3-3z"
            />
          </svg>
        )}
      </button>
    </div>
  );
}
