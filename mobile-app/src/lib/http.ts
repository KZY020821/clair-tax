import { aiServiceBaseUrl, backendApiBaseUrl } from "./backend-api";

const apiErrorShape = {
  message: "Unexpected API error",
};

export function buildAbsoluteFileUrl(fileUrl: string | null): string | null {
  if (!fileUrl) {
    return null;
  }

  return fileUrl.startsWith("http://") || fileUrl.startsWith("https://")
    ? fileUrl
    : `${backendApiBaseUrl}${fileUrl}`;
}

export async function getApiErrorMessage(
  response: Response,
  fallbackMessage: string,
): Promise<string> {
  try {
    const data = (await response.json()) as typeof apiErrorShape;
    if (typeof data?.message === "string" && data.message.trim() !== "") {
      return data.message;
    }
  } catch {
    return fallbackMessage;
  }

  return fallbackMessage;
}

export async function fetchJson<T>(
  url: string,
  parser: (value: unknown) => T,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(response, `Request failed (${response.status})`),
    );
  }

  const data: unknown = await response.json();
  return parser(data);
}

export function getBackendUrl(pathname: string): string {
  return `${backendApiBaseUrl}${pathname}`;
}

export function getAiServiceUrl(pathname: string): string {
  return `${aiServiceBaseUrl}${pathname}`;
}
