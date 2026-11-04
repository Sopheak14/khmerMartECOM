'use client';

import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { EmptyStoreCta } from '@/components/admin/EmptyStoreCta';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { SalesGroupDTO } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import {
  BarChart3,
  Calendar,
  Download,
  FileSpreadsheet,
  FileText,
  RotateCcw,
  AlertTriangle,
  Loader2,
  TrendingUp,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';
import { cn } from '@/lib/utils';

export default function AdminReportsPage() {
  const t = useTranslations('admin.reports');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const [mounted, setMounted] = useState(false);
  const [downloadingFormat, setDownloadingFormat] = useState<'csv' | 'xlsx' | null>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  const groupBy = searchParams.get('groupBy') || 'day';
  const from = searchParams.get('from') || '';
  const to = searchParams.get('to') || '';

  const updateFilters = (updates: Record<string, string | null>) => {
    const current = new URLSearchParams(Array.from(searchParams.entries()));
    Object.entries(updates).forEach(([key, value]) => {
      if (!value) {
        current.delete(key);
      } else {
        current.set(key, value);
      }
    });
    const search = current.toString();
    const query = search ? `?${search}` : '';
    router.push(`${pathname}${query}`);
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-reports-sales', { groupBy, from, to }],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.set('groupBy', groupBy);
      if (from) {
        params.set('from', new Date(`${from}T00:00:00Z`).toISOString());
      }
      if (to) {
        params.set('to', new Date(`${to}T23:59:59Z`).toISOString());
      }

      const res = await apiClient.get<SalesGroupDTO[]>(`/admin/reports/sales?${params.toString()}`);
      return res.data || [];
    },
  });

  const handleExport = async (format: 'csv' | 'xlsx') => {
    try {
      setDownloadingFormat(format);
      const params = new URLSearchParams();
      params.set('groupBy', groupBy);
      if (from) {
        params.set('from', new Date(`${from}T00:00:00Z`).toISOString());
      }
      if (to) {
        params.set('to', new Date(`${to}T23:59:59Z`).toISOString());
      }

      const endpoint = `/api/admin/reports/sales.${format}?${params.toString()}`;
      const token = useAuthStore.getState().token;

      const res = await fetch(endpoint, {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });

      if (!res.ok) {
        throw new Error(`Export failed with status ${res.status}`);
      }

      const contentDisposition = res.headers.get('Content-Disposition') || '';
      const match = contentDisposition.match(/filename="?([^";]+)"?/);
      const filename = match ? match[1] : `sales-${groupBy}.${format}`;

      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Export download error:', err);
    } finally {
      setDownloadingFormat(null);
    }
  };

  const reportItems: SalesGroupDTO[] = data || [];

  const chartData = reportItems.map((item: SalesGroupDTO) => ({
    name: item.groupKey,
    revenue: item.revenueCents / 100,
    orders: item.ordersCount,
    units: item.unitsSold,
  }));

  const totalOrders = reportItems.reduce((acc: number, curr: SalesGroupDTO) => acc + (curr.ordersCount ?? 0), 0);
  const totalUnits = reportItems.reduce((acc: number, curr: SalesGroupDTO) => acc + (curr.unitsSold ?? 0), 0);
  const totalRevenueCents = reportItems.reduce((acc: number, curr: SalesGroupDTO) => acc + (curr.revenueCents ?? 0), 0);

  return (
    <div className="space-y-6" data-testid="admin-reports-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <BarChart3 className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-sm text-muted mt-1">{t('subtitle')}</p>
        </div>

        {/* Export Buttons */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleExport('csv')}
            disabled={downloadingFormat !== null}
            data-testid="export-csv-btn"
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold bg-surface border border-border rounded-lg text-foreground hover:bg-muted/10 transition-colors shadow-sm disabled:opacity-50"
          >
            {downloadingFormat === 'csv' ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <FileText className="w-3.5 h-3.5 text-blue-500" />
            )}
            <span>{t('exportCsv')}</span>
          </button>
          <button
            onClick={() => handleExport('xlsx')}
            disabled={downloadingFormat !== null}
            data-testid="export-xlsx-btn"
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold bg-surface border border-border rounded-lg text-foreground hover:bg-muted/10 transition-colors shadow-sm disabled:opacity-50"
          >
            {downloadingFormat === 'xlsx' ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <FileSpreadsheet className="w-3.5 h-3.5 text-emerald-500" />
            )}
            <span>{t('exportXlsx')}</span>
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-surface border border-border rounded-xl p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Group By Selector */}
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs font-medium text-muted mr-1">{t('groupBy')}:</span>
          {[
            { id: 'day', label: t('groupByDay') },
            { id: 'month', label: t('groupByMonth') },
            { id: 'category', label: t('groupByCategory') },
            { id: 'product', label: t('groupByProduct') },
            { id: 'paymentMethod', label: t('groupByPaymentMethod') },
          ].map((item) => (
            <button
              key={item.id}
              onClick={() => updateFilters({ groupBy: item.id })}
              className={cn(
                'px-3 py-1.5 text-xs font-medium rounded-lg transition-colors border',
                groupBy === item.id
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'bg-background text-muted border-border hover:text-foreground'
              )}
            >
              {item.label}
            </button>
          ))}
        </div>

        {/* Date Range Inputs */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 bg-background border border-border rounded-lg px-2.5 py-1 text-xs">
            <Calendar className="w-3.5 h-3.5 text-muted" />
            <input
              type="date"
              value={from}
              onChange={(e) => updateFilters({ from: e.target.value })}
              className="bg-transparent text-foreground focus:outline-none"
              aria-label="From date"
            />
            <span className="text-muted">→</span>
            <input
              type="date"
              value={to}
              onChange={(e) => updateFilters({ to: e.target.value })}
              className="bg-transparent text-foreground focus:outline-none"
              aria-label="To date"
            />
          </div>
          {(from || to) && (
            <button
              onClick={() => updateFilters({ from: null, to: null })}
              className="text-xs text-muted hover:text-foreground underline px-1"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      <EmptyStoreCta />

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-surface border border-border rounded-xl p-4">
          <p className="text-xs font-medium text-muted">{t('ordersCount')}</p>
          <p className="text-xl font-bold text-foreground mt-1">{totalOrders.toLocaleString()}</p>
        </div>
        <div className="bg-surface border border-border rounded-xl p-4">
          <p className="text-xs font-medium text-muted">{t('unitsSold')}</p>
          <p className="text-xl font-bold text-foreground mt-1">{totalUnits.toLocaleString()}</p>
        </div>
        <div className="bg-surface border border-border rounded-xl p-4">
          <p className="text-xs font-medium text-muted">{t('revenue')}</p>
          <div className="mt-1">
            <span className="text-xl font-bold text-primary">{formatUsd(totalRevenueCents)}</span>
            <span className="text-xs text-muted ml-2">({formatKhr(totalRevenueCents)})</span>
          </div>
        </div>
      </div>

      {/* Chart Section */}
      <div className="bg-surface border border-border rounded-xl p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-foreground mb-4 flex items-center gap-2">
          <TrendingUp className="w-4 h-4 text-primary" />
          <span>{t('chartTitle')}</span>
        </h2>

        {isLoading ? (
          <div className="h-64 flex items-center justify-center">
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
          </div>
        ) : isError ? (
          <div className="h-64 flex flex-col items-center justify-center gap-2">
            <AlertTriangle className="w-6 h-6 text-destructive" />
            <p className="text-xs text-destructive">{commonT('error')}</p>
            <button
              onClick={() => refetch()}
              className="px-2.5 py-1 text-xs border border-border rounded hover:bg-muted/10"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : chartData.length === 0 ? (
          <div className="h-64 flex items-center justify-center text-xs text-muted" data-testid="reports-empty-chart">
            {from || to ? commonT('noData') : t('noSalesYet')}
          </div>
        ) : mounted ? (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                <XAxis
                  dataKey="name"
                  stroke="hsl(var(--muted))"
                  fontSize={11}
                  tickLine={false}
                  interval={0}
                  angle={chartData.length > 8 ? -30 : 0}
                  textAnchor={chartData.length > 8 ? 'end' : 'middle'}
                />
                <YAxis
                  stroke="hsl(var(--muted))"
                  fontSize={11}
                  tickLine={false}
                  tickFormatter={(val) => `$${val}`}
                />
                <Tooltip
                  formatter={(val: any) => [`$${Number(val).toFixed(2)}`, t('revenue')]}
                  contentStyle={{
                    backgroundColor: 'hsl(var(--surface))',
                    borderColor: 'hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '12px',
                  }}
                />
                <Bar dataKey="revenue" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        ) : null}
      </div>

      {/* Table Section */}
      <div className="bg-surface border border-border rounded-xl overflow-hidden shadow-sm">
        <div className="p-4 border-b border-border">
          <h2 className="text-sm font-semibold text-foreground">
            {t('title')} ({reportItems.length})
          </h2>
        </div>

        {isLoading ? (
          <div className="p-8 text-center">
            <Loader2 className="w-6 h-6 text-primary animate-spin mx-auto mb-2" />
            <p className="text-xs text-muted">{commonT('loading')}</p>
          </div>
        ) : reportItems.length === 0 ? (
          <div className="p-8 text-center text-xs text-muted">
            {from || to ? commonT('noData') : t('noSalesYet')}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-background/50 text-muted text-xs uppercase tracking-wider font-semibold">
                  <th className="py-3 px-4">{t('groupKey')}</th>
                  <th className="py-3 px-4 text-right">{t('ordersCount')}</th>
                  <th className="py-3 px-4 text-right">{t('unitsSold')}</th>
                  <th className="py-3 px-4 text-right">{t('revenue')} (USD)</th>
                  <th className="py-3 px-4 text-right">{t('revenue')} (KHR)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {reportItems.map((item: SalesGroupDTO, idx: number) => (
                  <tr key={`${item.groupKey}-${idx}`} className="hover:bg-muted/5 transition-colors">
                    <td className="py-3 px-4 font-medium text-foreground">{item.groupKey}</td>
                    <td className="py-3 px-4 text-right text-muted">{item.ordersCount.toLocaleString()}</td>
                    <td className="py-3 px-4 text-right text-muted">{item.unitsSold.toLocaleString()}</td>
                    <td className="py-3 px-4 text-right font-medium text-foreground">
                      {formatUsd(item.revenueCents)}
                    </td>
                    <td className="py-3 px-4 text-right text-xs text-muted">
                      {formatKhr(item.revenueCents)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t-2 border-border bg-background/80 font-bold text-foreground text-sm">
                  <td className="py-3 px-4">{commonT('total')}</td>
                  <td className="py-3 px-4 text-right">{totalOrders.toLocaleString()}</td>
                  <td className="py-3 px-4 text-right">{totalUnits.toLocaleString()}</td>
                  <td className="py-3 px-4 text-right text-primary">{formatUsd(totalRevenueCents)}</td>
                  <td className="py-3 px-4 text-right text-xs text-muted">{formatKhr(totalRevenueCents)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
