'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import {
  AdminPromotionDTO,
  AdminPromotionRequest,
  PromotionItemRequest,
  PromotionType,
  ProductCardDTO,
  ProductDetailDTO,
} from '@/types/api';
import { toCents, formatUsd } from '@/lib/money';
import {
  Sparkles,
  Plus,
  Edit2,
  Trash2,
  Loader2,
  X,
  AlertCircle,
  Calendar,
  Check,
  Search,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

interface SelectedItem {
  productId: number;
  variantId?: number;
  name: string;
  sku?: string;
  attributes?: Record<string, string>;
  priceCents: number;
  salePrice: string; // dollars as typed
}

const itemKey = (productId: number, variantId?: number) => `${productId}:${variantId ?? 'p'}`;

/** Parse "1199.00" -> 119900; returns null when not a valid non-negative dollar amount */
function parseDollars(value: string): number | null {
  const v = value.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(v)) return null;
  return toCents(parseFloat(v));
}

function variantLabel(attributes?: Record<string, string>) {
  return attributes ? Object.values(attributes).join(' / ') : '';
}

interface PickerRowProps {
  product: ProductCardDTO;
  isFlash: boolean;
  selected: Record<string, SelectedItem>;
  rowErrors: Record<string, string>;
  onToggle: (item: Omit<SelectedItem, 'salePrice'>) => void;
  onPrice: (key: string, value: string) => void;
}

