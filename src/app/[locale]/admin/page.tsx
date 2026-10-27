'use client';

import React from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { useRouter, usePathname, Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { DashboardDTO } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import { DashboardCharts } from '@/components/admin/DashboardCharts';
import {
  DollarSign,
  ShoppingBag,
  TrendingUp,
  Clock,
  AlertTriangle,
  ArrowRight,
  RotateCcw,
  Package,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminDashboardPage() {
  const t = useTranslations('admin.dashboard');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const range = (searchParams.get('range') || '30d') as 'today' | '7d' | '30d';

  const setRange = (newRange: 'today' | '7d' | '30d') => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('range', newRange);
    router.push(`${pathname}?${params.toString()}`);
  };

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: queryKeys.admin.dashboard(range),
    queryFn: async () => {
      const res = await apiClient<DashboardDTO>('/admin/dashboard', {
        query: { range },
      });
      return res.data;
    },
  });

  // Calculate revenue based on selected range
  const getSelectedRevenueCents = (dashboardData?: DashboardDTO) => {
    if (!dashboardData) return 0;
    if (range === 'today') return dashboardData.revenueTodayCents;
    if (range === '7d') return dashboardData.revenue7dCents;
    return dashboardData.revenue30dCents;
  };

  const currentRevenueCents = getSelectedRevenueCents(data);

  if (isLoading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="h-10 w-48 bg-muted/20 rounded-xl" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-28 bg-surface border border-border rounded-2xl p-5" />
          ))}
        </div>
        <div className="h-80 bg-surface border border-border rounded-2xl" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-12 text-center bg-surface border border-border rounded-2xl space-y-4 max-w-lg mx-auto">
        <div className="w-12 h-12 rounded-full bg-destructive/10 text-destructive flex items-center justify-center mx-auto">
          <AlertTriangle className="w-6 h-6" />
        </div>
        <div className="space-y-1">
          <h3 className="font-bold text-foreground text-sm">{commonT('errorTitle')}</h3>
          <p className="text-xs text-muted">{commonT('errorDesc')}</p>
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>{commonT('retry')}</span>
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="admin-dashboard-page">
      {/* Top Header & Range Switcher */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight" data-testid="dashboard-title">
            {t('title')}
          </h1>
          <p className="text-xs text-muted mt-0.5">Real-time store metrics & operational KPIs</p>
        </div>

        {/* Range Switcher (Today / 7d / 30d) in URL */}
        <div className="inline-flex items-center p-1 rounded-xl bg-muted/15 border border-border text-xs font-semibold">
          <button
            type="button"
            data-testid="range-today"
            onClick={() => setRange('today')}
            className={cn(
              'px-3 py-1.5 rounded-lg transition-colors cursor-pointer',
              range === 'today'
                ? 'bg-surface text-primary shadow-xs font-bold'
                : 'text-muted hover:text-foreground'
            )}
          >
            {t('rangeToday')}
          </button>
          <button
            type="button"
            data-testid="range-7d"
            onClick={() => setRange('7d')}
            className={cn(
              'px-3 py-1.5 rounded-lg transition-colors cursor-pointer',
              range === '7d'
                ? 'bg-surface text-primary shadow-xs font-bold'
                : 'text-muted hover:text-foreground'
            )}
          >
            {t('range7d')}
          </button>
          <button
            type="button"
            data-testid="range-30d"
            onClick={() => setRange('30d')}
            className={cn(
              'px-3 py-1.5 rounded-lg transition-colors cursor-pointer',
              range === '30d'
                ? 'bg-surface text-primary shadow-xs font-bold'
                : 'text-muted hover:text-foreground'
            )}
          >
            {t('range30d')}
          </button>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Revenue */}
        <div
          data-testid="kpi-card-revenue"
          className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted">{t('revenue')}</span>
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 text-blue-600 flex items-center justify-center">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="space-y-1">
            <div className="text-xl sm:text-2xl font-black text-foreground font-mono" data-testid="kpi-revenue-usd">
              {formatUsd(currentRevenueCents)}
            </div>
            <div className="text-xs text-muted font-medium font-mono" data-testid="kpi-revenue-khr">
              {formatKhr(currentRevenueCents)}
            </div>
          </div>
        </div>

        {/* Card 2: Orders */}
        <div
          data-testid="kpi-card-orders"
          className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted">{t('orders')}</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-700 flex items-center justify-center">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>
          <div className="space-y-1">
            <div className="text-xl sm:text-2xl font-black text-foreground font-mono" data-testid="kpi-orders-count">
              {data.ordersToday}
            </div>
            <div className="text-xs text-muted font-medium">
              <span>{data.ordersPending} {t('pendingOrders')}</span>
            </div>
          </div>
        </div>

        {/* Card 3: Avg Order Value */}
        <div
          data-testid="kpi-card-avg-order"
          className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted">{t('avgOrderValue')}</span>
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 text-amber-800 flex items-center justify-center">
              <TrendingUp className="w-4 h-4" />
            </div>
          </div>
          <div className="space-y-1">
            <div className="text-xl sm:text-2xl font-black text-foreground font-mono" data-testid="kpi-avg-order-usd">
              {formatUsd(data.avgOrderValueCents)}
            </div>
            <div className="text-xs text-muted font-medium font-mono" data-testid="kpi-avg-order-khr">
              {formatKhr(data.avgOrderValueCents)}
            </div>
          </div>
        </div>

        {/* Card 4: Pending Payments */}
        <div
          data-testid="kpi-card-pending-payments"
          className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-muted">{t('pendingPayments')}</span>
            <div className="w-8 h-8 rounded-lg bg-purple-500/10 text-purple-600 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="space-y-1">
            <div className="text-xl sm:text-2xl font-black text-foreground font-mono" data-testid="kpi-pending-payments">
              {data.pendingPayments}
            </div>
            <div className="text-xs text-muted font-medium">Awaiting customer payment</div>
          </div>
        </div>
      </div>

      {/* Visual Analytics Charts */}
      <DashboardCharts
        salesChart={data.salesChart}
        topProducts={data.topProducts}
        paymentMethodBreakdown={data.paymentMethodBreakdown}
      />

      {/* Tables Row: Low Stock Alert & Recent Orders */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Low-Stock Alert Table */}
        <div
          data-testid="dashboard-low-stock-card"
          className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4"
        >
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <span>{t('lowStockTitle')}</span>
              </h3>
              <p className="text-xs text-muted">{t('lowStockSubtitle')}</p>
            </div>
            <Link
              href="/admin/inventory"
              className="text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1"
            >
              <span>{t('viewAllInventory')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="overflow-x-auto">
            {!data.lowStock || data.lowStock.length === 0 ? (
              <div className="py-12 text-center text-xs text-muted">
                {t('noLowStock')}
              </div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border text-muted font-semibold">
                    <th className="pb-2.5">Product</th>
                    <th className="pb-2.5">SKU</th>
                    <th className="pb-2.5 text-center">Available</th>
                    <th className="pb-2.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {data.lowStock.slice(0, 5).map((item) => (
                    <tr key={item.sku} className="group hover:bg-muted/5 transition-colors">
                      <td className="py-2.5 font-medium text-foreground max-w-[180px] truncate">
                        {item.name}
                      </td>
                      <td className="py-2.5 font-mono text-[11px] text-muted">{item.sku}</td>
                      <td className="py-2.5 text-center">
                        <span className="inline-block px-2 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/15 text-amber-800 dark:text-amber-400">
                          {item.available} / {item.threshold}
                        </span>
                      </td>
                      <td className="py-2.5 text-right">
                        <Link
                          href={`/admin/inventory?q=${encodeURIComponent(item.sku)}`}
                          data-testid={`restock-quick-${item.sku}`}
                          className="px-2.5 py-1 rounded-md bg-primary/10 text-primary hover:bg-primary/20 text-xs font-semibold transition-colors"
                        >
                          {t('restockQuick')}
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Recent Orders Table */}
        <div
          data-testid="dashboard-recent-orders-card"
          className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4"
        >
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                <Package className="w-4 h-4 text-primary" />
                <span>{t('recentOrdersTitle')}</span>
              </h3>
              <p className="text-xs text-muted">{t('recentOrdersSubtitle')}</p>
            </div>
            <Link
              href="/admin/orders"
              className="text-xs font-semibold text-primary hover:underline inline-flex items-center gap-1"
            >
              <span>{t('viewAllOrders')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="overflow-x-auto">
            {!data.recentOrders || data.recentOrders.length === 0 ? (
              <div className="py-12 text-center text-xs text-muted">
                {t('noRecentOrders')}
              </div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border text-muted font-semibold">
                    <th className="pb-2.5">Order #</th>
                    <th className="pb-2.5">Customer</th>
                    <th className="pb-2.5">Total</th>
                    <th className="pb-2.5 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {data.recentOrders.slice(0, 5).map((order) => (
                    <tr key={order.orderNumber} className="hover:bg-muted/5 transition-colors">
                      <td className="py-2.5 font-mono font-bold">
                        <Link
                          href={`/admin/orders/${order.orderNumber}`}
                          className="text-primary hover:underline"
                        >
                          {order.orderNumber}
                        </Link>
                      </td>
                      <td className="py-2.5 text-foreground max-w-[130px] truncate">
                        {order.customer?.fullName || order.shippingAddress?.recipientName || 'Customer'}
                      </td>
                      <td className="py-2.5 font-mono font-medium text-foreground">
                        {formatUsd(order.totalCents)}
                      </td>
                      <td className="py-2.5 text-right">
                        <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-bold bg-muted/15 text-foreground border border-border">
                          {order.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
