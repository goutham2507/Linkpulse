import { useState } from "react";
import type { FormEvent } from "react";
import { disableLink, enableLink } from "../api/endpoints";
import ErrorBanner from "../components/ErrorBanner";

export default function AdminPage() {
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function run(action: "disable" | "enable") {
    const trimmed = code.trim();
    setError(null);
    setSuccess(null);
    if (!trimmed) {
      setError("Enter a link code.");
      return;
    }
    setBusy(true);
    try {
      await (action === "disable" ? disableLink(trimmed) : enableLink(trimmed));
      setSuccess(`Link "${trimmed}" ${action}d.`);
    } catch (err) {
      // A 403 here means the backend rejected our role, whatever the UI believed
      setError(err instanceof Error ? err.message : "Request failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <h1>Admin</h1>
      <ErrorBanner message={error} />
      {success && <div className="banner banner-success">{success}</div>}
      <form className="form" onSubmit={(e: FormEvent) => e.preventDefault()}>
        <label>
          Link code
          <input value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        <div className="row">
          <button type="button" disabled={busy} onClick={() => run("disable")}>
            Disable
          </button>
          <button type="button" disabled={busy} onClick={() => run("enable")}>
            Enable
          </button>
        </div>
      </form>
    </section>
  );
}
