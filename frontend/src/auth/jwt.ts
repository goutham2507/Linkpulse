// UX ONLY. This decodes the payload without verifying the signature, so nothing here is trusted.
// The backend re-validates every token and is the only real authority on identity and role.

export interface TokenInfo {
  email: string;
  role: string;
  expMs: number;
}

export function decodeToken(token: string): TokenInfo | null {
  try {
    const part = token.split(".")[1];
    if (!part) return null;
    // base64url -> base64, then restore padding
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (typeof payload !== "object" || payload === null) return null;
    const p = payload as Record<string, unknown>;
    if (typeof p.sub !== "string" || typeof p.exp !== "number") return null;
    return {
      email: p.sub,
      role: typeof p.role === "string" ? p.role : "USER",
      expMs: p.exp * 1000,
    };
  } catch {
    return null;
  }
}
