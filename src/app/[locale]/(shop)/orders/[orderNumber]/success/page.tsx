'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { useParams } from 'next/navigation';
import { Link } from '@/navigation';
import { CheckCircle2, ShoppingBag, ArrowRight, PackageCheck } from 'lucide-react';

export default function OrderSuccessPage() {
  const t = useTranslations('checkout');
  const params = useParams();
  const orderNumber = params?.orderNumber as string;

  return (
    <div
      className="container mx-auto px-4 py-16 max-w-xl text-center space-y-6"
      data-testid="order-success-page"
    >
      <div className="w-16 h-16 mx-auto rounded-full bg-green-500/10 text-green-600 dark:text-green-400 flex items-center justify-center animate-in zoom-in-75">
        <CheckCircle2 className="w-10 h-10" />
      </div>

      <div className="space-y-2">
        <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight">
          {t('orderSuccessTitle')}
        </h1>
        <p className="text-xs text-muted max-w-md mx-auto">
          {t('orderSuccessDesc')}
        </p>
      </div>

      <div className="p-6 rounded-card border border-border bg-surface space-y-4 shadow-xs text-left">
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <span className="text-xs font-bold text-muted uppercase tracking-wider">
            {t('orderNumber')}
          </span>
          <span
            data-testid="order-success-number"
            className="font-mono font-bold text-sm text-foreground bg-muted/15 px-2.5 py-1 rounded"
          >
            {orderNumber}
          </span>
        </div>

        <div className="space-y-2 text-xs text-muted">
          <div className="flex items-center gap-2 text-foreground font-semibold">
            <PackageCheck className="w-4 h-4 text-primary" />
            <span>{t('orderPlacedPendingShipment')}</span>
          </div>
          <p className="text-[11px] leading-relaxed">
            {t('orderSuccessCodInstructions')}
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
        <Link
          href={`/orders/${orderNumber}`}
          data-testid="view-order-btn"
          className="inline-flex items-center gap-1.5 px-6 py-2.5 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-xs text-foreground transition-colors"
        >
          <span>{t('viewOrder')}</span>
        </Link>
        <Link
          href="/products"
          data-testid="continue-shopping-btn"
          className="inline-flex items-center gap-1.5 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-xs shadow-xs hover:bg-primary/90 transition-colors"
        >
          <span>{t('continueShopping')}</span>
          <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
    </div>
  );
}
