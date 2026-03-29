export const backendApiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export function buildBackendUrl(pathOrUrl: string): string {
  if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
    return pathOrUrl;
  }

  return `${backendApiBaseUrl}${pathOrUrl}`;
}

export function backendFetch(
  pathOrUrl: string,
  init: RequestInit = {},
): Promise<Response> {
  return fetch(buildBackendUrl(pathOrUrl), {
    ...init,
    credentials: "include",
  });
}
