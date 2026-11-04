'use client';

import React, { useState, useRef } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import {
  ProductCardDTO,
  ProductDetailDTO,
  CategoryDTO,
  AdminVariantRequest,
  CsvImportResultDTO,
  ProductImageDTO,
} from '@/types/api';
import { formatUsd } from '@/lib/money';
import { getImageUrl } from '@/lib/images';
import { SafeImage } from '@/components/common/SafeImage';
import { useAuthStore } from '@/lib/store/useAuthStore';
import {
  Package,
  Search,
  Filter,
  Plus,
  Edit2,
  Trash2,
  Download,
  Upload,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  Loader2,
  X,
  AlertCircle,
  Image as ImageIcon,
  Check,
  Sparkles,
  Layers,
  ArrowUpDown,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

interface AttributeRow {
  name: string;
  values: string;
}

export default function AdminProductsPage() {
  const t = useTranslations('admin.products');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const q = searchParams.get('q') || '';
  const categoryParam = searchParams.get('category') || '';
  const activeParam = searchParams.get('active') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 20;

  const [searchInput, setSearchInput] = useState(q);

  // Modal states
  const [formOpen, setFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductDetailDTO | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importResult, setImportResult] = useState<CsvImportResultDTO | null>(null);
  const [isExporting, setIsExporting] = useState(false);

  // Form Fields
  const [name, setName] = useState('');
  const [nameKm, setNameKm] = useState('');
  const [sku, setSku] = useState('');
  const [slug, setSlug] = useState('');
  const [brand, setBrand] = useState('');
  const [description, setDescription] = useState('');
  const [descriptionKm, setDescriptionKm] = useState('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [priceDollars, setPriceDollars] = useState('');
  const [compareAtDollars, setCompareAtDollars] = useState('');
  const [initialStock, setInitialStock] = useState('10');
  const [active, setActive] = useState(true);

  // Variants Generator State
  const [attributes, setAttributes] = useState<AttributeRow[]>([]);
  const [variants, setVariants] = useState<AdminVariantRequest[]>([]);

  // Images state (for editing product)
  const [images, setImages] = useState<ProductImageDTO[]>([]);
  const [isUploadingImage, setIsUploadingImage] = useState(false);

  // Validation details error map
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formGeneralError, setFormGeneralError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

  const updateFilters = (newParams: Record<string, string | number | boolean | null | undefined>) => {
    const current = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') {
        current.delete(k);
      } else {
        current.set(k, String(v));
      }
    });
    router.push(`${pathname}?${current.toString()}`);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateFilters({ q: searchInput.trim() || null, page: 1 });
  };

  // Queries
  const { data: categoriesData } = useQuery({
    queryKey: queryKeys.admin.categories,
    queryFn: async () => {
      const res = await apiClient<CategoryDTO[]>('/admin/categories');
      return res.data;
    },
  });
  const categories = categoriesData || [];

  const { data: productsData, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.products({
      q: q || undefined,
      category: categoryParam || undefined,
      active: activeParam !== '' ? activeParam === 'true' : undefined,
      page: page - 1,
      size: pageSize,
    }),
    queryFn: async () => {
      const res = await apiClient<ProductCardDTO[]>('/admin/products', {
        query: {
          q: q || undefined,
          category: categoryParam || undefined,
          active: activeParam !== '' ? activeParam === 'true' : undefined,
          page: page - 1,
          size: pageSize,
        },
      });
      return res;
    },
  });

  const products = productsData?.data || [];

  // The list DTO carries no `active` flag, so ask the backend which products are inactive
  // (soft-deleted products that have orders stay listed but are hidden from the storefront).
  const { data: inactiveData } = useQuery({
    queryKey: ['admin', 'products', 'inactive-ids'],
    queryFn: async () => {
      const res = await apiClient<ProductCardDTO[]>('/admin/products', {
        query: { active: false, page: 0, size: 200 },
      });
      return new Set((res.data || []).map((p) => p.id));
    },
  });
  const isInactive = (item: ProductCardDTO) => item.active === false || !!inactiveData?.has(item.id);
  const meta = productsData?.meta;
  const totalPages = meta?.totalPages || 1;

  // Mutations
  const createMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient<ProductDetailDTO>('/admin/products', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: (newProd) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      toast.success('Product created successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        setFormGeneralError(err.message || 'Failed to create product');
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ id, payload }: { id: number; payload: any }) => {
      const res = await apiClient<ProductDetailDTO>(`/admin/products/${id}`, {
        method: 'PUT',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      toast.success('Product updated successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        setFormGeneralError(err.message || 'Failed to update product');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient(`/admin/products/${id}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      toast.success(t('deleteSuccess'));
      setDeleteConfirmId(null);
    },
    onError: (err: any) => {
      toast.error(err.message || t('deleteFailed'));
      setDeleteConfirmId(null);
    },
  });

  // Reorder Images Mutation
  const reorderImagesMutation = useMutation({
    mutationFn: async ({ prodId, ids }: { prodId: number; ids: number[] }) => {
      await apiClient(`/admin/products/${prodId}/images/order`, {
        method: 'PATCH',
        body: { ids },
      });
    },
    onSuccess: () => {
      toast.success('Image order saved');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to reorder images');
    },
  });

  const resetForm = () => {
    setEditingProduct(null);
    setName('');
    setNameKm('');
    setSku('');
    setSlug('');
    setBrand('');
    setDescription('');
    setDescriptionKm('');
    setCategoryId('');
    setPriceDollars('');
    setCompareAtDollars('');
    setInitialStock('10');
    setActive(true);
    setAttributes([]);
    setVariants([]);
    setImages([]);
    setFieldErrors({});
    setFormGeneralError(null);
  };

  const openCreateModal = () => {
    resetForm();
    setFormOpen(true);
  };

  const openEditModal = async (prodCard: ProductCardDTO) => {
    resetForm();
    try {
      const res = await apiClient<ProductDetailDTO>(`/admin/products/${prodCard.id}`);
      const full = res.data;
      setEditingProduct(full);
      setName(full.name || '');
      setNameKm(full.nameKm || '');
      setSku(full.sku || '');
      setSlug(full.slug || '');
      setBrand(full.brand || '');
      setDescription(full.description || '');
      setDescriptionKm(full.descriptionKm || '');
      setCategoryId(full.categoryId ? String(full.categoryId) : full.category?.id ? String(full.category.id) : '');
      setPriceDollars((full.priceCents / 100).toFixed(2));
      setCompareAtDollars(full.compareAtCents ? (full.compareAtCents / 100).toFixed(2) : '');
      setActive(full.active ?? true);
      setImages(full.images || []);

      if (full.variants && full.variants.length > 0) {
        setVariants(
          full.variants.map((v) => ({
            sku: v.sku,
            attributes: v.attributes,
            priceCents: v.priceCents,
            stock: v.stockOnHand || v.available || 10,
          }))
        );
      }
      setFormOpen(true);
    } catch (err: any) {
      toast.error(err.message || 'Failed to fetch product details');
    }
  };

  // Generate Cartesian Product of Attribute Combinations
  const handleGenerateCombinations = () => {
    const validAttrs = attributes.filter(
      (a) => a.name.trim() && a.values.trim()
    );
    if (validAttrs.length === 0) {
      toast.error('Please add at least one attribute with values');
      return;
    }

    const attrValuesMap = validAttrs.map((a) => ({
      name: a.name.trim(),
      vals: a.values.split(',').map((v) => v.trim()).filter(Boolean),
    }));

    let combos: Record<string, string>[] = [{}];
    attrValuesMap.forEach(({ name, vals }) => {
      const nextCombos: Record<string, string>[] = [];
      combos.forEach((combo) => {
        vals.forEach((val) => {
          nextCombos.push({ ...combo, [name]: val });
        });
      });
      combos = nextCombos;
    });

    const baseCents = Math.round((parseFloat(priceDollars) || 0) * 100);
    const baseSku = sku.trim() || 'PROD';
    const defStock = parseInt(initialStock, 10) || 10;

    const generated: AdminVariantRequest[] = combos.map((attrs, idx) => {
      const suffix = Object.values(attrs).join('-').toUpperCase().replace(/\s+/g, '');
      return {
        sku: `${baseSku}-${suffix || idx + 1}`,
        attributes: attrs,
        priceCents: baseCents,
        stock: defStock,
      };
    });

    setVariants(generated);
    toast.success(`Generated ${generated.length} variant combinations`);
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setFormGeneralError(null);

    const priceCents = Math.round((parseFloat(priceDollars) || 0) * 100);
    const compareAtCents = compareAtDollars
      ? Math.round((parseFloat(compareAtDollars) || 0) * 100)
      : undefined;

    const payload: any = {
      name: name.trim(),
      nameKm: nameKm.trim() || undefined,
      sku: sku.trim() || undefined,
      slug: slug.trim() || undefined,
      brand: brand.trim() || undefined,
      description: description.trim() || undefined,
      descriptionKm: descriptionKm.trim() || undefined,
      categoryId: categoryId ? parseInt(categoryId, 10) : undefined,
      priceCents,
      compareAtCents,
      active,
      variants: variants.length > 0 ? variants : undefined,
    };

    if (editingProduct) {
      updateMutation.mutate({ id: editingProduct.id, payload });
    } else {
      payload.initialStock = parseInt(initialStock, 10) || 0;
      createMutation.mutate(payload);
    }
  };

  // Image Upload Handler
  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!editingProduct || !e.target.files || e.target.files.length === 0) return;
    const file = e.target.files[0];
    setIsUploadingImage(true);

    try {
      const formData = new FormData();
      formData.append('file', file);

      const token = useAuthStore.getState().token;
      const res = await fetch(`http://localhost:4000/api/admin/products/${editingProduct.id}/images`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });

      if (!res.ok) {
        const errorJson = await res.json().catch(() => null);
        throw new Error(errorJson?.error?.message || 'Failed to upload image');
      }

      const json = await res.json();
      if (json.data) {
        setImages((prev) => [...prev, json.data]);
        toast.success('Image uploaded successfully');
      }
    } catch (err: any) {
      toast.error(err.message || 'Image upload failed');
    } finally {
      setIsUploadingImage(false);
      if (imageInputRef.current) imageInputRef.current.value = '';
    }
  };

  // Move Image Left / Right
  const moveImage = (index: number, direction: 'left' | 'right') => {
    if (!editingProduct) return;
    const newIdx = direction === 'left' ? index - 1 : index + 1;
    if (newIdx < 0 || newIdx >= images.length) return;

    const copy = [...images];
    const temp = copy[index];
    copy[index] = copy[newIdx];
    copy[newIdx] = temp;
    setImages(copy);

    const ids = copy.map((img) => img.id);
    reorderImagesMutation.mutate({ prodId: editingProduct.id, ids });
  };

  // Export CSV
  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      const token = useAuthStore.getState().token;
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';
      const res = await fetch(`${baseUrl}/admin/products/export.csv`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Failed to export CSV');

      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'products.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Products exported successfully');
    } catch (err: any) {
      toast.error(err.message || 'Export failed');
    } finally {
      setIsExporting(false);
    }
  };

  // Import CSV
  const handleImportCsv = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fileInputRef.current?.files || fileInputRef.current.files.length === 0) {
      toast.error('Please choose a CSV file to import');
      return;
    }

    const file = fileInputRef.current.files[0];
    const formData = new FormData();
    formData.append('file', file);

    try {
      const token = useAuthStore.getState().token;
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';
      const res = await fetch(`${baseUrl}/admin/products/import`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });

      const json = await res.json();
      if (json.success && json.data) {
        setImportResult(json.data);
        queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
        toast.success(`Import finished: ${json.data.imported} items processed`);
      } else {
        toast.error(json.error?.message || 'CSV Import failed');
      }
    } catch (err: any) {
      toast.error(err.message || 'Network error during import');
    }
  };

  return (
    <div className="space-y-6" data-testid="admin-products-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight flex items-center gap-2">
            <Package className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            data-testid="import-products-csv-btn"
            onClick={() => {
              setImportResult(null);
              setImportModalOpen(true);
            }}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border bg-surface hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors"
          >
            <Upload className="w-3.5 h-3.5 text-muted" />
            <span>{t('importCsv')}</span>
          </button>

          <button
            type="button"
            data-testid="export-products-csv-btn"
            disabled={isExporting}
            onClick={handleExportCsv}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border bg-surface hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors disabled:opacity-50"
          >
            {isExporting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5 text-muted" />}
            <span>{t('exportCsv')}</span>
          </button>

          <button
            type="button"
            data-testid="add-product-btn"
            onClick={openCreateModal}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{t('createProduct')}</span>
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-surface border border-border rounded-xl p-4 shadow-2xs space-y-3">
        <div className="flex flex-col md:flex-row items-stretch md:items-center gap-3">
          <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
            <div className="relative flex-1">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t('searchPlaceholder')}
                data-testid="product-search-input"
                className="w-full pl-9 pr-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-hidden focus:ring-2 focus:ring-primary/20"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-primary text-white text-xs sm:text-sm font-medium rounded-lg hover:bg-primary/90 transition-colors shrink-0"
            >
              {commonT('filter')}
            </button>
          </form>

          {/* Category Filter */}
          <select
            data-testid="product-category-filter"
            value={categoryParam}
            onChange={(e) => updateFilters({ category: e.target.value || null, page: 1 })}
            className="px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
          >
            <option value="">{t('allCategories')}</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>

          {/* Active Status Filter */}
          <select
            data-testid="product-active-filter"
            value={activeParam}
            onChange={(e) => updateFilters({ active: e.target.value !== '' ? e.target.value : null, page: 1 })}
            className="px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
          >
            <option value="">{t('allStatus')}</option>
            <option value="true">{t('statusActive')}</option>
            <option value="false">{t('statusInactive')}</option>
          </select>

          {(q || categoryParam || activeParam) && (
            <button
              type="button"
              onClick={() => {
                setSearchInput('');
                router.push(pathname);
              }}
              className="p-2 border border-border rounded-lg hover:bg-muted/10 text-muted hover:text-foreground"
              title={commonT('reset')}
            >
              <RotateCcw className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Table / List */}
      <div className="bg-surface border border-border rounded-xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 space-y-4">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="animate-pulse flex items-center justify-between py-3 border-b border-border/40">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-muted/20 rounded-lg" />
                  <div className="space-y-1.5">
                    <div className="h-4 bg-muted/20 rounded w-48" />
                    <div className="h-3 bg-muted/15 rounded w-24" />
                  </div>
                </div>
                <div className="h-4 bg-muted/20 rounded w-16" />
                <div className="h-4 bg-muted/20 rounded w-16" />
                <div className="h-8 bg-muted/20 rounded w-20" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <div className="p-12 text-center space-y-3">
            <AlertCircle className="w-10 h-10 text-red-700 mx-auto" />
            <h3 className="text-sm font-bold text-foreground">{commonT('errorTitle')}</h3>
            <button
              onClick={() => refetch()}
              className="px-4 py-2 bg-primary text-white text-xs font-semibold rounded-lg"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : products.length === 0 ? (
          <div className="p-12 text-center space-y-2 text-muted">
            <Package className="w-10 h-10 mx-auto text-muted/60" />
            {q || categoryParam || activeParam !== '' ? (
              <>
                <h3 className="text-sm font-bold text-foreground">{commonT('empty')}</h3>
                <p className="text-xs">No products found matching your search.</p>
              </>
            ) : (
              <div data-testid="admin-products-empty-state" className="space-y-3">
                <h3 className="text-sm font-bold text-foreground">{t('noProductsYet')}</h3>
                <p className="text-xs">{t('noProductsYetDesc')}</p>
                <button
                  type="button"
                  data-testid="add-first-product-btn"
                  onClick={openCreateModal}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  <span>{t('addFirstProduct')}</span>
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead>
                <tr className="bg-muted/10 border-b border-border text-muted font-semibold text-[11px] sm:text-xs">
                  <th className="py-3 px-4">{t('image')}</th>
                  <th className="py-3 px-4">{t('name')}</th>
                  <th className="py-3 px-4">{t('sku')}</th>
                  <th className="py-3 px-4">{t('price')}</th>
                  <th className="py-3 px-4 text-center">{t('status')}</th>
                  <th className="py-3 px-4 text-right">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {products.map((item) => (
                  <tr
                    key={item.id}
                    data-testid="product-row"
                    data-product-id={item.id}
                    data-testid-row={`product-row-${item.id}`}
                    className="hover:bg-muted/5 transition-colors"
                  >
                    <td className="py-3 px-4">
                      <div className="relative w-10 h-10 rounded-lg overflow-hidden border border-border">
                        <SafeImage src={getImageUrl(item.imageUrl)} alt={item.name} fill sizes="40px" className="object-cover" />
                      </div>
                    </td>
                    <td className="py-3 px-4 max-w-xs truncate font-medium text-foreground">
                      <div>{item.name}</div>
                      {item.nameKm && <div className="text-[11px] text-muted">{item.nameKm}</div>}
                    </td>
                    <td className="py-3 px-4 font-mono text-xs text-muted">{item.sku || '—'}</td>
                    <td className="py-3 px-4 font-mono font-bold text-foreground">
                      {formatUsd(item.priceCents)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <span
                        data-testid={`product-status-${item.id}`}
                        title={isInactive(item) ? t('inactiveHint') : undefined}
                        className={cn(
                          'inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold',
                          !isInactive(item)
                            ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400'
                            : 'bg-muted/20 text-muted'
                        )}
                      >
                        {!isInactive(item) ? t('statusActive') : t('inactiveBadge')}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEditModal(item)}
                          disabled={isInactive(item)}
                          data-testid={`edit-product-btn-${item.id}`}
                          className="p-1.5 rounded-lg border border-border hover:bg-muted/10 text-muted hover:text-foreground transition-colors disabled:opacity-40 disabled:pointer-events-none"
                          title={t('editProduct')}
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteConfirmId(item.id)}
                          disabled={isInactive(item)}
                          data-testid={`delete-product-btn-${item.id}`}
                          className="p-1.5 rounded-lg border border-border hover:bg-red-500/10 text-muted hover:text-red-600 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                          title={t('deleteProduct')}
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
                className="p-1.5 rounded-lg border border-border disabled:opacity-40"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages}
                onClick={() => updateFilters({ page: page + 1 })}
                className="p-1.5 rounded-lg border border-border disabled:opacity-40"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* CREATE / EDIT PRODUCT MODAL */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs overflow-y-auto">
          <div
            data-testid="product-form-modal"
            className="bg-surface border border-border rounded-2xl w-full max-w-4xl max-h-[90vh] shadow-2xl flex flex-col my-auto overflow-hidden animate-in zoom-in-95"
          >
            <div className="flex items-center justify-between p-5 border-b border-border bg-muted/5">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <Package className="w-5 h-5 text-primary" />
                <span>{editingProduct ? t('editProduct') : t('createProduct')}</span>
              </h3>
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="p-1 text-muted hover:text-foreground rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleFormSubmit} className="flex-1 overflow-y-auto p-6 space-y-6">
              {formGeneralError && (
                <div className="p-3 bg-destructive/10 border border-destructive/20 text-destructive text-xs rounded-xl font-medium">
                  {formGeneralError}
                </div>
              )}

              {/* Section 1: General Info */}
              <div className="space-y-4">
                <h4 className="text-xs font-bold uppercase tracking-wider text-muted flex items-center gap-1.5">
                  <Layers className="w-3.5 h-3.5" />
                  <span>{t('generalInfo')}</span>
                </h4>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('nameEn')} <span className="text-red-700">*</span>
                    </label>
                    <input
                      type="text"
                      data-testid="input-name"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="e.g. Kampot Black Pepper 100 g"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
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
                      data-testid="input-name-km"
                      value={nameKm}
                      onChange={(e) => setNameKm(e.target.value)}
                      placeholder="e.g. ម្រេចខ្មៅកំពត ១០០ ក្រាម"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('sku')}
                    </label>
                    <input
                      type="text"
                      data-testid="input-sku"
                      value={sku}
                      onChange={(e) => setSku(e.target.value)}
                      placeholder="e.g. SPC-1006"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg font-mono"
                    />
                    {fieldErrors.sku && (
                      <p className="text-xs text-red-700 mt-1">{fieldErrors.sku}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('brand')}
                    </label>
                    <input
                      type="text"
                      data-testid="input-brand"
                      value={brand}
                      onChange={(e) => setBrand(e.target.value)}
                      placeholder="e.g. Kampot Farms, Takeo Weavers"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('category')}
                    </label>
                    <select
                      data-testid="select-category"
                      value={categoryId}
                      onChange={(e) => setCategoryId(e.target.value)}
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
                    >
                      <option value="">{t('selectCategory')}</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="flex items-center gap-2 pt-6">
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        data-testid="input-active-toggle"
                        checked={active}
                        onChange={(e) => setActive(e.target.checked)}
                        className="sr-only peer"
                      />
                      <div className="w-11 h-6 bg-muted/40 peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
                      <span className="ml-3 text-xs font-semibold text-foreground">
                        {t('activeToggle')}
                      </span>
                    </label>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('descriptionEn')}
                  </label>
                  <textarea
                    data-testid="input-description"
                    rows={2}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1">
                    {t('descriptionKm')}
                  </label>
                  <textarea
                    data-testid="input-description-km"
                    rows={2}
                    value={descriptionKm}
                    onChange={(e) => setDescriptionKm(e.target.value)}
                    className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg"
                  />
                </div>
              </div>

              {/* Section 2: Pricing & Inventory */}
              <div className="space-y-4 pt-4 border-t border-border">
                <h4 className="text-xs font-bold uppercase tracking-wider text-muted">
                  {t('pricingInventory')}
                </h4>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('priceDollars')} <span className="text-red-700">*</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      data-testid="input-price"
                      value={priceDollars}
                      onChange={(e) => setPriceDollars(e.target.value)}
                      placeholder="e.g. 899.00"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg font-mono font-bold"
                      required
                    />
                    {fieldErrors.priceCents && (
                      <p className="text-xs text-red-700 mt-1">{fieldErrors.priceCents}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      {t('compareAtDollars')}
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      data-testid="input-compare-at"
                      value={compareAtDollars}
                      onChange={(e) => setCompareAtDollars(e.target.value)}
                      placeholder="e.g. 999.00"
                      className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg font-mono"
                    />
                  </div>

                  {!editingProduct && (
                    <div>
                      <label className="block text-xs font-semibold text-foreground mb-1">
                        {t('initialStock')}
                      </label>
                      <input
                        type="number"
                        min="0"
                        step="1"
                        data-testid="input-initial-stock"
                        value={initialStock}
                        onChange={(e) => setInitialStock(e.target.value)}
                        placeholder="10"
                        className="w-full px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg font-mono"
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Section 3: Variants Editor */}
              <div className="space-y-4 pt-4 border-t border-border">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">
                      {t('variantsTitle')}
                    </h4>
                    <p className="text-[11px] text-muted">{t('variantsSubtitle')}</p>
                  </div>
                  <button
                    type="button"
                    data-testid="add-attribute-btn"
                    onClick={() => setAttributes([...attributes, { name: '', values: '' }])}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-border text-xs font-semibold hover:bg-muted/10 transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>{t('addAttribute')}</span>
                  </button>
                </div>

                {attributes.map((attr, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <input
                      type="text"
                      placeholder={t('attributeName')}
                      data-testid={`attr-name-${idx}`}
                      value={attr.name}
                      onChange={(e) => {
                        const copy = [...attributes];
                        copy[idx].name = e.target.value;
                        setAttributes(copy);
                      }}
                      className="w-1/3 px-3 py-1.5 text-xs bg-background border border-border rounded-lg"
                    />
                    <input
                      type="text"
                      placeholder={t('attributeValues')}
                      data-testid={`attr-values-${idx}`}
                      value={attr.values}
                      onChange={(e) => {
                        const copy = [...attributes];
                        copy[idx].values = e.target.value;
                        setAttributes(copy);
                      }}
                      className="flex-1 px-3 py-1.5 text-xs bg-background border border-border rounded-lg"
                    />
                    <button
                      type="button"
                      onClick={() => setAttributes(attributes.filter((_, i) => i !== idx))}
                      className="p-1.5 text-muted hover:text-red-500 rounded"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}

                {attributes.length > 0 && (
                  <button
                    type="button"
                    data-testid="generate-variants-btn"
                    onClick={handleGenerateCombinations}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700 transition-colors"
                  >
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>{t('generateVariants')}</span>
                  </button>
                )}

                {/* Variants Table */}
                {variants.length > 0 && (
                  <div className="border border-border rounded-xl overflow-hidden mt-3">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-muted/10 text-muted border-b border-border font-semibold">
                        <tr>
                          <th className="py-2 px-3">Variant Combination</th>
                          <th className="py-2 px-3">{t('variantSku')}</th>
                          <th className="py-2 px-3">{t('variantPrice')}</th>
                          <th className="py-2 px-3">{t('variantStock')}</th>
                          <th className="py-2 px-3 text-right">Remove</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {variants.map((v, i) => (
                          <tr key={i} data-testid="variant-row">
                            <td className="py-2 px-3 font-medium">
                              {Object.entries(v.attributes || {})
                                .map(([k, val]) => `${k}: ${val}`)
                                .join(', ')}
                            </td>
                            <td className="py-2 px-3">
                              <input
                                type="text"
                                data-testid="variant-sku-input"
                                value={v.sku || ''}
                                onChange={(e) => {
                                  const copy = [...variants];
                                  copy[i].sku = e.target.value;
                                  setVariants(copy);
                                }}
                                className="w-full px-2 py-1 text-xs border border-border rounded bg-background font-mono"
                              />
                            </td>
                            <td className="py-2 px-3">
                              <input
                                type="number"
                                step="0.01"
                                data-testid="variant-price-input"
                                value={((v.priceCents || 0) / 100).toFixed(2)}
                                onChange={(e) => {
                                  const copy = [...variants];
                                  copy[i].priceCents = Math.round(parseFloat(e.target.value || '0') * 100);
                                  setVariants(copy);
                                }}
                                className="w-24 px-2 py-1 text-xs border border-border rounded bg-background font-mono font-bold"
                              />
                            </td>
                            <td className="py-2 px-3">
                              <input
                                type="number"
                                data-testid="variant-stock-input"
                                value={v.stock || 0}
                                onChange={(e) => {
                                  const copy = [...variants];
                                  copy[i].stock = parseInt(e.target.value || '0', 10);
                                  setVariants(copy);
                                }}
                                className="w-16 px-2 py-1 text-xs border border-border rounded bg-background font-mono"
                              />
                            </td>
                            <td className="py-2 px-3 text-right">
                              <button
                                type="button"
                                onClick={() => setVariants(variants.filter((_, idx) => idx !== i))}
                                className="text-muted hover:text-red-500"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Section 4: Image Gallery (When editing product) */}
              {editingProduct && (
                <div className="space-y-4 pt-4 border-t border-border">
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">
                        {t('imagesTitle')}
                      </h4>
                      <p className="text-[11px] text-muted">{t('imagesSubtitle')}</p>
                    </div>
                  </div>

                  {/* Dropzone */}
                  <div
                    data-testid="product-image-dropzone"
                    onClick={() => imageInputRef.current?.click()}
                    className="border-2 border-dashed border-border rounded-xl p-6 text-center hover:border-primary/50 hover:bg-muted/5 transition-all cursor-pointer space-y-2"
                  >
                    <input
                      type="file"
                      ref={imageInputRef}
                      onChange={handleImageUpload}
                      accept="image/jpeg,image/png,image/webp"
                      data-testid="product-image-file-input"
                      className="hidden"
                    />
                    {isUploadingImage ? (
                      <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto" />
                    ) : (
                      <ImageIcon className="w-8 h-8 text-muted mx-auto" />
                    )}
                    <p className="text-xs text-foreground font-medium">{t('dragDropImages')}</p>
                  </div>

                  {/* Image Grid with Reorder */}
                  {images.length > 0 && (
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      {images.map((img, idx) => (
                        <div
                          key={img.id}
                          data-testid="image-item"
                          className="relative group border border-border rounded-xl overflow-hidden bg-background"
                        >
                          <div className="relative w-full h-28">
                            <SafeImage src={getImageUrl(img.url)} alt="Product media" fill sizes="200px" className="object-cover" />
                          </div>
                          {idx === 0 && (
                            <span className="absolute top-1 left-1 px-1.5 py-0.5 rounded text-[10px] font-bold bg-primary text-white">
                              {t('primaryImage')}
                            </span>
                          )}
                          <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                            {idx > 0 && (
                              <button
                                type="button"
                                onClick={() => moveImage(idx, 'left')}
                                className="p-1 rounded bg-surface/80 hover:bg-surface text-foreground"
                                title="Move Left"
                              >
                                <ChevronLeft className="w-3.5 h-3.5" />
                              </button>
                            )}
                            {idx < images.length - 1 && (
                              <button
                                type="button"
                                onClick={() => moveImage(idx, 'right')}
                                className="p-1 rounded bg-surface/80 hover:bg-surface text-foreground"
                                title="Move Right"
                              >
                                <ChevronRight className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Submit Buttons */}
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
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
                  data-testid="save-product-submit-btn"
                  className="px-5 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50 inline-flex items-center gap-1.5 transition-colors shadow-xs"
                >
                  {(createMutation.isPending || updateMutation.isPending) && (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  )}
                  <span>{commonT('save')}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DELETE CONFIRM DIALOG */}
      {deleteConfirmId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div className="bg-surface border border-border rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-xl text-center">
            <Trash2 className="w-10 h-10 text-destructive mx-auto" />
            <h3 className="text-base font-bold text-foreground">{t('deleteProduct')}</h3>
            <p className="text-xs text-muted leading-relaxed">{t('deleteConfirm')}</p>
            <div className="flex items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => setDeleteConfirmId(null)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 text-xs font-semibold border border-border rounded-xl disabled:opacity-50"
              >
                {commonT('cancel')}
              </button>
              <button
                type="button"
                data-testid="confirm-delete-product-btn"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteConfirmId)}
                className="px-4 py-2 text-xs font-bold bg-destructive text-destructive-foreground rounded-xl hover:bg-destructive/90 disabled:opacity-50"
              >
                {deleteMutation.isPending ? t('deleting') : commonT('confirm')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CSV IMPORT MODAL */}
      {importModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div
            data-testid="import-csv-modal"
            className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl relative"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <Upload className="w-5 h-5 text-primary" />
                <span>{t('importTitle')}</span>
              </h3>
              <button
                type="button"
                onClick={() => setImportModalOpen(false)}
                className="text-muted hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-muted">{t('importSubtitle')}</p>

            <form onSubmit={handleImportCsv} className="space-y-4">
              <input
                type="file"
                ref={fileInputRef}
                accept=".csv,text/csv"
                data-testid="csv-file-input"
                className="w-full text-xs file:mr-3 file:py-2 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-primary file:text-primary-foreground border border-border rounded-xl p-2 bg-background"
                required
              />

              {importResult && (
                <div className="space-y-3 pt-2">
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 text-emerald-700 dark:text-emerald-400 text-xs rounded-xl font-bold">
                    {t('importSuccess', { count: importResult.imported })}
                  </div>

                  {importResult.errors && importResult.errors.length > 0 && (
                    <div className="space-y-2">
                      <span className="text-xs font-bold text-destructive">
                        {t('importErrorsTitle')} ({importResult.errors.length})
                      </span>
                      <div
                        data-testid="csv-errors-table"
                        className="max-h-36 overflow-y-auto border border-destructive/20 rounded-lg p-2 bg-destructive/5 space-y-1 text-[11px]"
                      >
                        {importResult.errors.map((err, i) => (
                          <div key={i} className="text-destructive">
                            {t('rowError', { row: err.row, message: err.message })}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
                <button
                  type="button"
                  onClick={() => setImportModalOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  data-testid="confirm-import-csv-btn"
                  className="px-4 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90"
                >
                  {t('importCsv')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
