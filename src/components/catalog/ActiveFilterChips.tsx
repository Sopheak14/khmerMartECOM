'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { X } from 'lucide-react';
import { formatUsd } from '@/lib/money';

interface ActiveFilterChipsProps {
  query?: string;
  brand?: string;
  minPrice?: string;
  maxPrice?: string;
  inStockOnly?: boolean;
  attributes: Record<string, string>;
  onRemoveQuery?: () => void;
  onRemoveBrand?: () => void;
  onRemovePrice?: () => void;
  onRemoveInStock?: () => void;
  onRemoveAttribute: (key: string) => void;
  onClearAll: () => void;
}

export function ActiveFilterChips({
  query,
  brand,
  minPrice,
  maxPrice,
  inStockOnly,
  attributes,
  onRemoveQuery,
  onRemoveBrand,
  onRemovePrice,
  onRemoveInStock,
  onRemoveAttribute,
  onClearAll,
}: ActiveFilterChipsProps) {
  const t = useTranslations('catalog');

  const hasAnyFilter =
    Boolean(query) ||
    Boolean(brand) ||
    Boolean(minPrice || maxPrice) ||
    Boolean(inStockOnly) ||
    Object.keys(attributes).length > 0;

  if (!hasAnyFilter) {
    return null;
  }

  return (
    <div data-testid="active-filter-chips" className="flex flex-wrap items-center gap-2 py-2">
      <span className="text-xs font-semibold text-muted mr-1">{t('activeFilters')}:</span>

      {/* Query Chip */}
      {query && (
        <span
          data-testid="filter-chip"
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-primary/10 text-primary border border-primary/20 font-medium"
        >
          <span>&quot;{query}&quot;</span>
          {onRemoveQuery && (
            <button
              type="button"
              onClick={onRemoveQuery}
              className="hover:opacity-75 focus:outline-none"
              aria-label="Remove search query filter"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </span>
      )}

      {/* Brand Chip */}
      {brand && (
        <span
          data-testid="filter-chip"
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-primary/10 text-primary border border-primary/20 font-medium"
        >
          <span>{brand}</span>
          {onRemoveBrand && (
            <button
              type="button"
              onClick={onRemoveBrand}
              className="hover:opacity-75 focus:outline-none"
              aria-label="Remove brand filter"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </span>
      )}

      {/* Price Range Chip */}
      {(minPrice || maxPrice) && (
        <span
          data-testid="filter-chip"
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-primary/10 text-primary border border-primary/20 font-medium"
        >
          <span>
            {minPrice ? `$${minPrice}` : '$0'} – {maxPrice ? `$${maxPrice}` : '∞'}
          </span>
          {onRemovePrice && (
            <button
              type="button"
              onClick={onRemovePrice}
              className="hover:opacity-75 focus:outline-none"
              aria-label="Remove price filter"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </span>
      )}

      {/* In Stock Chip */}
      {inStockOnly && (
        <span
          data-testid="filter-chip"
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-primary/10 text-primary border border-primary/20 font-medium"
        >
          <span>{t('inStockOnly')}</span>
          {onRemoveInStock && (
            <button
              type="button"
              onClick={onRemoveInStock}
              className="hover:opacity-75 focus:outline-none"
              aria-label="Remove in stock filter"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </span>
      )}

      {/* Attribute Chips */}
      {Object.entries(attributes).map(([key, val]) => (
        <span
          key={key}
          data-testid="filter-chip"
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs bg-primary/10 text-primary border border-primary/20 font-medium"
        >
          <span className="uppercase text-[10px] opacity-75">{key}:</span>
          <span>{val}</span>
          <button
            type="button"
            onClick={() => onRemoveAttribute(key)}
            className="hover:opacity-75 focus:outline-none"
            aria-label={`Remove ${key} filter`}
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </span>
      ))}

      {/* Clear All Button */}
      <button
        type="button"
        onClick={onClearAll}
        className="text-xs text-muted hover:text-primary underline ml-1 focus:outline-none"
      >
        {t('clearAll')}
      </button>
    </div>
  );
}
