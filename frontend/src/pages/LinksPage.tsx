import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { createLink, deleteLink, listMyLinks } from "../api/endpoints";
import type { CreateLinkResponse, LinkSummary, PageResponse } from "../api/types";
import ErrorBanner from "../components/ErrorBanner";
import ExternalLink from "../components/ExternalLink";

const PAGE_SIZE = 10;
const ALIAS_PATTERN = /^[a-zA-Z0-9_-]{4,20}$/;

function CreateLinkForm({ onCreated }: { onCreated: () => void }) {
  const [longUrl, setLongUrl] = useState("");
  const [alias, setAlias] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreateLinkResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCreated(null);
    setCopied(false);
    // Mirrors backend rules for UX only; the backend validates again.
    if (!longUrl.trim() || longUrl.length > 2048) {
      setError("Long URL is required (max 2048 characters).");
      return;
    }
    if (alias && !ALIAS_PATTERN.test(alias)) {
      setError("Alias must be 4-20 chars: letters, digits, _ or -");
      return;
    }
    setBusy(true);
    try {
      const res = await createLink({
        longUrl: longUrl.trim(),
        alias: alias || undefined,
        // datetime-local gives "YYYY-MM-DDTHH:mm"; backend wants seconds too
        expiresAt: expiresAt ? `${expiresAt}:00` : undefined,
      });
      setCreated(res);
      setLongUrl("");
      setAlias("");
      setExpiresAt("");
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create link.");
    } finally {
      setBusy(false);
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setError("Could not copy to clipboard.");
    }
  }

  return (
    <section>
      <h2>Create a link</h2>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit} className="form">
        <label>
          Long URL
          <input value={longUrl} onChange={(e) => setLongUrl(e.target.value)} maxLength={2048} required />
        </label>
        <label>
          Alias (optional, 4-20 chars)
          <input value={alias} onChange={(e) => setAlias(e.target.value)} maxLength={20} />
        </label>
        <label>
          Expires at (optional)
          <input type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create"}
        </button>
      </form>
      {created && (
        <div className="banner banner-success">
          Created: <ExternalLink url={created.shortUrl} />{" "}
          <button type="button" onClick={() => copy(created.shortUrl)}>
            {copied ? "Copied!" : "Copy"}
          </button>
        </div>
      )}
    </section>
  );
}

function LinksTable({ links, onDelete }: { links: LinkSummary[]; onDelete: (code: string) => void }) {
  if (links.length === 0) return <p>No links yet.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>Short URL</th>
            <th>Long URL</th>
            <th>Created</th>
            <th>Expires</th>
            <th>Active</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {links.map((l) => (
            <tr key={l.code}>
              <td>{l.code}</td>
              <td>
                <ExternalLink url={l.shortUrl} />
              </td>
              <td className="cell-long">
                <ExternalLink url={l.longUrl} />
              </td>
              <td>{l.createdAt}</td>
              <td>{l.expiresAt ?? "—"}</td>
              <td>{l.active ? "Yes" : "No"}</td>
              <td>
                <button type="button" onClick={() => onDelete(l.code)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function LinksPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageResponse<LinkSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (p: number) => {
    try {
      const res = await listMyLinks(p, PAGE_SIZE);
      setData(res);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load links.");
    }
  }, []);

  useEffect(() => {
    void load(page);
  }, [page, load]);

  async function onDelete(code: string) {
    if (!window.confirm(`Delete link "${code}"?`)) return;
    try {
      await deleteLink(code);
      // Step back if we just removed the last row of a later page
      if (data && data.content.length === 1 && page > 0) setPage(page - 1);
      else await load(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete link.");
    }
  }

  return (
    <>
      <h1>My Links</h1>
      <CreateLinkForm onCreated={() => (page === 0 ? load(0) : setPage(0))} />
      <h2>Your links</h2>
      <ErrorBanner message={error} />
      {data && <LinksTable links={data.content} onDelete={onDelete} />}
      {data && (
        <div className="row pager">
          <button type="button" disabled={page <= 0} onClick={() => setPage(page - 1)}>
            Prev
          </button>
          <span>
            Page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages}
          </span>
          <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
            Next
          </button>
        </div>
      )}
    </>
  );
}
