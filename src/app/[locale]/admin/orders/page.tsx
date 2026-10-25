'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { useRouter, usePathname, Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { OrderDTO, OrderStatus, PaymentStatus } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import {
  ShoppingBag,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  Eye,
  RotateCcw,
  AlertTriangle,
  Loader2,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminOrdersPage() {
  const t = useTranslations('admin.orders');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const status = searchParams.get('status') || '';
  const paymentStatus = searchParams.get('paymentStatus') || '';
  const from = searchParams.get('from') || '';
  const to = searchParams.get('to') || '';
  const q = searchParams.get('q') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 15;

  const [searchInput, setSearchInput] = useState(q);

  const updateFilters = (newParams: Record<string, string>) => {
    const params = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([k, v]) => {
      if (v) {
        params.set(k, v);
      } else {
        params.delete(k);
      }
    });
    // Reset to page 1 on filter changes if page wasn't explicitly changed
    if (!newParams.page) {
      params.set('page', '1');
    }
    router.push(`${pathname}?${params.toString()}`);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateFilters({ q: searchInput.trim() });
  };

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: queryKeys.admin.orders({
      status: status || undefined,
      paymentStatus: paymentStatus || undefined,
      from: from || undefined,
      to: to || undefined,
      q: q || undefined,
      page: page - 1,
      size: pageSize,
    }),
    queryFn: async () => {
      const res = await apiClient<OrderDTO[]>('/admin/orders', {
        query: {
          status: status || undefined,
          paymentStatus: paymentStatus || undefined,
          from: from || undefined,
          to: to || undefined,
          q: q || undefined,
          page: page - 1,
          size: pageSize,
        },
      });
      return {
        items: res.data || [],
        meta: res.meta,
      };
    },
  });

  const orders = data?.items || [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages || 1;

  const getOrderStatusBadge = (orderStatus: OrderStatus) => {
    switch (orderStatus) {
      case 'PENDING_PAYMENT':
        return 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20';
      case 'CONFIRMED':
        return 'bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/20';
      case 'PROCESSING':
        return 'bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20';
      case 'SHIPPED':
      case 'OUT_FOR_DELIVERY':
        return 'bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border-indigo-500/20';
      case 'DELIVERED':
        return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20';
      case 'CANCELLED':
      case 'PAYMENT_EXPIRED':
      case 'REFUNDED':
        return 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20';
      default:
        return 'bg-muted/15 text-muted border-border';
    }
  };

  const getPaymentStatusBadge = (pStatus: PaymentStatus) => {
    switch (pStatus) {
      case 'PAID':
        return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20';
      case 'PENDING':
        return 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20';
      case 'FAILED':
      case 'EXPIRED':
        return 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20';
      case 'REFUNDED':
        return 'bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20';
      default:
        return 'bg-muted/15 text-muted border-border';
    }
  };

  return (
    <div className="space-y-6" data-testid="admin-orders-page">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight" data-testid="orders-title">
            {t('title')}
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>
        {meta?.total !== undefined && (
          <span className="text-xs font-semibold px-3 py-1 rounded-full bg-primary/10 text-primary border border-primary/20">
            {t('itemsCount', { count: meta.total })}
          </span>
        )}
      </div>

      {/* Filter Bar */}
      <div className="bg-surface border border-border rounded-2xl p-4 shadow-2xs space-y-3">
        <form onSubmit={handleSearchSubmit} className="flex flex-col md:flex-row gap-3">
          {/* Search Input */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input
              type="text"
              data-testid="order-search-input"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder={t('searchPlaceholder')}
              className="w-full pl-9 pr-4 py-2 rounded-xl border border-border bg-background text-xs text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
            />
          </div>

          {/* Status Filter */}
          <select
            data-testid="order-status-filter"
            value={status}
            onChange={(e) => updateFilters({ status: e.target.value })}
            className="px-3 py-2 rounded-xl border border-border bg-background text-xs text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
          >
            <option value="">{t('allStatuses')}</option>
            <option value="PENDING_PAYMENT">Pending Payment</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="PROCESSING">Processing</option>
            <option value="SHIPPED">Shipped</option>
            <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
            <option value="DELIVERED">Delivered</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="REFUNDED">Refunded</option>
          </select>

          {/* Payment Status Filter */}
          <select
            data-testid="payment-status-filter"
            value={paymentStatus}
            onChange={(e) => updateFilters({ paymentStatus: e.target.value })}
            className="px-3 py-2 rounded-xl border border-border bg-background text-xs text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
          >
            <option value="">{t('allPaymentStatuses')}</option>
            <option value="PENDING">Pending</option>
            <option value="PAID">Paid</option>
            <option value="FAILED">Failed</option>
            <option value="EXPIRED">Expired</option>
            <option value="REFUNDED">Refunded</option>
          </select>

          {/* Filter / Reset Buttons */}
          <div className="flex gap-2">
            <button
              type="submit"
              data-testid="apply-filters-btn"
              className="px-4 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors cursor-pointer"
            >
              {commonT('filter')}
            </button>
            {(status || paymentStatus || q) && (
              <button
                type="button"
                data-testid="reset-filters-btn"
                onClick={() => {
                  setSearchInput('');
                  router.push(pathname);
                }}
                className="px-3 py-2 rounded-xl border border-border bg-background text-muted hover:text-foreground text-xs font-semibold transition-colors cursor-pointer"
              >
                {commonT('reset')}
              </button>
            )}
          </div>
        </form>
      </div>

      {/* Orders Table Card */}
      <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="py-20 text-center space-y-3">
            <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" />
            <p className="text-xs text-muted">{commonT('loading')}</p>
          </div>
        ) : error ? (
          <div className="py-16 text-center space-y-3 max-w-sm mx-auto">
            <AlertTriangle className="w-8 h-8 text-destructive mx-auto" />
            <p className="text-xs font-semibold text-foreground">{commonT('errorTitle')}</p>
            <button
              type="button"
              onClick={() => refetch()}
              className="px-4 py-1.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : orders.length === 0 ? (
          <div className="py-20 text-center space-y-3" data-testid="orders-empty-state">
            <div className="w-12 h-12 rounded-full bg-muted/10 text-muted flex items-center justify-center mx-auto">
              <ShoppingBag className="w-6 h-6" />
            </div>
            <p className="text-xs text-muted font-medium">{t('noOrders')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs" data-testid="admin-orders-table">
              <thead>
                <tr className="border-b border-border bg-muted/5 text-muted font-semibold">
                  <th className="py-3 px-4">{t('orderNumber')}</th>
                  <th className="py-3 px-4">{t('customer')}</th>
                  <th className="py-3 px-4">{t('date')}</th>
                  <th className="py-3 px-4">{t('total')}</th>
                  <th className="py-3 px-4">{t('paymentStatus')}</th>
                  <th className="py-3 px-4">{t('orderStatus')}</th>
                  <th className="py-3 px-4 text-right">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {orders.map((order) => {
                  const placedDate = new Date(order.placedAt).toLocaleDateString(
                    locale === 'km' ? 'km-KH' : 'en-US',
                    { dateStyle: 'medium' }
                  );

                  return (
                    <tr
                      key={order.orderNumber}
                      data-testid="order-row"
                      data-order-number={order.orderNumber}
                      data-row-testid={`admin-order-row-${order.orderNumber}`}
                      className="hover:bg-muted/5 transition-colors"
                    >
                      {/* Order Number */}
                      <td className="py-3 px-4 font-mono font-bold">
                        <Link
                          href={`/admin/orders/${order.orderNumber}`}
                          data-testid={`order-link-${order.orderNumber}`}
                          className="text-primary hover:underline"
                        >
                          {order.orderNumber}
                        </Link>
                      </td>

                      {/* Customer */}
                      <td className="py-3 px-4 text-foreground font-medium max-w-[160px] truncate">
                        {order.customer?.fullName || order.shippingAddress?.recipientName || 'Customer'}
                      </td>

                      {/* Date */}
                      <td className="py-3 px-4 text-muted whitespace-nowrap">{placedDate}</td>

                      {/* Total */}
                      <td className="py-3 px-4 whitespace-nowrap">
                        <div className="font-mono font-bold text-foreground">
                          {formatUsd(order.totalCents)}
                        </div>
                        <div className="font-mono text-[11px] text-muted">
                          {formatKhr(order.totalCents)}
                        </div>
                      </td>

                      {/* Payment Status */}
                      <td className="py-3 px-4 whitespace-nowrap">
                        <div className="space-y-0.5">
                          <span
                            className={cn(
                              'inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold border',
                              getPaymentStatusBadge(order.paymentStatus)
                            )}
                          >
                            {order.paymentStatus}
                          </span>
                          <span className="block text-[10px] text-muted uppercase font-mono">
                            {order.paymentMethod}
                          </span>
                        </div>
                      </td>

                      {/* Order Status */}
                      <td className="py-3 px-4 whitespace-nowrap">
                        <span
                          className={cn(
                            'inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold border',
                            getOrderStatusBadge(order.status)
                          )}
                        >
                          {order.status}
                        </span>
                      </td>

                      {/* Action */}
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <Link
                          href={`/admin/orders/${order.orderNumber}`}
                          data-testid="view-order-btn"
                          data-view-order-id={order.orderNumber}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-border bg-background hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors"
                        >
                          <Eye className="w-3.5 h-3.5 text-muted" />
                          <span>{t('viewDetails')}</span>
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Server Pagination Controls */}
        {totalPages > 1 && (
          <div
            data-testid="admin-orders-pagination"
            className="p-4 border-t border-border flex items-center justify-between text-xs text-muted"
          >
            <span>
              {commonT('pageOf', { page, total: totalPages })}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                data-testid="orders-prev-page-btn"
                disabled={page <= 1}
                onClick={() => updateFilters({ page: String(page - 1) })}
                className="p-1.5 rounded-lg border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors cursor-pointer"
                aria-label="Previous page"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                type="button"
                data-testid="orders-next-page-btn"
                disabled={page >= totalPages}
                onClick={() => updateFilters({ page: String(page + 1) })}
                className="p-1.5 rounded-lg border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors cursor-pointer"
                aria-label="Next page"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
