import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router";
import { configureClient } from "../api/client";
import { decodeToken, type TokenInfo } from "./jwt";

// sessionStorage (not localStorage): the token dies when the tab closes.
const STORAGE_KEY = "linkpulse.token";

interface AuthState {
  token: string | null;
  email: string | null;
  isAdmin: boolean;
  login: (token: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function loadStoredToken(): string | null {
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (!stored) return null;
    // Drop tokens that are garbled or already expired
    const info = decodeToken(stored);
    if (!info || info.expMs <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return stored;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [token, setToken] = useState<string | null>(loadStoredToken);

  const info: TokenInfo | null = useMemo(() => (token ? decodeToken(token) : null), [token]);

  const login = useCallback((newToken: string) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, newToken);
    } catch {
      // storage blocked: the in-memory token still works for this page load
    }
    setToken(newToken);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // nothing to clear
    }
  }, []);

  // Route all API calls through the current token; a 401 on a protected call ends the session.
  useEffect(() => {
    configureClient({
      getToken: () => token,
      onUnauthorized: () => {
        logout();
        navigate("/login", { replace: true });
      },
    });
  }, [token, logout, navigate]);

  // Auto-logout when the token expires (setTimeout max is ~24.8 days; tokens are far shorter).
  useEffect(() => {
    if (!info) return;
    const delay = Math.max(0, info.expMs - Date.now());
    const id = setTimeout(() => {
      logout();
      navigate("/login", { replace: true });
    }, delay);
    return () => clearTimeout(id);
  }, [info, logout, navigate]);

  const value: AuthState = {
    token,
    email: info?.email ?? null,
    // UX only: hides the nav link. The backend enforces ADMIN on every admin call.
    isAdmin: info?.role === "ADMIN",
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
