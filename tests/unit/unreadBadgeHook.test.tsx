import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useUnreadNotifications } from '@/hooks/useUnreadNotifications';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { useNotificationStore } from '@/lib/store/useNotificationStore';
import * as apiClientModule from '@/lib/apiClient';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

describe('useUnreadNotifications Hook', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.useFakeTimers();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    useAuthStore.getState().clearAuth();
    useNotificationStore.getState().markAllAsRead();

    // Reset visibilityState mock
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('does not poll when user is logged out', async () => {
    const apiSpy = vi.spyOn(apiClientModule, 'apiClient').mockResolvedValue({
      data: { count: 5 },
    } as any);

    const { result } = renderHook(() => useUnreadNotifications({ pollInterval: 1000 }), { wrapper });

    expect(result.current.count).toBe(0);
    expect(result.current.isPolling).toBe(false);

    // Fast-forward 5 seconds
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(apiSpy).not.toHaveBeenCalled();
    expect(result.current.count).toBe(0);
  });

  it('polls when user is logged in and tab is visible', async () => {
    const apiSpy = vi.spyOn(apiClientModule, 'apiClient').mockResolvedValue({
      data: { count: 3 },
    } as any);

    // Set user
    useAuthStore.getState().setAuth('mock-token', {
      id: 1,
      email: 'test@khmermart.local',
      fullName: 'Test User',
      role: 'CUSTOMER',
      isActive: true,
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    });

    const { result } = renderHook(() => useUnreadNotifications({ pollInterval: 30000 }), { wrapper });

    expect(result.current.isPolling).toBe(true);
    expect(apiSpy).toHaveBeenCalledTimes(1);

    // Fast forward 30 seconds
    await act(async () => {
      vi.advanceTimersByTime(30000);
    });

    expect(apiSpy).toHaveBeenCalledTimes(2);

    // Fast forward another 30 seconds
    await act(async () => {
      vi.advanceTimersByTime(30000);
    });

    expect(apiSpy).toHaveBeenCalledTimes(3);
  });

  it('stops polling when document becomes hidden, and resumes when visible', async () => {
    const apiSpy = vi.spyOn(apiClientModule, 'apiClient').mockResolvedValue({
      data: { count: 2 },
    } as any);

    useAuthStore.getState().setAuth('mock-token', {
      id: 1,
      email: 'test@khmermart.local',
      fullName: 'Test User',
      role: 'CUSTOMER',
      isActive: true,
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    });

    const { result } = renderHook(() => useUnreadNotifications({ pollInterval: 30000 }), { wrapper });

    expect(apiSpy).toHaveBeenCalledTimes(1);

    // Tab becomes hidden
    act(() => {
      Object.defineProperty(document, 'visibilityState', {
        value: 'hidden',
        writable: true,
        configurable: true,
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    expect(result.current.isPolling).toBe(false);

    // Advance 60 seconds while hidden
    act(() => {
      vi.advanceTimersByTime(60000);
    });

    // Should NOT have made any new calls while hidden
    expect(apiSpy).toHaveBeenCalledTimes(1);

    // Tab becomes visible again
    await act(async () => {
      Object.defineProperty(document, 'visibilityState', {
        value: 'visible',
        writable: true,
        configurable: true,
      });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    expect(result.current.isPolling).toBe(true);
    // Should have fetched immediately on becoming visible
    expect(apiSpy).toHaveBeenCalledTimes(2);

    // Advance 30s while visible
    await act(async () => {
      vi.advanceTimersByTime(30000);
    });

    expect(apiSpy).toHaveBeenCalledTimes(3);
  });

  it('stops polling and resets count to 0 on logout', async () => {
    const apiSpy = vi.spyOn(apiClientModule, 'apiClient').mockResolvedValue({
      data: { count: 4 },
    } as any);

    useAuthStore.getState().setAuth('mock-token', {
      id: 1,
      email: 'test@khmermart.local',
      fullName: 'Test User',
      role: 'CUSTOMER',
      isActive: true,
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    });

    const { result } = renderHook(() => useUnreadNotifications({ pollInterval: 30000 }), { wrapper });

    expect(apiSpy).toHaveBeenCalledTimes(1);

    // User logs out
    act(() => {
      useAuthStore.getState().clearAuth();
    useNotificationStore.getState().markAllAsRead();
    });

    expect(result.current.count).toBe(0);
    expect(result.current.isPolling).toBe(false);

    // Advance 60 seconds after logout
    act(() => {
      vi.advanceTimersByTime(60000);
    });

    // No further calls
    expect(apiSpy).toHaveBeenCalledTimes(1);
  });
});
