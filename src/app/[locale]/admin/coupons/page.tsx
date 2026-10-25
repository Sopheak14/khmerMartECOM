'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { AdminCouponDTO, AdminCouponRequest, CouponType } from '@/types/api';
import { formatUsd } from '@/lib/money';
import {
  Ticket,
  Plus,
  Edit2,
  Trash2,
  Loader2,
  X,
  AlertCircle,
  Clock,
  CheckCircle2,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function AdminCouponsPage() {
  const t = useTranslations('admin.coupons');
  const commonT = useTranslations('admin.common');
  const queryClient = useQueryClient();

  const [formOpen, setFormOpen] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<AdminCouponDTO | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  // Form Fields
  const [code, setCode] = useState('');
  const [type, setType] = useState<CouponType>('FIXED');
  const [valueInput, setValueInput] = useState('');
  const [description, setDescription] = useState('');
  const [minOrderDollars, setMinOrderDollars] = useState('');
  const [maxDiscountDollars, setMaxDiscountDollars] = useState('');
  const [maxUses, setMaxUses] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [active, setActive] = useState(true);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { data: coupons, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.coupons,
    queryFn: async () => {
      const res = await apiClient<AdminCouponDTO[]>('/admin/coupons');
      return res.data;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (payload: AdminCouponRequest) => {
      const res = await apiClient<AdminCouponDTO>('/admin/coupons', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.coupons });
      toast.success('Coupon created successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        toast.error(err.message || 'Failed to create coupon');
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ id, payload }: { id: number; payload: AdminCouponRequest }) => {
      const res = await apiClient<AdminCouponDTO>(`/admin/coupons/${id}`, {
        method: 'PUT',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.coupons });
      toast.success('Coupon updated successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        toast.error(err.message || 'Failed to update coupon');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient(`/admin/coupons/${id}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.coupons });
      toast.success('Coupon deleted successfully');
      setDeleteConfirmId(null);
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to delete coupon');
      setDeleteConfirmId(null);
    },
  });

  const resetForm = () => {
    setEditingCoupon(null);
    setCode('');
    setType('FIXED');
    setValueInput('');
    setDescription('');
    setMinOrderDollars('');
    setMaxDiscountDollars('');
    setMaxUses('');
    setStartsAt('');
    setEndsAt('');
    setActive(true);
    setFieldErrors({});
  };

  const openCreateModal = () => {
    resetForm();
    setFormOpen(true);
  };

  const openEditModal = (c: AdminCouponDTO) => {
    resetForm();
    setEditingCoupon(c);
    setCode(c.code);
    setType(c.type);
    setValueInput(c.type === 'FIXED' ? (c.value / 100).toFixed(2) : String(c.value));
    setDescription(c.description || '');
    setMinOrderDollars(c.minOrderCents ? (c.minOrderCents / 100).toFixed(2) : '');
    setMaxDiscountDollars(c.maxDiscountCents ? (c.maxDiscountCents / 100).toFixed(2) : '');
    setMaxUses(c.maxUses ? String(c.maxUses) : '');
    setStartsAt(c.startsAt ? c.startsAt.slice(0, 16) : '');
    setEndsAt(c.endsAt ? c.endsAt.slice(0, 16) : '');
    setActive(c.active);
    setFormOpen(true);
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const valNum = parseFloat(valueInput) || 0;
    const value = type === 'FIXED' ? Math.round(valNum * 100) : Math.round(valNum);

    const minOrderCents = minOrderDollars
      ? Math.round((parseFloat(minOrderDollars) || 0) * 100)
      : undefined;
    const maxDiscountCents = maxDiscountDollars
      ? Math.round((parseFloat(maxDiscountDollars) || 0) * 100)
      : undefined;

    const payload: AdminCouponRequest = {
      code: code.trim().toUpperCase(),
      type,
      value,
      description: description.trim() || undefined,
      minOrderCents,
      maxDiscountCents,
      maxUses: maxUses ? parseInt(maxUses, 10) : undefined,
      startsAt: startsAt ? new Date(startsAt).toISOString() : undefined,
      endsAt: endsAt ? new Date(endsAt).toISOString() : undefined,
      active,
    };

    if (editingCoupon) {
      updateMutation.mutate({ id: editingCoupon.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const formatDatePhnomPenh = (iso?: string) => {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleString('en-US', {
        timeZone: 'Asia/Phnom_Penh',
        dateStyle: 'medium',
        timeStyle: 'short',
      });
    } catch {
      return iso;
    }
  };

  const items = coupons || [];

  return (
    <div className="space-y-6" data-testid="admin-coupons-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight flex items-center gap-2">
            <Ticket className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>

        <button
          type="button"
          data-testid="add-coupon-btn"
          onClick={openCreateModal}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs self-start sm:self-auto"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>{t('createCoupon')}</span>
        </button>
      </div>

      {/* Table Container */}
      <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 flex items-center justify-center space-x-2">
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
            <span className="text-xs text-muted">{commonT('loading')}</span>
          </div>
        ) : isError ? (
          <div className="p-8 text-center space-y-2">
            <AlertCircle className="w-8 h-8 text-red-500 mx-auto" />
            <p className="text-xs text-muted">{commonT('errorTitle')}</p>
            <button
              onClick={() => refetch()}
              className="px-3 py-1.5 bg-primary text-white text-xs rounded-lg"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : items.length === 0 ? (
          <div className="p-12 text-center text-xs text-muted">{commonT('empty')}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead>
                <tr className="bg-muted/10 border-b border-border text-muted font-semibold text-[11px] sm:text-xs">
                  <th className="py-3 px-4">{t('code')}</th>
                  <th className="py-3 px-4">{t('type')}</th>
                  <th className="py-3 px-4">{t('value')}</th>
                  <th className="py-3 px-4">{t('minOrder')}</th>
                  <th className="py-3 px-4">{t('usageLimit')}</th>
                  <th className="py-3 px-4">Validity (Asia/Phnom_Penh)</th>
                  <th className="py-3 px-4 text-center">{t('active')}</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {items.map((item) => (
                  <tr
                    key={item.id}
                    data-testid="coupon-row"
                    data-coupon-code={item.code}
                    className="hover:bg-muted/5 transition-colors"
                  >
                    <td className="py-3 px-4 font-mono font-bold text-primary">
                      {item.code}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-muted/20 text-foreground">
                        {item.type}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-mono font-bold text-foreground">
                      {item.type === 'PERCENT' ? `${item.value}%` : formatUsd(item.value)}
                    </td>
                    <td className="py-3 px-4 font-mono text-muted">
                      {item.minOrderCents ? formatUsd(item.minOrderCents) : '—'}
                    </td>
                    <td className="py-3 px-4 text-muted">
                      {item.usedCount} / {item.maxUses ?? '∞'}
                    </td>
                    <td className="py-3 px-4 text-[11px] text-muted space-y-0.5">
                      <div>From: {formatDatePhnomPenh(item.startsAt)}</div>
                      <div>To: {formatDatePhnomPenh(item.endsAt)}</div>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={cn(
                          'inline-block px-2 py-0.5 rounded-full text-[11px] font-bold',
                          item.active
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                            : 'bg-muted/20 text-muted'
                        )}
                      >
                        {item.active ? t('statusActive') : t('statusInactive')}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEditModal(item)}
                          data-testid={`edit-coupon-btn-${item.code}`}
                          className="p-1.5 rounded-lg border border-border hover:bg-muted/10 text-muted hover:text-foreground"
                          title={t('editCoupon')}
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteConfirmId(item.id)}
                          data-testid={`delete-coupon-btn-${item.code}`}
                          className="p-1.5 rounded-lg border border-border hover:bg-red-500/10 text-muted hover:text-red-600"
                          title="Delete"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* CREATE / EDIT MODAL */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div
            data-testid="coupon-form-modal"
            className="bg-surface border border-border rounded-2xl max-w-lg w-full p-6 space-y-4 shadow-xl text-left max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">
                {editingCoupon ? t('editCoupon') : t('createCoupon')}
              </h3>
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="text-muted hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleFormSubmit} className="space-y-4 text-xs sm:text-sm">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('code')} <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    data-testid="coupon-code-input"
                    value={code}
                    onChange={(e) => setCode(e.target.value.toUpperCase())}
                    placeholder="e.g. SAVE5 or E2E-TEST"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg uppercase font-mono font-bold"
                    required
                  />
                  {fieldErrors.code && (
                    <p className="text-xs text-red-600 mt-1">{fieldErrors.code}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('type')}
                  </label>
                  <select
                    data-testid="coupon-type-select"
                    value={type}
                    onChange={(e) => setType(e.target.value as CouponType)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                  >
                    <option value="FIXED">{t('typeFixed')}</option>
                    <option value="PERCENT">{t('typePercent')}</option>
                    <option value="FREE_SHIPPING">{t('typeFreeShipping')}</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('value')} <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    data-testid="coupon-value-input"
                    value={valueInput}
                    onChange={(e) => setValueInput(e.target.value)}
                    placeholder={type === 'PERCENT' ? '10' : '3.00'}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg font-mono font-bold"
                    required
                  />
                  <p className="text-[11px] text-muted mt-1">
                    {type === 'PERCENT' ? t('valuePercentHelp') : t('valueFixedHelp')}
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('minOrder')}
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    data-testid="coupon-min-order-input"
                    value={minOrderDollars}
                    onChange={(e) => setMinOrderDollars(e.target.value)}
                    placeholder="e.g. 10.00"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('maxDiscount')}
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    data-testid="coupon-max-discount-input"
                    value={maxDiscountDollars}
                    onChange={(e) => setMaxDiscountDollars(e.target.value)}
                    placeholder="e.g. 20.00"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('usageLimit')}
                  </label>
                  <input
                    type="number"
                    min="1"
                    data-testid="coupon-max-uses-input"
                    value={maxUses}
                    onChange={(e) => setMaxUses(e.target.value)}
                    placeholder="e.g. 100"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('startsAt')}
                  </label>
                  <input
                    type="datetime-local"
                    data-testid="coupon-starts-at-input"
                    value={startsAt}
                    onChange={(e) => setStartsAt(e.target.value)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('endsAt')}
                  </label>
                  <input
                    type="datetime-local"
                    data-testid="coupon-ends-at-input"
                    value={endsAt}
                    onChange={(e) => setEndsAt(e.target.value)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('description')}
                </label>
                <input
                  type="text"
                  data-testid="coupon-desc-input"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="e.g. Special promotional discount"
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="coupon-active"
                  data-testid="coupon-active-checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="rounded border-border text-primary"
                />
                <label htmlFor="coupon-active" className="text-xs font-semibold text-foreground">
                  {t('active')}
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-4 border-t border-border">
                <button
                  type="button"
                  onClick={() => setFormOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  data-testid="coupon-submit-btn"
                  className="px-5 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90"
                >
                  {t('saveCoupon')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DELETE CONFIRM DIALOG */}
      {deleteConfirmId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div className="bg-surface border border-border rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-xl text-center">
            <Trash2 className="w-10 h-10 text-destructive mx-auto" />
            <h3 className="text-base font-bold text-foreground">Delete Coupon</h3>
            <p className="text-xs text-muted leading-relaxed">{t('deleteConfirm')}</p>
            <div className="flex items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => setDeleteConfirmId(null)}
                className="px-4 py-2 text-xs font-semibold border border-border rounded-xl"
              >
                {commonT('cancel')}
              </button>
              <button
                type="button"
                data-testid="confirm-delete-coupon-btn"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteConfirmId)}
                className="px-4 py-2 text-xs font-bold bg-destructive text-destructive-foreground rounded-xl hover:bg-destructive/90"
              >
                {deleteMutation.isPending ? 'Deleting...' : commonT('confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
