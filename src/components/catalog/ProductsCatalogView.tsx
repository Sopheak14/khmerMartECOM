'use client';

import React, { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useRouter, usePathname } from '@/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { ProductCardDTO, ProductFacets, PaginationMeta } from '@/types/api';
import { ProductCard } from '@/components/common/ProductCard';
import { FilterSidebar } from './FilterSidebar';
import { ActiveFilterChips } from './ActiveFilterChips';
import { Pagination } from './Pagination';
import { SlidersHorizontal, PackageSearch, ArrowUpDown } from 'lucide-react';

interface ProductsCatalogViewProps {
  initialProducts?: ProductCardDTO[];
  initialFacets?: ProductFacets;
  initialMeta?: PaginationMeta;
  khrRate?: number;
  categoryTitle?: string;
}

export function ProductsCatalogView({
  initialProducts,
  initialFacets,
  initialMeta,
  khrRate = 4100,
  categoryTitle,
}: ProductsCatalogViewProps) {
  const t = useTranslations('catalog');
  const common = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);

  // Parse filters directly from URL searchParams
  const q = searchParams.get('q') || '';
  const brand = searchParams.get('brand') || '';
  const minPrice = searchParams.get('minPrice') || '';
  const maxPrice = searchParams.get('maxPrice') || '';
  const inStock = searchParams.get('inStock') === 'true';
  const sort = searchParams.get('sort') || 'newest';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const categoryId = searchParams.get('categoryId') || '';

  // Parse attributes from attr[key]
  const attributes: Record<string, string> = useMemo(() => {
    const map: Record<string, string> = {};
    searchParams.forEach((val, key) => {
      if (key.startsWith('attr[') && key.endsWith(']')) {
        const attrKey = key.slice(5, -1);
        map[attrKey] = val;
      }
    });
    return map;
  }, [searchParams]);

  // Construct query object for API client
  const apiQueryParams = useMemo(() => {
    const params: Record<string, any> = {
      page,
      limit: 20,
    };
    if (q) params.q = q;
    if (brand) params.brand = brand;
    if (minPrice) params.minPrice = minPrice;
    if (maxPrice) params.maxPrice = maxPrice;
    if (inStock) params.inStock = true;
    if (sort) params.sort = sort;
    if (categoryId) params.categoryId = categoryId;

    Object.entries(attributes).forEach(([k, v]) => {
      params[`attr[${k}]`] = v;
    });

    return params;
  }, [q, brand, minPrice, maxPrice, inStock, sort, page, categoryId, attributes]);

  // Query products from API, seeded by initial data from Server Component
  const { data, isLoading } = useQuery({
    queryKey: ['products', apiQueryParams],
    queryFn: async () => {
      const res = await apiClient<ProductCardDTO[]>('/products', {
        query: apiQueryParams,
        auth: false,
      });
      return {
        products: res.data || [],
        meta: res.meta || { page: 1, limit: 20, total: 0, totalPages: 1 },
        facets: res.facets as ProductFacets,
      };
    },
    initialData: initialProducts
      ? {
          products: initialProducts,
          meta: initialMeta || { page: 1, limit: 20, total: initialProducts.length, totalPages: 1 },
          facets: initialFacets || { brands: [], attributes: [], priceRange: { minCents: 0, maxCents: 0 } },
        }
      : undefined,
  });

  const products = data?.products || [];
  const meta = data?.meta || { page: 1, limit: 20, total: 0, totalPages: 1 };
  const facets = data?.facets || initialFacets;

  // Helper to push new URL params while preserving other filters
  const updateUrlParams = (updates: Record<string, string | null | undefined>) => {
    const params = new URLSearchParams(searchParams.toString());

    Object.entries(updates).forEach(([key, val]) => {
      if (val === null || val === undefined || val === '') {
        params.delete(key);
      } else {
        params.set(key, val);
      }
    });

    // Reset page to 1 whenever filters change (unless page was explicitly updated)
    if (!('page' in updates)) {
      params.delete('page');
    }

    const qs = params.toString();
    router.push(`${pathname}${qs ? `?${qs}` : ''}`);
  };

  const handleFilterChange = (updates: {
    brand?: string | null;
    minPrice?: string | null;
    maxPrice?: string | null;
    inStock?: boolean | null;
    attributes?: Record<string, string>;
  }) => {
    const params = new URLSearchParams(searchParams.toString());

    if (updates.brand !== undefined) {
      if (updates.brand) params.set('brand', updates.brand);
      else params.delete('brand');
    }

    if (updates.minPrice !== undefined) {
      if (updates.minPrice) params.set('minPrice', updates.minPrice);
      else params.delete('minPrice');
    }

    if (updates.maxPrice !== undefined) {
      if (updates.maxPrice) params.set('maxPrice', updates.maxPrice);
      else params.delete('maxPrice');
    }

    if (updates.inStock !== undefined) {
      if (updates.inStock) params.set('inStock', 'true');
      else params.delete('inStock');
    }

    if (updates.attributes !== undefined) {
      // Clear existing attr[*]
      const keysToDelete: string[] = [];
      params.forEach((_, key) => {
        if (key.startsWith('attr[')) keysToDelete.push(key);
      });
      keysToDelete.forEach((k) => params.delete(k));

      // Set new attr[*]
      Object.entries(updates.attributes).forEach(([k, v]) => {
        if (v) params.set(`attr[${k}]`, v);
      });
    }

    params.delete('page');
    const qs = params.toString();
    router.push(`${pathname}${qs ? `?${qs}` : ''}`);
  };

  const handleClearAll = () => {
    const params = new URLSearchParams();
    if (categoryId) params.set('categoryId', categoryId);
    const qs = params.toString();
    router.push(`${pathname}${qs ? `?${qs}` : ''}`);
  };

  const handleRemoveAttribute = (attrKey: string) => {
    const next = { ...attributes };
    delete next[attrKey];
    handleFilterChange({ attributes: next });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Top Bar: Title & Sort */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-border">
        <div>
          {!categoryTitle && (
            <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-foreground">
              {q ? `"${q}"` : t('title')}
            </h1>
          )}
          <p className="text-xs text-muted mt-1" data-testid="products-count">
            {t('showingResults', { count: meta.total })}
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Mobile Filter Toggle Button */}
          <button
            type="button"
            onClick={() => setMobileFilterOpen(!mobileFilterOpen)}
            className="lg:hidden flex items-center gap-2 px-3.5 py-2 rounded-btn border border-border bg-surface text-xs font-semibold text-foreground shadow-xs"
          >
            <SlidersHorizontal className="w-3.5 h-3.5" />
            <span>{t('filters')}</span>
          </button>

          {/* Sort Select */}
          <div className="flex items-center gap-2">
            <label htmlFor="sort-select" className="text-xs text-muted whitespace-nowrap hidden sm:inline">
              <ArrowUpDown className="w-3 h-3 inline mr-1" />
              {t('sortBy')}:
            </label>
            <select
              id="sort-select"
              data-testid="sort-select"
              value={sort}
              onChange={(e) => updateUrlParams({ sort: e.target.value })}
              className="h-9 px-3 text-xs rounded-btn border border-border bg-surface text-foreground font-medium focus:ring-1 focus:ring-primary focus:outline-none cursor-pointer"
            >
              <option value="newest">{t('sortNewest')}</option>
              <option value="price_asc">{t('sortPriceAsc')}</option>
              <option value="price_desc">{t('sortPriceDesc')}</option>
              <option value="popular">{t('sortPopular')}</option>
              <option value="rating">{t('sortRating')}</option>
            </select>
          </div>
        </div>
      </div>

      {/* Active Filter Chips */}
      <ActiveFilterChips
        query={q}
        brand={brand}
        minPrice={minPrice}
        maxPrice={maxPrice}
        inStockOnly={inStock}
        attributes={attributes}
        onRemoveQuery={() => updateUrlParams({ q: null })}
        onRemoveBrand={() => updateUrlParams({ brand: null })}
        onRemovePrice={() => updateUrlParams({ minPrice: null, maxPrice: null })}
        onRemoveInStock={() => updateUrlParams({ inStock: null })}
        onRemoveAttribute={handleRemoveAttribute}
        onClearAll={handleClearAll}
      />

      {/* Main Content Layout */}
      <div className="flex flex-col lg:flex-row gap-8 mt-6">
        {/* Desktop Filter Sidebar */}
        <div className="hidden lg:block">
          <FilterSidebar
            facets={facets}
            selectedBrand={brand}
            minPrice={minPrice}
            maxPrice={maxPrice}
            selectedAttributes={attributes}
            inStockOnly={inStock}
            onFilterChange={handleFilterChange}
            onClearAll={handleClearAll}
          />
        </div>

        {/* Mobile Filter Drawer */}
        {mobileFilterOpen && (
          <div className="lg:hidden fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex justify-end">
            <div className="w-4/5 max-w-sm h-full bg-surface p-6 overflow-y-auto">
              <div className="flex items-center justify-between pb-4 mb-4 border-b border-border">
                <span className="font-bold text-base">{t('filters')}</span>
                <button
                  type="button"
                  onClick={() => setMobileFilterOpen(false)}
                  className="text-xs px-2.5 py-1 rounded bg-muted/20 font-medium"
                >
                  ✕
                </button>
              </div>
              <FilterSidebar
                facets={facets}
                selectedBrand={brand}
                minPrice={minPrice}
                maxPrice={maxPrice}
                selectedAttributes={attributes}
                inStockOnly={inStock}
                onFilterChange={(updates) => {
                  handleFilterChange(updates);
                  setMobileFilterOpen(false);
                }}
                onClearAll={() => {
                  handleClearAll();
                  setMobileFilterOpen(false);
                }}
              />
            </div>
          </div>
        )}

        {/* Product Grid & Pagination */}
        <div className="flex-1 min-w-0">
          {products.length === 0 ? (
            /* Empty State */
            <div
              data-testid="products-empty-state"
              className="text-center py-16 px-4 bg-surface rounded-card border border-border my-4"
            >
              <div className="w-14 h-14 mx-auto rounded-full bg-muted/20 flex items-center justify-center text-muted mb-4">
                <PackageSearch className="w-7 h-7" />
              </div>
              <h3 className="text-lg font-bold text-foreground">{t('noProducts')}</h3>
              <p className="text-xs text-muted max-w-md mx-auto mt-1 mb-6">
                {t('noProductsDesc')}
              </p>
              <button
                type="button"
                onClick={handleClearAll}
                className="px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-semibold hover:bg-primary/90 transition-colors shadow-xs"
              >
                {t('clearAll')}
              </button>
            </div>
          ) : (
            /* Product Cards Grid */
            <>
              <div
                data-testid="product-grid"
                className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-6"
              >
                {products.map((product) => (
                  <ProductCard
                    key={product.id}
                    product={product}
                    khrRate={khrRate}
                  />
                ))}
              </div>

              {/* Pagination */}
              <Pagination
                currentPage={meta.page}
                totalPages={meta.totalPages}
                onPageChange={(nextPage) => updateUrlParams({ page: String(nextPage) })}
              />
            </>
          )}
        </div>
      </div>
    </div>
  );
}
