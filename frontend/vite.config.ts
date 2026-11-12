import { defineConfig, loadEnv, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

// Same-origin: the browser only talks to the Vite origin, so the backend needs no CORS config.
function proxyTo(backendUrl: string) {
  return {
    "/auth": { target: backendUrl, changeOrigin: true },
    "/v1": { target: backendUrl, changeOrigin: true },
  };
}

const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self'",
  "img-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

// Build only: the dev server needs inline scripts for React Fast Refresh, which this CSP would block.
function cspPlugin(): Plugin {
  return {
    name: "inject-csp",
    apply: "build",
    transformIndexHtml() {
      return [
        {
          tag: "meta",
          attrs: { "http-equiv": "Content-Security-Policy", content: CSP },
          injectTo: "head-prepend",
        },
      ];
    },
  };
}

export default defineConfig(({ mode }) => {
  // Empty prefix = also read non-VITE_ vars, so a plain BACKEND_URL env var works (no @types/node needed).
  const env = loadEnv(mode, ".", "");
  const proxy = proxyTo(env.BACKEND_URL || "http://localhost:8080");
  return {
    plugins: [react(), cspPlugin()],
    server: { proxy },
    preview: { proxy },
    build: {
      sourcemap: false,
      // Modern browsers don't need the preload polyfill, and it would add extra injected script code.
      modulePreload: { polyfill: false },
    },
  };
});
