'use client';

import { useState, useEffect, useRef } from 'react';
import { useRouter } from '@/navigation';
import { useTranslations } from 'next-intl';
import { Search, Loader2 } from 'lucide-react';
import { apiClient } from '@/lib/apiClient';
import { formatUsd } from '@/lib/money';
import Image from 'next/image';

interface SuggestResult {
  products: { slug: string; name: string; imageUrl: string; priceCents: number }[];
  categories: { slug: string; name: string }[];
}

export function SearchBar() {
  const t = useTranslations('common');
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<SuggestResult | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!query.trim() || query.length < 2) {
      setSuggestions(null);
      return;
    }

    const timer = setTimeout(async () => {
      setIsLoading(true);
      try {
        const res = await apiClient<SuggestResult>('/search/suggest', {
          query: { q: query },
          auth: false,
        });
        setSuggestions(res.data);
        setIsOpen(true);
      } catch {
        setSuggestions(null);
      } finally {
        setIsLoading(false);
      }
    }, 250);

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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      setIsOpen(false);
      router.push(`/search?q=${encodeURIComponent(query.trim())}`);
    }
  };

  return (
    <div ref={containerRef} className="relative w-full max-w-xl">
      <form onSubmit={handleSubmit} className="relative flex items-center">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => query.length >= 2 && setIsOpen(true)}
          placeholder={t('searchPlaceholder')}
          className="w-full h-10 pl-10 pr-10 rounded-btn border border-border/80 bg-surface text-sm text-foreground placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-primary shadow-sm"
        />
        <Search className="absolute left-3 w-4 h-4 text-muted pointer-events-none" />
        {isLoading && (
          <Loader2 className="absolute right-3 w-4 h-4 text-muted animate-spin" />
        )}
      </form>

      {/* Autocomplete suggestions dropdown */}
      {isOpen && suggestions && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-surface border border-border rounded-card shadow-lg z-50 overflow-hidden divide-y divide-border">
          {/* Categories suggestions */}
          {suggestions.categories?.length > 0 && (
            <div className="p-2">
              <span className="text-xs font-semibold text-muted px-2 uppercase tracking-wider">
                Categories
              </span>
              <div className="mt-1 space-y-1">
                {suggestions.categories.map((c) => (
                  <button
                    key={c.slug}
                    type="button"
                    onClick={() => {
                      setIsOpen(false);
                      router.push(`/category/${c.slug}`);
                    }}
                    className="w-full text-left px-2 py-1.5 text-xs rounded hover:bg-muted/10 flex items-center justify-between"
                  >
                    <span className="font-medium text-foreground">{c.name}</span>
                    <span className="text-muted text-[10px]">Category</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Products suggestions */}
          {suggestions.products?.length > 0 && (
            <div className="p-2">
              <span className="text-xs font-semibold text-muted px-2 uppercase tracking-wider">
                Products
              </span>
              <div className="mt-1 space-y-1">
                {suggestions.products.map((p) => (
                  <button
                    key={p.slug}
                    type="button"
                    onClick={() => {
                      setIsOpen(false);
                      router.push(`/products/${p.slug}`);
                    }}
                    className="w-full text-left px-2 py-1.5 text-xs rounded hover:bg-muted/10 flex items-center gap-2.5"
                  >
                    <div className="relative w-8 h-8 rounded overflow-hidden flex-shrink-0 bg-muted/20">
                      {p.imageUrl ? (
                        <Image
                          src={p.imageUrl}
                          alt={p.name}
                          fill
                          className="object-cover"
                        />
                      ) : null}
                    </div>
                    <span className="font-medium text-foreground truncate flex-1">{p.name}</span>
                    <span className="font-semibold text-primary">{formatUsd(p.priceCents)}</span>
                  </button>
                ))}
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
