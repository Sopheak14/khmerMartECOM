'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { ReviewDTO } from '@/types/api';
import { Star, CheckCircle, MessageSquare } from 'lucide-react';

interface ReviewsListProps {
  reviews: ReviewDTO[];
}

export function ReviewsList({ reviews }: ReviewsListProps) {
  const t = useTranslations('product');

  if (!reviews || reviews.length === 0) {
    return (
      <div
        data-testid="no-reviews-box"
        className="p-8 text-center bg-surface rounded-card border border-border text-muted text-xs"
      >
        {t('noReviews')}
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="approved-reviews-list">
      <h3 className="font-bold text-lg text-foreground">{t('reviewsList')}</h3>

      <div className="space-y-3">
        {reviews.map((rev) => (
          <div
            key={rev.id}
            data-testid="review-item"
            className="p-4 bg-surface rounded-card border border-border space-y-2.5"
          >
            <div className="flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-sm text-foreground">
                  {rev.user?.fullName || 'KhmerMart Customer'}
                </span>
                {rev.verifiedPurchase && (
                  <span className="flex items-center gap-1 text-[11px] text-green-600 dark:text-green-400 font-medium bg-green-500/10 px-2 py-0.5 rounded-full">
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
              <h4 className="font-semibold text-xs text-foreground">
                {rev.title === 'Verified Purchase Review' ? t('verifiedPurchaseReview') : rev.title}
              </h4>
            )}

            <p className="text-xs text-muted leading-relaxed">{rev.body}</p>

            {/* Admin Reply */}
            {rev.adminReply && (
              <div className="mt-3 p-3 bg-muted/15 rounded-btn border-l-2 border-primary space-y-1">
                <div className="flex items-center gap-1 text-[11px] font-bold text-primary">
                  <MessageSquare className="w-3 h-3" />
                  <span>{t('storeReply')}</span>
                </div>
                <p className="text-xs text-foreground italic">{rev.adminReply}</p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