function PickerRow({ product, isFlash, selected, rowErrors, onToggle, onPrice }: PickerRowProps) {
  const t = useTranslations('admin.promotions');
  const [open, setOpen] = useState(false);

  const { data: detail, isLoading } = useQuery({
    queryKey: ['admin', 'promotion-picker', product.id],
    queryFn: async () => {
      const res = await apiClient<ProductDetailDTO>(`/admin/products/${product.id}`);
      return res.data;
    },
    enabled: open,
  });

  const selectedCount = Object.values(selected).filter((i) => i.productId === product.id).length;
  const variants = detail?.variants ?? [];

  const options: { key: string; label: string; sku?: string; priceCents: number; item: Omit<SelectedItem, 'salePrice'> }[] =
    variants.length > 0
      ? variants.map((v) => ({
          key: itemKey(product.id, v.id),
          label: variantLabel(v.attributes),
          sku: v.sku,
          priceCents: v.priceCents,
          item: {
            productId: product.id,
            variantId: v.id,
            name: product.name,
            sku: v.sku,
            attributes: v.attributes,
            priceCents: v.priceCents,
          },
        }))
      : detail
        ? [
            {
              key: itemKey(product.id),
              label: t('wholeProduct'),
              sku: product.sku,
              priceCents: detail.priceCents,
              item: { productId: product.id, name: product.name, sku: product.sku, priceCents: detail.priceCents },
            },
          ]
        : [];

  return (
    <div data-testid={`picker-item-${product.id}`} className="py-1">
      <button
        type="button"
        aria-expanded={open}
        data-testid={`picker-toggle-${product.id}`}
        onClick={() => setOpen(!open)}
        className="w-full p-2 rounded-lg flex items-center justify-between text-left hover:bg-muted/10"
      >
        <span className="min-w-0 pr-2">
          <span className="block text-xs truncate font-semibold text-foreground">{product.name}</span>
          <span className="block text-[10px] text-muted font-mono">{product.sku}</span>
        </span>
        <span className="flex items-center gap-2 shrink-0">
          {selectedCount > 0 && (
            <span className="text-[10px] font-bold text-primary">{selectedCount}</span>
          )}
          {open ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
        </span>
      </button>

      {open && (
        <div className="pl-3 pr-1 pb-1 space-y-1">
          {isLoading && <Loader2 className="w-4 h-4 animate-spin text-primary" />}
          {options.map((o) => {
            const sel = selected[o.key];
            const err = rowErrors[o.key];
            return (
              <div
                key={o.key}
                data-testid={o.item.variantId ? `variant-row-${o.item.variantId}` : `product-row-${product.id}`}
                className="rounded-lg border border-border/50 p-2 space-y-1"
              >
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id={`pick-${o.key}`}
                    data-testid={o.item.variantId ? `variant-checkbox-${o.item.variantId}` : `product-checkbox-${product.id}`}
                    checked={Boolean(sel)}
                    onChange={() => onToggle(o.item)}
                    className="rounded border-border"
                  />
                  <label htmlFor={`pick-${o.key}`} className="flex-1 min-w-0 text-xs cursor-pointer">
                    <span className="font-semibold text-foreground">{o.label}</span>
                    {o.sku && <span className="ml-2 font-mono text-[10px] text-muted">{o.sku}</span>}
                  </label>
                  <span className="text-xs font-mono font-bold text-foreground">{formatUsd(o.priceCents)}</span>
                </div>
                {sel && isFlash && (
                  <div className="flex items-center gap-2 pl-6">
                    <label htmlFor={`sale-${o.key}`} className="text-[11px] text-muted">
                      {t('salePrice')} ($)
                    </label>
                    <input
                      id={`sale-${o.key}`}
                      type="text"
                      inputMode="decimal"
                      data-testid={
                        o.item.variantId ? `variant-sale-price-${o.item.variantId}` : `product-sale-price-${product.id}`
                      }
                      value={sel.salePrice}
                      onChange={(e) => onPrice(o.key, e.target.value)}
                      placeholder="0.00"
                      className="w-28 px-2 py-1 text-xs font-mono bg-background border border-border rounded-md"
                    />
                  </div>
                )}
                {err && (
                  <p data-testid="picker-item-error" role="alert" className="text-[11px] text-red-700 pl-6">
                    {err}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function AdminPromotionsPage() {
  const t = useTranslations('admin.promotions');
  const commonT = useTranslations('admin.common');
  const queryClient = useQueryClient();

  const [formOpen, setFormOpen] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState<AdminPromotionDTO | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  // Form Fields
  const [name, setName] = useState('');
  const [nameKm, setNameKm] = useState('');
  const [type, setType] = useState<PromotionType>('FLASH_SALE');
  const [discountPercent, setDiscountPercent] = useState('10');
  const [thresholdDollars, setThresholdDollars] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [active, setActive] = useState(true);
  const [selectedItems, setSelectedItems] = useState<Record<string, SelectedItem>>({});
  const [submittedOrder, setSubmittedOrder] = useState<string[]>([]);
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [productSearch, setProductSearch] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Query promotions
  const { data: promotions, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.promotions,
    queryFn: async () => {
      const res = await apiClient<AdminPromotionDTO[]>('/admin/promotions');
      return res.data;
    },
  });

  // Query products for product picker
  const { data: productsData } = useQuery({
    queryKey: queryKeys.admin.products({ page: 0, size: 100 }),
    queryFn: async () => {
      const res = await apiClient<ProductCardDTO[]>('/admin/products', {
        query: { page: 0, size: 100 },
      });
      return res.data;
    },
  });
  const allProducts = productsData || [];

  const handleSaveError = (err: ApiError, fallback: string) => {
    if (err.details && typeof err.details === 'object') {
      setFieldErrors(err.details);
    }
    // Validation/conflict responses: keep the message visible in the form, details sit under each row
    if (err.status === 409 || err.status === 400) {
      setFormError(err.message || fallback);
    } else {
      toast.error(err.message || fallback);
    }
  };

  const createMutation = useMutation({
    mutationFn: async (payload: AdminPromotionRequest) => {
      const res = await apiClient<AdminPromotionDTO>('/admin/promotions', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.promotions });
      toast.success('Promotion created successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => handleSaveError(err, 'Failed to create promotion'),
  });

  const updateMutation = useMutation({
    mutationFn: async ({ id, payload }: { id: number; payload: AdminPromotionRequest }) => {
      const res = await apiClient<AdminPromotionDTO>(`/admin/promotions/${id}`, {
        method: 'PUT',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.promotions });
      toast.success('Promotion updated successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => handleSaveError(err, 'Failed to update promotion'),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient(`/admin/promotions/${id}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.promotions });
      toast.success('Promotion deleted successfully');
      setDeleteConfirmId(null);
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to delete promotion');
      setDeleteConfirmId(null);
    },
  });

  const resetForm = () => {
    setEditingPromotion(null);
    setName('');
    setNameKm('');
    setType('FLASH_SALE');
    setDiscountPercent('10');
    setThresholdDollars('');
    setStartsAt('');
    setEndsAt('');
    setActive(true);
    setSelectedItems({});
    setSubmittedOrder([]);
    setClientErrors({});
    setFormError('');
    setProductSearch('');
    setFieldErrors({});
  };

  const openCreateModal = () => {
    resetForm();
    // Default starts now, ends in 7 days
    const now = new Date();
    const future = new Date(Date.now() + 7 * 86400000);
    setStartsAt(now.toISOString().slice(0, 16));
    setEndsAt(future.toISOString().slice(0, 16));
    setFormOpen(true);
  };

  const openEditModal = (p: AdminPromotionDTO) => {
    resetForm();
    setEditingPromotion(p);
    setName(p.name);
    setNameKm(p.nameKm || '');
    setType(p.type);
    setDiscountPercent(p.discountPercent ? String(p.discountPercent) : '10');
    setThresholdDollars(p.thresholdCents ? (p.thresholdCents / 100).toFixed(2) : '');
    setStartsAt(p.startsAt ? p.startsAt.slice(0, 16) : '');
    setEndsAt(p.endsAt ? p.endsAt.slice(0, 16) : '');
    setActive(p.active);
    const initial: Record<string, SelectedItem> = {};
    if (p.items && p.items.length > 0) {
      for (const it of p.items) {
        initial[itemKey(it.productId, it.variantId)] = {
          productId: it.productId,
          variantId: it.variantId,
          name: it.productName,
          sku: it.sku,
          attributes: it.attributes,
          priceCents: it.priceCents,
          salePrice: it.salePriceCents ? (it.salePriceCents / 100).toFixed(2) : '',
        };
      }
    } else {
      for (const pid of p.productIds || []) {
        initial[itemKey(pid)] = { productId: pid, name: `#${pid}`, priceCents: 0, salePrice: '' };
      }
    }
    setSelectedItems(initial);
    setFormOpen(true);
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setFormError('');

    const entries = Object.entries(selectedItems);
    const errors: Record<string, string> = {};
    const items: PromotionItemRequest[] = [];
    for (const [key, it] of entries) {
      let salePriceCents: number | undefined;
      if (type === 'FLASH_SALE') {
        const cents = parseDollars(it.salePrice);
        if (cents === null || cents <= 0) {
          errors[key] = t('salePriceRequired');
        } else if (it.priceCents > 0 && cents >= it.priceCents) {
          errors[key] = t('salePriceBelow', { price: formatUsd(it.priceCents) });
        } else {
          salePriceCents = cents;
        }
      }
      items.push({ productId: it.productId, variantId: it.variantId, salePriceCents });
    }
    setClientErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSubmittedOrder(entries.map(([key]) => key));

    const thresholdCents = thresholdDollars
      ? Math.round((parseFloat(thresholdDollars) || 0) * 100)
      : undefined;

    const payload: AdminPromotionRequest = {
      name: name.trim(),
      nameKm: nameKm.trim() || undefined,
      type,
      discountPercent: discountPercent ? parseInt(discountPercent, 10) : undefined,
      thresholdCents,
      startsAt: new Date(startsAt).toISOString(),
      endsAt: new Date(endsAt).toISOString(),
      active,
      // On edit always send items (even []) so unticking everything replaces the list; omitting it leaves products untouched
      items: editingPromotion || items.length > 0 ? items : undefined,
    };

    if (editingPromotion) {
      updateMutation.mutate({ id: editingPromotion.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const toggleItem = (item: Omit<SelectedItem, 'salePrice'>) => {
    const key = itemKey(item.productId, item.variantId);
    setSelectedItems((prev) => {
      const next = { ...prev };
      if (next[key]) delete next[key];
      else next[key] = { ...item, salePrice: '' };
      return next;
    });
  };

  const setItemPrice = (key: string, value: string) => {
    setSelectedItems((prev) => ({ ...prev, [key]: { ...prev[key], salePrice: value } }));
  };

  // Row-level errors: client validation + backend details keyed items[i].field / items[i]
  const rowErrors: Record<string, string> = { ...clientErrors };
  submittedOrder.forEach((key, idx) => {
    const msgs = Object.entries(fieldErrors)
      .filter(([k]) => k === `items[${idx}]` || k.startsWith(`items[${idx}].`))
      .map(([, v]) => String(v));
    if (msgs.length > 0 && !rowErrors[key]) rowErrors[key] = msgs.join(' · ');
  });

  const filteredPickerProducts = allProducts.filter(
    (p) =>
      p.name.toLowerCase().includes(productSearch.toLowerCase()) ||
      (p.sku && p.sku.toLowerCase().includes(productSearch.toLowerCase()))
  );

  const items = promotions || [];

  return (
    <div className="space-y-6" data-testid="admin-promotions-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>

        <button
          type="button"
          data-testid="add-promotion-btn"
          onClick={openCreateModal}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs self-start sm:self-auto"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>{t('createPromotion')}</span>
        </button>
      </div>

      {/* Table */}
      <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 flex items-center justify-center space-x-2">
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
            <span className="text-xs text-muted">{commonT('loading')}</span>
          </div>
        ) : isError ? (
          <div className="p-8 text-center space-y-2">
            <AlertCircle className="w-8 h-8 text-red-700 mx-auto" />
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
                  <th className="py-3 px-4">{t('nameEn')}</th>
                  <th className="py-3 px-4">{t('type')}</th>
                  <th className="py-3 px-4">{t('discountPercent')}</th>
                  <th className="py-3 px-4">Period</th>
                  <th className="py-3 px-4 text-center">{t('active')}</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {items.map((item) => (
                  <tr
                    key={item.id}
                    data-testid="promotion-row"
                    data-promotion-id={item.id}
                    className="hover:bg-muted/5 transition-colors"
                  >
                    <td className="py-3 px-4 font-bold text-foreground">
                      <div>{item.name}</div>
                      {item.nameKm && <div className="text-[11px] text-muted font-normal">{item.nameKm}</div>}
                    </td>
                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-primary/10 text-primary">
                        {item.type}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-mono font-bold text-foreground">
                      {item.discountPercent ? `${item.discountPercent}%` : '—'}
                    </td>
                    <td className="py-3 px-4 text-[11px] text-muted space-y-0.5">
                      <div>Start: {new Date(item.startsAt).toLocaleDateString()}</div>
                      <div>End: {new Date(item.endsAt).toLocaleDateString()}</div>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        className={cn(
                          'inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold',
                          item.active
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                            : 'bg-muted/20 text-muted'
                        )}
                      >
                        {item.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEditModal(item)}
                          data-testid={`edit-promotion-btn-${item.id}`}
                          className="p-1.5 rounded-lg border border-border hover:bg-muted/10 text-muted hover:text-foreground"
                          title={t('editPromotion')}
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteConfirmId(item.id)}
                          data-testid={`delete-promotion-btn-${item.id}`}
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

      {/* CREATE / EDIT PROMOTION MODAL */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div
            data-testid="promotion-form-modal"
            className="bg-surface border border-border rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-xl text-left max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">
                {editingPromotion ? t('editPromotion') : t('createPromotion')}
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
                    {t('nameEn')} <span className="text-red-700">*</span>
                  </label>
                  <input
                    type="text"
                    data-testid="promotion-name-input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g. Flash Sale March 2026"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                    required
                  />
                  {fieldErrors.name && (
                    <p className="text-xs text-red-700 mt-1">{fieldErrors.name}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('nameKm')}
                  </label>
                  <input
                    type="text"
                    data-testid="promotion-name-km-input"
                    value={nameKm}
                    onChange={(e) => setNameKm(e.target.value)}
                    placeholder="e.g. ការលក់ពិសេស Flash Sale"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('type')}
                  </label>
                  <select
                    data-testid="promotion-type-select"
                    value={type}
                    onChange={(e) => setType(e.target.value as PromotionType)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                  >
                    <option value="FLASH_SALE">{t('typeFlashSale')}</option>
                    <option value="SEASONAL">{t('typeSeasonal')}</option>
                    <option value="BOGO">{t('typeBogo')}</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('discountPercent')}
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="100"
                    data-testid="promotion-discount-input"
                    value={discountPercent}
                    onChange={(e) => setDiscountPercent(e.target.value)}
                    placeholder="10"
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('startsAt')} <span className="text-red-700">*</span>
                  </label>
                  <input
                    type="datetime-local"
                    data-testid="promotion-starts-input"
                    value={startsAt}
                    onChange={(e) => setStartsAt(e.target.value)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                    required
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('endsAt')} <span className="text-red-700">*</span>
                  </label>
                  <input
                    type="datetime-local"
                    data-testid="promotion-ends-input"
                    value={endsAt}
                    onChange={(e) => setEndsAt(e.target.value)}
                    className="w-full px-3 py-2 bg-background border border-border rounded-lg"
                    required
                  />
                </div>
              </div>

              {formError && (
                <p
                  data-testid="promotion-form-error"
                  role="alert"
                  className="text-xs font-semibold text-red-700 bg-red-500/10 px-3 py-2 rounded-lg"
                >
                  {formError}
                </p>
              )}

              {/* Product Picker */}
              <div className="space-y-2 pt-2 border-t border-border">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-semibold text-foreground">
                    {t('selectProducts')}
                  </label>
                  <span className="text-[11px] font-bold text-primary">
                    {t('selectedCount', { count: Object.keys(selectedItems).length })}
                  </span>
                </div>

                <div className="relative">
                  <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
                  <input
                    type="text"
                    placeholder="Filter products..."
                    value={productSearch}
                    onChange={(e) => setProductSearch(e.target.value)}
                    className="w-full pl-8 pr-3 py-1.5 text-xs bg-background border border-border rounded-lg"
                  />
                </div>

                <div
                  data-testid="promotion-product-picker"
                  className="max-h-72 overflow-y-auto border border-border rounded-xl p-2 space-y-1 bg-background divide-y divide-border/30"
                >
                  {filteredPickerProducts.map((p) => (
                    <PickerRow
                      key={p.id}
                      product={p}
                      isFlash={type === 'FLASH_SALE'}
                      selected={selectedItems}
                      rowErrors={rowErrors}
                      onToggle={toggleItem}
                      onPrice={setItemPrice}
                    />
                  ))}
                </div>
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="promo-active"
                  data-testid="promotion-active-checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="rounded border-border text-primary"
                />
                <label htmlFor="promo-active" className="text-xs font-semibold text-foreground">
                  {t('active')}
                </label>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-border">
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
                  data-testid="promotion-submit-btn"
                  className="px-5 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90"
                >
                  {t('savePromotion')}
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
            <h3 className="text-base font-bold text-foreground">Delete Promotion</h3>
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
                data-testid="confirm-delete-promo-btn"
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
