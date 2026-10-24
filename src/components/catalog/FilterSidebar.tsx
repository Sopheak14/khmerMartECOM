'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { ProductFacets } from '@/types/api';
import { formatUsd } from '@/lib/money';
import { Filter, Check, ChevronDown, ChevronUp, RotateCcw } from 'lucide-react';

interface FilterSidebarProps {
  facets?: ProductFacets;
  selectedBrand?: string;
  minPrice?: string;
  maxPrice?: string;
  selectedAttributes: Record<string, string>;
  inStockOnly?: boolean;
  onFilterChange: (updates: {
    brand?: string | null;
    minPrice?: string | null;
    maxPrice?: string | null;
    inStock?: boolean | null;
    attributes?: Record<string, string>;
  }) => void;
  onClearAll: () => void;
}

export function FilterSidebar({
  facets,
  selectedBrand,
  minPrice: initialMinPrice = '',
  maxPrice: initialMaxPrice = '',
  selectedAttributes,
  inStockOnly = false,
  onFilterChange,
  onClearAll,
}: FilterSidebarProps) {
  const t = useTranslations('catalog');
  const [minPriceInput, setMinPriceInput] = useState(initialMinPrice);
  const [maxPriceInput, setMaxPriceInput] = useState(initialMaxPrice);
  const [brandsOpen, setBrandsOpen] = useState(true);

  useEffect(() => {
    setMinPriceInput(initialMinPrice);
  }, [initialMinPrice]);

  useEffect(() => {
    setMaxPriceInput(initialMaxPrice);
  }, [initialMaxPrice]);

  const handlePriceApply = (e: React.FormEvent) => {
    e.preventDefault();
    onFilterChange({
      minPrice: minPriceInput || null,
      maxPrice: maxPriceInput || null,
    });
  };

  const handleBrandSelect = (brandVal: string) => {
    if (selectedBrand === brandVal) {
      onFilterChange({ brand: null });
    } else {
      onFilterChange({ brand: brandVal });
    }
  };

  const handleAttributeToggle = (attrName: string, attrVal: string) => {
    const next = { ...selectedAttributes };
    if (next[attrName] === attrVal) {
      delete next[attrName];
    } else {
      next[attrName] = attrVal;
    }
    onFilterChange({ attributes: next });
  };

  return (
    <aside className="w-full lg:w-64 space-y-6 flex-shrink-0" data-testid="filter-sidebar">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-border">
        <div className="flex items-center gap-2 font-bold text-base text-foreground">
          <Filter className="w-4 h-4 text-primary" />
          <span>{t('filters')}</span>
        </div>
        <button
          type="button"
          data-testid="clear-all-filters-btn"
          onClick={onClearAll}
          className="text-xs text-muted hover:text-primary flex items-center gap-1 transition-colors"
        >
          <RotateCcw className="w-3 h-3" />
          <span>{t('clearAll')}</span>
        </button>
      </div>

      {/* In-Stock Toggle */}
      <div className="p-3 bg-muted/10 rounded-card border border-border flex items-center justify-between">
        <label htmlFor="inStockFilter" className="text-sm font-medium text-foreground cursor-pointer select-none">
          {t('inStockOnly')}
        </label>
        <input
          id="inStockFilter"
          data-testid="in-stock-filter"
          type="checkbox"
          checked={inStockOnly}
          onChange={(e) => onFilterChange({ inStock: e.target.checked ? true : null })}
          className="w-4 h-4 text-primary rounded border-border focus:ring-primary cursor-pointer"
        />
      </div>

      {/* Price Range Filter */}
      <div className="space-y-3 pb-5 border-b border-border">
        <h3 className="font-semibold text-sm text-foreground">{t('priceRange')}</h3>
        <form onSubmit={handlePriceApply} className="space-y-2.5">
          <div className="flex items-center gap-2">
            <div className="flex-1">
              <input
                type="number"
                data-testid="min-price-input"
                placeholder={t('minPrice')}
                value={minPriceInput}
                onChange={(e) => setMinPriceInput(e.target.value)}
                className="w-full text-xs h-8 px-2 rounded-btn border border-border bg-surface text-foreground focus:ring-1 focus:ring-primary focus:outline-none"
              />
            </div>
            <span className="text-muted text-xs">–</span>
            <div className="flex-1">
              <input
                type="number"
                data-testid="max-price-input"
                placeholder={t('maxPrice')}
                value={maxPriceInput}
                onChange={(e) => setMaxPriceInput(e.target.value)}
                className="w-full text-xs h-8 px-2 rounded-btn border border-border bg-surface text-foreground focus:ring-1 focus:ring-primary focus:outline-none"
              />
            </div>
          </div>
          <button
            type="submit"
            data-testid="apply-price-btn"
            className="w-full h-7 rounded-btn bg-secondary text-secondary-foreground text-xs font-medium hover:bg-secondary/90 transition-colors"
          >
            {t('applyPrice')}
          </button>
        </form>
      </div>

      {/* Brands Facet */}
      {facets?.brands && facets.brands.length > 0 && (
        <div className="space-y-3 pb-5 border-b border-border">
          <button
            type="button"
            onClick={() => setBrandsOpen(!brandsOpen)}
            className="flex items-center justify-between w-full font-semibold text-sm text-foreground"
          >
            <span>{t('brand')}</span>
            {brandsOpen ? <ChevronUp className="w-4 h-4 text-muted" /> : <ChevronDown className="w-4 h-4 text-muted" />}
          </button>
          {brandsOpen && (
            <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
              {facets.brands.map((b) => {
                const isSelected = selectedBrand === b.value;
                return (
                  <button
                    key={b.value}
                    data-testid={`brand-filter-${b.value}`}
                    type="button"
                    onClick={() => handleBrandSelect(b.value)}
                    className={`w-full flex items-center justify-between text-xs py-1 px-1.5 rounded transition-colors ${
                      isSelected
                        ? 'bg-primary/10 text-primary font-bold'
                        : 'text-foreground hover:bg-muted/10'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <div
                        className={`w-3.5 h-3.5 rounded border flex items-center justify-center ${
                          isSelected ? 'bg-primary border-primary text-white' : 'border-border'
                        }`}
                      >
                        {isSelected && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                      </div>
                      <span className="truncate">{b.value}</span>
                    </div>
                    <span className="text-[10px] text-muted font-medium bg-muted/20 px-1.5 py-0.5 rounded-full">
                      {b.count}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Specification Attributes (RAM, Storage, Color, etc.) */}
      {facets?.attributes && facets.attributes.length > 0 && (
        <div className="space-y-4">
          <h3 className="font-semibold text-sm text-foreground">{t('specifications')}</h3>
          {facets.attributes.map((attr) => (
            <div key={attr.name} className="space-y-2">
              <span className="text-xs uppercase font-bold tracking-wider text-muted">
                {attr.name}
              </span>
              <div className="flex flex-wrap gap-1.5">
                {attr.values.map((v) => {
                  const isSelected = selectedAttributes[attr.name] === v.value;
                  return (
                    <button
                      key={v.value}
                      data-testid={`attr-filter-${attr.name}-${v.value}`}
                      type="button"
                      onClick={() => handleAttributeToggle(attr.name, v.value)}
                      className={`text-xs px-2.5 py-1 rounded-btn border transition-all flex items-center gap-1.5 ${
                        isSelected
                          ? 'border-primary bg-primary text-primary-foreground font-semibold shadow-xs'
                          : 'border-border bg-surface text-foreground hover:border-primary/50'
                      }`}
                    >
                      <span>{v.value}</span>
                      <span
                        className={`text-[10px] px-1 rounded-full ${
                          isSelected ? 'bg-white/20 text-white' : 'bg-muted/20 text-muted'
                        }`}
                      >
                        {v.count}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}
    </aside>
  );
}
