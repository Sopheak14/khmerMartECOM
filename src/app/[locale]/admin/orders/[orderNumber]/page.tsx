'use client';

import React, { useState } from 'react';
import { useParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useRouter } from '@/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { AdminPaymentDTO, OrderDTO, OrderStatus, ShipmentStatus, TrackingDTO } from '@/types/api';
import { FormDialog } from '@/components/common/FormDialog';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { errorMessageKey, extractRefundInfo, BANK_REFERENCE_RULE, REASON_RULE, TONE_CLASSES, paymentStatusTone } from '@/lib/refund';
import { formatUsd, formatKhr } from '@/lib/money';
import { osmUrl } from '@/lib/geo';
import { SafeImage } from '@/components/common/SafeImage';
import { getImageUrl } from '@/lib/images';
import { getDiscountLines } from '@/lib/orderDiscounts';
import { useOrderStatusLabel } from '@/hooks/useOrderStatusLabel';
import {
  ArrowLeft,
  Truck,
  CreditCard,
  MapPin,
  User,
  Clock,
  Printer,
  XCircle,
  RotateCcw,
  PlusCircle,
  AlertTriangle,
  Loader2,
  CheckCircle2,
  X,
  Copy,
  Check,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

const ALLOWED_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT: ['CONFIRMED', 'CANCELLED', 'PAYMENT_EXPIRED'],
  CONFIRMED: ['PROCESSING', 'SHIPPED', 'CANCELLED', 'REFUNDED'],
  PROCESSING: ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED: ['OUT_FOR_DELIVERY', 'DELIVERED'],
  OUT_FOR_DELIVERY: ['DELIVERED'],
  DELIVERED: ['REFUNDED'],
  CANCELLED: [],
  PAYMENT_EXPIRED: [],
  REFUNDED: [],
};

