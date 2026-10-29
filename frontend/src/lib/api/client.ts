import { appConfig } from "@/config/app";
import { ErrorCodes } from "@/lib/api/error-codes";
import { resolveErrorMessage } from "@/lib/api/error-i18n";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId?: string,
    public readonly messageKey?: string,
  ) {
    super(message);
  }
}

export const identityChangedEvent = "studio:identity-changed";

export function userScopeHeaders(userId: string): Record<string, string> {
  return { "X-Studio-User": userId };
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Accept-Language"))
    headers.set("Accept-Language", appConfig.errorLocale);
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(
      0,
      ErrorCodes.REQUEST_FAILED,
      appConfig.requestFailureMessage,
    );
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    if (
      body?.code === ErrorCodes.IDENTITY_CHANGED &&
      typeof window !== "undefined"
    ) {
      window.dispatchEvent(new Event(identityChangedEvent));
    }
    const fallback =
      typeof body?.message === "string" && body.message.trim()
        ? body.message
        : appConfig.requestFailureMessage;
    const key =
      typeof body?.messageKey === "string" ? body.messageKey : undefined;
    const message = key
      ? await resolveErrorMessage(key, fallback, appConfig.errorLocale)
      : fallback;
    if (options.signal?.aborted)
      throw new DOMException("Request aborted", "AbortError");
    const requestId =
      typeof body?.requestId === "string" &&
      /^[A-Za-z0-9-]{1,128}$/.test(body.requestId)
        ? body.requestId
        : undefined;
    const displayMessage =
      response.status >= 500 && requestId
        ? appConfig.errorLocale === "en"
          ? `${message} (Request ID: ${requestId})`
          : `${message}（请求编号：${requestId}）`
        : message;
    throw new ApiError(
      response.status,
      typeof body?.code === "string" ? body.code : ErrorCodes.REQUEST_FAILED,
      displayMessage,
      requestId,
      key,
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
