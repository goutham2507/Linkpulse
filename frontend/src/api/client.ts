import type { ErrorResponse } from "./types";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// Set by AuthContext so this module stays free of React.
let tokenProvider: () => string | null = () => null;
let unauthorizedHandler: () => void = () => {};

export function configureClient(opts: {
  getToken: () => string | null;
  onUnauthorized: () => void;
}): void {
  tokenProvider = opts.getToken;
  unauthorizedHandler = opts.onUnauthorized;
}

const GENERIC_ERROR = "Something went wrong. Please try again.";

async function readErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as Partial<ErrorResponse>;
    if (typeof body.message === "string" && body.message) return body.message;
  } catch {
    // body was not JSON (e.g. a proxy error page): use the fallback
  }
  return GENERIC_ERROR;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  // Public endpoints (login/register): never send a token, and a 401 here means bad credentials.
  auth?: boolean;
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = opts;
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const token = auth ? tokenProvider() : null;
  if (token) headers["Authorization"] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Cannot reach the server. Check your connection.");
  }

  if (!res.ok) {
    // Only a 401 on a protected call means our session is dead; the backend is the authority.
    if (res.status === 401 && auth) unauthorizedHandler();
    throw new ApiError(res.status, await readErrorMessage(res));
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
