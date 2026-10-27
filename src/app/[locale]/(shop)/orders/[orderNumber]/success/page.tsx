'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { useParams, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { OrderDTO, PaymentStatusDTO } from '@/types/api';
import { downloadInvoice } from '@/lib/downloadInvoice';
import {
  CheckCircle2,
  ArrowRight,
  PackageCheck,
  CreditCard,
  QrCode,
  Building2,
  Banknote,
  Download,
  Copy,
  Check,
  Loader2,
  FileText,
} from 'lucide-react';
import { toast } from 'sonner';

export default function OrderSuccessPage() {
  const t = useTranslations('checkout');
  const params = useParams();
  const searchParams = useSearchParams();
  const orderNumber = params?.orderNumber as string;

  const [copied, setCopied] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [hash, setHash] = useState<string | null>(searchParams.get('hash') || null);

  const paymentId = searchParams.get('paymentId');

  // Fetch Order
  const { data: order, isLoading } = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: async () => {
      const res = await apiClient<OrderDTO>(`/orders/${orderNumber}`);
      return res.data;
    },
    enabled: Boolean(orderNumber),
  });

  // If hash not in URL but paymentId is present, fetch payment status
  useEffect(() => {
    if (!hash && paymentId) {
      apiClient<PaymentStatusDTO>(`/payments/${paymentId}/status`)
        .then((res) => {
          if (res.data?.bakongHash) {
            setHash(res.data.bakongHash);
          }
        })
        .catch(() => {});
    }
  }, [hash, paymentId]);

  const handleCopyHash = () => {
    if (!hash) return;
    navigator.clipboard.writeText(hash);
    setCopied(true);
    toast.success(t('copied') || 'Copied!');
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadInvoice = async () => {
    if (isDownloading || !orderNumber) return;
    setIsDownloading(true);
    try {
      const filename = await downloadInvoice(orderNumber);
      toast.success(`Downloaded ${filename}`);
    } catch (err: any) {
      toast.error(err.message || 'Failed to download invoice');
    } finally {
      setIsDownloading(false);
    }
  };

  const shortenHash = (h: string) => {
    if (h.length <= 16) return h;
    return `${h.slice(0, 8)}...${h.slice(-6)}`;
  };

  const getPaymentMethodDisplay = (method?: string) => {
    switch (method) {
      case 'KHQR':
        return {
          icon: QrCode,
          label: t('paymentKhqrTitle'),
          badgeClass: 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20',
        };
      case 'BANK_TRANSFER':
        return {
          icon: Building2,
          label: t('paymentBankTitle'),
          badgeClass: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
        };
      case 'COD':
      default:
        return {
          icon: Banknote,
          label: t('paymentCodTitle'),
          badgeClass: 'bg-green-500/10 text-green-800 dark:text-green-400 border-green-500/20',
        };
    }
  };

  const paymentMethodInfo = getPaymentMethodDisplay(order?.paymentMethod);
  const MethodIcon = paymentMethodInfo.icon;

  return (
    <div
      className="container mx-auto px-4 py-12 sm:py-16 max-w-xl text-center space-y-6"
      data-testid="order-success-page"
    >
      {/* Success Icon */}
      <div className="w-16 h-16 mx-auto rounded-full bg-green-500/10 text-green-800 dark:text-green-400 flex items-center justify-center animate-in zoom-in-75">
        <CheckCircle2 className="w-10 h-10" />
      </div>

      {/* Title */}
      <div className="space-y-2">
        <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight">
          {t('orderSuccessTitle')}
        </h1>
        <p className="text-xs text-muted max-w-md mx-auto">
          {t('orderSuccessDesc')}
        </p>
      </div>

      {/* Order Details Card */}
      <div className="p-6 rounded-card border border-border bg-surface space-y-4 shadow-xs text-left">
        {/* Order Number Row */}
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

        {/* Payment Method Row */}
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <span className="text-xs font-bold text-muted uppercase tracking-wider">
            {t('paymentMethod')}
          </span>
          <div
            data-testid="payment-method-badge"
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold border ${paymentMethodInfo.badgeClass}`}
          >
            <MethodIcon className="w-3.5 h-3.5 shrink-0" />
            <span>{paymentMethodInfo.label}</span>
          </div>
        </div>

        {/* Bakong Hash (if available) */}
        {hash && (
          <div
            className="p-3 bg-muted/10 rounded-btn border border-border flex items-center justify-between gap-2"
            data-testid="bakong-hash-container"
          >
            <div className="space-y-0.5 min-w-0">
              <span className="text-[10px] font-bold text-muted uppercase tracking-wider block">
                {t('bakongHash')}
              </span>
              <span
                data-testid="bakong-hash-display"
                className="font-mono text-xs font-bold text-foreground truncate block"
                title={hash}
              >
                {shortenHash(hash)}
              </span>
            </div>
            <button
              type="button"
              data-testid="copy-bakong-hash-btn"
              onClick={handleCopyHash}
              className="p-1.5 rounded text-muted hover:text-foreground hover:bg-muted/15 transition-colors shrink-0 flex items-center gap-1 text-xs font-semibold"
              title="Copy Transaction Hash"
            >
              {copied ? (
                <>
                  <Check className="w-3.5 h-3.5 text-green-500" />
                  <span className="text-[11px] text-green-800 font-bold" data-testid="copied-tooltip">
                    {t('copied')}
                  </span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5" />
                  <span className="text-[11px]">Copy</span>
                </>
              )}
            </button>
          </div>
        )}

        {/* Status Instructions */}
        <div className="space-y-2 text-xs text-muted pt-1">
          <div className="flex items-center gap-2 text-foreground font-semibold">
            <PackageCheck className="w-4 h-4 text-primary" />
            <span>{t('orderPlacedPendingShipment')}</span>
          </div>
          <p className="text-[11px] leading-relaxed">
            {order?.paymentMethod === 'COD'
              ? t('orderSuccessCodInstructions')
              : 'Your payment has been received and confirmed. You will receive an email and notification once your order is dispatched.'}
          </p>
        </div>

        {/* Download Invoice Button */}
        <div className="pt-2 border-t border-border">
          <button
            type="button"
            data-testid="download-invoice-btn"
            onClick={handleDownloadInvoice}
            disabled={isDownloading}
            className="w-full py-2.5 px-4 rounded-btn border border-border bg-background hover:bg-muted/10 font-bold text-xs text-foreground flex items-center justify-center gap-2 transition-colors disabled:opacity-50"
          >
            {isDownloading ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                <span>{t('downloading')}</span>
              </>
            ) : (
              <>
                <FileText className="w-3.5 h-3.5 text-primary" />
                <span>{t('downloadInvoice')}</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Navigation Buttons */}
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
