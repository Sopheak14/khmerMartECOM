import React from 'react';
import { formatUsd, formatKhr, DEFAULT_KHR_RATE } from '@/lib/money';
import { cn } from '@/lib/utils';

export interface PriceProps {
  cents: number;
  compareAtCents?: number | null;
  salePriceCents?: number | null;
  khrRate?: number;
  size?: 'sm' | 'md' | 'lg' | 'xl';
  showKhr?: boolean;
  className?: string;
}

export function Price({
  cents,
  compareAtCents,
  salePriceCents,
  khrRate = DEFAULT_KHR_RATE,
  size = 'md',
  showKhr = true,
  className,
}: PriceProps) {
  // If salePriceCents is active and lower than regular price (cents)
  const isSaleActive = salePriceCents !== undefined && salePriceCents !== null && salePriceCents < cents;
  const currentPrice = isSaleActive ? salePriceCents! : cents;

  // Struck price ("was" price):
  // 1. When on sale, "was" price is the regular price (cents) of the base/shown variant
  // 2. When not on sale, "was" price is compareAtCents (if higher than cents)
  const originalPrice = isSaleActive
    ? cents
    : (compareAtCents && compareAtCents > cents ? compareAtCents : null);

  const hasDiscount = Boolean(originalPrice && originalPrice > currentPrice);

  const sizeClasses = {
    sm: 'text-sm',
    md: 'text-base font-semibold',
    lg: 'text-xl font-bold',
    xl: 'text-2xl lg:text-3xl font-extrabold',
  }[size];

  const khrSizeClasses = {
    sm: 'text-xs',
    md: 'text-xs',
    lg: 'text-sm',
    xl: 'text-sm font-medium',
  }[size];

  return (
    <div className={cn('inline-flex flex-col tabular-nums', className)}>
      <div className="flex items-baseline gap-2 flex-wrap">
        <span
          className={cn(
            sizeClasses,
            hasDiscount ? 'text-primary' : 'text-foreground'
          )}
        >
          {formatUsd(currentPrice)}
        </span>

        {hasDiscount && originalPrice && (
          <span className="text-xs lg:text-sm text-muted-foreground line-through">
            {formatUsd(originalPrice)}
          </span>
        )}
      </div>

      {showKhr && (
        <span className={cn('text-muted-foreground font-normal', khrSizeClasses)}>
          {formatKhr(currentPrice, khrRate)}
        </span>
      )}
    </div>
  );
}
