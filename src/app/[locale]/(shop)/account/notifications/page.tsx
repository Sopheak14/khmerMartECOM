'use client';

import React, { useState } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { NotificationDTO } from '@/types/api';
import { formatRelativeTime } from '@/lib/relativeTime';
import { useUnreadNotifications } from '@/hooks/useUnreadNotifications';
import {
  Bell,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Loader2,
  CheckCircle2,
  Package,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function NotificationsPage() {
  const t = useTranslations('notifications');
  const locale = useLocale();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { markAllRead, decrement } = useUnreadNotifications();

  const [page, setPage] = useState(0);
  const pageSize = 10;

  const { data, isLoading } = useQuery({
    queryKey: queryKeys.notifications.list({ page, size: pageSize }),
    queryFn: async () => {
      const res = await apiClient<NotificationDTO[]>('/notifications', {
        query: { page, size: pageSize },
      });
      return {
        items: res.data || [],
        meta: res.meta,
      };
    },
  });

  const notifications = data?.items || [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages || 1;

  // Mark all as read mutation
  const markAllMutation = useMutation({
    mutationFn: async () => {
      await apiClient('/notifications/read-all', { method: 'PATCH' });
    },
    onSuccess: () => {
      markAllRead();
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      toast.success(t('allCaughtUp'));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to mark all as read');
    },
  });

  const handleNotificationClick = async (item: NotificationDTO) => {
    if (!item.readAt) {
      try {
        await apiClient(`/notifications/${item.id}/read`, { method: 'PATCH' });
        decrement();
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
      } catch {
        // ignore
      }
    }

    if (item.link) {
      let target = item.link;
      if (target.startsWith('/account/orders/')) {
        target = target.replace('/account/orders/', '/orders/');
      }
      router.push(target);
    }
  };

  const getLocalizedContent = (notif: NotificationDTO) => {
    if (locale === 'km') {
      switch (notif.type) {
        case 'ORDER_PLACED':
          return {
            title: t('ORDER_PLACED_title'),
            body: notif.body?.includes('KM-')
              ? `ការបញ្ជាទិញ ${notif.body.match(/KM-[A-Za-z0-9-]+/)?.[0] || ''} របស់អ្នកត្រូវបានដាក់ដោយជោគជ័យ។`
              : t('ORDER_PLACED_body'),
          };
        case 'ORDER_PAID':
          return {
            title: t('ORDER_PAID_title'),
            body: notif.body?.includes('KM-')
              ? `ការទូទាត់សម្រាប់ការបញ្ជាទិញ ${notif.body.match(/KM-[A-Za-z0-9-]+/)?.[0] || ''} ត្រូវបានបញ្ជាក់។`
              : t('ORDER_PAID_body'),
          };
        case 'ORDER_SHIPPED':
          return {
            title: t('ORDER_SHIPPED_title'),
            body: t('ORDER_SHIPPED_body'),
          };
        case 'ORDER_DELIVERED':
          return {
            title: t('ORDER_DELIVERED_title'),
            body: t('ORDER_DELIVERED_body'),
          };
        case 'ORDER_CANCELLED':
          return {
            title: t('ORDER_CANCELLED_title'),
            body: t('ORDER_CANCELLED_body'),
          };
        case 'INVENTORY_LOW':
          return {
            title: t('INVENTORY_LOW_title'),
            body: t('INVENTORY_LOW_body'),
          };
      }
    }
    return {
      title: notif.title,
      body: notif.body,
    };
  };

  return (
    <div
      className="bg-surface border border-border rounded-card p-6 shadow-xs space-y-6"
      data-testid="notifications-page-container"
    >
      {/* Top Header */}
      <div className="flex items-center justify-between flex-wrap gap-4 pb-4 border-b border-border">
        <div>
          <h2 className="text-lg sm:text-xl font-bold text-foreground">
            {t('title')}
          </h2>
          <p className="text-xs text-muted mt-0.5">
            {meta?.total ? `Total ${meta.total} notifications` : t('allCaughtUp')}
          </p>
        </div>

        <button
          type="button"
          data-testid="mark-all-read-btn"
          onClick={() => markAllMutation.mutate()}
          disabled={markAllMutation.isPending || notifications.length === 0}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-btn border border-border bg-background hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors disabled:opacity-50 cursor-pointer"
        >
          {markAllMutation.isPending ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : (
            <CheckCheck className="w-3.5 h-3.5 text-primary" />
          )}
          <span>{t('markAllAsRead')}</span>
        </button>
      </div>

      {/* Notifications List */}
      <div className="space-y-3">
        {isLoading ? (
          <div className="py-12 text-center text-xs text-muted space-y-2">
            <Loader2 className="w-6 h-6 animate-spin mx-auto text-primary" />
            <p>Loading notifications...</p>
          </div>
        ) : notifications.length === 0 ? (
          <div
            data-testid="empty-notifications"
            className="py-16 text-center text-xs text-muted space-y-3"
          >
            <div className="w-12 h-12 rounded-full bg-muted/10 text-muted/60 flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h3 className="font-bold text-sm text-foreground">{t('allCaughtUp')}</h3>
              <p>{t('noNotifications')}</p>
            </div>
          </div>
        ) : (
          notifications.map((item) => {
            const localized = getLocalizedContent(item);
            const isUnread = !item.readAt;

            return (
              <div
                key={item.id}
                data-testid="notification-page-item"
                data-notification-id={item.id}
                onClick={() => handleNotificationClick(item)}
                className={cn(
                  'p-4 rounded-xl border border-border transition-all cursor-pointer flex items-start gap-3.5 text-left',
                  isUnread
                    ? 'bg-primary/5 border-primary/30 hover:border-primary/50'
                    : 'bg-background hover:bg-muted/5'
                )}
              >
                <div className="mt-0.5 shrink-0">
                  {isUnread ? (
                    <span className="w-2.5 h-2.5 rounded-full bg-primary block ring-4 ring-primary/20" />
                  ) : (
                    <span className="w-2.5 h-2.5 rounded-full bg-muted/30 block" />
                  )}
                </div>

                <div className="flex-1 min-w-0 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <h4
                      className={cn(
                        'text-xs sm:text-sm truncate text-foreground',
                        isUnread ? 'font-bold' : 'font-medium'
                      )}
                    >
                      {localized.title}
                    </h4>
                    <span className="text-[11px] text-muted shrink-0 font-medium">
                      {formatRelativeTime(item.createdAt, locale)}
                    </span>
                  </div>

                  <p className="text-xs text-muted leading-relaxed line-clamp-2">
                    {localized.body}
                  </p>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div
          data-testid="notifications-pagination"
          className="pt-4 border-t border-border flex items-center justify-between text-xs text-muted"
        >
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="p-2 rounded-btn border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors"
              aria-label="Previous page"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              className="p-2 rounded-btn border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors"
              aria-label="Next page"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
