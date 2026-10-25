import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useKhqrPayment } from '@/hooks/useKhqrPayment';
import { apiClient } from '@/lib/apiClient';

vi.mock('@/lib/apiClient', () => ({
  apiClient: vi.fn(),
}));

class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  listeners: Record<string, ((event: any) => void)[]> = {};
  closed = false;
  onerror: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    this.listeners = {};
    MockEventSource.instances.push(this);
  }

  addEventListener(event: string, handler: (event: any) => void) {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }
    this.listeners[event].push(handler);
  }

  emit(event: string, data: any) {
    const handlers = this.listeners[event] || [];
    for (const h of handlers) {
      h({ data: typeof data === 'string' ? data : JSON.stringify(data) });
    }
  }

  close() {
    this.closed = true;
  }
}

describe('useKhqrPayment Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    MockEventSource.instances = [];
    (globalThis as any).EventSource = MockEventSource;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('initializes in PENDING, connects to EventSource, transitions to PAID on status event, and triggers onPaid callback', async () => {
    const mockKhqrData = {
      paymentId: 101,
      orderNumber: 'KM-TEST-001',
      status: 'PENDING',
      method: 'KHQR',
      currency: 'USD',
      amount: 50.0,
      amountCents: 5000,
      qrString: 'sample-qr-string',
      qrImageDataUrl: 'data:image/png;base64,...',
      md5: 'mock-md5',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      merchantName: 'KhmerMart',
      mode: 'MOCK',
      streamToken: 'stream-token-123',
    };

    (apiClient as any).mockResolvedValueOnce({
      data: mockKhqrData,
    });

    const onPaidMock = vi.fn();
    const { result } = renderHook(() =>
      useKhqrPayment({ orderNumber: 'KM-TEST-001', onPaid: onPaidMock })
    );

    // Initial loading
    expect(result.current.isLoading).toBe(true);

    // After fetch completes
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
      expect(result.current.status).toBe('PENDING');
      expect(result.current.payment?.paymentId).toBe(101);
    });

    // Verify EventSource connected
    expect(MockEventSource.instances.length).toBe(1);
    const es = MockEventSource.instances[0];
    expect(es.url).toContain('/payments/101/stream?token=stream-token-123');

    // Emit PAID status event over SSE
    act(() => {
      es.emit('status', {
        paymentId: 101,
        status: 'PAID',
        orderStatus: 'CONFIRMED',
        bakongHash: 'MOCK-HASH-123',
        paidAt: new Date().toISOString(),
        expiresAt: mockKhqrData.expiresAt,
      });
    });

    // Status should be updated to PAID and EventSource closed
    expect(result.current.status).toBe('PAID');
    expect(result.current.orderStatus).toBe('CONFIRMED');
    expect(result.current.bakongHash).toBe('MOCK-HASH-123');
    expect(es.closed).toBe(true);
    expect(onPaidMock).toHaveBeenCalledTimes(1);
  });

  it('regenerate creates a new payment and reconnects EventSource with new paymentId', async () => {
    const mockKhqr1 = {
      paymentId: 101,
      orderNumber: 'KM-TEST-002',
      status: 'PENDING',
      method: 'KHQR',
      currency: 'USD',
      amount: 25.0,
      amountCents: 2500,
      qrString: 'qr-1',
      qrImageDataUrl: 'data:image/png;base64,1',
      md5: 'md5-1',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      merchantName: 'KhmerMart',
      mode: 'MOCK',
      streamToken: 'token-1',
    };

    const mockKhqr2 = {
      paymentId: 102,
      orderNumber: 'KM-TEST-002',
      status: 'PENDING',
      method: 'KHQR',
      currency: 'USD',
      amount: 25.0,
      amountCents: 2500,
      qrString: 'qr-2',
      qrImageDataUrl: 'data:image/png;base64,2',
      md5: 'md5-2',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      merchantName: 'KhmerMart',
      mode: 'MOCK',
      streamToken: 'token-2',
    };

    (apiClient as any)
      .mockResolvedValueOnce({ data: mockKhqr1 })
      .mockResolvedValueOnce({ data: mockKhqr2 });

    const { result } = renderHook(() => useKhqrPayment({ orderNumber: 'KM-TEST-002' }));

    await waitFor(() => {
      expect(result.current.payment?.paymentId).toBe(101);
    });

    const es1 = MockEventSource.instances[0];

    // Call regenerate
    await act(async () => {
      await result.current.regenerate();
    });

    expect(es1.closed).toBe(true);
    expect(result.current.payment?.paymentId).toBe(102);
    expect(MockEventSource.instances.length).toBe(2);
    const es2 = MockEventSource.instances[1];
    expect(es2.url).toContain('/payments/102/stream?token=token-2');
  });

  it('falls back to polling on SSE error and updates status via polling', async () => {
    vi.useFakeTimers();

    const mockKhqr = {
      paymentId: 201,
      orderNumber: 'KM-TEST-003',
      status: 'PENDING',
      method: 'KHQR',
      currency: 'USD',
      amount: 30.0,
      amountCents: 3000,
      qrString: 'qr-err',
      qrImageDataUrl: 'data:image/png;base64,...',
      md5: 'md5-err',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      merchantName: 'KhmerMart',
      mode: 'MOCK',
      streamToken: 'token-err',
    };

    (apiClient as any).mockResolvedValueOnce({ data: mockKhqr });

    const { result } = renderHook(() => useKhqrPayment({ orderNumber: 'KM-TEST-003' }));

    // Run pending timers to resolve initial fetch
    await vi.runAllTimersAsync();

    expect(MockEventSource.instances.length).toBe(1);
    const es = MockEventSource.instances[0];

    // Trigger SSE error
    act(() => {
      es.onerror?.();
    });

    expect(es.closed).toBe(true);

    // Mock polling status endpoint returning PAID
    (apiClient as any).mockResolvedValueOnce({
      data: {
        paymentId: 201,
        status: 'PAID',
        orderStatus: 'CONFIRMED',
        paidAt: new Date().toISOString(),
        bakongHash: 'MOCK-HASH-POLL',
        expiresAt: mockKhqr.expiresAt,
      },
    });

    // Advance 3s timer for polling interval
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });

    expect(result.current.status).toBe('PAID');
    expect(result.current.bakongHash).toBe('MOCK-HASH-POLL');

    vi.useRealTimers();
  });

  it('cleans up EventSource and intervals on unmount (no leaks)', async () => {
    const mockKhqr = {
      paymentId: 301,
      orderNumber: 'KM-TEST-004',
      status: 'PENDING',
      method: 'KHQR',
      currency: 'USD',
      amount: 15.0,
      amountCents: 1500,
      qrString: 'qr-clean',
      qrImageDataUrl: 'data:image/png;base64,...',
      md5: 'md5-clean',
      expiresAt: new Date(Date.now() + 600000).toISOString(),
      merchantName: 'KhmerMart',
      mode: 'MOCK',
      streamToken: 'token-clean',
    };

    (apiClient as any).mockResolvedValueOnce({ data: mockKhqr });

    const { unmount } = renderHook(() => useKhqrPayment({ orderNumber: 'KM-TEST-004' }));

    await waitFor(() => {
      expect(MockEventSource.instances.length).toBe(1);
    });

    const es = MockEventSource.instances[0];
    expect(es.closed).toBe(false);

    // Unmount
    unmount();
    expect(es.closed).toBe(true);
  });
});
