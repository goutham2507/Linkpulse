// Mirrors the backend DTOs. LocalDateTime values arrive as ISO strings without a zone.

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface RegisterResponse {
  id: number;
  email: string;
  role: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface CreateLinkRequest {
  longUrl: string;
  alias?: string;
  expiresAt?: string;
}

export interface CreateLinkResponse {
  code: string;
  shortUrl: string;
  longUrl: string;
}

export interface LinkSummary {
  code: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  expiresAt: string | null;
  active: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
