import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient, ApiError } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';

describe('apiClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.getState().clearAuth();
  });

  it('unwraps success envelope and returns data and meta', async () => {
    const mockData = { id: 1001, name: 'Takeo Hol Silk Sampot' };
    const mockMeta = { page: 1, limit: 20, total: 1, totalPages: 1 };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: (header: string) => (header === 'content-type' ? 'application/json' : null),
      },
      json: async () => ({
        success: true,
        data: mockData,
        meta: mockMeta,
      }),
    });

    const res = await apiClient<typeof mockData>('/products/1001');
    expect(res.data).toEqual(mockData);
    expect(res.meta).toEqual(mockMeta);
  });

  it('throws ApiError with error details on failure', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      headers: {
        get: (header: string) => (header === 'content-type' ? 'application/json' : null),
      },
      json: async () => ({
        success: false,
        error: {
          code: 'NOT_FOUND',
          message: 'Product not found',
        },
      }),
    });

    await expect(apiClient('/products/99999')).rejects.toThrow(ApiError);
    try {
      await apiClient('/products/99999');
    } catch (err: any) {
      expect(err.code).toBe('NOT_FOUND');
      expect(err.status).toBe(404);
      expect(err.message).toBe('Product not found');
    }
  });

  it('parallel 401 TOKEN_EXPIRED requests trigger exactly ONE refresh call, then retry', async () => {
    useAuthStore.getState().setAuth('expired-token', {
      id: 1,
      email: 'test@example.com',
      fullName: 'Test User',
      role: 'CUSTOMER',
      isActive: true,
      emailVerified: true,
      createdAt: new Date().toISOString(),
    });

    let refreshCallCount = 0;
    const fetchMock = vi.fn().mockImplementation(async (url: string, init?: RequestInit) => {
      // 1. Initial requests return 401 TOKEN_EXPIRED
      if (url.includes('/users/me') && init?.headers && (init.headers as any)['Authorization'] === 'Bearer expired-token') {
        return {
          ok: false,
          status: 401,
          clone: () => ({
            json: async () => ({
              success: false,
              error: { code: 'TOKEN_EXPIRED', message: 'Token expired' },
            }),
          }),
          headers: { get: () => 'application/json' },
          json: async () => ({
            success: false,
            error: { code: 'TOKEN_EXPIRED', message: 'Token expired' },
          }),
        };
      }

      // 2. Refresh endpoint
      if (url.includes('/auth/refresh')) {
        refreshCallCount++;
        // Small delay to simulate network call so parallel requests collide
        await new Promise((r) => setTimeout(r, 20));
        return {
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({
            success: true,
            data: {
              accessToken: 'new-valid-token',
              user: { id: 1, email: 'test@example.com', fullName: 'Test User', role: 'CUSTOMER' },
            },
          }),
        };
      }

      // 3. Retried requests with new token
      if (url.includes('/users/me') && init?.headers && (init.headers as any)['Authorization'] === 'Bearer new-valid-token') {
        return {
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({
            success: true,
            data: { id: 1, fullName: 'Test User' },
          }),
        };
      }

      throw new Error(`Unexpected request to: ${url}`);
    });

    global.fetch = fetchMock;

    // Fire parallel requests
    const [res1, res2] = await Promise.all([
      apiClient<{ id: number; fullName: string }>('/users/me'),
      apiClient<{ id: number; fullName: string }>('/users/me'),
    ]);

    expect(refreshCallCount).toBe(1);
    expect(res1.data.fullName).toBe('Test User');
    expect(res2.data.fullName).toBe('Test User');
    expect(useAuthStore.getState().token).toBe('new-valid-token');
  });

  it('refresh failure clears auth and throws ApiError', async () => {
    useAuthStore.getState().setAuth('expired-token', {
      id: 1,
      email: 'test@example.com',
      fullName: 'Test User',
      role: 'CUSTOMER',
      isActive: true,
      emailVerified: true,
      createdAt: new Date().toISOString(),
    });

    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/auth/refresh')) {
        return {
          ok: false,
          status: 401,
          headers: { get: () => 'application/json' },
          json: async () => ({
            success: false,
            error: { code: 'INVALID_REFRESH_TOKEN', message: 'Refresh token invalid' },
          }),
        };
      }

      return {
        ok: false,
        status: 401,
        clone: () => ({
          json: async () => ({
            success: false,
            error: { code: 'TOKEN_EXPIRED', message: 'Token expired' },
          }),
        }),
        headers: { get: () => 'application/json' },
        json: async () => ({
          success: false,
          error: { code: 'TOKEN_EXPIRED', message: 'Token expired' },
        }),
      };
    });

    global.fetch = fetchMock;

    await expect(apiClient('/users/me')).rejects.toThrow(ApiError);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });
});
