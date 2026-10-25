'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Bell, CheckCircle2, ChevronRight, Loader2, Package, Sparkles } from 'lucide-react';
import { Link, useRouter } from '@/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations, useLocale } from 'next-intl';
import { useUnreadNotifications } from '@/hooks/useUnreadNotifications';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { NotificationDTO } from '@/types/api';
import { formatRelativeTime } from '@/lib/relativeTime';
import { cn } from '@/lib/utils';

export function NotificationBell() {
  const t = useTranslations('notifications');
  const locale = useLocale();
  const router = useRouter();
  const { user } = useAuthStore();
  const { count, decrement, refetch } = useUnreadNotifications();
  const queryClient = useQueryClient();

  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close on outside click or escape
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  // Query latest 5 notifications
  const { data: notificationsData, isLoading } = useQuery({
    queryKey: queryKeys.notifications.list({ page: 0, size: 5 }),
    queryFn: async () => {
      const res = await apiClient<NotificationDTO[]>('/notifications', {
        query: { page: 0, size: 5 },
      });
      return res.data;
    },
    enabled: Boolean(user && isOpen),
  });

  const notifications = notificationsData || [];

  const handleNotificationClick = async (notification: NotificationDTO) => {
    setIsOpen(false);

    // Mark as read if not already read
    if (!notification.readAt) {
      try {
        await apiClient(`/notifications/${notification.id}/read`, { method: 'PATCH' });
        decrement();
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
      } catch {
        // ignore
      }
    }

    // Navigate to linked order or target link if present
    if (notification.link) {
      let target = notification.link;
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

  if (!user) {
    return (
      <Link
        href="/login"
        aria-label="Notifications"
        className="relative p-2 rounded-btn hover:bg-muted/10 text-foreground transition-colors"
      >
        <Bell className="w-5 h-5 text-muted hover:text-foreground transition-colors" />
      </Link>
    );
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        aria-label="View notifications"
        aria-expanded={isOpen}
        data-testid="notification-bell-btn"
        onClick={() => {
          refetch();
          setIsOpen(!isOpen);
        }}
        className="relative p-2 rounded-btn hover:bg-muted/10 text-foreground transition-colors cursor-pointer focus:outline-hidden focus-visible:ring-2 focus-visible:ring-primary"
      >
        <Bell className="w-5 h-5 text-muted hover:text-foreground transition-colors" />
        {count > 0 && (
          <span
            data-testid="notification-bell-badge"
            className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-primary-foreground bg-primary rounded-chip ring-2 ring-surface animate-in zoom-in-75"
          >
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {/* Notifications Dropdown */}
      {isOpen && (
        <div
          data-testid="notification-dropdown"
          className="absolute right-0 mt-2 w-80 sm:w-96 bg-surface border border-border rounded-2xl shadow-xl z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-150"
        >
          {/* Header */}
          <div className="px-4 py-3 border-b border-border flex items-center justify-between bg-muted/5">
            <div className="flex items-center gap-2">
              <span className="font-bold text-xs uppercase tracking-wider text-foreground">
                {t('title')}
              </span>
              {count > 0 && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-primary/10 text-primary">
                  {count} new
                </span>
              )}
            </div>
            <Link
              href="/account/notifications"
              onClick={() => setIsOpen(false)}
              className="text-[11px] font-medium text-primary hover:underline"
            >
              {t('viewAll')}
            </Link>
          </div>

          {/* List of 5 latest notifications */}
          <div className="max-h-80 overflow-y-auto divide-y divide-border">
            {isLoading ? (
              <div className="p-6 text-center text-xs text-muted space-y-2">
                <Loader2 className="w-5 h-5 animate-spin mx-auto text-primary" />
                <p>Loading notifications...</p>
              </div>
            ) : notifications.length === 0 ? (
              <div className="p-8 text-center text-xs text-muted space-y-2">
                <CheckCircle2 className="w-8 h-8 text-muted/40 mx-auto" />
                <p className="font-medium">{t('allCaughtUp')}</p>
                <p className="text-[11px] text-muted/70">{t('noNotifications')}</p>
              </div>
            ) : (
              notifications.map((item) => {
                const localized = getLocalizedContent(item);
                const isUnread = !item.readAt;

                return (
                  <div
                    key={item.id}
                    data-testid="notification-dropdown-item"
                    onClick={() => handleNotificationClick(item)}
                    className={cn(
                      'p-3.5 hover:bg-muted/10 transition-colors cursor-pointer text-left space-y-1 relative block',
                      isUnread && 'bg-primary/5'
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0">
                        {isUnread && (
                          <span className="w-2 h-2 rounded-full bg-primary shrink-0" />
                        )}
                        <h4
                          className={cn(
                            'text-xs truncate text-foreground',
                            isUnread ? 'font-bold' : 'font-medium'
                          )}
                        >
                          {localized.title}
                        </h4>
                      </div>
                      <span className="text-[10px] text-muted shrink-0">
                        {formatRelativeTime(item.createdAt, locale)}
                      </span>
                    </div>

                    <p className="text-[11px] text-muted line-clamp-2 leading-relaxed">
                      {localized.body}
                    </p>
                  </div>
                );
              })
            )}
          </div>

          {/* Footer */}
          <div className="p-2.5 border-t border-border bg-muted/5 text-center">
            <Link
              href="/account/notifications"
              data-testid="view-all-notifications-btn"
              onClick={() => setIsOpen(false)}
              className="inline-flex items-center justify-center gap-1 w-full py-1.5 text-xs font-bold text-foreground hover:text-primary transition-colors"
            >
              <span>{t('viewAll')}</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
