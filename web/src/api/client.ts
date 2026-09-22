import type { ApiResponse } from './types';

export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number,
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type TokenProvider = () => Promise<string | null>;

let getToken: TokenProvider = async () => null;

export function configureApi(provider: TokenProvider): void {
  getToken = provider;
}

/** `Accept: application/json` on the messages endpoint gets the plain JSON `TurnResponse`
 * instead of the SSE stream `ConversationController` also serves on the same path — content
 * negotiation, not a different route (see `guideline/api/mi-assistant-service-api.md`). */
async function request<T>(method: string, path: string, body?: unknown): Promise<ApiResponse<T>> {
  const token = await getToken();
  const response = await fetch(`/api/v1${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      'Accept-Language': 'vi',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return { success: true, data: null, meta: null, error: null, requestId: null, timestamp: '' };
  }

  let payload: ApiResponse<T> | null = null;
  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    payload = null;
  }

  if (!response.ok || payload?.success === false) {
    const err = payload?.error;
    throw new ApiError(
      err?.code ?? `HTTP-${response.status}`,
      err?.message ?? `Yêu cầu thất bại (${response.status}).`,
      response.status,
      err?.details ?? {},
    );
  }
  if (!payload) throw new ApiError('CM-999', 'Phản hồi rỗng từ máy chủ.', response.status);
  return payload;
}

export const api = {
  async get<T>(path: string): Promise<T> {
    return (await request<T>('GET', path)).data as T;
  },
  async post<T>(path: string, body?: unknown): Promise<T> {
    return (await request<T>('POST', path, body ?? {})).data as T;
  },
  async patch<T>(path: string, body?: unknown): Promise<T> {
    return (await request<T>('PATCH', path, body ?? {})).data as T;
  },
  async del<T>(path: string): Promise<T> {
    return (await request<T>('DELETE', path)).data as T;
  },
};
