'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { ReviewDTO, PaginationMeta } from '@/types/api';
import { Star, CheckCircle, MessageSquare, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';

interface ReviewsListProps {
  productId?: number;
  reviews?: ReviewDTO[];
}

export function ReviewsList({ productId, reviews: initialReviews = [] }: ReviewsListProps) {
  const t = useTranslations('reviews');
  const prodT = useTranslations('product');

  const [sort, setSort] = useState<'newest' | 'highest' | 'lowest'>('newest');
  const [page, setPage] = useState(0);
  const pageSize = 10;

  const getSortParam = () => {
    switch (sort) {
      case 'highest':
        return 'rating,desc';
      case 'lowest':
        return 'rating,asc';
      case 'newest':
      default:
        return 'createdAt,desc';
    }
  };

  const { data, isLoading } = useQuery({
    queryKey: productId ? queryKeys.products.reviews(productId, { page, sort }) : ['reviews', sort, page],
    queryFn: async () => {
      if (!productId) {
        return { items: initialReviews, meta: undefined };
      }
      const res = await apiClient<ReviewDTO[]>(`/products/${productId}/reviews`, {
        query: {
          page,
          size: pageSize,
          sort: getSortParam(),
        },
      });
      return {
        items: res.data || [],
        meta: res.meta,
      };
    },
    initialData:
      page === 0 && sort === 'newest' && initialReviews.length > 0
        ? { items: initialReviews, meta: undefined }
        : undefined,
    enabled: Boolean(productId),
  });

  const reviews = data?.items || initialReviews;
  const meta: PaginationMeta | undefined = data?.meta;
  const totalPages = meta?.totalPages || 1;

  return (
    <div className="space-y-4" data-testid="approved-reviews-list">
      {/* Header with Title and Sort Select */}
      <div className="flex items-center justify-between flex-wrap gap-3 pb-2">
        <h3 className="font-bold text-lg text-foreground">
          {prodT('reviewsList')} {meta?.total ? `(${meta.total})` : reviews.length > 0 ? `(${reviews.length})` : ''}
        </h3>

        <div className="flex items-center gap-2">
          <label htmlFor="reviews-sort-select" className="text-xs font-semibold text-muted">
            {t('sortBy')}:
          </label>
          <select
            id="reviews-sort-select"
            data-testid="reviews-sort-select"
            value={sort}
            onChange={(e) => {
              setSort(e.target.value as any);
              setPage(0);
            }}
            className="text-xs py-1.5 px-2.5 rounded-btn border border-border bg-surface text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20 cursor-pointer"
          >
            <option value="newest">{t('sortNewest')}</option>
            <option value="highest">{t('sortHighest')}</option>
            <option value="lowest">{t('sortLowest')}</option>
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="p-8 text-center bg-surface rounded-card border border-border text-xs text-muted space-y-2">
          <Loader2 className="w-5 h-5 animate-spin mx-auto text-primary" />
          <p>Loading reviews...</p>
        </div>
      ) : !reviews || reviews.length === 0 ? (
        <div
          data-testid="no-reviews-box"
          className="p-8 text-center bg-surface rounded-card border border-border text-muted text-xs"
        >
          {prodT('noReviews')}
        </div>
      ) : (
        <div className="space-y-3">
          {reviews.map((rev) => (
            <div
              key={rev.id}
              data-testid="review-item"
              data-review-id={rev.id}
              className="p-4 bg-surface rounded-card border border-border space-y-2.5"
            >
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-sm text-foreground">
                    {rev.user?.fullName || 'KhmerMart Customer'}
                  </span>
                  {rev.verifiedPurchase && (
                    <span
                      data-testid="verified-purchase-badge"
                      className="flex items-center gap-1 text-[11px] text-green-800 dark:text-green-400 font-medium bg-green-500/10 px-2 py-0.5 rounded-full"
                    >
                      <CheckCircle className="w-3 h-3" />
                      <span>{t('verifiedPurchase')}</span>
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Star
                      key={star}
                      className={`w-3.5 h-3.5 ${
                        star <= rev.rating
                          ? 'fill-amber-400 text-amber-400'
                          : 'fill-muted/20 text-muted/30'
                      }`}
                    />
                  ))}
                </div>
              </div>

              {rev.title && (
                <h4 className="font-semibold text-xs text-foreground" data-testid="review-title">
                  {rev.title === 'Verified Purchase Review' ? prodT('verifiedPurchaseReview') : rev.title}
                </h4>
              )}

              <p className="text-xs text-muted leading-relaxed" data-testid="review-body">
                {rev.body}
              </p>

              {/* Admin Reply */}
              {rev.adminReply && (
                <div
                  data-testid="review-admin-reply"
                  className="mt-3 p-3 bg-muted/15 rounded-btn border-l-2 border-primary space-y-1"
                >
                  <div className="flex items-center gap-1 text-[11px] font-bold text-primary">
                    <MessageSquare className="w-3 h-3" />
                    <span>{prodT('storeReply')}</span>
                  </div>
                  <p className="text-xs text-foreground italic">{rev.adminReply}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div
          data-testid="reviews-pagination"
          className="pt-4 border-t border-border flex items-center justify-between text-xs text-muted"
        >
          <span>
            {t('page', { current: page + 1, total: totalPages })}
          </span>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="p-1.5 rounded-btn border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors cursor-pointer"
              aria-label={t('prev')}
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              className="p-1.5 rounded-btn border border-border hover:bg-muted/10 disabled:opacity-40 transition-colors cursor-pointer"
              aria-label={t('next')}
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
