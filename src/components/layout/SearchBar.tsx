'use client';

import { useState, useEffect, useRef, useMemo } from 'react';
import { useRouter } from '@/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { Search, Loader2 } from 'lucide-react';
import { apiClient } from '@/lib/apiClient';
import { formatUsd } from '@/lib/money';
import Image from 'next/image';

interface SuggestItem {
  type: 'category' | 'product';
  slug: string;
  name: string;
  nameKm?: string;
  imageUrl?: string;
  priceCents?: number;
}

interface ApiSuggestResponse {
  products: { slug: string; name: string; nameKm?: string; imageUrl: string; priceCents: number }[];
  categories: { slug: string; name: string; nameKm?: string }[];
}

export function SearchBar() {
  const t = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<ApiSuggestResponse | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Flattened items for easy keyboard navigation
  const flatItems: SuggestItem[] = useMemo(() => {
    if (!suggestions) return [];
    const items: SuggestItem[] = [];
    if (suggestions.categories?.length) {
      suggestions.categories.forEach((c) => {
        items.push({ type: 'category', ...c });
      });
    }
    if (suggestions.products?.length) {
      suggestions.products.forEach((p) => {
        items.push({ type: 'product', ...p });
      });
    }
    return items;
  }, [suggestions]);

  useEffect(() => {
    if (!query.trim() || query.length < 2) {
      setSuggestions(null);
      setIsOpen(false);
      setActiveIndex(-1);
      return;
    }

    const timer = setTimeout(async () => {
      setIsLoading(true);
      try {
        const res = await apiClient<ApiSuggestResponse>('/search/suggest', {
          query: { q: query.trim() },
          auth: false,
        });
        setSuggestions(res.data);
        setIsOpen(true);
        setActiveIndex(-1);
      } catch {
        setSuggestions(null);
      } finally {
        setIsLoading(false);
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [query]);

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const navigateToItem = (item: SuggestItem) => {
    setIsOpen(false);
    setQuery('');
    if (item.type === 'category') {
      router.push(`/category/${item.slug}`);
    } else {
      router.push(`/products/${item.slug}`);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (activeIndex >= 0 && flatItems[activeIndex]) {
      navigateToItem(flatItems[activeIndex]);
      return;
    }

    if (query.trim()) {
      setIsOpen(false);
      router.push(`/products?q=${encodeURIComponent(query.trim())}`);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!isOpen || flatItems.length === 0) {
      if (e.key === 'Enter') {
        handleSubmit(e);
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((prev) => (prev < flatItems.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((prev) => (prev > 0 ? prev - 1 : flatItems.length - 1));
    } else if (e.key === 'Escape') {
      setIsOpen(false);
      setActiveIndex(-1);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeIndex >= 0 && flatItems[activeIndex]) {
        navigateToItem(flatItems[activeIndex]);
      } else {
        handleSubmit(e);
      }
    }
  };

  return (
    <div ref={containerRef} className="relative w-full max-w-xl">
      <form onSubmit={handleSubmit} className="relative flex items-center">
        <input
          ref={inputRef}
          data-testid="search-input"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => query.trim().length >= 2 && setIsOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={t('searchPlaceholder')}
          className="w-full h-10 pl-10 pr-10 rounded-btn border border-border/80 bg-surface text-sm text-foreground placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-primary shadow-sm"
          autoComplete="off"
        />
        <Search className="absolute left-3 w-4 h-4 text-muted pointer-events-none" />
        {isLoading && (
          <Loader2 className="absolute right-3 w-4 h-4 text-muted animate-spin" />
        )}
      </form>

      {/* Autocomplete suggestions dropdown */}
      {isOpen && suggestions && (
        <div
          data-testid="search-suggestions"
          className="absolute top-full left-0 right-0 mt-1 bg-surface border border-border rounded-card shadow-lg z-50 overflow-hidden divide-y divide-border"
        >
          {/* Categories suggestions */}
          {suggestions.categories?.length > 0 && (
            <div className="p-2">
              <span className="text-xs font-semibold text-muted px-2 uppercase tracking-wider">
                {t('categories')}
              </span>
              <div className="mt-1 space-y-1">
                {suggestions.categories.map((c) => {
                  const globalIdx = flatItems.findIndex(
                    (it) => it.type === 'category' && it.slug === c.slug
                  );
                  const isSelected = globalIdx === activeIndex;
                  const displayName = locale === 'km' && c.nameKm ? c.nameKm : c.name;

                  return (
                    <button
                      key={c.slug}
                      data-testid="suggestion-item"
                      data-active={isSelected}
                      type="button"
                      onClick={() => navigateToItem({ type: 'category', ...c })}
                      onMouseEnter={() => setActiveIndex(globalIdx)}
                      className={`w-full text-left px-2 py-1.5 text-xs rounded flex items-center justify-between transition-colors ${
                        isSelected ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-muted/10 text-foreground'
                      }`}
                    >
                      <span className="font-medium truncate">{displayName}</span>
                      <span className="text-muted text-[10px] uppercase font-semibold">Category</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Products suggestions */}
          {suggestions.products?.length > 0 && (
            <div className="p-2">
              <span className="text-xs font-semibold text-muted px-2 uppercase tracking-wider">
                {t('products')}
              </span>
              <div className="mt-1 space-y-1">
                {suggestions.products.map((p) => {
                  const globalIdx = flatItems.findIndex(
                    (it) => it.type === 'product' && it.slug === p.slug
                  );
                  const isSelected = globalIdx === activeIndex;
                  const displayName = locale === 'km' && p.nameKm ? p.nameKm : p.name;

                  return (
                    <button
                      key={p.slug}
                      data-testid="suggestion-item"
                      data-active={isSelected}
                      type="button"
                      onClick={() => navigateToItem({ type: 'product', ...p })}
                      onMouseEnter={() => setActiveIndex(globalIdx)}
                      className={`w-full text-left px-2 py-1.5 text-xs rounded flex items-center gap-2.5 transition-colors ${
                        isSelected ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-muted/10 text-foreground'
                      }`}
                    >
                      <div className="relative w-8 h-8 rounded overflow-hidden flex-shrink-0 bg-muted/20">
                        {p.imageUrl ? (
                          <Image
                            src={p.imageUrl}
                            alt={displayName}
                            fill
                            className="object-cover"
                          />
                        ) : null}
                      </div>
                      <span className="font-medium truncate flex-1">{displayName}</span>
                      {p.priceCents !== undefined && (
                        <span className="font-semibold text-primary">{formatUsd(p.priceCents)}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {suggestions.categories?.length === 0 && suggestions.products?.length === 0 && (
            <div className="p-4 text-center text-xs text-muted">
              {t('emptyState')}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
