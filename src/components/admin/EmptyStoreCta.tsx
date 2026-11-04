'use client';

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { PackagePlus, FolderPlus } from 'lucide-react';
import { apiClient } from '@/lib/apiClient';
import { CategoryDTO, ProductCardDTO } from '@/types/api';

/** How many products / categories the store has (null while unknown or when the user may not read them). */
export function useStoreCounts() {
  const products = useQuery({
    queryKey: ['admin', 'store-setup', 'products'],
    retry: false,
    queryFn: async () => {
      const res = await apiClient<ProductCardDTO[]>('/admin/products', { query: { page: 0, size: 1 } });
      return res.meta?.total ?? (res.data || []).length;
    },
  });
  const categories = useQuery({
    queryKey: ['admin', 'store-setup', 'categories'],
    retry: false,
    queryFn: async () => {
      const res = await apiClient<CategoryDTO[]>('/admin/categories');
      return (res.data || []).length;
    },
  });
  return {
    productCount: products.isSuccess ? products.data : null,
    categoryCount: categories.isSuccess ? categories.data : null,
  };
}

/**
 * Setup banner for a brand-new store. Renders nothing once the store has both a category and a product
 * (or while that is still unknown), so it never gets in the way of a populated store.
 */
export function EmptyStoreCta({ className = '' }: { className?: string }) {
  const t = useTranslations('admin.emptyStore');
  const { productCount, categoryCount } = useStoreCounts();

  const needsProduct = productCount === 0;
  const needsCategory = categoryCount === 0;
  if (!needsProduct && !needsCategory) return null;

  return (
    <div
      data-testid="empty-store-cta"
      className={`bg-primary/5 border border-primary/20 rounded-2xl p-5 sm:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${className}`}
    >
      <div className="space-y-1">
        <h2 className="text-sm sm:text-base font-bold text-foreground">{t('title')}</h2>
        <p className="text-xs text-muted max-w-xl">{t('desc')}</p>
      </div>
      <div className="flex flex-wrap gap-2 flex-shrink-0">
        {needsCategory && (
          <Link
            href="/admin/categories"
            data-testid="cta-add-category"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-btn border border-border bg-surface text-xs font-bold text-foreground hover:bg-muted/10 transition-colors"
          >
            <FolderPlus className="w-4 h-4" />
            <span>{t('addCategory')}</span>
          </Link>
        )}
        {needsProduct && (
          <Link
            href="/admin/products"
            data-testid="cta-add-product"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors"
          >
            <PackagePlus className="w-4 h-4" />
            <span>{t('addProduct')}</span>
          </Link>
        )}
      </div>
    </div>
  );
}
