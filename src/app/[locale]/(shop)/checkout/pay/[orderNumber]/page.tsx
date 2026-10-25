'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { useParams } from 'next/navigation';
import { Link } from '@/navigation';
import { QrCode, ArrowLeft, CheckCircle2 } from 'lucide-react';

export default function CheckoutPayPage() {
  const t = useTranslations('checkout');
  const params = useParams();
  const orderNumber = params?.orderNumber as string;

  return (
    <div className="container mx-auto px-4 py-16 max-w-xl text-center space-y-6">
      <div className="w-16 h-16 mx-auto rounded-full bg-primary/10 flex items-center justify-center text-primary">
        <QrCode className="w-8 h-8" />
      </div>

      <div className="space-y-2">
        <h1 className="text-2xl font-black text-foreground tracking-tight">
          {t('khqrPaymentTitle')}
        </h1>
        <p className="text-xs text-muted">
          Order Number: <strong className="text-foreground font-mono" data-testid="order-number-display">{orderNumber}</strong>
        </p>
      </div>

      <div className="p-6 rounded-card border border-border bg-surface space-y-4 shadow-xs">
        <div className="p-4 bg-muted/10 rounded-btn border border-dashed border-border flex flex-col items-center justify-center space-y-2">
          <QrCode className="w-32 h-32 text-foreground/80 animate-pulse" />
          <span className="text-[11px] text-muted font-medium">
            * Phase 5 Placeholder: Real-time Bakong KHQR dynamic QR & WebSocket arrive in Phase 5.
          </span>
        </div>

        <p className="text-xs text-muted">
          Your order has been placed in PENDING_PAYMENT status. Once KHQR payment is completed, you will be redirected to the order confirmation page.
        </p>
      </div>

      <div className="pt-2">
        <Link
          href="/"
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-xs shadow-xs hover:bg-primary/90 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>{t('returnHome')}</span>
        </Link>
      </div>
    </div>
  );
}
