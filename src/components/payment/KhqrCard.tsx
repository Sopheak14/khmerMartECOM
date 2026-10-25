'use client';

import React, { useState, useEffect, useRef } from 'react';
import Image from 'next/image';
import { useTranslations, useLocale } from 'next-intl';
import { useRouter } from '@/navigation';
import { useKhqrPayment } from '@/hooks/useKhqrPayment';
import { calculateKhqrCountdown } from '@/lib/khqrCountdown';
import { formatUsd, formatKhr } from '@/lib/money';
import { apiClient } from '@/lib/apiClient';
import {
  Download,
  ExternalLink,
  RefreshCw,
  XCircle,
  AlertCircle,
  CheckCircle2,
  Loader2,
  Clock,
  ArrowLeft,
  QrCode,
  ShieldAlert,
} from 'lucide-react';
import { toast } from 'sonner';

interface KhqrCardProps {
  orderNumber: string;
  khrRate?: number;
}

export function KhqrCard({ orderNumber, khrRate = 4100 }: KhqrCardProps) {
  const t = useTranslations('khqr');
  const common = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();

  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [isCancelling, setIsCancelling] = useState(false);
  const [isMobile, setIsMobile] = useState(false);

  const {
    payment,
    status,
    orderStatus,
    isLoading,
    error,
    isRegenerating,
    isSimulating,
    regenerate,
    simulatePaid,
  } = useKhqrPayment({
    orderNumber,
    onPaid: (statusDto) => {
      toast.success(t('paymentSuccessful'));
      // Wait 2 seconds before redirecting to success page
      setTimeout(() => {
        const queryParams = new URLSearchParams();
        if (statusDto?.bakongHash) queryParams.set('hash', statusDto.bakongHash);
        if (statusDto?.paymentId) queryParams.set('paymentId', String(statusDto.paymentId));
        const qs = queryParams.toString();
        router.push(`/orders/${orderNumber}/success${qs ? `?${qs}` : ''}`);
      }, 2000);
    },
  });

  // Countdown state
  const [countdown, setCountdown] = useState<{
    formatted: string;
    isExpired: boolean;
    isUnderOneMinute: boolean;
  }>({ formatted: '--:--', isExpired: false, isUnderOneMinute: false });

  // Mobile detection
  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(
        window.innerWidth <= 768 ||
          /Android|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
      );
    };
    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Countdown timer effect
  useEffect(() => {
    if (!payment?.expiresAt) return;

    const updateTimer = () => {
      const res = calculateKhqrCountdown(payment.expiresAt);
      setCountdown({
        formatted: res.formatted,
        isExpired: res.isExpired,
        isUnderOneMinute: res.isUnderOneMinute,
      });
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [payment?.expiresAt]);

  // Handle Download QR to canvas PNG
  const handleDownloadQr = () => {
    if (!payment?.qrImageDataUrl) return;

    const canvas = document.createElement('canvas');
    canvas.width = 600;
    canvas.height = 780;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Background
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Red Header Band (#C8102E)
    ctx.fillStyle = '#C8102E';
    ctx.fillRect(0, 0, canvas.width, 110);

    // KHQR Title
    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 36px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('KHQR', canvas.width / 2, 55);

    ctx.font = '600 18px sans-serif';
    ctx.fillText(payment.merchantName || 'KhmerMart Store', canvas.width / 2, 90);

    // Amount & Order Number
    ctx.fillStyle = '#111827';
    ctx.font = 'bold 32px sans-serif';
    ctx.fillText(formatUsd(payment.amountCents), canvas.width / 2, 170);

    ctx.fillStyle = '#6B7280';
    ctx.font = '600 18px sans-serif';
    ctx.fillText(formatKhr(payment.amountCents, khrRate), canvas.width / 2, 205);

    ctx.font = '14px monospace';
    ctx.fillText(`Order: ${orderNumber}`, canvas.width / 2, 235);

    // Dashed divider line
    ctx.strokeStyle = '#D1D5DB';
    ctx.setLineDash([8, 8]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(40, 260);
    ctx.lineTo(canvas.width - 40, 260);
    ctx.stroke();
    ctx.setLineDash([]);

    // Draw QR code image
    const img = new window.Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const qrSize = 360;
      const qrX = (canvas.width - qrSize) / 2;
      const qrY = 280;
      ctx.drawImage(img, qrX, qrY, qrSize, qrSize);

      // Draw Center Emblem (center mark <= 15% width, size = 50px)
      const emblemSize = 50;
      const emblemX = (canvas.width - emblemSize) / 2;
      const emblemY = qrY + (qrSize - emblemSize) / 2;

      ctx.beginPath();
      ctx.arc(canvas.width / 2, qrY + qrSize / 2, emblemSize / 2, 0, Math.PI * 2);
      ctx.fillStyle = '#FFFFFF';
      ctx.fill();
      ctx.strokeStyle = '#C8102E';
      ctx.lineWidth = 3;
      ctx.stroke();

      ctx.fillStyle = '#C8102E';
      ctx.font = 'bold 24px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(payment.currency === 'KHR' ? '៛' : '$', canvas.width / 2, qrY + qrSize / 2);

      // Footer
      ctx.fillStyle = '#6B7280';
      ctx.font = '14px sans-serif';
      ctx.textBaseline = 'alphabetic';
      ctx.fillText('Scan with Bakong or any Cambodian Banking App', canvas.width / 2, 700);

      // Trigger download
      const link = document.createElement('a');
      link.download = `khqr-${orderNumber}.png`;
      link.href = canvas.toDataURL('image/png');
      link.click();
      toast.success('KHQR Code downloaded!');
    };
    img.src = payment.qrImageDataUrl;
  };

  // Handle Cancel Order
  const handleCancelOrder = async () => {
    setIsCancelling(true);
    try {
      await apiClient(`/orders/${orderNumber}/cancel`, {
        method: 'POST',
        body: { reason: cancelReason.trim() || 'Cancelled by customer on payment page' },
      });
      toast.success(t('orderCancelled'));
      setIsCancelDialogOpen(false);
      router.push('/account/orders');
    } catch (err: any) {
      toast.error(err.message || 'Failed to cancel order');
    } finally {
      setIsCancelling(false);
    }
  };

  if (isLoading) {
    return (
      <div className="max-w-md mx-auto p-8 text-center space-y-4 bg-surface border border-border rounded-2xl shadow-sm">
        <Loader2 className="w-10 h-10 text-primary animate-spin mx-auto" />
        <p className="text-xs text-muted">Generating secure Bakong KHQR...</p>
      </div>
    );
  }

  if (error || !payment) {
    return (
      <div className="max-w-md mx-auto p-6 bg-surface border border-red-500/20 rounded-2xl shadow-sm text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-red-500 mx-auto" />
        <div className="space-y-1">
          <h2 className="text-base font-bold text-foreground">{t('paymentFailed')}</h2>
          <p className="text-xs text-muted">{error || 'Could not retrieve payment information'}</p>
        </div>
        <button
          onClick={() => window.location.reload()}
          className="px-4 py-2 text-xs font-bold bg-primary text-primary-foreground rounded-btn hover:bg-primary/90 transition-colors"
        >
          Try Again
        </button>
      </div>
    );
  }

  const isExpired = status === 'EXPIRED' || countdown.isExpired;
  const isPaid = status === 'PAID';

  return (
    <div className="max-w-md mx-auto space-y-6" data-testid="khqr-payment-container">
      {/* KHQR Card */}
      <div className="relative bg-surface border border-border rounded-2xl shadow-xl overflow-hidden transition-all">
        {/* Red Bakong Header Band (#C8102E) */}
        <div className="bg-[#C8102E] text-white px-6 py-4 text-center space-y-1">
          <div className="flex items-center justify-center gap-1.5 font-black text-2xl tracking-wider">
            <span>KHQR</span>
          </div>
          <p className="text-xs font-medium text-white/90 truncate">
            {payment.merchantName || 'KhmerMart'}
          </p>
        </div>

        {/* Amount & Order Header */}
        <div className="px-6 pt-5 pb-3 text-center space-y-1">
          <div className="text-3xl font-black text-foreground tracking-tight" data-testid="khqr-amount-usd">
            {formatUsd(payment.amountCents)}
          </div>
          <div className="text-xs font-semibold text-muted bg-muted/15 inline-block px-3 py-1 rounded-full" data-testid="khqr-amount-khr">
            {formatKhr(payment.amountCents, khrRate)}
          </div>
          <div className="text-[11px] font-mono text-muted pt-1">
            {t('orderNumber')}: <strong className="text-foreground" data-testid="order-number-display">{orderNumber}</strong>
          </div>
        </div>

        {/* Ticket-style Dashed Divider with Notches */}
        <div className="relative flex items-center justify-between py-2 overflow-hidden">
          {/* Left Notch */}
          <div className="w-5 h-8 bg-background border-r border-border rounded-r-full -ml-3" />
          {/* Dashed Line */}
          <div className="w-full border-t-2 border-dashed border-border mx-2" />
          {/* Right Notch */}
          <div className="w-5 h-8 bg-background border-l border-border rounded-l-full -mr-3" />
        </div>

        {/* QR Code Container (260px from qrImageDataUrl, center mark <= 15%) */}
        <div className="p-6 pt-2 flex flex-col items-center justify-center space-y-4">
          <div
            className="relative w-[260px] h-[260px] bg-white p-2 rounded-xl border border-border shadow-xs flex items-center justify-center"
            data-testid="khqr-code-box"
          >
            {/* Base QR image */}
            <Image
              src={payment.qrImageDataUrl}
              alt="Bakong KHQR Code"
              width={260}
              height={260}
              unoptimized
              className={`w-full h-full object-contain transition-all duration-300 ${
                isExpired ? 'opacity-20 grayscale blur-[1px]' : ''
              }`}
              data-testid="khqr-image"
            />

            {/* Center Emblem ($ or ៛) ≤ 15% width (36px for 260px box) */}
            {!isExpired && (
              <div
                className="absolute inset-0 m-auto w-9 h-9 rounded-full bg-white border-2 border-[#C8102E] shadow-md flex items-center justify-center text-[#C8102E] font-black text-base select-none pointer-events-none"
                data-testid="khqr-center-emblem"
                aria-hidden="true"
              >
                {payment.currency === 'KHR' ? '៛' : '$'}
              </div>
            )}

            {/* Expired Overlay */}
            {isExpired && !isPaid && (
              <div
                className="absolute inset-0 bg-background/80 backdrop-blur-xs rounded-xl flex flex-col items-center justify-center p-4 text-center space-y-3 animate-in fade-in duration-200"
                data-testid="qr-expired-overlay"
              >
                <div className="w-10 h-10 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center">
                  <Clock className="w-5 h-5" />
                </div>
                <div className="space-y-0.5">
                  <h4 className="text-sm font-bold text-foreground">{t('qrExpired')}</h4>
                  <p className="text-[11px] text-muted max-w-[190px] leading-tight">
                    {t('qrExpiredDesc')}
                  </p>
                </div>
                <button
                  type="button"
                  data-testid="regenerate-khqr-btn"
                  onClick={regenerate}
                  disabled={isRegenerating}
                  className="px-4 py-2 rounded-btn bg-primary text-primary-foreground font-bold text-xs flex items-center gap-1.5 shadow-xs hover:bg-primary/90 transition-colors disabled:opacity-60"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isRegenerating ? 'animate-spin' : ''}`} />
                  <span>{isRegenerating ? t('regenerating') : t('regenerateQr')}</span>
                </button>
              </div>
            )}
          </div>

          {/* Countdown Display (mm:ss) */}
          {!isPaid && !isExpired && (
            <div
              className={`flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-full transition-colors ${
                countdown.isUnderOneMinute
                  ? 'bg-red-500/10 text-red-600 dark:text-red-400 font-bold animate-pulse'
                  : 'bg-muted/15 text-muted'
              }`}
              data-testid="khqr-countdown"
            >
              <Clock className="w-3.5 h-3.5" />
              <span>
                {t('expiresIn')} {countdown.formatted}
              </span>
            </div>
          )}

          {/* Live Status Announcer (aria-live) */}
          <div aria-live="polite" className="w-full text-center">
            {isPaid ? (
              <div
                className="p-3 bg-green-500/10 border border-green-500/20 text-green-600 dark:text-green-400 rounded-btn flex items-center justify-center gap-2 text-xs font-bold animate-in zoom-in-95"
                data-testid="payment-status-paid"
              >
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>{t('paymentSuccessful')} {t('redirecting')}</span>
              </div>
            ) : status === 'FAILED' ? (
              <div
                className="p-3 bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 rounded-btn flex items-center justify-center gap-2 text-xs font-bold"
                data-testid="payment-status-failed"
              >
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{t('paymentFailed')}</span>
              </div>
            ) : !isExpired ? (
              <div
                className="flex items-center justify-center gap-2 text-xs font-semibold text-muted"
                data-testid="payment-status-pending"
              >
                <span className="w-2 h-2 rounded-full bg-primary animate-ping" />
                <span>{t('waitingForPayment')}</span>
              </div>
            ) : null}
          </div>
        </div>

        {/* 3 Numbered Steps (EN / KM) */}
        <div className="bg-muted/10 border-t border-border px-6 py-4 space-y-2 text-left">
          <p className="text-[11px] font-bold text-muted uppercase tracking-wider">
            {t('subtitle')}
          </p>
          <ol className="text-xs text-foreground space-y-1.5 list-none font-medium">
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-primary/10 text-primary flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">
                1
              </span>
              <span>{t('step1')}</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-primary/10 text-primary flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">
                2
              </span>
              <span>{t('step2')}</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-primary/10 text-primary flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">
                3
              </span>
              <span>{t('step3')}</span>
            </li>
          </ol>
        </div>
      </div>

      {/* DEV MODE Simulation Button (MOCK mode only) */}
      {payment.mode === 'MOCK' && !isPaid && (
        <div className="text-center">
          <button
            type="button"
            data-testid="simulate-paid-btn"
            onClick={simulatePaid}
            disabled={isSimulating}
            className="w-full py-2.5 px-4 rounded-btn border-2 border-dashed border-amber-500 bg-amber-500/10 hover:bg-amber-500/20 text-amber-700 dark:text-amber-300 text-xs font-bold transition-colors flex items-center justify-center gap-2"
          >
            {isSimulating ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <ShieldAlert className="w-3.5 h-3.5" />
            )}
            <span>{isSimulating ? t('simulating') : t('devModeSimulate')}</span>
          </button>
        </div>
      )}

      {/* Action Buttons */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
        {/* Download QR */}
        <button
          type="button"
          data-testid="download-qr-btn"
          onClick={handleDownloadQr}
          className="w-full h-10 flex items-center justify-center gap-2 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-foreground transition-colors"
        >
          <Download className="w-4 h-4" />
          <span>{t('downloadQr')}</span>
        </button>

        {/* Open in banking app (Mobile + deepLink only) */}
        {payment.deepLink && isMobile && (
          <a
            href={payment.deepLink}
            data-testid="open-banking-app-btn"
            className="w-full h-10 flex items-center justify-center gap-2 rounded-btn bg-[#C8102E] hover:bg-[#a60d26] text-white font-bold transition-colors shadow-xs"
          >
            <ExternalLink className="w-4 h-4" />
            <span>{t('openBankingApp')}</span>
          </a>
        )}

        {/* Change payment method */}
        <button
          type="button"
          data-testid="change-payment-method-btn"
          onClick={() => router.push('/checkout')}
          className="w-full h-10 flex items-center justify-center gap-2 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-muted hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>{t('changePaymentMethod')}</span>
        </button>

        {/* Cancel order */}
        <button
          type="button"
          data-testid="cancel-order-btn"
          onClick={() => setIsCancelDialogOpen(true)}
          className="w-full h-10 flex items-center justify-center gap-2 rounded-btn border border-red-500/20 text-red-600 dark:text-red-400 hover:bg-red-500/10 font-bold transition-colors"
        >
          <XCircle className="w-4 h-4" />
          <span>{t('cancelOrder')}</span>
        </button>
      </div>

      {/* Cancel Order Confirm Dialog */}
      {isCancelDialogOpen && (
        <div
          data-testid="cancel-order-dialog"
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200"
        >
          <div className="bg-surface border border-border rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-2xl text-left">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center shrink-0">
                <AlertCircle className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-base text-foreground leading-snug">
                {t('cancelOrderConfirmTitle')}
              </h3>
            </div>

            <p className="text-xs text-muted leading-relaxed">
              {t('cancelOrderConfirmDesc')}
            </p>

            <div className="space-y-1.5">
              <label htmlFor="cancel-reason" className="text-xs font-semibold text-foreground">
                {t('cancelReasonLabel')}
              </label>
              <textarea
                id="cancel-reason"
                data-testid="cancel-reason-input"
                rows={2}
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder={t('cancelReasonPlaceholder')}
                className="w-full text-xs p-2.5 rounded-btn border border-border bg-background focus:outline-hidden focus:ring-2 focus:ring-primary/20 text-foreground"
              />
            </div>

            <div className="grid grid-cols-2 gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsCancelDialogOpen(false)}
                disabled={isCancelling}
                className="h-9 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground transition-colors"
              >
                {t('keepOrder')}
              </button>
              <button
                type="button"
                data-testid="confirm-cancel-order-btn"
                onClick={handleCancelOrder}
                disabled={isCancelling}
                className="h-9 rounded-btn bg-red-600 hover:bg-red-700 text-white text-xs font-bold shadow-xs transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
              >
                {isCancelling ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <span>{t('confirmCancel')}</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
