'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { formatUsd, formatKhr } from '@/lib/money';
import { Flame, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';

interface FlashSalePriceCountdownProps {
  priceCents: number;
  originalPriceCents?: number;
  flashSaleEndsAt?: string;
  khrRate: number;
  isOnSale?: boolean;
}

export function FlashSalePriceCountdown({
  priceCents,
  originalPriceCents,
  flashSaleEndsAt,
  khrRate,
  isOnSale = true,
}: FlashSalePriceCountdownProps) {
  const t = useTranslations('product');
  const [mounted, setMounted] = useState(false);
  const [timeLeft, setTimeLeft] = useState<{
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
    expired: boolean;
  } | null>(null);

  useEffect(() => {
    setMounted(true);
    if (!flashSaleEndsAt) return;

    function calculateTime() {
      const diff = new Date(flashSaleEndsAt!).getTime() - new Date().getTime();
      if (diff <= 0) {
        return { days: 0, hours: 0, minutes: 0, seconds: 0, expired: true };
      }
      return {
        days: Math.floor(diff / (1000 * 60 * 60 * 24)),
        hours: Math.floor((diff / (1000 * 60 * 60)) % 24),
        minutes: Math.floor((diff / 1000 / 60) % 60),
        seconds: Math.floor((diff / 1000) % 60),
        expired: false,
      };
    }

    setTimeLeft(calculateTime());
    const interval = setInterval(() => {
      setTimeLeft(calculateTime());
    }, 1000);

    return () => clearInterval(interval);
  }, [flashSaleEndsAt]);

  const hasDiscount = Boolean(isOnSale && originalPriceCents && originalPriceCents > priceCents);

  return (
    <div className="space-y-3" data-testid="price-and-countdown">
      {/* Price display */}
      <div className="flex items-baseline gap-3 flex-wrap">
        <span
          className={cn(
            'text-3xl sm:text-4xl font-black tracking-tight',
            hasDiscount ? 'text-primary' : 'text-foreground'
          )}
          data-testid="product-price"
        >
          {formatUsd(priceCents)}
        </span>

        {hasDiscount && originalPriceCents && (
          <span
            className="text-lg sm:text-xl text-muted line-through font-medium"
            data-testid="original-price"
          >
            {formatUsd(originalPriceCents)}
          </span>
        )}

        <span className="text-sm font-semibold text-muted bg-muted/15 px-2.5 py-1 rounded-btn" data-testid="khr-price">
          {formatKhr(priceCents, khrRate)}
        </span>
      </div>

      {/* Flash Sale Banner & Countdown (Hidden when variant is not on sale) */}
      {isOnSale && mounted && flashSaleEndsAt && timeLeft && !timeLeft.expired && (
        <div
          data-testid="flash-sale-countdown-box"
          className="p-3 bg-red-500/10 border border-red-500/20 rounded-card flex items-center justify-between flex-wrap gap-2 text-red-600 dark:text-red-400"
        >
          <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider">
            <Flame className="w-4 h-4 fill-red-500 text-red-500 animate-pulse" />
            <span>{t('flashSaleEnds')}</span>
          </div>

          <div className="flex items-center gap-1.5 font-mono text-xs font-bold" suppressHydrationWarning>
            <Clock className="w-3.5 h-3.5" />
            {timeLeft.days > 0 && (
              <span className="bg-red-500 text-white px-1.5 py-0.5 rounded" suppressHydrationWarning>
                {timeLeft.days}{t('days')}
              </span>
            )}
            <span className="bg-red-500 text-white px-1.5 py-0.5 rounded" suppressHydrationWarning>
              {String(timeLeft.hours).padStart(2, '0')}{t('hours')}
            </span>
            <span>:</span>
            <span className="bg-red-500 text-white px-1.5 py-0.5 rounded" suppressHydrationWarning>
              {String(timeLeft.minutes).padStart(2, '0')}{t('mins')}
            </span>
            <span>:</span>
            <span className="bg-red-500 text-white px-1.5 py-0.5 rounded" suppressHydrationWarning>
              {String(timeLeft.seconds).padStart(2, '0')}{t('secs')}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
