'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter, usePathname, Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { useAuthStore } from '@/lib/store/useAuthStore';
import {
  AdminPaymentDTO,
  PaymentStatus,
  PaymentMethod,
  ReconciliationReportDTO,
} from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import {
  CreditCard,
  Download,
  Copy,
  Check,
  FileCode,
  RotateCcw,
  AlertTriangle,
  X,
  ChevronLeft,
  ChevronRight,
  Loader2,
  CheckCircle2,
  RefreshCw,
  Search,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function AdminPaymentsPage() {
  const t = useTranslations('admin.payments');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const tab = searchParams.get('tab') || 'transactions';
  const status = searchParams.get('status') || '';
  const method = searchParams.get('method') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 15;

  // Drawer & Dialog states
  const [selectedRawResponse, setSelectedRawResponse] = useState<any | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);

  // Refund dialog state
  const [refundPayment, setRefundPayment] = useState<AdminPaymentDTO | null>(null);
  const [refundAmountCents, setRefundAmountCents] = useState<number>(0);
  const [refundReason, setRefundReason] = useState<string>('');

  const updateFilters = (newParams: Record<string, string>) => {
    const params = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([k, v]) => {
      if (v) {
        params.set(k, v);
      } else {
        params.delete(k);
      }
    });
    if (!newParams.page) {
      params.set('page', '1');
    }
    router.push(`${pathname}?${params.toString()}`);
  };

  // Fetch payments list
  const { data: paymentsData, isLoading: paymentsLoading, error: paymentsError, refetch: refetchPayments } = useQuery({
    queryKey: queryKeys.admin.payments({
      status: status || undefined,
      method: method || undefined,
      page: page - 1,
      size: pageSize,
    }),
    queryFn: async () => {
      const res = await apiClient<AdminPaymentDTO[]>('/admin/payments', {
        query: {
          status: status || undefined,
          method: method || undefined,
          page: page - 1,
          size: pageSize,
        },
      });
      return {
        items: res.data || [],
        meta: res.meta,
      };
    },
    enabled: tab === 'transactions',
  });

  // Fetch reconciliation report
  const { data: reconData, isLoading: reconLoading, error: reconError, refetch: refetchRecon } = useQuery({
    queryKey: ['admin', 'payments', 'reconciliation'],
    queryFn: async () => {
      const res = await apiClient<ReconciliationReportDTO>('/admin/payments/reconciliation');
      return res.data;
    },
    enabled: tab === 'reconciliation',
  });

  // Refund mutation
  const refundMutation = useMutation({
    mutationFn: async ({ id, amountCents, reason }: { id: number; amountCents?: number; reason: string }) => {
      const res = await apiClient(`/admin/payments/${id}/refund`, {
        method: 'POST',
        body: { amountCents, reason },
      });
      return res.data;
    },
    onSuccess: () => {
      setRefundPayment(null);
      setRefundReason('');
      queryClient.invalidateQueries({ queryKey: ['admin', 'payments'] });
      toast.success('Refund processed successfully');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to process refund');
    },
  });

  // Approve Bank Transfer mutation
  const approveMutation = useMutation({
    mutationFn: async (id: number) => {
      const res = await apiClient(`/admin/payments/${id}/approve-bank-transfer`, {
        method: 'POST',
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'payments'] });
      toast.success(t('approveSuccess'));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to approve bank transfer');
    },
  });

  // Export CSV handler
  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      const token = useAuthStore.getState().token;
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';
      const res = await fetch(`${baseUrl}/admin/payments/export.csv`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to export CSV');

      const disposition = res.headers.get('content-disposition') || '';
      let filename = 'payments.csv';
      const match = disposition.match(/filename="?([^"]+)"?/);
      if (match && match[1]) {
        filename = match[1];
      }

      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Payments CSV downloaded successfully');
    } catch (err: any) {
      toast.error(err.message || 'Failed to export CSV');
    } finally {
      setIsExporting(false);
    }
  };

  const handleCopyHash = (hash: string) => {
    navigator.clipboard.writeText(hash);
    setCopiedHash(hash);
    toast.success(t('hashCopied'));
    setTimeout(() => setCopiedHash(null), 2000);
  };

  const payments = paymentsData?.items || [];
  const meta = paymentsData?.meta;
  const totalPages = meta?.totalPages || 1;

  const shortenHash = (h?: string) => {
    if (!h) return '—';
    if (h.length <= 16) return h;
    return `${h.slice(0, 8)}...${h.slice(-6)}`;
  };

  const getStatusBadge = (s: PaymentStatus) => {
    switch (s) {
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
    <div className="space-y-6" data-testid="admin-payments-page">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight" data-testid="payments-title">
            {t('title')}
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>

        {/* Export CSV button */}
        <button
          type="button"
          data-testid="export-payments-csv-btn"
          disabled={isExporting}
          onClick={handleExportCsv}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border border-border bg-surface hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors shadow-2xs disabled:opacity-50 cursor-pointer self-start sm:self-auto"
        >
          {isExporting ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : (
            <Download className="w-3.5 h-3.5 text-primary" />
          )}
          <span>{isExporting ? t('exporting') : t('exportCsv')}</span>
        </button>
      </div>

      {/* Navigation Tabs: All Transactions vs Reconciliation */}
      <div className="flex items-center gap-2 border-b border-border text-xs font-semibold">
        <button
          type="button"
          data-testid="payments-tab-transactions"
          onClick={() => updateFilters({ tab: 'transactions' })}
          className={cn(
            'px-4 py-2.5 border-b-2 transition-colors cursor-pointer',
            tab === 'transactions'
              ? 'border-primary text-primary font-bold'
              : 'border-transparent text-muted hover:text-foreground'
          )}
        >
          {t('allPaymentsTab')}
        </button>
        <button
          type="button"
          data-testid="payments-tab-reconciliation"
          onClick={() => updateFilters({ tab: 'reconciliation' })}
          className={cn(
            'px-4 py-2.5 border-b-2 transition-colors cursor-pointer flex items-center gap-1.5',
            tab === 'reconciliation'
              ? 'border-primary text-primary font-bold'
              : 'border-transparent text-muted hover:text-foreground'
          )}
        >
          <span>{t('reconciliationTab')}</span>
          {reconData?.mismatchCount ? (
            <span className="px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-amber-500/15 text-amber-600">
              {reconData.mismatchCount}
            </span>
          ) : null}
        </button>
      </div>

      {/* Tab 1: Transactions Table */}
      {tab === 'transactions' && (
        <div className="space-y-4">
          {/* Filters Bar */}
          <div className="bg-surface border border-border rounded-2xl p-4 shadow-2xs flex flex-wrap gap-3">
            <select
              data-testid="payment-method-filter"
              value={method}
              onChange={(e) => updateFilters({ method: e.target.value })}
              className="px-3 py-2 rounded-xl border border-border bg-background text-xs text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
            >
              <option value="">All Payment Methods</option>
              <option value="KHQR">KHQR</option>
              <option value="COD">Cash on Delivery</option>
              <option value="BANK_TRANSFER">Bank Transfer</option>
            </select>

            <select
              data-testid="payment-status-filter"
              value={status}
              onChange={(e) => updateFilters({ status: e.target.value })}
              className="px-3 py-2 rounded-xl border border-border bg-background text-xs text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
            >
              <option value="">All Statuses</option>
              <option value="PAID">PAID</option>
              <option value="PENDING">PENDING</option>
              <option value="FAILED">FAILED</option>
              <option value="EXPIRED">EXPIRED</option>
              <option value="REFUNDED">REFUNDED</option>
            </select>

            {(method || status) && (
              <button
                type="button"
                onClick={() => router.push(pathname)}
                className="px-3 py-2 rounded-xl border border-border text-xs text-muted hover:text-foreground"
              >
                {commonT('reset')}
              </button>
            )}
          </div>

          {/* Payments Table */}
          <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden">
            {paymentsLoading ? (
              <div className="py-20 text-center space-y-3">
                <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" />
                <p className="text-xs text-muted">{commonT('loading')}</p>
              </div>
            ) : paymentsError ? (
              <div className="py-16 text-center space-y-3 max-w-sm mx-auto">
                <AlertTriangle className="w-8 h-8 text-destructive mx-auto" />
                <p className="text-xs font-semibold">{commonT('errorTitle')}</p>
                <button
                  type="button"
                  onClick={() => refetchPayments()}
                  className="px-4 py-1.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
                >
                  {commonT('retry')}
                </button>
              </div>
            ) : payments.length === 0 ? (
              <div className="py-20 text-center space-y-3" data-testid="payments-empty-state">
                <CreditCard className="w-10 h-10 text-muted mx-auto" />
                <p className="text-xs text-muted font-medium">{t('noPayments')}</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs" data-testid="admin-payments-table">
                  <thead>
                    <tr className="border-b border-border bg-muted/5 text-muted font-semibold">
                      <th className="py-3 px-4">Order #</th>
                      <th className="py-3 px-4">{t('method')}</th>
                      <th className="py-3 px-4">{t('status')}</th>
                      <th className="py-3 px-4">{t('amount')}</th>
                      <th className="py-3 px-4">{t('bakongHash')}</th>
                      <th className="py-3 px-4">{t('payerAccount')}</th>
                      <th className="py-3 px-4">{t('paidAt')}</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {payments.map((p) => {
                      const paidDate = p.paidAt
                        ? new Date(p.paidAt).toLocaleString(locale === 'km' ? 'km-KH' : 'en-US', {
                            dateStyle: 'short',
                            timeStyle: 'short',
                          })
                        : '—';

                      return (
                        <tr key={p.id} className="hover:bg-muted/5 transition-colors">
                          <td className="py-3 px-4 font-mono font-bold">
                            <Link
                              href={`/admin/orders/${p.orderNumber}`}
                              className="text-primary hover:underline"
                            >
                              {p.orderNumber}
                            </Link>
                          </td>
                          <td className="py-3 px-4 font-semibold text-foreground uppercase">
                            {p.method}
                          </td>
                          <td className="py-3 px-4 whitespace-nowrap">
                            <span
                              className={cn(
                                'inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold border',
                                getStatusBadge(p.status)
                              )}
                            >
                              {p.status}
                            </span>
                          </td>
                          <td className="py-3 px-4 whitespace-nowrap">
                            <span className="font-mono font-bold text-foreground block">
                              {formatUsd(p.amountCents)}
                            </span>
                            {p.amountKhr ? (
                              <span className="font-mono text-[10px] text-muted block">
                                {formatKhr(p.amountCents)}
                              </span>
                            ) : null}
                          </td>
                          <td className="py-3 px-4 whitespace-nowrap">
                            {p.bakongHash ? (
                              <div className="flex items-center gap-1.5 font-mono text-[11px]">
                                <span className="text-muted">{shortenHash(p.bakongHash)}</span>
                                <button
                                  type="button"
                                  data-testid={`copy-hash-btn-${p.id}`}
                                  onClick={() => handleCopyHash(p.bakongHash!)}
                                  className="p-1 rounded-md hover:bg-muted/15 text-muted hover:text-foreground transition-colors cursor-pointer"
                                  title={t('copyHash')}
                                >
                                  {copiedHash === p.bakongHash ? (
                                    <Check className="w-3.5 h-3.5 text-emerald-500" />
                                  ) : (
                                    <Copy className="w-3.5 h-3.5" />
                                  )}
                                </button>
                              </div>
                            ) : (
                              <span className="text-muted">—</span>
                            )}
                          </td>
                          <td className="py-3 px-4 font-mono text-xs text-muted max-w-[120px] truncate">
                            {p.fromAccountId || '—'}
                          </td>
                          <td className="py-3 px-4 text-muted whitespace-nowrap">{paidDate}</td>
                          <td className="py-3 px-4 text-right whitespace-nowrap">
                            <div className="flex items-center justify-end gap-1.5">
                              {/* Raw Response Drawer Button */}
                              {p.rawResponse && (
                                <button
                                  type="button"
                                  data-testid={`view-raw-btn-${p.id}`}
                                  onClick={() => setSelectedRawResponse(p.rawResponse)}
                                  className="px-2 py-1 rounded-md border border-border hover:bg-muted/10 text-[11px] font-semibold text-foreground inline-flex items-center gap-1 cursor-pointer"
                                  title={t('viewRaw')}
                                >
                                  <FileCode className="w-3 h-3 text-primary" />
                                  <span>JSON</span>
                                </button>
                              )}

                              {/* Refund Button for paid orders */}
                              {p.status === 'PAID' && (
                                <button
                                  type="button"
                                  data-testid={`refund-payment-btn-${p.id}`}
                                  onClick={() => {
                                    setRefundPayment(p);
                                    setRefundAmountCents(p.amountCents);
                                  }}
                                  className="px-2 py-1 rounded-md bg-purple-500/10 text-purple-700 dark:text-purple-400 hover:bg-purple-500/20 text-[11px] font-semibold transition-colors cursor-pointer"
                                >
                                  {t('refund')}
                                </button>
                              )}

                              {/* Approve Bank Transfer Button */}
                              {p.method === 'BANK_TRANSFER' && p.status === 'PENDING' && (
                                <button
                                  type="button"
                                  data-testid={`approve-transfer-btn-${p.id}`}
                                  onClick={() => approveMutation.mutate(p.id)}
                                  className="px-2 py-1 rounded-md bg-emerald-600 text-white hover:bg-emerald-700 text-[11px] font-bold transition-colors cursor-pointer"
                                >
                                  {t('approveBankTransfer')}
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="p-4 border-t border-border flex items-center justify-between text-xs text-muted">
                <span>{commonT('pageOf', { page, total: totalPages })}</span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={page <= 1}
                    onClick={() => updateFilters({ page: String(page - 1) })}
                    className="p-1.5 rounded-lg border border-border hover:bg-muted/10 disabled:opacity-40"
                    aria-label="Previous page"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages}
                    onClick={() => updateFilters({ page: String(page + 1) })}
                    className="p-1.5 rounded-lg border border-border hover:bg-muted/10 disabled:opacity-40"
                    aria-label="Next page"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 2: Reconciliation View */}
      {tab === 'reconciliation' && (
        <div className="space-y-4" data-testid="reconciliation-view">
          {reconLoading ? (
            <div className="py-20 text-center space-y-3">
              <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" />
              <p className="text-xs text-muted">Analyzing payment & order ledger records...</p>
            </div>
          ) : reconError ? (
            <div className="py-16 text-center space-y-3 max-w-sm mx-auto">
              <AlertTriangle className="w-8 h-8 text-destructive mx-auto" />
              <p className="text-xs font-semibold">{commonT('errorTitle')}</p>
              <button
                type="button"
                onClick={() => refetchRecon()}
                className="px-4 py-1.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
              >
                {commonT('retry')}
              </button>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Summary Banner */}
              <div className="bg-surface border border-border rounded-2xl p-6 shadow-2xs flex items-center justify-between flex-wrap gap-4">
                <div className="space-y-1">
                  <h3 className="font-bold text-sm text-foreground">{t('reconciliationReport')}</h3>
                  <p className="text-xs text-muted">
                    Last ledger verification run:{' '}
                    {new Date(reconData?.generatedAt || Date.now()).toLocaleString()}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={cn(
                      'px-3.5 py-1.5 rounded-full text-xs font-bold border',
                      reconData?.mismatchCount === 0
                        ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20'
                        : 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20'
                    )}
                  >
                    Status: {reconData?.status || 'OK'}
                  </span>
                  <button
                    type="button"
                    onClick={() => refetchRecon()}
                    className="p-2 rounded-xl border border-border hover:bg-muted/10 text-muted hover:text-foreground"
                    title="Refresh report"
                  >
                    <RefreshCw className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Mismatches List */}
              <div className="bg-surface border border-border rounded-2xl p-6 shadow-2xs">
                {!reconData?.mismatches || reconData.mismatches.length === 0 ? (
                  <div className="py-12 text-center space-y-3" data-testid="reconciliation-clean-state">
                    <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto" />
                    <p className="text-xs font-semibold text-foreground">{t('noMismatches')}</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                      {t('mismatchesFound', { count: reconData.mismatchCount })}
                    </h4>
                    <div className="divide-y divide-border/60">
                      {reconData.mismatches.map((m, idx) => (
                        <div key={idx} className="py-3 flex items-center justify-between gap-4 text-xs">
                          <div>
                            <span className="font-bold text-foreground block">{m.type}</span>
                            <p className="text-muted">{m.description}</p>
                          </div>
                          <div className="font-mono text-muted text-right">
                            {m.orderNumber && <p>Order: {m.orderNumber}</p>}
                            {m.paymentId && <p>Payment ID: {m.paymentId}</p>}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Slide-over Drawer: Raw Gateway JSON Viewer */}
      {selectedRawResponse && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="raw-response-drawer"
          className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-xs animate-in fade-in"
        >
          <div className="w-full max-w-lg bg-surface border-l border-border h-full p-6 shadow-2xl flex flex-col space-y-4 animate-in slide-in-from-right duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div>
                <h3 className="font-bold text-sm text-foreground">{t('rawResponseTitle')}</h3>
                <p className="text-xs text-muted">{t('rawResponseSubtitle')}</p>
              </div>
              <button
                type="button"
                data-testid="close-raw-drawer-btn"
                onClick={() => setSelectedRawResponse(null)}
                className="p-1.5 rounded-lg text-muted hover:text-foreground hover:bg-muted/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-auto bg-slate-950 text-emerald-400 p-4 rounded-xl font-mono text-xs border border-border/80">
              <pre>{JSON.stringify(selectedRawResponse, null, 2)}</pre>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedRawResponse(null)}
                className="px-4 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90"
              >
                {t('close')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Refund Modal Dialog */}
      {refundPayment && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="refund-payment-dialog"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in"
        >
          <div className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl relative text-left">
            <button
              type="button"
              onClick={() => setRefundPayment(null)}
              className="absolute top-4 right-4 text-muted hover:text-foreground"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="font-bold text-base text-foreground">{t('refund')}</h3>
            <p className="text-xs text-muted">
              Order {refundPayment.orderNumber} · Max refund: {formatUsd(refundPayment.amountCents)}
            </p>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                refundMutation.mutate({
                  id: refundPayment.id,
                  amountCents: refundAmountCents,
                  reason: refundReason,
                });
              }}
              className="space-y-4 text-xs"
            >
              <div>
                <label htmlFor="refund-amount-input" className="block font-semibold text-foreground mb-1">
                  Refund Amount (USD Cents)
                </label>
                <input
                  type="number"
                  id="refund-amount-input"
                  data-testid="refund-amount-input"
                  max={refundPayment.amountCents}
                  min={1}
                  value={refundAmountCents}
                  onChange={(e) => setRefundAmountCents(parseInt(e.target.value, 10) || 0)}
                  className="w-full p-2.5 rounded-btn border border-border bg-background text-foreground"
                  required
                />
              </div>

              <div>
                <label htmlFor="refund-reason-input" className="block font-semibold text-foreground mb-1">
                  Refund Reason
                </label>
                <textarea
                  id="refund-reason-input"
                  data-testid="refund-reason-input"
                  rows={3}
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  placeholder="State reason for customer refund"
                  className="w-full p-2.5 rounded-btn border border-border bg-background text-foreground resize-none"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setRefundPayment(null)}
                  className="h-9 rounded-btn border border-border hover:bg-muted/10 font-bold text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  data-testid="confirm-refund-btn"
                  disabled={refundMutation.isPending}
                  className="h-9 rounded-btn bg-purple-600 hover:bg-purple-700 text-white font-bold disabled:opacity-50 flex items-center justify-center gap-1.5"
                >
                  {refundMutation.isPending ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <span>Process Refund</span>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
