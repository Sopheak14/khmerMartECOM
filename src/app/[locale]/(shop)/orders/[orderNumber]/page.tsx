'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import { getImageUrl } from '@/lib/images';
import { useParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations, useLocale } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { OrderDTO, OrderStatus, CartDTO } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import { downloadInvoice } from '@/lib/downloadInvoice';
import { useCartDrawerStore } from '@/lib/store/useCartDrawerStore';
import { queryKeys } from '@/lib/queryKeys';
import {
  Package,
  MapPin,
  CreditCard,
  Truck,
  ArrowLeft,
  Loader2,
  CheckCircle2,
  Clock,
  XCircle,
  Download,
  RotateCcw,
  Navigation,
  AlertCircle,
  FileText,
  Calendar,
  Star,
} from 'lucide-react';
import { ReviewDialog } from '@/components/product/ReviewDialog';
import { toast } from 'sonner';

export default function OrderDetailPage() {
  const t = useTranslations('orders');
  const common = useTranslations('common');
  const reviewsT = useTranslations('reviews');
  const locale = useLocale();
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  const openCartDrawer = useCartDrawerStore((state) => state.open);

  const orderNumber = params?.orderNumber as string;

  const [isCancelDialogOpen, setIsCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [isDownloading, setIsDownloading] = useState(false);
  const [reviewItem, setReviewItem] = useState<{ productId: number; productName: string } | null>(null);
  const [reviewedProductIds, setReviewedProductIds] = useState<Set<number>>(new Set());

  const {
    data: order,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: async () => {
      const res = await apiClient<OrderDTO>(`/orders/${orderNumber}`);
      return res.data;
    },
    enabled: Boolean(orderNumber),
  });

  // Cancel order mutation
  const cancelOrderMutation = useMutation({
    mutationFn: async (reason: string) => {
      const res = await apiClient<OrderDTO>(`/orders/${orderNumber}/cancel`, {
        method: 'POST',
        body: { reason: reason.trim() || 'Cancelled by customer' },
      });
      return res.data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData(['order', orderNumber], data);
      queryClient.invalidateQueries({ queryKey: ['account-orders'] });
      setIsCancelDialogOpen(false);
      toast.success(common('orderCancelled') || 'Order cancelled successfully');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to cancel order');
    },
  });

  // Buy again (reorder) mutation
  const reorderMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient<CartDTO>(`/orders/${orderNumber}/reorder`, {
        method: 'POST',
      });
      return res.data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.cart.current, data);
      openCartDrawer();
      toast.success(t('reorderSuccess'));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to reorder items');
    },
  });

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

  const getStatusBadge = (status: OrderStatus) => {
    switch (status) {
      case 'PENDING_PAYMENT':
        return {
          label: t('status.PENDING_PAYMENT'),
          className: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
          icon: Clock,
        };
      case 'CONFIRMED':
        return {
          label: t('status.CONFIRMED'),
          className: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
          icon: CheckCircle2,
        };
      case 'PROCESSING':
        return {
          label: t('status.PROCESSING'),
          className: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20',
          icon: Package,
        };
      case 'SHIPPED':
      case 'OUT_FOR_DELIVERY':
        return {
          label: t('status.SHIPPED'),
          className: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20',
          icon: Truck,
        };
      case 'DELIVERED':
        return {
          label: t('status.DELIVERED'),
          className: 'bg-green-500/10 text-green-600 dark:text-green-400 border-green-500/20',
          icon: CheckCircle2,
        };
      case 'CANCELLED':
      case 'PAYMENT_EXPIRED':
      case 'REFUNDED':
        return {
          label: t(`status.${status}`) || status,
          className: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20',
          icon: XCircle,
        };
      default:
        return {
          label: status,
          className: 'bg-muted/15 text-muted border-border',
          icon: Clock,
        };
    }
  };

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-16 max-w-2xl text-center space-y-4">
        <Loader2 className="w-8 h-8 mx-auto animate-spin text-primary" />
        <p className="text-xs text-muted">Loading order {orderNumber}...</p>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="container mx-auto px-4 py-16 max-w-lg text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-red-500 mx-auto" />
        <h2 className="text-lg font-bold text-foreground">Order Not Found</h2>
        <p className="text-xs text-muted">We could not locate order {orderNumber}.</p>
        <Link
          href="/account/orders"
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
        >
          <span>Back to My Orders</span>
        </Link>
      </div>
    );
  }

  const statusInfo = getStatusBadge(order.status);
  const StatusIcon = statusInfo.icon;
  const placedDate = new Date(order.placedAt).toLocaleString(
    locale === 'km' ? 'km-KH' : 'en-US',
    { dateStyle: 'medium', timeStyle: 'short' }
  );

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl space-y-6" data-testid="order-detail-page">
      {/* Top Bar Navigation */}
      <div className="flex items-center justify-between">
        <Link
          href="/account/orders"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>{t('title')}</span>
        </Link>
        <span
          data-testid="order-number-display"
          className="text-xs font-mono font-bold bg-muted/15 px-2.5 py-1 rounded text-foreground"
        >
          {orderNumber}
        </span>
      </div>

      {/* Main Order Card */}
      <div className="bg-surface border border-border rounded-2xl p-6 sm:p-8 space-y-8 shadow-xs">
        {/* Header Information */}
        <div className="flex items-start justify-between flex-wrap gap-4 pb-6 border-b border-border">
          <div className="space-y-1.5">
            <h1 className="text-2xl font-black text-foreground tracking-tight">
              {t('orderCardNumber', { number: orderNumber })}
            </h1>
            <p className="text-xs text-muted flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" />
              <span>
                {t('placedOn')} {placedDate}
              </span>
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span
              data-testid="order-detail-status-badge"
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold border ${statusInfo.className}`}
            >
              <StatusIcon className="w-3.5 h-3.5" />
              <span>{statusInfo.label}</span>
            </span>
          </div>
        </div>

        {/* Timeline from OrderDTO.timeline */}
        {order.timeline && order.timeline.length > 0 && (
          <div className="space-y-4" data-testid="order-timeline">
            <h3 className="font-bold text-xs uppercase tracking-wider text-muted">
              {t('timeline')}
            </h3>

            <div className="relative pl-6 space-y-6 border-l-2 border-primary/20 ml-3">
              {order.timeline.map((event, idx) => {
                const eventDate = new Date(event.at).toLocaleString(
                  locale === 'km' ? 'km-KH' : 'en-US',
                  { dateStyle: 'short', timeStyle: 'short' }
                );
                const eventStatusInfo = getStatusBadge(event.status);

                return (
                  <div key={idx} className="relative group" data-testid="timeline-event">
                    {/* Circle Dot on Line */}
                    <div className="absolute -left-[31px] top-0.5 w-4 h-4 rounded-full bg-primary border-4 border-surface shadow-xs" />

                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-xs text-foreground">
                          {eventStatusInfo.label}
                        </span>
                        <span className="text-[11px] text-muted">({eventDate})</span>
                      </div>
                      {event.note && (
                        <p className="text-xs text-muted">{event.note}</p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Order Items List */}
        <div className="space-y-3" data-testid="order-items-list">
          <h3 className="font-bold text-xs uppercase tracking-wider text-muted">
            {t('itemsOrdered')} ({order.items.length})
          </h3>

          <div className="divide-y divide-border border border-border rounded-xl overflow-hidden">
            {order.items.map((item) => (
              <div
                key={item.id}
                data-testid="order-item-row"
                className="p-4 flex items-center justify-between gap-4 text-xs bg-background/50 hover:bg-muted/5 transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="relative w-14 h-14 rounded-lg bg-muted/10 overflow-hidden shrink-0 border border-border">
                    {item.imageUrl ? (
                      <Image src={getImageUrl(item.imageUrl)} alt={item.name} fill className="object-cover" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-[10px] text-muted">
                        Item
                      </div>
                    )}
                  </div>
                  <div className="space-y-0.5 min-w-0">
                    <h4 className="font-bold text-foreground truncate">{item.name}</h4>
                    {item.attributes && Object.keys(item.attributes).length > 0 && (
                      <p className="text-[11px] text-muted">
                        {Object.entries(item.attributes)
                          .map(([k, v]) => `${k}: ${v}`)
                          .join(' • ')}
                      </p>
                    )}
                    <p className="text-muted text-[11px]">
                      {formatUsd(item.unitPriceCents)} × {item.quantity}
                    </p>
                  </div>
                </div>

                <div className="flex flex-col items-end gap-2 shrink-0">
                  <div className="text-right">
                    <span className="font-bold text-foreground text-sm">
                      {formatUsd(item.lineTotalCents)}
                    </span>
                    <span className="block text-[11px] text-muted">
                      {formatKhr(item.lineTotalCents)}
                    </span>
                  </div>

                  {(item.canReview || (order.status === 'DELIVERED' && order.canReview)) &&
                  !reviewedProductIds.has(item.productId) ? (
                    <button
                      type="button"
                      data-testid={`write-review-btn-${item.productId}`}
                      onClick={() =>
                        setReviewItem({
                          productId: item.productId,
                          productName: item.name,
                        })
                      }
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-btn text-xs font-semibold bg-primary/10 text-primary hover:bg-primary/20 transition-colors cursor-pointer"
                    >
                      <Star className="w-3.5 h-3.5 fill-primary/20" />
                      <span>{reviewsT('writeReview')}</span>
                    </button>
                  ) : reviewedProductIds.has(item.productId) ? (
                    <span
                      data-testid={`reviewed-btn-${item.productId}`}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-btn text-xs font-semibold bg-green-500/10 text-green-700 dark:text-green-400"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>{reviewsT('reviewed')}</span>
                    </span>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Details Grid: Address, Payment, Totals */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t border-border">
          {/* Left Column: Address & Payment */}
          <div className="space-y-6">
            {/* Shipping Address */}
            <div className="space-y-2" data-testid="order-delivery-address">
              <h4 className="font-bold text-xs uppercase tracking-wider text-muted flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-primary" />
                <span>{t('shippingAddress')}</span>
              </h4>
              <div className="p-3.5 rounded-xl border border-border bg-background space-y-1 text-xs">
                <p className="font-bold text-foreground">
                  {order.shippingAddress.recipientName}{' '}
                  <span className="font-normal text-muted font-mono">({order.shippingAddress.phone})</span>
                </p>
                <p className="text-muted">
                  {order.shippingAddress.line1}
                  {order.shippingAddress.district ? `, ${order.shippingAddress.district}` : ''}
                </p>
                <p className="text-muted">
                  {order.shippingAddress.province}
                  {order.shippingAddress.postalCode ? ` ${order.shippingAddress.postalCode}` : ''}
                </p>
              </div>
            </div>

            {/* Payment Info */}
            <div className="space-y-2" data-testid="order-payment-info">
              <h4 className="font-bold text-xs uppercase tracking-wider text-muted flex items-center gap-1.5">
                <CreditCard className="w-3.5 h-3.5 text-primary" />
                <span>{t('paymentDetails')}</span>
              </h4>
              <div className="p-3.5 rounded-xl border border-border bg-background space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted">{t('paymentMethod')}:</span>
                  <strong className="text-foreground uppercase">{order.paymentMethod}</strong>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted">{t('paymentStatus')}:</span>
                  <span
                    className={`font-bold px-2 py-0.5 rounded text-[11px] ${
                      order.paymentStatus === 'PAID'
                        ? 'bg-green-500/10 text-green-600'
                        : order.paymentStatus === 'PENDING'
                        ? 'bg-amber-500/10 text-amber-600'
                        : 'bg-muted/15 text-muted'
                    }`}
                  >
                    {order.paymentStatus}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Server Totals */}
          <div className="space-y-4 bg-background p-5 rounded-xl border border-border self-start">
            <h4 className="font-bold text-xs uppercase tracking-wider text-muted">
              {t('orderSummary')}
            </h4>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between text-muted">
                <span>{t('subtotal')}</span>
                <span className="font-semibold text-foreground">{formatUsd(order.subtotalCents)}</span>
              </div>

              {order.discountCents > 0 && (
                <div className="flex justify-between text-green-600 dark:text-green-400 font-medium">
                  <span>
                    {t('discount')}
                    {order.couponCode ? ` (${order.couponCode})` : ''}
                  </span>
                  <span>-{formatUsd(order.discountCents)}</span>
                </div>
              )}

              <div className="flex justify-between text-muted">
                <span>{t('shippingFee')}</span>
                <span className="font-semibold text-foreground">
                  {order.shippingCents === 0 ? t('free') : formatUsd(order.shippingCents)}
                </span>
              </div>

              <div className="pt-3 border-t border-border flex justify-between items-baseline font-bold text-base text-foreground">
                <span>{t('total')}</span>
                <div className="text-right">
                  <span className="text-primary text-xl font-black" data-testid="order-detail-total-usd">
                    {formatUsd(order.totalCents)}
                  </span>
                  <span className="block text-xs font-normal text-muted" data-testid="order-detail-total-khr">
                    {formatKhr(order.totalCents)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Action Buttons Bar */}
        <div className="pt-6 border-t border-border flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            {/* Pay Now Button (if PENDING_PAYMENT) */}
            {order.status === 'PENDING_PAYMENT' && (
              <Link
                href={
                  order.paymentMethod === 'BANK_TRANSFER'
                    ? `/checkout/bank-transfer/${orderNumber}`
                    : `/checkout/pay/${orderNumber}`
                }
                data-testid="pay-now-btn"
                className="px-5 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-xs shadow-xs hover:bg-primary/90 transition-colors flex items-center gap-1.5"
              >
                <CreditCard className="w-3.5 h-3.5" />
                <span>{t('payNow')}</span>
              </Link>
            )}

            {/* Cancel Order Button (if canCancel) */}
            {order.canCancel && (
              <button
                type="button"
                data-testid="cancel-order-btn"
                onClick={() => setIsCancelDialogOpen(true)}
                className="px-4 py-2.5 rounded-btn border border-red-500/20 text-red-600 dark:text-red-400 hover:bg-red-500/10 font-bold text-xs transition-colors flex items-center gap-1.5"
              >
                <XCircle className="w-3.5 h-3.5" />
                <span>{t('cancel')}</span>
              </button>
            )}

            {/* Buy Again Button */}
            <button
              type="button"
              data-testid="buy-again-btn"
              onClick={() => reorderMutation.mutate()}
              disabled={reorderMutation.isPending}
              className="px-4 py-2.5 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-xs text-foreground transition-colors flex items-center gap-1.5 disabled:opacity-50"
            >
              {reorderMutation.isPending ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <RotateCcw className="w-3.5 h-3.5 text-primary" />
              )}
              <span>{reorderMutation.isPending ? t('reordering') : t('buyAgain')}</span>
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            {/* Track Shipment Button (links to /track/[trackingNumber] when order has trackingNumber) */}
            {order.trackingNumber && (
              <Link
                href={`/track/${order.trackingNumber}`}
                data-testid="track-shipment-btn"
                className="px-4 py-2.5 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-xs text-foreground transition-colors flex items-center gap-1.5"
              >
                <Navigation className="w-3.5 h-3.5 text-primary" />
                <span>{t('trackShipment')}</span>
              </Link>
            )}

            {/* Download Invoice Button */}
            <button
              type="button"
              data-testid="download-invoice-btn"
              onClick={handleDownloadInvoice}
              disabled={isDownloading}
              className="px-4 py-2.5 rounded-btn border border-border bg-surface hover:bg-muted/10 font-bold text-xs text-foreground transition-colors flex items-center gap-1.5 disabled:opacity-50"
            >
              {isDownloading ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Download className="w-3.5 h-3.5 text-primary" />
              )}
              <span>{isDownloading ? t('downloadingInvoice') : t('downloadInvoice')}</span>
            </button>
          </div>
        </div>
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
                {t('cancelConfirmTitle')}
              </h3>
            </div>

            <p className="text-xs text-muted leading-relaxed">
              {t('cancelConfirmDesc')}
            </p>

            <div className="space-y-1.5">
              <label htmlFor="cancel-reason" className="text-xs font-semibold text-foreground">
                Reason (optional)
              </label>
              <textarea
                id="cancel-reason"
                data-testid="cancel-reason-input"
                rows={2}
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder={t('reasonPlaceholder')}
                className="w-full text-xs p-2.5 rounded-btn border border-border bg-background focus:outline-hidden focus:ring-2 focus:ring-primary/20 text-foreground"
              />
            </div>

            <div className="grid grid-cols-2 gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsCancelDialogOpen(false)}
                disabled={cancelOrderMutation.isPending}
                className="h-9 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground transition-colors"
              >
                {t('keepOrder')}
              </button>
              <button
                type="button"
                data-testid="confirm-cancel-btn"
                onClick={() => cancelOrderMutation.mutate(cancelReason)}
                disabled={cancelOrderMutation.isPending}
                className="h-9 rounded-btn bg-red-600 hover:bg-red-700 text-white text-xs font-bold shadow-xs transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5"
              >
                {cancelOrderMutation.isPending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <span>{t('confirmCancel')}</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Review Dialog */}
      {reviewItem && (
        <ReviewDialog
          isOpen={Boolean(reviewItem)}
          productId={reviewItem.productId}
          productName={reviewItem.productName}
          onClose={() => setReviewItem(null)}
          onSuccess={(newReview) => {
            setReviewedProductIds((prev) => new Set(prev).add(reviewItem.productId));
            refetch();
            queryClient.invalidateQueries({
              queryKey: queryKeys.products.reviews(reviewItem.productId),
            });
          }}
        />
      )}
    </div>
  );
}
