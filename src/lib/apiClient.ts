import { ApiEnvelope, PaginationMeta } from "@/types/api";
import { useAuthStore } from "./store/useAuthStore";

export class ApiError extends Error {
  code: string;
  details?: Record<string, any>;
  status: number;

  constructor(code: string, message: string, status: number, details?: Record<string, any>) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: any;
  query?: Record<string, string | number | boolean | undefined | null>;
  auth?: boolean;
}

export interface ApiResponse<T> {
  data: T;
  meta?: PaginationMeta;
  facets?: any;
}

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:4000/api";

// Shared promise to prevent multiple refresh calls simultaneously
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (!res.ok) {
        useAuthStore.getState().clearAuth();
        return null;
      }

      const json: ApiEnvelope<{ accessToken: string; user: any }> = await res.json();
      if (json.success && json.data?.accessToken) {
        useAuthStore.getState().setAuth(json.data.accessToken, json.data.user);
        return json.data.accessToken;
      } else {
        useAuthStore.getState().clearAuth();
        return null;
      }
    } catch {
      useAuthStore.getState().clearAuth();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function apiClient<T>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResponse<T>> {
  const { method = "GET", body, query, headers = {}, auth = true, ...rest } = options;

  let url = path.startsWith("http") ? path : `${BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;

  if (query) {
    const searchParams = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") {
        searchParams.append(k, String(v));
      }
    });
    const qs = searchParams.toString();
    if (qs) {
      url += (url.includes("?") ? "&" : "?") + qs;
    }
  }

  const reqHeaders: Record<string, string> = {
    ...((headers as Record<string, string>) || {}),
  };

  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (!isFormData && body && !reqHeaders["Content-Type"]) {
    reqHeaders["Content-Type"] = "application/json";
  }

  // If request requires auth and session is not yet initialized in browser, wait briefly for AuthProvider
  if (process.env.NODE_ENV !== "test" && auth && typeof window !== "undefined" && !useAuthStore.getState().isInitialized) {
    await new Promise<void>((resolve) => {
      if (useAuthStore.getState().isInitialized) return resolve();
      const unsub = useAuthStore.subscribe((state) => {
        if (state.isInitialized) {
          unsub();
          resolve();
        }
      });
      setTimeout(() => {
        try {
          unsub();
        } catch {}
        resolve();
      }, 1500);
    });
  }

  const token = useAuthStore.getState().token;
  if (auth && token && !reqHeaders["Authorization"]) {
    reqHeaders["Authorization"] = `Bearer ${token}`;
  }

  const fetchOptions: RequestInit = {
    method,
    headers: reqHeaders,
    credentials: "include",
    body: isFormData ? body : body ? JSON.stringify(body) : undefined,
    ...rest,
  };

  let response: Response;
  try {
    response = await fetch(url, fetchOptions);
  } catch (err: any) {
    throw new ApiError(
      "NETWORK_ERROR",
      err?.message || "Failed to connect to server",
      0
    );
  }

  // Handle Token Expiry & Automatic Refresh Retry
  const isAuthRoute = path.includes('/auth/login') || path.includes('/auth/refresh') || path.includes('/auth/register');
  if (response.status === 401 && !isAuthRoute) {
    let errorJson: any = null;
    try {
      const clone = response.clone();
      errorJson = await clone.json();
    } catch {
      // ignore
    }

    if (errorJson?.error?.code === "TOKEN_EXPIRED" || response.status === 401) {
      const newToken = await refreshAccessToken();
      if (newToken) {
        reqHeaders["Authorization"] = `Bearer ${newToken}`;
        const retryResponse = await fetch(url, {
          ...fetchOptions,
          headers: reqHeaders,
        });
        return handleResponse<T>(retryResponse);
      } else {
        useAuthStore.getState().clearAuth();
      }
    }
  }

  return handleResponse<T>(response);
}

async function handleResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const contentType = response.headers.get("content-type");
  const isJson = contentType && contentType.includes("application/json");

  if (!isJson) {
    if (!response.ok) {
      throw new ApiError("INTERNAL_ERROR", `HTTP Error ${response.status}`, response.status);
    }
    return { data: (await response.text()) as unknown as T };
  }

  const envelope: ApiEnvelope<T> = await response.json();

  if (!response.ok || !envelope.success) {
    const errorData = envelope.error || {
      code: `HTTP_${response.status}`,
      message: response.statusText || "Request failed",
    };
    throw new ApiError(
      errorData.code,
      errorData.message,
      response.status,
      errorData.details
    );
  }

  return {
    data: envelope.data,
    meta: envelope.meta,
    facets: (envelope as any).facets,
  };
}
