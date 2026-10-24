import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient, ApiError } from '@/lib/apiClient';

describe('apiClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('unwraps success envelope and returns data and meta', async () => {
    const mockData = { id: 1001, name: 'Laptop Pro 15' };
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
});
