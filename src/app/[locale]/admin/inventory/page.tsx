'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { InventoryDTO, InventoryMovementDTO, InventoryMovementType } from '@/types/api';
import {
  Boxes,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  AlertTriangle,
  Loader2,
  PlusCircle,
  Sliders,
  History,
  X,
  CheckCircle2,
  AlertCircle,
  PackageCheck,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminInventoryPage() {
  const t = useTranslations('admin.inventory');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const lowStock = searchParams.get('lowStock') === 'true';
  const q = searchParams.get('q') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 20;

  const [searchInput, setSearchInput] = useState(q);

  // Modal / Drawer state
  const [selectedItem, setSelectedItem] = useState<InventoryDTO | null>(null);
  const [restockDialogOpen, setRestockDialogOpen] = useState(false);
  const [adjustDialogOpen, setAdjustDialogOpen] = useState(false);
  const [movementsDrawerOpen, setMovementsDrawerOpen] = useState(false);

  // Form states
  const [quantity, setQuantity] = useState<number | string>('');
  const [reason, setReason] = useState('');
  const [formErrors, setFormErrors] = useState<{ quantity?: string; reason?: string; general?: string }>({});

  const updateFilters = (newParams: Record<string, string | number | boolean | null | undefined>) => {
    const current = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([key, val]) => {
      if (val === null || val === undefined || val === '') {
        current.delete(key);
      } else {
        current.set(key, String(val));
      }
    });
    router.push(`${pathname}?${current.toString()}`);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateFilters({ q: searchInput.trim() || null, page: 1 });
  };

  const handleToggleLowStock = () => {
    updateFilters({ lowStock: !lowStock ? 'true' : null, page: 1 });
  };

  const handleResetFilters = () => {
    setSearchInput('');
    router.push(pathname);
  };

  // Main inventory query
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.inventory({
      lowStock: lowStock ? true : undefined,
      q: q || undefined,
      page: page - 1,
      size: pageSize,
    }),
    queryFn: async () => {
      const res = await apiClient<InventoryDTO[]>('/admin/inventory', {
        query: {
          lowStock: lowStock ? true : undefined,
          q: q || undefined,
          page: page - 1,
          size: pageSize,
        },
      });
      return res;
    },
  });

  const items = data?.data || [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages || 1;

  // Movements query for drawer
  const {
    data: movementsData,
    isLoading: isLoadingMovements,
  } = useQuery({
    queryKey: queryKeys.admin.inventoryMovements(selectedItem?.id || 0),
    queryFn: async () => {
      if (!selectedItem?.id) return { data: [] as InventoryMovementDTO[] };
      const res = await apiClient<InventoryMovementDTO[]>(`/admin/inventory/${selectedItem.id}/movements`);
      return res;
    },
    enabled: movementsDrawerOpen && !!selectedItem?.id,
  });

  // Restock mutation
  const restockMutation = useMutation({
    mutationFn: async ({ id, qty, resn }: { id: number; qty: number; resn?: string }) => {
      const res = await apiClient<InventoryDTO>(`/admin/inventory/${id}/restock`, {
        method: 'POST',
        body: { quantity: qty, reason: resn || undefined },
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      setRestockDialogOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFormErrors({
          quantity: err.details.quantity,
          reason: err.details.reason,
          general: err.message,
        });
      } else if (err.code === 'VALIDATION_ERROR' || err.message) {
        setFormErrors({
          quantity: err.message,
          general: err.message,
        });
      } else {
        setFormErrors({ general: err.message || 'Restock failed' });
      }
    },
  });

  // Adjust mutation
  const adjustMutation = useMutation({
    mutationFn: async ({ id, qty, resn }: { id: number; qty: number; resn?: string }) => {
      const res = await apiClient<InventoryDTO>(`/admin/inventory/${id}/adjust`, {
        method: 'POST',
        body: { quantity: qty, reason: resn || undefined },
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'inventory'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      setAdjustDialogOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFormErrors({
          quantity: err.details.quantity,
          reason: err.details.reason,
          general: err.message,
        });
      } else if (err.code === 'VALIDATION_ERROR' || err.message) {
        setFormErrors({
          quantity: err.message,
          general: err.message,
        });
      } else {
        setFormErrors({ general: err.message || 'Adjustment failed' });
      }
    },
  });

  const resetForm = () => {
    setQuantity('');
    setReason('');
    setFormErrors({});
  };

  const openRestock = (item: InventoryDTO) => {
    setSelectedItem(item);
    resetForm();
    setRestockDialogOpen(true);
  };

  const openAdjust = (item: InventoryDTO) => {
    setSelectedItem(item);
    resetForm();
    setAdjustDialogOpen(true);
  };

  const openMovements = (item: InventoryDTO) => {
    setSelectedItem(item);
    setMovementsDrawerOpen(true);
  };

  const handleRestockSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    const num = Number(quantity);
    if (!quantity || isNaN(num) || num <= 0) {
      setFormErrors({ quantity: 'Restock quantity must be greater than zero' });
      return;
    }
    if (selectedItem) {
      restockMutation.mutate({ id: selectedItem.id, qty: num, resn: reason.trim() });
    }
  };

  const handleAdjustSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    const num = Number(quantity);
    if (quantity === '' || isNaN(num)) {
      setFormErrors({ quantity: 'Quantity is required' });
      return;
    }
    if (selectedItem) {
      adjustMutation.mutate({ id: selectedItem.id, qty: num, resn: reason.trim() });
    }
  };

  const getMovementTypeBadge = (type: InventoryMovementType) => {
    switch (type) {
      case 'RESTOCK':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-green-500/10 text-green-700 dark:text-green-400">RESTOCK</span>;
      case 'ADJUST':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-blue-500/10 text-blue-700 dark:text-blue-400">ADJUST</span>;
      case 'RESERVE':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-500/10 text-amber-700 dark:text-amber-400">RESERVE</span>;
      case 'RELEASE':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-purple-500/10 text-purple-700 dark:text-purple-400">RELEASE</span>;
      case 'SALE':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-500/10 text-indigo-700 dark:text-indigo-400">SALE</span>;
      case 'RETURN':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400">RETURN</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-muted/20 text-muted">{type}</span>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Boxes className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs sm:text-sm text-muted mt-1">{t('subtitle')}</p>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="bg-surface border border-border rounded-xl p-4 shadow-2xs space-y-4">
        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-3">
          {/* Search */}
          <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
            <div className="relative flex-1">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t('searchPlaceholder')}
                data-testid="inventory-search-input"
                className="w-full pl-9 pr-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              />
            </div>
            <button
              type="submit"
              data-testid="inventory-search-submit"
              className="px-4 py-2 bg-primary text-white text-xs sm:text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors shrink-0"
            >
              {commonT('filter')}
            </button>
          </form>

          {/* Low Stock Toggle & Reset */}
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleToggleLowStock}
              data-testid="filter-low-stock"
              className={cn(
                'flex items-center gap-1.5 px-3 py-2 text-xs sm:text-sm font-medium rounded-lg border transition-all',
                lowStock
                  ? 'bg-amber-500/15 border-amber-500 text-amber-800 dark:text-amber-300'
                  : 'bg-background border-border text-foreground hover:bg-muted/10'
              )}
            >
              <AlertTriangle className={cn('w-4 h-4', lowStock ? 'text-amber-600' : 'text-muted')} />
              <span>{t('lowStockFilter')}</span>
            </button>

            {(q || lowStock) && (
              <button
                type="button"
                onClick={handleResetFilters}
                data-testid="inventory-reset-filters"
                className="flex items-center gap-1 px-3 py-2 text-xs sm:text-sm text-muted hover:text-foreground border border-border rounded-lg bg-background hover:bg-muted/10 transition-colors"
                title={commonT('reset')}
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>{commonT('reset')}</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Inventory Table / States */}
      <div className="bg-surface border border-border rounded-xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 space-y-4">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="animate-pulse flex items-center justify-between gap-4 py-3 border-b border-border/40">
                <div className="space-y-2 flex-1">
                  <div className="h-4 bg-muted/20 rounded w-1/3" />
                  <div className="h-3 bg-muted/15 rounded w-1/4" />
                </div>
                <div className="h-4 bg-muted/20 rounded w-16" />
                <div className="h-4 bg-muted/20 rounded w-16" />
                <div className="h-8 bg-muted/20 rounded w-24" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <div className="p-12 text-center space-y-3">
            <AlertCircle className="w-10 h-10 text-red-500 mx-auto" />
            <h3 className="text-sm font-bold text-foreground">{commonT('errorTitle')}</h3>
            <p className="text-xs text-muted max-w-sm mx-auto">{commonT('errorDesc')}</p>
            <button
              onClick={() => refetch()}
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-xs font-semibold rounded-lg hover:bg-primary-hover transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>{commonT('retry')}</span>
            </button>
          </div>
        ) : items.length === 0 ? (
          <div className="p-12 text-center space-y-3" data-testid="inventory-empty-state">
            <PackageCheck className="w-10 h-10 text-muted mx-auto" />
            <h3 className="text-sm font-bold text-foreground">{commonT('empty')}</h3>
            <p className="text-xs text-muted">No inventory records matching your criteria.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs sm:text-sm">
              <thead>
                <tr className="bg-muted/10 border-b border-border text-muted font-semibold text-[11px] sm:text-xs">
                  <th className="py-3 px-4">{t('product')}</th>
                  <th className="py-3 px-4">{t('sku')}</th>
                  <th className="py-3 px-4 text-center">{t('onHand')}</th>
                  <th className="py-3 px-4 text-center">{t('reserved')}</th>
                  <th className="py-3 px-4 text-center">{t('available')}</th>
                  <th className="py-3 px-4 text-center">{t('threshold')}</th>
                  <th className="py-3 px-4 text-center">{t('status')}</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {items.map((item) => {
                  const isLow = item.available <= item.lowStockThreshold || item.lowStockAlerted;
                  const isOut = item.available <= 0;

                  return (
                    <tr
                      key={item.id}
                      data-testid="inventory-row"
                      data-sku={item.sku}
                      data-low-stock={isLow ? 'true' : 'false'}
                      className={cn(
                        'transition-colors group',
                        isLow
                          ? 'bg-amber-50/60 dark:bg-amber-950/20 border-l-4 border-l-amber-500 hover:bg-amber-100/40 dark:hover:bg-amber-950/30'
                          : 'hover:bg-muted/5'
                      )}
                    >
                      <td className="py-3.5 px-4 font-medium text-foreground">
                        <div className="max-w-[200px] sm:max-w-xs truncate">{item.productName}</div>
                        {item.variantId && (
                          <div className="text-[11px] text-muted">Variant #{item.variantId}</div>
                        )}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs text-muted">{item.sku}</td>
                      <td className="py-3.5 px-4 text-center font-medium">{item.quantityOnHand}</td>
                      <td className="py-3.5 px-4 text-center text-muted">{item.quantityReserved}</td>
                      <td className="py-3.5 px-4 text-center font-bold">
                        <span
                          className={cn(
                            'px-2 py-0.5 rounded text-xs',
                            isOut
                              ? 'bg-red-500/15 text-red-700 dark:text-red-400'
                              : isLow
                              ? 'bg-amber-500/15 text-amber-700 dark:text-amber-400'
                              : 'text-foreground'
                          )}
                        >
                          {item.available}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-center text-muted">{item.lowStockThreshold}</td>
                      <td className="py-3.5 px-4 text-center">
                        {isOut ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-red-500/10 text-red-700 dark:text-red-400">
                            {t('statusOut')}
                          </span>
                        ) : isLow ? (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-amber-500/15 text-amber-700 dark:text-amber-400">
                            <AlertTriangle className="w-3 h-3" />
                            <span>{t('statusLow')}</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-green-500/10 text-green-700 dark:text-green-400">
                            {t('statusHealthy')}
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            type="button"
                            onClick={() => openRestock(item)}
                            data-testid={`restock-btn-${item.sku}`}
                            className="px-2.5 py-1 text-xs font-semibold rounded bg-primary/10 text-primary hover:bg-primary/20 transition-colors"
                          >
                            {t('restock')}
                          </button>
                          <button
                            type="button"
                            onClick={() => openAdjust(item)}
                            data-testid={`adjust-btn-${item.sku}`}
                            className="px-2.5 py-1 text-xs font-medium rounded border border-border hover:bg-muted/10 transition-colors"
                          >
                            {t('adjust')}
                          </button>
                          <button
                            type="button"
                            onClick={() => openMovements(item)}
                            data-testid={`movements-btn-${item.sku}`}
                            className="p-1 text-muted hover:text-foreground rounded hover:bg-muted/10 transition-colors"
                            title={t('history')}
                          >
                            <History className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {meta && meta.totalPages > 1 && (
          <div className="p-4 border-t border-border flex items-center justify-between text-xs sm:text-sm">
            <span className="text-muted">
              {commonT('pageOf', { page: meta.page, total: meta.totalPages })}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page <= 1}
                onClick={() => updateFilters({ page: page - 1 })}
                className="p-1.5 rounded-lg border border-border text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:bg-muted/10 transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages}
                onClick={() => updateFilters({ page: page + 1 })}
                className="p-1.5 rounded-lg border border-border text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:bg-muted/10 transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* RESTOCK MODAL DIALOG */}
      {restockDialogOpen && selectedItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
          <div className="bg-surface border border-border rounded-2xl w-full max-w-md p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-foreground">{t('restockTitle')}</h3>
                <p className="text-xs text-muted">{selectedItem.productName} ({selectedItem.sku})</p>
              </div>
              <button
                type="button"
                onClick={() => setRestockDialogOpen(false)}
                className="p-1 text-muted hover:text-foreground rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleRestockSubmit} className="space-y-4">
              {formErrors.general && (
                <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-600 text-xs">
                  {formErrors.general}
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('quantity')} <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  min="1"
                  step="1"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  placeholder="e.g. 10"
                  data-testid="restock-quantity-input"
                  className={cn(
                    'w-full px-3 py-2 text-sm bg-background border rounded-lg focus:outline-hidden focus:ring-2 transition-all',
                    formErrors.quantity
                      ? 'border-red-500 focus:ring-red-500/20'
                      : 'border-border focus:ring-primary/20 focus:border-primary'
                  )}
                />
                {formErrors.quantity ? (
                  <p data-testid="restock-error-quantity" className="text-xs text-red-600 mt-1 font-medium">
                    {formErrors.quantity}
                  </p>
                ) : (
                  <p className="text-[11px] text-muted mt-1">{t('quantityHelp')}</p>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('reason')}
                </label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder={t('reasonPlaceholder')}
                  data-testid="restock-reason-input"
                  className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                />
                {formErrors.reason && (
                  <p className="text-xs text-red-600 mt-1 font-medium">{formErrors.reason}</p>
                )}
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
                <button
                  type="button"
                  onClick={() => setRestockDialogOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground rounded-lg"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  disabled={restockMutation.isPending}
                  data-testid="restock-submit-btn"
                  className="px-4 py-2 text-xs font-semibold bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors inline-flex items-center gap-1.5"
                >
                  {restockMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>{t('restock')}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ADJUST MODAL DIALOG */}
      {adjustDialogOpen && selectedItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
          <div className="bg-surface border border-border rounded-2xl w-full max-w-md p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-foreground">{t('adjustTitle')}</h3>
                <p className="text-xs text-muted">{selectedItem.productName} ({selectedItem.sku})</p>
              </div>
              <button
                type="button"
                onClick={() => setAdjustDialogOpen(false)}
                className="p-1 text-muted hover:text-foreground rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAdjustSubmit} className="space-y-4">
              {formErrors.general && (
                <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30 text-red-600 text-xs">
                  {formErrors.general}
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('quantity')} <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  step="1"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  placeholder="e.g. +5 or -2"
                  data-testid="adjust-quantity-input"
                  className={cn(
                    'w-full px-3 py-2 text-sm bg-background border rounded-lg focus:outline-hidden focus:ring-2 transition-all',
                    formErrors.quantity
                      ? 'border-red-500 focus:ring-red-500/20'
                      : 'border-border focus:ring-primary/20 focus:border-primary'
                  )}
                />
                {formErrors.quantity ? (
                  <p data-testid="adjust-error-quantity" className="text-xs text-red-600 mt-1 font-medium">
                    {formErrors.quantity}
                  </p>
                ) : (
                  <p className="text-[11px] text-muted mt-1">{t('adjustQuantityHelp')}</p>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('reason')}
                </label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder={t('reasonPlaceholder')}
                  data-testid="adjust-reason-input"
                  className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
                <button
                  type="button"
                  onClick={() => setAdjustDialogOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground rounded-lg"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  disabled={adjustMutation.isPending}
                  data-testid="adjust-submit-btn"
                  className="px-4 py-2 text-xs font-semibold bg-primary text-white rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors inline-flex items-center gap-1.5"
                >
                  {adjustMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  <span>{t('adjust')}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MOVEMENTS HISTORY DRAWER */}
      {movementsDrawerOpen && selectedItem && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-xs">
          <div
            data-testid="inventory-movements-drawer"
            className="w-full max-w-md bg-surface border-l border-border h-full shadow-2xl flex flex-col p-6 space-y-4 overflow-hidden"
          >
            <div className="flex items-center justify-between border-b border-border pb-4">
              <div>
                <h3 className="text-base font-bold text-foreground">{t('movementsTitle')}</h3>
                <p className="text-xs text-muted">
                  {t('movementsSubtitle', { sku: selectedItem.sku })}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setMovementsDrawerOpen(false)}
                className="p-1.5 text-muted hover:text-foreground rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-3 pr-1">
              {isLoadingMovements ? (
                <div className="py-12 flex flex-col items-center justify-center space-y-2">
                  <Loader2 className="w-6 h-6 text-primary animate-spin" />
                  <span className="text-xs text-muted">{commonT('loading')}</span>
                </div>
              ) : !movementsData?.data || movementsData.data.length === 0 ? (
                <div className="py-12 text-center text-xs text-muted">
                  {t('noMovements')}
                </div>
              ) : (
                <div className="divide-y divide-border/60">
                  {movementsData.data.map((movement) => (
                    <div
                      key={movement.id}
                      data-testid="inventory-movement-item"
                      data-movement-type={movement.type}
                      className="py-3 space-y-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          {getMovementTypeBadge(movement.type)}
                          <span
                            className={cn(
                              'font-bold font-mono',
                              movement.quantity > 0 ? 'text-green-600' : 'text-red-500'
                            )}
                          >
                            {movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity}
                          </span>
                        </div>
                        <span className="text-[11px] text-muted font-mono">
                          {new Date(movement.createdAt).toLocaleString(locale === 'km' ? 'km-KH' : 'en-US', {
                            dateStyle: 'short',
                            timeStyle: 'short',
                          })}
                        </span>
                      </div>

                      {movement.reason && (
                        <p className="text-foreground text-xs">{movement.reason}</p>
                      )}

                      <div className="flex items-center gap-3 text-[11px] text-muted">
                        {movement.refOrderId && (
                          <span>Order #{movement.refOrderId}</span>
                        )}
                        {movement.actorId && (
                          <span>{t('actor')}: User #{movement.actorId}</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
