# LinkPulse Frontend

React 19 + TypeScript + Vite single-page app for the `shortener-service` backend.

## Run

```bash
npm install
npm run dev        # http://localhost:5173, proxies /auth and /v1 to the backend
npm run build      # type-check + production build into dist/
npm run preview    # serve dist/ (also proxies /auth and /v1)
```

The backend defaults to `http://localhost:8080`. Override it with the `BACKEND_URL` environment variable:

```bash
BACKEND_URL=http://localhost:9090 npm run dev          # bash
$env:BACKEND_URL = "http://localhost:9090"; npm run dev # PowerShell
```

Start the backend first (`shortener-service`). To get an admin account, change a user's role to `ADMIN` in the database, then log in again.

Frontend routes: `/login`, `/register`, `/links`, `/admin`. They deliberately avoid the `/auth` and `/v1` prefixes, which belong to the API proxy.

Short links (`/{code}`) are served by the backend, not this app. The `shortUrl` returned by the API is used as-is.

## Security notes

- **Same-origin only.** The app calls relative URLs (`/v1/links`); Vite proxies them to the backend, so no CORS configuration is needed. In production, put both behind one origin (reverse proxy).
- **Token storage.** The JWT lives in React state and is mirrored to `sessionStorage` (never `localStorage`), so it is gone when the tab closes. Logout clears both. The tradeoff: any XSS could still read it, which is why the CSP and no-raw-HTML rules below matter.
- **Token payload is not trusted.** `auth/jwt.ts` decodes the payload (no signature check) only to show the email, show the Admin link and schedule auto-logout. The backend verifies every token and enforces roles; a non-admin who forces the admin UI still gets 403.
- **Auto-logout.** Any 401 from a protected call clears the session and redirects to `/login`. A timer also logs out when the token's `exp` passes. A 401 from `/auth/login` is just "bad credentials" and does not trigger this.
- **Rendering.** No `dangerouslySetInnerHTML`, `eval` or `innerHTML`. Server and user data is rendered as text, which React escapes.
- **Links.** `util/safeUrl.ts` allows only `http:` and `https:` hrefs; anything else (`javascript:`, `data:`) renders as plain text. External links use `target="_blank" rel="noopener noreferrer"`.
- **No secret logging.** Tokens and passwords are never logged, and password fields are cleared after submit.
- **CSP (production build only).** A Vite plugin injects a `<meta>` Content-Security-Policy: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'`. There are no inline scripts or `style` attributes. It is skipped in dev because React Fast Refresh needs inline scripts. Source maps are off and the modulepreload polyfill is disabled. If you deploy behind your own server, also send the CSP as an HTTP header (a meta tag cannot cover `frame-ancestors`).
- **Client validation is UX only.** It mirrors backend rules, but the backend's `message` from the error response is always what is shown.
- **No secrets in the bundle.** There are no keys or credentials in the frontend code.
