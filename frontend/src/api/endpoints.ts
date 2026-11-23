import { request } from "./client";
import type {
  CreateLinkRequest,
  CreateLinkResponse,
  LinkSummary,
  LoginRequest,
  LinkStats,
  LoginResponse,
  PageResponse,
  RegisterRequest,
  RegisterResponse,
  ServiceName,
  ServiceState,
  StatsSummary,
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

export function getLinkStats(code: string, days: number): Promise<LinkStats> {
  return request(`/v1/stats/${encodeURIComponent(code)}?days=${days}`);
}

export function getStatsSummary(days: number): Promise<StatsSummary> {
  return request(`/v1/stats/summary?days=${days}`);
}

// Public probe of a service's actuator health. Never throws: a dead service is a normal answer here.
export async function getServiceHealth(service: ServiceName): Promise<ServiceState> {
  try {
    const res = await fetch(`/svc-health/${service}`);
    const body = (await res.json()) as { status?: string };
    if (body.status === "UP") return "UP";
    return body.status === "DOWN" ? "DOWN" : "UNREACHABLE";
  } catch {
    // network error, or the proxy answered with a non-JSON error page
    return "UNREACHABLE";
  }
}
