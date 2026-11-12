// Only http(s) URLs may become an href; javascript:, data:, etc. are rendered as plain text instead.
export function safeHref(url: string): string | null {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : null;
  } catch {
    return null;
  }
}
