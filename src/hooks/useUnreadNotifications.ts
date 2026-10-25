'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { useNotificationStore } from '@/lib/store/useNotificationStore';
import { apiClient } from '@/lib/apiClient';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/lib/queryKeys';

export interface UseUnreadNotificationsOptions {
  pollInterval?: number;
  initialCount?: number;
}

export function useUnreadNotifications(options: UseUnreadNotificationsOptions = {}) {
  const { pollInterval = 30000, initialCount } = options;
  const { user } = useAuthStore();
  const count = useNotificationStore((state) => state.unreadCount);
  const setUnreadCount = useNotificationStore((state) => state.setUnreadCount);
  const decrement = useNotificationStore((state) => state.decrementUnreadCount);
  const markAllRead = useNotificationStore((state) => state.markAllAsRead);

  const [isTabVisible, setIsTabVisible] = useState<boolean>(() => {
    if (typeof document !== 'undefined') {
      return document.visibilityState === 'visible';
    }
    return true;
  });

  const queryClient = useQueryClient();
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  // Set initial count if specified
  useEffect(() => {
    if (initialCount !== undefined) {
      setUnreadCount(initialCount);
    }
  }, [initialCount, setUnreadCount]);

  const fetchUnreadCount = useCallback(async () => {
    if (!useAuthStore.getState().user) {
      setUnreadCount(0);
      return;
    }

    try {
      const res = await apiClient<{ count: number }>('/notifications/unread-count');
      const newCount = res.data?.count ?? 0;
      setUnreadCount(newCount);
      queryClient?.setQueryData(queryKeys.notifications.unreadCount, { count: newCount });
    } catch {
      // Ignore network errors during polling
    }
  }, [queryClient, setUnreadCount]);

  // Tab visibility tracker & custom refresh event listener
  useEffect(() => {
    if (typeof document === 'undefined') return;

    const handleVisibilityChange = () => {
      const visible = document.visibilityState === 'visible';
      setIsTabVisible(visible);
    };

    const handleCustomRefresh = () => {
      if (document.visibilityState === 'visible') {
        fetchUnreadCount();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('khmermart:notification-refresh', handleCustomRefresh);
    window.addEventListener('focus', handleCustomRefresh);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('khmermart:notification-refresh', handleCustomRefresh);
      window.removeEventListener('focus', handleCustomRefresh);
    };
  }, [fetchUnreadCount]);

  // Polling controller: ONLY poll when logged in AND tab is visible
  useEffect(() => {
    // If logged out: clear any interval, reset count to 0, stop
    if (!user) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      setUnreadCount(0);
      return;
    }

    // If tab is hidden: stop polling
    if (!isTabVisible) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    // Tab is visible and user is logged in -> fetch immediately and set interval
    fetchUnreadCount();

    intervalRef.current = setInterval(() => {
      fetchUnreadCount();
    }, pollInterval);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [user, isTabVisible, pollInterval, fetchUnreadCount, setUnreadCount]);

  return {
    count,
    setCount: setUnreadCount,
    refetch: fetchUnreadCount,
    markAllRead,
    decrement,
    isPolling: Boolean(user && isTabVisible),
  };
}
