import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { register } from "../api/endpoints";
import ErrorBanner from "../components/ErrorBanner";

export default function RegisterPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    // Mirrors backend rules for UX only; the backend validates again.
    if (password.length < 8 || password.length > 72) {
      setError("Password must be 8-72 characters.");
      return;
    }
    setBusy(true);
    try {
      await register({ email: email.trim(), password });
      navigate("/login", { replace: true, state: { message: "Registration successful. Please log in." } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed.");
    } finally {
      setPassword("");
      setBusy(false);
    }
  }

  return (
    <section>
      <h1>Register</h1>
      <ErrorBanner message={error} />
      <form onSubmit={onSubmit} className="form">
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" maxLength={255} required />
        </label>
        <label>
          Password (8-72 characters)
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Registering…" : "Register"}
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </section>
  );
}
