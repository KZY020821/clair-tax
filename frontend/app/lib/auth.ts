import { z } from "zod";
import { backendFetch } from "./backend-api";

const apiErrorSchema = z.object({
  message: z.string().min(1),
});

const magicLinkRequestResponseSchema = z.object({
  message: z.string().min(1),
  debugVerifyUrl: z.string().url().nullable().optional(),
});

const authSessionSchema = z.object({
  authenticated: z.boolean(),
  id: z.string().uuid().nullable(),
  email: z.string().email().nullable(),
  mode: z.string().min(1),
});

const AUTH_SYNC_CHANNEL = "clair-tax-auth";
const AUTH_SYNC_STORAGE_KEY = "clair-tax-auth-sync";

export const authSessionQueryKey = ["auth-session"] as const;

export type AuthSession = z.infer<typeof authSessionSchema>;
export type MagicLinkRequestResult = z.infer<typeof magicLinkRequestResponseSchema>;
export type AuthSyncEvent = {
  type: "signed-in" | "signed-out";
  timestamp: number;
};

async function getApiErrorMessage(
  response: Response,
  fallbackMessage: string,
): Promise<string> {
  try {
    const data: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(data);

    if (parsed.success) {
      return parsed.data.message;
    }
  } catch {
    return fallbackMessage;
  }

  return fallbackMessage;
}

export async function fetchAuthSession(): Promise<AuthSession> {
  const response = await backendFetch("/api/auth/session", {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load the auth session (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();
  return authSessionSchema.parse(data);
}

export async function requestMagicLink(email: string): Promise<MagicLinkRequestResult> {
  const response = await backendFetch("/api/auth/magic-link/request", {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ email }),
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to send the sign-in email (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();
  return magicLinkRequestResponseSchema.parse(data);
}

export async function logoutCurrentSession(): Promise<void> {
  const response = await backendFetch("/api/auth/logout", {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to sign out (${response.status})`,
      ),
    );
  }
}

export function broadcastAuthEvent(type: AuthSyncEvent["type"]): void {
  if (typeof window === "undefined") {
    return;
  }

  const payload = JSON.stringify({
    type,
    timestamp: Date.now(),
  } satisfies AuthSyncEvent);

  if ("BroadcastChannel" in window) {
    const channel = new BroadcastChannel(AUTH_SYNC_CHANNEL);
    channel.postMessage(payload);
    channel.close();
  }

  window.localStorage.setItem(AUTH_SYNC_STORAGE_KEY, payload);
}

export function subscribeToAuthEvents(
  onEvent: (event: AuthSyncEvent) => void,
): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }

  const handlePayload = (payload: unknown) => {
    if (typeof payload !== "string") {
      return;
    }

    try {
      const parsed = JSON.parse(payload) as AuthSyncEvent;
      if (
        (parsed.type === "signed-in" || parsed.type === "signed-out") &&
        typeof parsed.timestamp === "number"
      ) {
        onEvent(parsed);
      }
    } catch {
      // Ignore malformed cross-tab payloads.
    }
  };

  const handleStorage = (event: StorageEvent) => {
    if (event.key === AUTH_SYNC_STORAGE_KEY && event.newValue) {
      handlePayload(event.newValue);
    }
  };

  const channel =
    "BroadcastChannel" in window
      ? new BroadcastChannel(AUTH_SYNC_CHANNEL)
      : null;

  channel?.addEventListener("message", (event) => handlePayload(event.data));
  window.addEventListener("storage", handleStorage);

  return () => {
    channel?.close();
    window.removeEventListener("storage", handleStorage);
  };
}
