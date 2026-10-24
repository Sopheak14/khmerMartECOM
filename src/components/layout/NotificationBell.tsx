'use client';

import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { Link } from '@/navigation';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';

export function NotificationBell() {
  const { user } = useAuthStore();

  const { data } = useQuery({
    queryKey: queryKeys.notifications.unreadCount,
    queryFn: async () => {
      const res = await apiClient<{ count: number }>('/notifications/unread-count');
      return res.data;
    },
    enabled: !!user,
    refetchInterval: 30000, // Polling every 30 seconds
  });

  const count = data?.count || 0;

  return (
    <Link
      href="/account/notifications"
      aria-label="View notifications"
      className="relative p-2 rounded-btn hover:bg-muted/10 text-foreground transition-colors"
    >
      <Bell className="w-5 h-5 text-muted hover:text-foreground transition-colors" />
      {count > 0 && (
        <span className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-primary-foreground bg-primary rounded-chip ring-2 ring-surface animate-in zoom-in-75">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}
