'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { Star } from 'lucide-react';

interface RatingSummaryProps {
  avgRating: number;
  reviewCount: number;
  ratingBreakdown?: {
    '5': number;
    '4': number;
    '3': number;
    '2': number;
    '1': number;
  };
}

export function RatingSummary({
  avgRating,
  reviewCount,
  ratingBreakdown = { '5': 0, '4': 0, '3': 0, '2': 0, '1': 0 },
}: RatingSummaryProps) {
  const t = useTranslations('product');

  const total = reviewCount || Object.values(ratingBreakdown).reduce((a, b) => a + b, 0);

  const starLevels: Array<'5' | '4' | '3' | '2' | '1'> = ['5', '4', '3', '2', '1'];

  return (
    <div className="p-6 bg-surface rounded-card border border-border space-y-6" data-testid="rating-summary">
      <h3 className="font-bold text-lg text-foreground">{t('ratingSummary')}</h3>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
        {/* Big Score Block */}
        <div className="md:col-span-4 text-center sm:text-left flex flex-col items-center sm:items-start justify-center">
          <span className="text-5xl font-black text-foreground tracking-tight" data-testid="avg-rating">
            {avgRating > 0 ? avgRating.toFixed(1) : '0.0'}
          </span>
          <div className="flex items-center gap-1 my-2">
            {[1, 2, 3, 4, 5].map((star) => (
              <Star
                key={star}
                className={`w-5 h-5 ${
                  star <= Math.round(avgRating)
                    ? 'fill-amber-400 text-amber-400'
                    : 'fill-muted/20 text-muted/30'
                }`}
              />
            ))}
          </div>
          <span className="text-xs text-muted font-medium" data-testid="total-reviews-count">
            {t('basedOnReviews', { count: total })}
          </span>
        </div>

        {/* 5 -> 1 Rating Breakdown Progress Bars */}
        <div className="md:col-span-8 space-y-2" data-testid="rating-breakdown-bars">
          {starLevels.map((stars) => {
            const count = ratingBreakdown[stars] || 0;
            const percentage = total > 0 ? Math.round((count / total) * 100) : 0;

            return (
              <div
                key={stars}
                data-testid={`rating-bar-${stars}`}
                className="flex items-center gap-3 text-xs"
              >
                <div className="flex items-center gap-1 w-12 text-muted font-semibold flex-shrink-0">
                  <span>{stars}</span>
                  <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                </div>

                <div className="flex-1 h-2.5 bg-muted/20 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-amber-400 rounded-full transition-all duration-500"
                    style={{ width: `${percentage}%` }}
                  />
                </div>

                <div className="w-12 text-right text-muted font-medium flex-shrink-0">
                  <span data-testid={`rating-count-${stars}`}>{count}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
