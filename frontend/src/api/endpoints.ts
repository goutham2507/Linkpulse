import { request } from "./client";
import type {
  CreateLinkRequest,
  CreateLinkResponse,
  LinkSummary,
  LoginRequest,
  LoginResponse,
  PageResponse,
  RegisterRequest,
  RegisterResponse,
} from "./types";

export function register(req: RegisterRequest): Promise<RegisterResponse> {
  return request("/auth/register", { method: "POST", body: req, auth: false });
}

export function login(req: LoginRequest): Promise<LoginResponse> {
  return request("/auth/login", { method: "POST", body: req, auth: false });
}

export function createLink(req: CreateLinkRequest): Promise<CreateLinkResponse> {
  return request("/v1/links", { method: "POST", body: req });
}

export function listMyLinks(page: number, size: number): Promise<PageResponse<LinkSummary>> {
  return request(`/v1/links?page=${page}&size=${size}`);
}

export function deleteLink(code: string): Promise<void> {
  return request(`/v1/links/${encodeURIComponent(code)}`, { method: "DELETE" });
}

export function disableLink(code: string): Promise<void> {
  return request(`/v1/admin/links/${encodeURIComponent(code)}/disable`, { method: "PATCH" });
}

export function enableLink(code: string): Promise<void> {
  return request(`/v1/admin/links/${encodeURIComponent(code)}/enable`, { method: "PATCH" });
}
