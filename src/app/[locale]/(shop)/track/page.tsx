'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from '@/navigation';
import { Truck, Search, ArrowRight, Package } from 'lucide-react';

export default function TrackPage() {
  const t = useTranslations('track');
  const router = useRouter();
  const [trackingNumber, setTrackingNumber] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = trackingNumber.trim();
    if (trimmed) {
      router.push(`/track/${encodeURIComponent(trimmed)}`);
    }
  };

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-12 sm:py-20 space-y-8" data-testid="track-page">
      <div className="text-center space-y-3">
        <div className="w-14 h-14 rounded-2xl bg-primary/10 text-primary flex items-center justify-center mx-auto shadow-xs">
          <Truck className="w-7 h-7" />
        </div>
        <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight">
          {t('title')}
        </h1>
        <p className="text-xs sm:text-sm text-muted max-w-md mx-auto leading-relaxed">
          {t('subtitle')}
        </p>
      </div>

      <div className="bg-surface border border-border rounded-2xl p-6 sm:p-8 shadow-sm">
        <form onSubmit={handleSubmit} className="space-y-4" data-testid="track-search-form">
          <div className="relative">
            <Search className="w-5 h-5 text-muted absolute left-4 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              id="tracking-number-input"
              data-testid="tracking-number-input"
              value={trackingNumber}
              onChange={(e) => setTrackingNumber(e.target.value)}
              placeholder={t('placeholder')}
              className="w-full pl-12 pr-4 py-3.5 rounded-xl border border-border bg-background text-sm text-foreground placeholder:text-muted/60 focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary font-mono tracking-wide"
              required
            />
          </div>

          <button
            type="submit"
            data-testid="track-submit-btn"
            className="w-full py-3.5 px-6 rounded-xl bg-primary text-primary-foreground text-sm font-bold shadow-xs hover:bg-primary/90 transition-all flex items-center justify-center gap-2 cursor-pointer"
          >
            <span>{t('trackButton')}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>
      </div>
    </div>
  );
}
