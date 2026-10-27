'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts';
import { formatUsd } from '@/lib/money';
import { SalesChartItem, TopProductItem, PaymentBreakdownItem } from '@/types/api';
import { TrendingUp, Award, PieChart as PieIcon } from 'lucide-react';

const COLORS = ['#2563eb', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4'];

interface DashboardChartsProps {
  salesChart?: SalesChartItem[];
  topProducts?: TopProductItem[];
  paymentMethodBreakdown?: PaymentBreakdownItem[];
}

export function DashboardCharts({
  salesChart = [],
  topProducts = [],
  paymentMethodBreakdown = [],
}: DashboardChartsProps) {
  const t = useTranslations('admin.dashboard');
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-surface border border-border rounded-2xl p-6 h-80 animate-pulse bg-muted/10" />
        <div className="bg-surface border border-border rounded-2xl p-6 h-80 animate-pulse bg-muted/10" />
      </div>
    );
  }

  // Format sales chart data
  const formattedSales = (salesChart || []).map((item) => ({
    ...item,
    revenueUsd: item.revenueCents / 100,
    displayDate: item.date ? item.date.slice(5) : '',
  }));

  // Format top products
  const formattedTopProducts = (topProducts || []).slice(0, 5).map((p) => ({
    ...p,
    shortName: p.name.length > 18 ? `${p.name.slice(0, 18)}...` : p.name,
    revenueUsd: p.revenueCents / 100,
  }));

  // Format payment breakdown
  const formattedPayments = (paymentMethodBreakdown || []).map((p) => ({
    name: p.method,
    value: p.count,
    amountUsd: p.amountCents / 100,
  }));

  return (
    <div className="space-y-6">
      {/* Row 1: Sales Trend (2 cols) + Payment Methods (1 col) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Sales Trend Line/Area Chart */}
        <div
          data-testid="dashboard-sales-chart-card"
          className="lg:col-span-2 bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4"
        >
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-primary" />
                <span>{t('salesTrendTitle')}</span>
              </h3>
              <p className="text-xs text-muted">{t('salesTrendSubtitle')}</p>
            </div>
          </div>

          <div className="h-72 w-full pt-2">
            {formattedSales.length === 0 ? (
              <div className="h-full flex items-center justify-center text-xs text-muted">
                {t('noChartData')}
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%" aria-label={t('salesTrendTitle')}>
                <AreaChart data={formattedSales} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="revenueGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#2563eb" stopOpacity={0.25} />
                      <stop offset="95%" stopColor="#2563eb" stopOpacity={0.0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#88888820" />
                  <XAxis
                    dataKey="displayDate"
                    tick={{ fontSize: 11, fill: '#888888' }}
                    tickLine={false}
                    axisLine={{ stroke: '#88888825' }}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#888888' }}
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={(val) => `$${val}`}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'hsl(var(--surface))',
                      borderColor: 'hsl(var(--border))',
                      borderRadius: '0.75rem',
                      fontSize: '0.75rem',
                      boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                    }}
                    formatter={(val: any, name: any) => [
                      name === 'revenueUsd' ? formatUsd(Number(val) * 100) : val,
                      name === 'revenueUsd' ? 'Revenue' : 'Orders',
                    ]}
                  />
                  <Area
                    type="monotone"
                    dataKey="revenueUsd"
                    stroke="#2563eb"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#revenueGrad)"
                    name="revenueUsd"
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Payment Methods Donut Chart */}
        <div
          data-testid="dashboard-payment-chart-card"
          className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4"
        >
          <div className="space-y-1">
            <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
              <PieIcon className="w-4 h-4 text-primary" />
              <span>{t('paymentMethodsTitle')}</span>
            </h3>
            <p className="text-xs text-muted">{t('paymentMethodsSubtitle')}</p>
          </div>

          <div className="h-72 w-full flex items-center justify-center">
            {formattedPayments.length === 0 ? (
              <div className="text-xs text-muted">{t('noChartData')}</div>
            ) : (
              <>
              <ul className="sr-only" aria-label={t('paymentMethodsTitle')}>
                {formattedPayments.map((p: any) => (
                  <li key={p.name}>
                    {p.name}: {p.value}
                  </li>
                ))}
              </ul>
              <div aria-hidden="true" className="w-full h-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart accessibilityLayer={false} tabIndex={-1}>
                  <Pie
                    rootTabIndex={-1}
                    data={formattedPayments}
                    cx="50%"
                    cy="45%"
                    innerRadius={55}
                    outerRadius={80}
                    paddingAngle={4}
                    dataKey="value"
                  >
                    {formattedPayments.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'hsl(var(--surface))',
                      borderColor: 'hsl(var(--border))',
                      borderRadius: '0.75rem',
                      fontSize: '0.75rem',
                    }}
                    formatter={(val: any, name: any, item: any) => [
                      `${val} orders (${formatUsd(item.payload.amountUsd * 100)})`,
                      name,
                    ]}
                  />
                  <Legend
                    verticalAlign="bottom"
                    iconType="circle"
                    formatter={(val) => <span className="text-xs text-foreground font-medium">{val}</span>}
                  />
                </PieChart>
              </ResponsiveContainer>
              </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Row 2: Top Products Bar Chart */}
      <div
        data-testid="dashboard-top-products-chart-card"
        className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4"
      >
        <div className="space-y-1">
          <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
            <Award className="w-4 h-4 text-primary" />
            <span>{t('topProductsTitle')}</span>
          </h3>
          <p className="text-xs text-muted">{t('topProductsSubtitle')}</p>
        </div>

        <div className="h-64 w-full pt-2">
          {formattedTopProducts.length === 0 ? (
            <div className="h-full flex items-center justify-center text-xs text-muted">
              {t('noChartData')}
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%" aria-label={t('topProductsTitle')}>
              <BarChart data={formattedTopProducts} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#88888820" />
                <XAxis
                  dataKey="shortName"
                  tick={{ fontSize: 11, fill: '#888888' }}
                  tickLine={false}
                  interval={0}
                  angle={-15}
                  textAnchor="end"
                />
                <YAxis tick={{ fontSize: 11, fill: '#888888' }} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--surface))',
                    borderColor: 'hsl(var(--border))',
                    borderRadius: '0.75rem',
                    fontSize: '0.75rem',
                  }}
                  formatter={(val: any, name: any) => [
                    name === 'soldQty' ? `${val} units` : formatUsd(Number(val) * 100),
                    name === 'soldQty' ? 'Units Sold' : 'Revenue',
                  ]}
                />
                <Bar dataKey="soldQty" fill="#10b981" radius={[4, 4, 0, 0]} name="soldQty" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </div>
  );
}
