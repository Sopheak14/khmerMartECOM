'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  const t = useTranslations('catalog');

  if (totalPages <= 1) return null;

  return (
    <div data-testid="catalog-pagination" className="flex items-center justify-between border-t border-border pt-6 mt-6">
      <button
        type="button"
        disabled={currentPage <= 1}
        onClick={() => onPageChange(currentPage - 1)}
        className="flex items-center gap-1 px-3 py-1.5 rounded-btn border border-border text-xs font-medium text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        <ChevronLeft className="w-3.5 h-3.5" />
        <span>{t('paginationPrev')}</span>
      </button>

      <span className="text-xs text-muted font-medium">
        {t('page', { page: currentPage, totalPages })}
      </span>

      <button
        type="button"
        disabled={currentPage >= totalPages}
        onClick={() => onPageChange(currentPage + 1)}
        className="flex items-center gap-1 px-3 py-1.5 rounded-btn border border-border text-xs font-medium text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
      >
        <span>{t('paginationNext')}</span>
        <ChevronRight className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}
