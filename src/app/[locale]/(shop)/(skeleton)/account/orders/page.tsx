'use client';

import React, { useState } from 'react';
import { SafeImage } from '@/components/common/SafeImage';
import { getImageUrl } from '@/lib/images';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { OrderDTO, OrderStatus } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import {
  Package,
  Calendar,
  ChevronRight,
  ChevronLeft,
  ArrowRight,
  Loader2,
  Clock,
  CheckCircle2,
  Truck,
  XCircle,
  CreditCard,
  ShoppingBag,
} from 'lucide-react';

export default function AccountOrdersPage() {
  const t = useTranslations('orders');
  const common = useTranslations('common');
  const locale = useLocale();

  const [activeTab, setActiveTab] = useState<string>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(0);

  const statusFilter = activeTab === 'ALL' ? undefined : (activeTab as OrderStatus);

  const { data: response, isLoading } = useQuery({
    queryKey: ['account-orders', statusFilter, currentPage],
    queryFn: async () => {
      const res = await apiClient<OrderDTO[]>('/orders', {
        query: {
          page: currentPage,
          size: 10,
          ...(statusFilter ? { status: statusFilter } : {}),
        },
      });
      return res;
    },
  });

  const orders = response?.data || [];
  const meta = response?.meta;
  const totalPages = meta?.totalPages || 1;

  const tabs = [
    { key: 'ALL', label: t('tabAll') },
    { key: 'PENDING_PAYMENT', label: t('tabPending') },
    { key: 'CONFIRMED', label: t('tabConfirmed') },
    { key: 'PROCESSING', label: t('tabProcessing') },
    { key: 'SHIPPED', label: t('tabShipped') },
    { key: 'DELIVERED', label: t('tabDelivered') },
    { key: 'CANCELLED', label: t('tabCancelled') },
  ];

  const getStatusBadge = (status: OrderStatus) => {
    switch (status) {
      case 'PENDING_PAYMENT':
        return {
          label: t('status.PENDING_PAYMENT'),
          className: 'bg-amber-500/10 text-amber-800 dark:text-amber-400 border-amber-500/20',
          icon: Clock,
        };
      case 'CONFIRMED':
        return {
          label: t('status.CONFIRMED'),
          className: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
          icon: CheckCircle2,
        };
      case 'PROCESSING':
        return {
          label: t('status.PROCESSING'),
          className: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20',
          icon: Package,
        };
      case 'SHIPPED':
        return {
          label: t('status.SHIPPED'),
          className: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20',
          icon: Truck,
        };
      case 'OUT_FOR_DELIVERY':
        return {
          label: t('status.OUT_FOR_DELIVERY'),
          className: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20',
          icon: Truck,
        };
      case 'DELIVERED':
        return {
          label: t('status.DELIVERED'),
          className: 'bg-green-500/10 text-green-800 dark:text-green-400 border-green-500/20',
          icon: CheckCircle2,
        };
      case 'CANCELLED':
      case 'PAYMENT_EXPIRED':
      case 'REFUNDED':
        return {
          label: t(`status.${status}`) || status,
          className: 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20',
          icon: XCircle,
        };
      default:
        return {
          label: status,
          className: 'bg-muted/15 text-muted border-border',
          icon: Clock,
        };
    }
  };

  return (
    <div className="space-y-6" data-testid="account-orders-page">
      <div>
        <h2 className="text-xl sm:text-2xl font-black text-foreground tracking-tight">
          {t('title')}
        </h2>
        <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
      </div>

      {/* Status Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-2 border-b border-border scrollbar-none">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              data-testid={`orders-tab-${tab.key.toLowerCase()}`}
              onClick={() => {
                setActiveTab(tab.key);
                setCurrentPage(0);
              }}
              className={`px-3 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-colors ${
                isActive
                  ? 'bg-primary text-primary-foreground shadow-xs'
                  : 'bg-surface hover:bg-muted/10 text-muted hover:text-foreground border border-border'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Orders List Content */}
      {isLoading ? (
        <div className="p-12 text-center space-y-3 bg-surface border border-border rounded-2xl">
          <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto" />
          <p className="text-xs text-muted">Loading your orders...</p>
        </div>
      ) : orders.length === 0 ? (
        <div
          data-testid="orders-empty"
          className="p-12 text-center space-y-4 bg-surface border border-border rounded-2xl"
        >
          <div className="w-14 h-14 mx-auto rounded-full bg-muted/10 flex items-center justify-center text-muted">
            <ShoppingBag className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <h3 className="text-base font-bold text-foreground">{t('noOrders')}</h3>
            <p className="text-xs text-muted max-w-sm mx-auto">{t('noOrdersDesc')}</p>
          </div>
          <Link
            href="/products"
            className="inline-flex items-center gap-1.5 px-5 py-2.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs hover:bg-primary/90 transition-colors"
          >
            <span>Explore Products</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      ) : (
        <div className="space-y-4" data-testid="orders-list">
          {orders.map((order) => {
            const statusInfo = getStatusBadge(order.status);
            const StatusIcon = statusInfo.icon;
            const placedDate = new Date(order.placedAt).toLocaleDateString(
              locale === 'km' ? 'km-KH' : 'en-US',
              { year: 'numeric', month: 'short', day: 'numeric' }
            );

            return (
              <div
                key={order.id}
                data-testid="order-card"
                className="bg-surface border border-border rounded-2xl p-5 shadow-xs space-y-4 hover:border-primary/40 transition-colors"
              >
                {/* Header: Order Number & Status Badge */}
                <div className="flex items-center justify-between flex-wrap gap-2 pb-3 border-b border-border">
                  <div className="flex items-center gap-3">
                    <span
                      data-testid="order-card-number"
                      className="font-mono font-bold text-sm text-foreground bg-muted/15 px-2.5 py-1 rounded"
                    >
                      {order.orderNumber}
                    </span>
                    <span className="text-xs text-muted flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5" />
                      <span>{placedDate}</span>
                    </span>
                  </div>

                  <span
                    data-testid="order-status-badge"
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold border ${statusInfo.className}`}
                  >
                    <StatusIcon className="w-3.5 h-3.5" />
                    <span>{statusInfo.label}</span>
                  </span>
                </div>

                {/* Items Preview */}
                <div className="space-y-2">
                  <div className="divide-y divide-border">
                    {order.items.slice(0, 3).map((item) => (
                      <div key={item.id} className="py-2 flex items-center justify-between gap-3 text-xs">
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="relative w-10 h-10 rounded bg-muted/10 overflow-hidden shrink-0 border border-border">
                            {item.imageUrl ? (
                              <SafeImage src={getImageUrl(item.imageUrl)} alt={item.name} fill className="object-cover" />
                            ) : (
                              <div className="w-full h-full flex items-center justify-center text-[10px] text-muted">
                                Item
                              </div>
                            )}
                          </div>
                          <div className="min-w-0">
                            <p className="font-bold text-foreground truncate">{item.name}</p>
                            <p className="text-[11px] text-muted">
                              {formatUsd(item.unitPriceCents)} × {item.quantity}
                            </p>
                          </div>
                        </div>
                        <span className="font-bold text-foreground shrink-0">
                          {formatUsd(item.lineTotalCents)}
                        </span>
                      </div>
                    ))}
                  </div>

                  {order.items.length > 3 && (
                    <p className="text-[11px] text-muted italic">
                      + {order.items.length - 3} more items
                    </p>
                  )}
                </div>

                {/* Footer: Totals & Action Buttons */}
                <div className="flex items-center justify-between pt-3 border-t border-border flex-wrap gap-3">
                  <div>
                    <span className="text-[11px] text-muted block">{t('total')}</span>
                    <span className="text-base font-black text-foreground" data-testid="order-total-usd">
                      {formatUsd(order.totalCents)}
                    </span>
                    <span className="text-[11px] text-muted font-normal ml-1.5" data-testid="order-total-khr">
                      {formatKhr(order.totalCents)}
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    {order.status === 'PENDING_PAYMENT' && (
                      <Link
                        href={
                          order.paymentMethod === 'BANK_TRANSFER'
                            ? `/checkout/bank-transfer/${order.orderNumber}`
                            : `/checkout/pay/${order.orderNumber}`
                        }
                        data-testid="order-pay-now-btn"
                        className="px-3.5 py-1.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs hover:bg-primary/90 transition-colors"
                      >
                        {t('payNow')}
                      </Link>
                    )}
                    <Link
                      href={`/orders/${order.orderNumber}`}
                      data-testid="order-view-details-btn"
                      className="px-3.5 py-1.5 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground flex items-center gap-1 transition-colors"
                    >
                      <span>{t('orderDetail')}</span>
                      <ChevronRight className="w-3.5 h-3.5" />
                    </Link>
                  </div>
                </div>
              </div>
            );
          })}

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div
              className="flex items-center justify-between pt-4 border-t border-border"
              data-testid="orders-pagination"
            >
              <button
                type="button"
                data-testid="orders-prev-page"
                disabled={currentPage === 0}
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
                <span>Previous</span>
              </button>

              <span className="text-xs text-muted">
                Page {currentPage + 1} of {totalPages}
              </span>

              <button
                type="button"
                data-testid="orders-next-page"
                disabled={currentPage >= totalPages - 1}
                onClick={() => setCurrentPage((p) => p + 1)}
                className="px-3 py-1.5 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <span>Next</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