export default function AdminOrderDetailPage() {
  const t = useTranslations('admin.orders');
  const tAddr = useTranslations('address');
  const commonT = useTranslations('admin.common');
  const orderStatusLabel = useOrderStatusLabel();
  const rt = useTranslations('refunds');
  const errT = useTranslations('errors');
  const isAdmin = useAuthStore((st) => st.user?.role === 'ADMIN');
  const locale = useLocale();
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const orderNumber = params?.orderNumber as string;

  // Dialog States
  const [shipDialogOpen, setShipDialogOpen] = useState(false);
  const [carrierInput, setCarrierInput] = useState('KhmerMart Express');
  const [shippedTrackingNumber, setShippedTrackingNumber] = useState<string | null>(null);

  const [trackingDialogOpen, setTrackingDialogOpen] = useState(false);
  const [eventStatus, setEventStatus] = useState<ShipmentStatus>('IN_TRANSIT');
  const [eventLocation, setEventLocation] = useState('Phnom Penh Sorting Facility');
  const [eventNote, setEventNote] = useState('');

  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [markSentOpen, setMarkSentOpen] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [copiedAccount, setCopiedAccount] = useState(false);

  const [selectedNextStatus, setSelectedNextStatus] = useState<string>('');
  const [transitionError, setTransitionError] = useState<string | null>(null);

  // Fetch Order Details
  const { data: order, isLoading, error, refetch } = useQuery({
    queryKey: queryKeys.admin.orderDetail(orderNumber),
    queryFn: async () => {
      const res = await apiClient<OrderDTO>(`/admin/orders/${orderNumber}`);
      return res.data;
    },
    enabled: Boolean(orderNumber),
  });

  // Shipment tracking events query
  const { data: trackingInfo } = useQuery({
    queryKey: queryKeys.tracking.detail(order?.trackingNumber || ''),
    queryFn: async () => {
      if (!order?.trackingNumber) return null;
      const res = await apiClient<TrackingDTO>(`/track/${order.trackingNumber}`);
      return res.data;
    },
    enabled: Boolean(order?.trackingNumber),
  });

  // Allowed next statuses
  const allowedNextStatuses = order?.status ? ALLOWED_TRANSITIONS[order.status] || [] : [];

  // Status update mutation
  const statusMutation = useMutation({
    mutationFn: async (newStatus: string) => {
      setTransitionError(null);
      const res = await apiClient<OrderDTO>(`/admin/orders/${orderNumber}/status`, {
        method: 'PATCH',
        body: { status: newStatus },
      });
      return res.data;
    },
    onSuccess: (updatedOrder) => {
      queryClient.setQueryData(queryKeys.admin.orderDetail(orderNumber), updatedOrder);
      queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
      toast.success(t('statusUpdateSuccess'));
      setSelectedNextStatus('');
    },
    onError: (err: any) => {
      const msg = err?.message || 'Invalid status transition';
      const code = err?.code ? `${err.code}: ` : '';
      setTransitionError(`${code}${msg}`);
      toast.error(msg);
    },
  });

  // Ship order mutation
  const shipMutation = useMutation({
    mutationFn: async (carrier: string) => {
      const res = await apiClient<OrderDTO>(`/admin/orders/${orderNumber}/ship`, {
        method: 'POST',
        body: { carrier },
      });
      return res.data;
    },
    onSuccess: (updatedOrder) => {
      setShippedTrackingNumber(updatedOrder.trackingNumber || null);
      queryClient.setQueryData(queryKeys.admin.orderDetail(orderNumber), updatedOrder);
      queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
      if (updatedOrder.trackingNumber) {
        queryClient.invalidateQueries({ queryKey: queryKeys.tracking.detail(updatedOrder.trackingNumber) });
      }
      toast.success(t('shipSuccess', { trackingNumber: updatedOrder.trackingNumber }));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to ship order');
    },
  });

  // Add tracking event mutation
  const trackingMutation = useMutation({
    mutationFn: async () => {
      await apiClient(`/admin/orders/${orderNumber}/tracking-events`, {
        method: 'POST',
        body: {
          status: eventStatus,
          location: eventLocation,
          note: eventNote || undefined,
        },
      });
    },
    onSuccess: () => {
      setTrackingDialogOpen(false);
      setEventNote('');
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.orderDetail(orderNumber) });
      if (order?.trackingNumber) {
        queryClient.invalidateQueries({ queryKey: queryKeys.tracking.detail(order.trackingNumber) });
      }
      toast.success(t('eventSuccess'));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to add tracking event');
    },
  });

  const errorText = (err: any, fallback: string) => {
    const key = errorMessageKey(err);
    return key ? errT(key) : err?.message || fallback;
  };

  const afterOrderChange = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.orderDetail(orderNumber) });
    queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'payments'] });
  };

  // Cancel the order (also approves a customer's request): reason required
  const cancelMutation = useMutation({
    mutationFn: async (reason: string) => {
      const res = await apiClient<OrderDTO>(`/admin/orders/${orderNumber}/cancel`, {
        method: 'POST',
        body: { reason },
      });
      return res.data;
    },
    onSuccess: () => {
      setCancelDialogOpen(false);
      setApproveDialogOpen(false);
      afterOrderChange();
      toast.success(t('cancelSuccess'));
    },
    onError: (err: any) => {
      setDialogError(errorText(err, 'Failed to cancel order'));
      afterOrderChange();
    },
  });

  // Refuse a customer's cancellation request: reason required, shown to the customer
  const rejectMutation = useMutation({
    mutationFn: async (reason: string) => {
      const res = await apiClient<OrderDTO>(`/admin/orders/${orderNumber}/cancel-request/reject`, {
        method: 'POST',
        body: { reason },
      });
      return res.data;
    },
    onSuccess: () => {
      setRejectDialogOpen(false);
      afterOrderChange();
      toast.success(rt('banner.rejected'));
    },
    onError: (err: any) => {
      setDialogError(errorText(err, 'Failed to reject request'));
      afterOrderChange();
    },
  });

  // The admin order view carries the refund (with paymentId). Fall back to an exact orderNumber lookup if it is missing.
  const refundPending = order?.paymentStatus === 'REFUND_PENDING';
  const { data: lookedUpPayment } = useQuery({
    queryKey: ['admin', 'payments', 'refund-pending', orderNumber],
    queryFn: async () => {
      const res = await apiClient<AdminPaymentDTO[]>('/admin/payments', { query: { orderNumber } });
      return (res.data || []).find((p) => p.orderNumber === orderNumber) ?? null;
    },
    enabled: refundPending && isAdmin && !order?.refund?.paymentId,
  });
  const refundPaymentId = order?.refund?.paymentId ?? lookedUpPayment?.id;
  const refundPayment = refundPaymentId ? { id: refundPaymentId, refund: order?.refund ?? lookedUpPayment?.refund } : null;

  const markSentMutation = useMutation({
    mutationFn: async ({ bankReference, note }: { bankReference: string; note: string }) => {
      const res = await apiClient(`/admin/payments/${refundPayment!.id}/refund/complete`, {
        method: 'POST',
        body: { bankReference, note: note || undefined },
      });
      return res.data;
    },
    onSuccess: () => {
      setMarkSentOpen(false);
      afterOrderChange();
      toast.success(rt('markSent.success'));
    },
    onError: (err: any) => {
      setDialogError(errorText(err, 'Failed to mark refund as sent'));
      afterOrderChange();
    },
  });

  if (isLoading) {
    return (
      <div className="py-24 text-center space-y-4">
        <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" />
        <p className="text-xs text-muted">{commonT('loading')}</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="p-12 text-center bg-surface border border-border rounded-2xl space-y-4 max-w-lg mx-auto">
        <AlertTriangle className="w-8 h-8 text-destructive mx-auto" />
        <h3 className="font-bold text-foreground text-sm">{commonT('errorTitle')}</h3>
        <button
          type="button"
          onClick={() => refetch()}
          className="px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
        >
          {commonT('retry')}
        </button>
      </div>
    );
  }

  const refundInfo = extractRefundInfo({ refund: refundPayment?.refund ?? undefined, timeline: order.timeline });
  const placedDate = new Date(order.placedAt).toLocaleString(
    locale === 'km' ? 'km-KH' : 'en-US',
    { dateStyle: 'full', timeStyle: 'short' }
  );

  return (
    <div className="space-y-6" data-testid="admin-order-detail-page">
      {/* Top Breadcrumb & Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/60">
        <div className="space-y-1">
          <Link
            href="/admin/orders"
            className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Orders</span>
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-xl sm:text-2xl font-black text-foreground font-mono" data-testid="admin-order-number">
              {order.orderNumber}
            </h1>
            <span
              data-testid="admin-order-status-badge"
              className="px-2.5 py-0.5 rounded-full text-xs font-bold border bg-primary/10 text-primary border-primary/20"
            >
              {order.status}
            </span>
          </div>
          <p className="text-xs text-muted">{t('detailSubtitle', { date: placedDate })}</p>
        </div>

        {/* Action Buttons: Print, Ship, Add Event, Cancel */}
        <div className="flex items-center flex-wrap gap-2">
          {/* Print Invoice Button */}
          <button
            type="button"
            data-testid="print-invoice-btn"
            onClick={() => window.print()}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border bg-surface hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors cursor-pointer"
          >
            <Printer className="w-3.5 h-3.5 text-muted" />
            <span>{t('printInvoice')}</span>
          </button>

          {/* Ship Button (if not already shipped) */}
          {(order.status === 'CONFIRMED' || order.status === 'PROCESSING') && !order.trackingNumber && (
            <button
              type="button"
              data-testid="open-ship-dialog-btn"
              onClick={() => {
                setShippedTrackingNumber(null);
                setShipDialogOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs cursor-pointer"
            >
              <Truck className="w-3.5 h-3.5" />
              <span>{t('shipOrder')}</span>
            </button>
          )}

          {/* Add Tracking Event Button (if shipped or in transit) */}
          {(order.status === 'SHIPPED' || order.status === 'OUT_FOR_DELIVERY') && (
            <button
              type="button"
              data-testid="open-tracking-event-dialog-btn"
              onClick={() => setTrackingDialogOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700 transition-colors shadow-xs cursor-pointer"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              <span>{t('addTrackingEvent')}</span>
            </button>
          )}

          {/* Cancel Order Button */}
          {(order.status === 'PENDING_PAYMENT' || order.status === 'CONFIRMED' || order.status === 'PROCESSING') && (
            <button
              type="button"
              data-testid="open-cancel-dialog-btn"
              onClick={() => {
                setDialogError(null);
                setCancelDialogOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl border border-destructive/30 text-destructive hover:bg-destructive/10 text-xs font-semibold transition-colors cursor-pointer"
            >
              <XCircle className="w-3.5 h-3.5" />
              <span>{t('cancelOrder')}</span>
            </button>
          )}
        </div>
      </div>

      {/* Customer asked to cancel */}
      {order.cancelRequested && (
        <div
          role="region"
          aria-label={rt('banner.title')}
          data-testid="cancel-request-banner"
          className="rounded-2xl border border-orange-500/40 bg-orange-500/10 p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        >
          <div className="space-y-1 min-w-0">
            <p className="text-sm font-bold text-orange-900 dark:text-orange-300 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span>{rt('banner.title')}</span>
            </p>
            <p className="text-xs text-foreground break-words" data-testid="cancel-request-reason">
              {rt('banner.reason', { reason: order.cancelRequestReason || '—' })}
            </p>
          </div>
          <div className="flex gap-2 shrink-0">
            <button
              type="button"
              data-testid="approve-cancel-btn"
              onClick={() => {
                setDialogError(null);
                setApproveDialogOpen(true);
              }}
              className="px-3.5 py-2 rounded-xl bg-red-700 hover:bg-red-800 text-white text-xs font-bold cursor-pointer"
            >
              {rt('banner.approve')}
            </button>
            <button
              type="button"
              data-testid="reject-cancel-btn"
              onClick={() => {
                setDialogError(null);
                setRejectDialogOpen(true);
              }}
              className="px-3.5 py-2 rounded-xl border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground cursor-pointer"
            >
              {rt('banner.reject')}
            </button>
          </div>
        </div>
      )}

      {/* Main Grid: Order Details & Sidebar Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column (2 cols): Items & Timeline */}
        <div className="lg:col-span-2 space-y-6">
          {/* Order Items Card */}
          <div className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4">
            <h3 className="font-bold text-sm text-foreground">{t('orderItems')}</h3>
            <div className="divide-y divide-border/60">
              {order.items?.map((item) => (
                <div
                  key={item.id}
                  data-testid={`admin-order-item-${item.productId}`}
                  className="py-3.5 flex items-center justify-between gap-4 first:pt-0 last:pb-0"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="relative w-12 h-12 rounded-lg overflow-hidden border border-border shrink-0">
                      <SafeImage src={getImageUrl(item.imageUrl)} alt={item.name} fill sizes="48px" className="object-cover" />
                    </div>
                    <div className="min-w-0 space-y-0.5">
                      <p className="text-xs font-bold text-foreground truncate">{item.name}</p>
                      {item.attributes && Object.keys(item.attributes).length > 0 && (
                        <p className="text-[11px] text-muted">
                          {Object.entries(item.attributes)
                            .map(([k, v]) => `${k}: ${v}`)
                            .join(' · ')}
                        </p>
                      )}
                      <p className="text-[11px] text-muted">
                        Qty: <strong className="text-foreground">{item.quantity}</strong> ×{' '}
                        {formatUsd(item.unitPriceCents)}
                      </p>
                    </div>
                  </div>
                  <div className="text-right whitespace-nowrap">
                    <p className="text-xs font-mono font-bold text-foreground">
                      {formatUsd(item.lineTotalCents)}
                    </p>
                    <p className="text-[10px] font-mono text-muted">
                      {formatKhr(item.lineTotalCents)}
                    </p>
                  </div>
                </div>
              ))}
            </div>

            {/* Subtotal, Shipping, Total summary */}
            <div className="pt-4 border-t border-border space-y-1.5 text-xs">
              <div className="flex justify-between text-muted">
                <span>Subtotal:</span>
                <span className="font-mono text-foreground font-semibold">
                  {formatUsd(order.subtotalCents)}
                </span>
              </div>
              {getDiscountLines(order, locale, {
                coupon: (code) => t('couponLine', { code }),
                discounts: t('discounts'),
              }).map((line) => (
                <div
                  key={line.key}
                  data-testid={`admin-order-discount-line-${line.kind}`}
                  className="flex justify-between text-emerald-700 font-medium"
                >
                  <span data-testid="admin-order-discounts-label">{line.label}:</span>
                  <span className="font-mono" data-testid="admin-order-discounts-amount">-{formatUsd(line.amountCents)}</span>
                </div>
              ))}
              <div className="flex justify-between text-muted">
                <span>Shipping ({order.shippingMethod?.name || 'Standard'}):</span>
                <span className="font-mono text-foreground font-semibold">
                  {formatUsd(order.shippingCents)}
                </span>
              </div>
              <div className="flex justify-between text-sm font-bold text-foreground pt-2 border-t border-border">
                <span>Total:</span>
                <div className="text-right">
                  <span className="font-mono text-primary font-black">
                    {formatUsd(order.totalCents)}
                  </span>
                  <span className="block text-[11px] font-mono text-muted font-medium">
                    {formatKhr(order.totalCents)}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Timeline Card */}
          <div className="bg-surface border border-border rounded-2xl p-5 sm:p-6 shadow-2xs space-y-4">
            <h3 className="font-bold text-sm text-foreground">{t('orderTimeline')}</h3>
            <div className="relative pl-6 space-y-6 border-l-2 border-primary/20 ml-3">
              {order.timeline?.map((evt, idx) => (
                <div key={idx} className="relative group space-y-1" data-testid="admin-timeline-event">
                  <div className="absolute -left-[31px] top-1 w-4 h-4 rounded-full border-4 border-surface bg-primary" />
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-foreground" data-testid="admin-timeline-status">{orderStatusLabel(evt.status)}</span>
                    <span className="text-[11px] text-muted">
                      {new Date(evt.at).toLocaleString(locale === 'km' ? 'km-KH' : 'en-US', {
                        dateStyle: 'short',
                        timeStyle: 'short',
                      })}
                    </span>
                  </div>
                  {evt.note && <p className="text-xs text-muted leading-relaxed">{evt.note}</p>}
                </div>
              ))}
            </div>

            {/* Shipment Events */}
            {trackingInfo?.events && trackingInfo.events.length > 0 && (
              <div className="pt-4 border-t border-border space-y-3">
                <span className="text-xs font-bold uppercase tracking-wider text-muted">
                  Shipment Events
                </span>
                <div className="space-y-2">
                  {trackingInfo.events.map((evt, idx) => (
                    <div
                      key={idx}
                      data-testid="admin-shipment-event"
                      className="p-3 rounded-xl bg-muted/5 border border-border text-xs space-y-1"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-primary">{evt.status}</span>
                        <span className="text-[11px] text-muted">
                          {new Date(evt.at).toLocaleString(locale === 'km' ? 'km-KH' : 'en-US', {
                            dateStyle: 'short',
                            timeStyle: 'short',
                          })}
                        </span>
                      </div>
                      {evt.location && (
                        <p className="text-foreground font-medium">📍 {evt.location}</p>
                      )}
                      {evt.note && <p className="text-muted text-[11px]">{evt.note}</p>}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right Column (1 col): Controls, Customer, Address, Payment */}
        <div className="space-y-6">
          {/* Status Transition Control Card */}
          <div
            data-testid="status-transition-card"
            className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3"
          >
            <h3 className="font-bold text-xs uppercase tracking-wider text-muted">
              {t('nextStatus')}
            </h3>

            {allowedNextStatuses.length === 0 ? (
              <p className="text-xs text-muted">{t('noAllowedTransitions')}</p>
            ) : (
              <div className="space-y-3">
                <select
                  data-testid="status-transition-select"
                  value={selectedNextStatus}
                  onChange={(e) => {
                    setSelectedNextStatus(e.target.value);
                    setTransitionError(null);
                  }}
                  className="w-full text-xs p-2.5 rounded-xl border border-border bg-background text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
                >
                  <option value="">-- Select Next Status --</option>
                  {allowedNextStatuses.map((st) => (
                    <option key={st} value={st}>
                      {st}
                    </option>
                  ))}
                </select>

                <button
                  type="button"
                  data-testid="update-status-btn"
                  disabled={!selectedNextStatus || statusMutation.isPending}
                  onClick={() => statusMutation.mutate(selectedNextStatus)}
                  className="w-full py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors disabled:opacity-50 cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {statusMutation.isPending ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <span>Update Status</span>
                  )}
                </button>
              </div>
            )}

            {/* Error Message for Invalid Transition */}
            {transitionError && (
              <div
                data-testid="status-transition-error"
                className="p-2.5 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs font-semibold"
              >
                {transitionError}
              </div>
            )}
          </div>

          {/* Customer & Address Card */}
          <div className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-4">
            <div className="space-y-2">
              <h3 className="font-bold text-xs uppercase tracking-wider text-muted flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-primary" />
                <span>{t('customerInfo')}</span>
              </h3>
              <div className="text-xs space-y-0.5">
                <p className="font-semibold text-foreground" data-testid="admin-customer-name">
                  {order.customer?.fullName || order.shippingAddress?.recipientName}
                </p>
                <p className="text-muted">{order.customer?.email}</p>
                <p className="text-muted">{order.customer?.phone || order.shippingAddress?.phone}</p>
              </div>
            </div>

            <div className="pt-3 border-t border-border space-y-2">
              <h3 className="font-bold text-xs uppercase tracking-wider text-muted flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-primary" />
                <span>{t('shippingAddress')}</span>
              </h3>
              <div className="text-xs text-muted space-y-0.5">
                <p className="text-foreground font-medium">{order.shippingAddress?.line1}</p>
                <p>
                  {order.shippingAddress?.district ? `${order.shippingAddress.district}, ` : ''}
                  {order.shippingAddress?.province}
                </p>
                {order.shippingAddress?.postalCode && <p>Postal: {order.shippingAddress.postalCode}</p>}
                {order.shippingAddress?.latitude != null && order.shippingAddress?.longitude != null && (
                  <p>
                    <a
                      data-testid="admin-order-open-map"
                      href={osmUrl(order.shippingAddress.latitude, order.shippingAddress.longitude)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary font-semibold hover:underline"
                    >
                      📍 {tAddr('openInMap')}
                    </a>
                  </p>
                )}
              </div>
            </div>

            {/* Tracking Number badge if shipped */}
            {order.trackingNumber && (
              <div className="pt-3 border-t border-border space-y-1">
                <span className="text-[11px] font-bold text-muted uppercase tracking-wider">
                  {t('trackingNumber')}
                </span>
                <div className="flex items-center justify-between gap-2">
                  <span
                    data-testid="admin-order-tracking-number"
                    id="order-tracking-number"
                    className="font-mono font-bold text-xs text-primary"
                  >
                    {order.trackingNumber}
                  </span>
                  <Link
                    href={`/track/${order.trackingNumber}`}
                    target="_blank"
                    className="text-[11px] text-primary hover:underline"
                  >
                    Public Track ↗
                  </Link>
                </div>
                {order.carrier && <p className="text-[11px] text-muted">Carrier: {order.carrier}</p>}
              </div>
            )}
          </div>

          {/* Refund to send (manual bank transfer) */}
          {refundPending && (
            <div
              data-testid="refund-card"
              className="bg-orange-500/5 border border-orange-500/40 rounded-2xl p-5 shadow-2xs space-y-3"
            >
              <h3 className="font-bold text-xs uppercase tracking-wider text-orange-900 dark:text-orange-300 flex items-center gap-1.5">
                <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
                <span>{rt('card.title')}</span>
              </h3>
              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between gap-2">
                  <span className="text-muted">{rt('card.amount')}:</span>
                  <span className="font-mono font-bold text-foreground" data-testid="refund-card-amount">
                    {formatUsd(refundInfo.amountCents ?? order.totalCents)}
                  </span>
                </div>
                <div className="flex justify-between items-center gap-2">
                  <span className="text-muted shrink-0">{rt('card.payer')}:</span>
                  <span className="flex items-center gap-1.5 min-w-0">
                    <span className="font-mono font-bold text-foreground truncate" data-testid="refund-card-account">
                      {refundInfo.payerAccount || rt('card.noAccount')}
                    </span>
                    {refundInfo.payerAccount && (
                      <button
                        type="button"
                        data-testid="refund-card-copy-btn"
                        onClick={() => {
                          navigator.clipboard?.writeText(refundInfo.payerAccount!).catch(() => {});
                          setCopiedAccount(true);
                          setTimeout(() => setCopiedAccount(false), 2000);
                        }}
                        className="inline-flex items-center gap-1 px-2 py-1 rounded-md border border-border hover:bg-muted/10 text-[11px] font-semibold text-foreground cursor-pointer"
                      >
                        {copiedAccount ? (
                          <Check className="w-3 h-3 text-emerald-600" aria-hidden="true" />
                        ) : (
                          <Copy className="w-3 h-3" aria-hidden="true" />
                        )}
                        <span>{copiedAccount ? rt('card.copied') : rt('card.copy')}</span>
                      </button>
                    )}
                  </span>
                </div>
              </div>
              <div className="text-xs text-foreground space-y-1">
                <p className="font-semibold">{rt('card.stepsTitle')}</p>
                <ol className="list-decimal pl-5 space-y-0.5 text-muted">
                  <li>{rt('card.step1')}</li>
                  <li>{rt('card.step2')}</li>
                  <li>{rt('card.step3')}</li>
                </ol>
              </div>
              {isAdmin ? (
                <button
                  type="button"
                  data-testid="mark-refund-sent-btn"
                  disabled={!refundPayment}
                  onClick={() => {
                    setDialogError(null);
                    setMarkSentOpen(true);
                  }}
                  className="w-full h-9 rounded-btn bg-orange-700 hover:bg-orange-800 text-white text-xs font-bold disabled:opacity-50 cursor-pointer"
                >
                  {rt('card.markSent')}
                </button>
              ) : (
                <p className="text-[11px] text-muted" data-testid="refund-admin-only-note">
                  {rt('card.adminOnly')}
                </p>
              )}
            </div>
          )}

          {/* Payment Details Card */}
          <div className="bg-surface border border-border rounded-2xl p-5 shadow-2xs space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-muted flex items-center gap-1.5">
              <CreditCard className="w-3.5 h-3.5 text-primary" />
              <span>{t('paymentDetails')}</span>
            </h3>
            <div className="space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-muted">Method:</span>
                <span className="font-bold text-foreground uppercase">{order.paymentMethod}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">Status:</span>
                <span
                  data-testid="admin-payment-status"
                  className={cn(
                    'font-bold px-2 py-0.5 rounded text-[11px] border',
                    TONE_CLASSES[paymentStatusTone(order.paymentStatus)],
                  )}
                >
                  {order.paymentStatus}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">Amount:</span>
                <span className="font-mono font-bold text-foreground">
                  {formatUsd(order.totalCents)}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Ship Order Modal Dialog */}
      {shipDialogOpen && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="ship-dialog"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in"
        >
          <div className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl relative text-left">
            <button
              type="button"
              onClick={() => setShipDialogOpen(false)}
              aria-label="Close dialog"
              className="absolute top-4 right-4 text-muted hover:text-foreground"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <Truck className="w-5 h-5 text-primary" />
              <span>{t('shipOrder')}</span>
            </h3>

            {shippedTrackingNumber ? (
              <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-700 dark:text-emerald-400 space-y-2">
                <CheckCircle2 className="w-6 h-6 mx-auto" />
                <p className="text-center font-bold text-xs" data-testid="ship-success-message">
                  {t('shipSuccess', { trackingNumber: shippedTrackingNumber })}
                </p>
                <div className="text-center pt-2">
                  <button
                    type="button"
                    onClick={() => setShipDialogOpen(false)}
                    className="px-4 py-1.5 rounded-btn bg-emerald-600 text-white text-xs font-bold"
                  >
                    Done
                  </button>
                </div>
              </div>
            ) : (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  shipMutation.mutate(carrierInput);
                }}
                className="space-y-4"
              >
                <div>
                  <label htmlFor="ship-carrier-input" className="block text-xs font-semibold text-foreground mb-1">
                    {t('carrier')}
                  </label>
                  <input
                    type="text"
                    id="ship-carrier-input"
                    data-testid="ship-carrier-input"
                    value={carrierInput}
                    onChange={(e) => setCarrierInput(e.target.value)}
                    placeholder={t('carrierPlaceholder')}
                    className="w-full text-xs p-2.5 rounded-btn border border-border bg-background text-foreground"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShipDialogOpen(false)}
                    className="h-9 rounded-btn border border-border hover:bg-muted/10 text-xs font-bold text-foreground"
                  >
                    {commonT('cancel')}
                  </button>
                  <button
                    type="submit"
                    data-testid="confirm-ship-btn"
                    disabled={shipMutation.isPending}
                    className="h-9 rounded-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-bold disabled:opacity-50 flex items-center justify-center gap-1.5"
                  >
                    {shipMutation.isPending ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <span>{t('shipOrder')}</span>
                    )}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* Add Tracking Event Modal Dialog */}
      {trackingDialogOpen && (
        <div
          role="dialog"
          aria-modal="true"
          data-testid="add-tracking-event-dialog"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in"
        >
          <div className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl relative text-left">
            <button
              type="button"
              onClick={() => setTrackingDialogOpen(false)}
              aria-label="Close dialog"
              className="absolute top-4 right-4 text-muted hover:text-foreground"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <PlusCircle className="w-5 h-5 text-indigo-600" />
              <span>{t('addTrackingEvent')}</span>
            </h3>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                trackingMutation.mutate();
              }}
              className="space-y-3 text-xs"
            >
              <div>
                <label htmlFor="event-status-select" className="block font-semibold text-foreground mb-1">
                  {t('eventStatus')}
                </label>
                <select
                  id="event-status-select"
                  data-testid="event-status-select"
                  value={eventStatus}
                  onChange={(e) => setEventStatus(e.target.value as ShipmentStatus)}
                  className="w-full p-2.5 rounded-btn border border-border bg-background text-foreground"
                >
                  <option value="IN_TRANSIT">IN_TRANSIT</option>
                  <option value="OUT_FOR_DELIVERY">OUT_FOR_DELIVERY</option>
                  <option value="DELIVERED">DELIVERED</option>
                  <option value="FAILED">FAILED</option>
                </select>
              </div>

              <div>
                <label htmlFor="event-location-input" className="block font-semibold text-foreground mb-1">
                  {t('location')}
                </label>
                <input
                  type="text"
                  id="event-location-input"
                  data-testid="event-location-input"
                  value={eventLocation}
                  onChange={(e) => setEventLocation(e.target.value)}
                  placeholder={t('locationPlaceholder')}
                  className="w-full p-2.5 rounded-btn border border-border bg-background text-foreground"
                  required
                />
              </div>

              <div>
                <label htmlFor="event-note-input" className="block font-semibold text-foreground mb-1">
                  {t('note')}
                </label>
                <textarea
                  id="event-note-input"
                  data-testid="event-note-input"
                  rows={3}
                  value={eventNote}
                  onChange={(e) => setEventNote(e.target.value)}
                  placeholder={t('notePlaceholder')}
                  className="w-full p-2.5 rounded-btn border border-border bg-background text-foreground resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setTrackingDialogOpen(false)}
                  className="h-9 rounded-btn border border-border hover:bg-muted/10 font-bold text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  data-testid="submit-tracking-event-btn"
                  disabled={trackingMutation.isPending}
                  className="h-9 rounded-btn bg-indigo-600 hover:bg-indigo-700 text-white font-bold disabled:opacity-50 flex items-center justify-center gap-1.5"
                >
                  {trackingMutation.isPending ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <span>Add Event</span>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Cancel order (reason required) */}
      <FormDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        title={rt('adminCancel.title')}
        description={rt('adminCancel.desc')}
        fields={[{ name: 'reason', label: t('cancelReason'), multiline: true, placeholder: t('cancelReasonPlaceholder'), testId: 'cancel-reason-input', ...REASON_RULE }]}
        submitLabel={rt('adminCancel.confirm')}
        cancelLabel={commonT('cancel')}
        destructive
        pending={cancelMutation.isPending}
        error={dialogError}
        onSubmit={(v) => cancelMutation.mutate(v.reason)}
        dialogTestId="cancel-order-dialog"
        submitTestId="confirm-cancel-order-btn"
      />

      {/* Approve the customer's request */}
      <FormDialog
        open={approveDialogOpen}
        onOpenChange={setApproveDialogOpen}
        title={rt('banner.approveTitle')}
        description={rt('banner.approveDesc')}
        fields={[{ name: 'reason', label: rt('banner.approveReasonLabel'), multiline: true, testId: 'approve-reason-input', ...REASON_RULE }]}
        submitLabel={rt('banner.approve')}
        cancelLabel={commonT('cancel')}
        destructive
        pending={cancelMutation.isPending}
        error={dialogError}
        onSubmit={(v) => cancelMutation.mutate(v.reason)}
        dialogTestId="approve-cancel-dialog"
        submitTestId="confirm-approve-cancel-btn"
      />

      {/* Reject the customer's request */}
      <FormDialog
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        title={rt('banner.rejectTitle')}
        description={rt('banner.rejectDesc')}
        fields={[{ name: 'reason', label: rt('banner.rejectReasonLabel'), multiline: true, testId: 'reject-reason-input', ...REASON_RULE }]}
        submitLabel={rt('banner.reject')}
        cancelLabel={commonT('cancel')}
        pending={rejectMutation.isPending}
        error={dialogError}
        onSubmit={(v) => rejectMutation.mutate(v.reason)}
        dialogTestId="reject-cancel-dialog"
        submitTestId="confirm-reject-cancel-btn"
      />

      {/* Mark the manual refund as sent (ADMIN only) */}
      <FormDialog
        open={markSentOpen}
        onOpenChange={setMarkSentOpen}
        title={rt('markSent.title')}
        description={rt('markSent.desc', {
          order: order.orderNumber,
          amount: formatUsd(refundInfo.amountCents ?? order.totalCents),
          account: refundInfo.payerAccount || rt('card.noAccount'),
        })}
        fields={[
          { name: 'bankReference', label: rt('markSent.bankReferenceLabel'), placeholder: rt('markSent.bankReferencePlaceholder'), testId: 'bank-reference-input', ...BANK_REFERENCE_RULE },
          { name: 'note', label: rt('markSent.noteLabel'), multiline: true, testId: 'refund-note-input', maxLength: 500 },
        ]}
        submitLabel={rt('markSent.submit')}
        cancelLabel={commonT('cancel')}
        pending={markSentMutation.isPending}
        error={dialogError}
        onSubmit={(v) => markSentMutation.mutate({ bankReference: v.bankReference, note: v.note })}
        dialogTestId="mark-refund-sent-dialog"
        submitTestId="confirm-mark-refund-sent-btn"
      />
    </div>
  );
}
