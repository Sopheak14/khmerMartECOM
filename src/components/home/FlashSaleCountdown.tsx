'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';

export interface FlashSaleCountdownProps {
  endsAt: string;
}

export function FlashSaleCountdown({ endsAt }: FlashSaleCountdownProps) {
  const t = useTranslations('home');
  const [timeLeft, setTimeLeft] = useState<{
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
  }>({ days: 0, hours: 0, minutes: 0, seconds: 0 });

  useEffect(() => {
    function computeTimeLeft() {
      const targetTime = new Date(endsAt).getTime();
      const now = Date.now();
      const diff = Math.max(0, targetTime - now);

      const days = Math.floor(diff / (1000 * 60 * 60 * 24));
      const hours = Math.floor((diff / (1000 * 60 * 60)) % 24);
      const minutes = Math.floor((diff / 1000 / 60) % 60);
      const seconds = Math.floor((diff / 1000) % 60);

      return { days, hours, minutes, seconds };
    }

    setTimeLeft(computeTimeLeft());
    const interval = setInterval(() => {
      setTimeLeft(computeTimeLeft());
    }, 1000);

    return () => clearInterval(interval);
  }, [endsAt]);

  const pad = (n: number) => String(n).padStart(2, '0');

  return (
    <div className="flex items-center gap-1.5 font-mono text-xs sm:text-sm font-bold text-foreground">
      {timeLeft.days > 0 && (
        <>
          <div className="flex items-center gap-0.5 bg-secondary text-secondary-foreground px-2 py-1 rounded-btn shadow-sm">
            <span>{pad(timeLeft.days)}</span>
            <span className="text-[10px] text-accent ml-0.5">{t('days')}</span>
          </div>
          <span className="text-muted">:</span>
        </>
      )}

      <div className="flex items-center gap-0.5 bg-secondary text-secondary-foreground px-2 py-1 rounded-btn shadow-sm">
        <span>{pad(timeLeft.hours)}</span>
        <span className="text-[10px] text-accent ml-0.5">{t('hours')}</span>
      </div>
      <span className="text-muted">:</span>

      <div className="flex items-center gap-0.5 bg-secondary text-secondary-foreground px-2 py-1 rounded-btn shadow-sm">
        <span>{pad(timeLeft.minutes)}</span>
        <span className="text-[10px] text-accent ml-0.5">{t('mins')}</span>
      </div>
      <span className="text-muted">:</span>

      <div className="flex items-center gap-0.5 bg-primary text-primary-foreground px-2 py-1 rounded-btn shadow-sm">
        <span>{pad(timeLeft.seconds)}</span>
        <span className="text-[10px] text-white/90 ml-0.5">{t('secs')}</span>
      </div>
    </div>
  );
}
