'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import { getImageUrl } from '@/lib/images';
import { useTranslations, useLocale } from 'next-intl';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from '@/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { CheckoutPreviewDTO, CheckoutResponseDTO, PaymentMethod } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import { MapPin, Truck, CreditCard, ShoppingBag, ArrowLeft, ShieldCheck, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

interface ReviewStepProps {
  preview: CheckoutPreviewDTO;
  selectedAddressId: number;
  selectedShippingCode: string;
  selectedPaymentMethod: PaymentMethod;
  notes: string;
  onNotesChange: (notes: string) => void;
  onBack: () => void;
}

export function ReviewStep({
  preview,
  selectedAddressId,
  selectedShippingCode,
  selectedPaymentMethod,
  notes,
  onNotesChange,
  onBack,
}: ReviewStepProps) {
  const t = useTranslations('checkout');
  const locale = useLocale();
  const router = useRouter();
  const queryClient = useQueryClient();

  // Stable idempotency key per checkout attempt
  const [idempotencyKey] = useState(() =>
    typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `idem-${Date.now()}`
  );

  const isSubmittingRef = React.useRef(false);

  // Place order mutation
  const placeOrderMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient<CheckoutResponseDTO>('/checkout', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: {
          addressId: selectedAddressId,
          shippingMethodCode: selectedShippingCode,
          paymentMethod: selectedPaymentMethod,
          notes: notes.trim() || undefined,
        },
      });
      return res.data;
    },
    onSuccess: (data) => {
      // Clear cart cache
      queryClient.invalidateQueries({ queryKey: queryKeys.cart.current });
      toast.success(t('orderPlacedSuccess'));

      const orderNumber = data.order.orderNumber;
      if (data.next === 'KHQR_PAYMENT' || selectedPaymentMethod === 'KHQR') {
        router.push(`/checkout/pay/${orderNumber}`);
      } else if (data.next === 'BANK_TRANSFER_SLIP' || selectedPaymentMethod === 'BANK_TRANSFER') {
        router.push(`/checkout/bank-transfer/${orderNumber}`);
      } else {
        router.push(`/orders/${orderNumber}/success`);
      }
    },
    onError: (err: any) => {
      isSubmittingRef.current = false;
      if (err instanceof ApiError) {
        if (err.code === 'PRICE_CHANGED' || err.code === 'OUT_OF_STOCK') {
          toast.error(err.message || 'Cart item changed or out of stock');
          router.push(`/cart?error=${err.code}`);
          return;
        }
      }
      toast.error(err?.message || 'Failed to place order');
    },
  });

  const handlePlaceOrder = () => {
    if (isSubmittingRef.current || placeOrderMutation.isPending) return;
    isSubmittingRef.current = true;
    placeOrderMutation.mutate();
  };

  const { items, address, shippingMethod, subtotalCents, discountCents, shippingCents, totalCents, totalKhr } =
    preview;

  return (
    <div className="space-y-6" data-testid="step-review">
      <h2 className="text-lg font-bold text-foreground">{t('reviewYourOrder')}</h2>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Address Card */}
        <div className="p-4 rounded-card border border-border bg-surface space-y-2">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted">
            <MapPin className="w-4 h-4 text-primary" />
            <span>{t('shippingAddress')}</span>
          </div>
          <div className="text-xs space-y-0.5 text-muted">
            <p className="font-bold text-foreground">{address.recipientName}</p>
            <p>{address.phone}</p>
            <p>
              {address.line1}
              {address.line2 ? `, ${address.line2}` : ''}
            </p>
            <p>
              {address.district}, {locale === 'km' && address.provinceKm ? address.provinceKm : address.province} {address.postalCode || ''}
            </p>
          </div>
        </div>

        {/* Shipping Method Card */}
        <div className="p-4 rounded-card border border-border bg-surface space-y-2">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted">
            <Truck className="w-4 h-4 text-primary" />
            <span>{t('shippingMethod')}</span>
          </div>
          <div className="text-xs space-y-0.5 text-muted">
            <p className="font-bold text-foreground">
              {locale === 'km' && shippingMethod.nameKm ? shippingMethod.nameKm : shippingMethod.name}
            </p>
            <p>
              {shippingCents === 0 ? t('free') : formatUsd(shippingCents)}
            </p>
            <p className="text-muted/70">
              {shippingMethod.etaDaysMin === 0
                ? t('etaSameDay')
                : t('etaDays', { min: shippingMethod.etaDaysMin, max: shippingMethod.etaDaysMax })}
            </p>
          </div>
        </div>

        {/* Payment Method Card */}
        <div className="p-4 rounded-card border border-border bg-surface space-y-2">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted">
            <CreditCard className="w-4 h-4 text-primary" />
            <span>{t('paymentMethod')}</span>
          </div>
          <div className="text-xs space-y-0.5 text-muted">
            <p className="font-bold text-foreground">
              {selectedPaymentMethod === 'KHQR'
                ? 'Bakong KHQR'
                : selectedPaymentMethod === 'COD'
                ? t('paymentCodTitle')
                : selectedPaymentMethod === 'BANK_TRANSFER'
                ? t('paymentBankTitle')
                : selectedPaymentMethod}
            </p>
            {selectedPaymentMethod === 'KHQR' && (
              <p className="text-green-800 dark:text-green-400 font-medium">
                {t('instantKhqrPayment')}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Items Preview */}
      <div className="p-5 rounded-card border border-border bg-surface space-y-4">
        <div className="flex items-center gap-2 font-bold text-sm text-foreground">
          <ShoppingBag className="w-4 h-4 text-primary" />
          <span>{t('itemsOrdered')} ({items.length})</span>
        </div>

        <div className="divide-y divide-border/60">
          {items.map((item) => {
            const name = locale === 'km' && item.nameKm ? item.nameKm : item.name;
            return (
              <div key={item.id} className="py-3 flex items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="relative w-12 h-12 rounded bg-muted/20 overflow-hidden shrink-0 border border-border/50">
                    {item.imageUrl ? (
                      <Image
                        src={getImageUrl(item.imageUrl)}
                        alt={name}
                        fill
                        className="object-cover"
                        sizes="48px"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-[10px] text-muted">
                        No img
                      </div>
                    )}
                  </div>
                  <div>
                    <h4 className="font-bold text-xs text-foreground line-clamp-1">{name}</h4>
                    <p className="text-[11px] text-muted">
                      {formatUsd(item.unitPriceCents)} × {item.quantity}
                    </p>
                  </div>
                </div>

                <div className="text-right">
                  <span className="font-bold text-xs text-foreground block">
                    {formatUsd(item.lineTotalCents)}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Financial Summary & Notes */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Order Notes */}
        <div className="lg:col-span-6 space-y-2">
          <label className="text-xs font-bold uppercase tracking-wider text-muted block">
            {t('orderNotes')} ({t('optional')})
          </label>
          <textarea
            data-testid="order-notes-input"
            value={notes}
            onChange={(e) => onNotesChange(e.target.value)}
            placeholder={t('orderNotesPlaceholder')}
            rows={3}
            className="w-full p-3 rounded-btn border border-border bg-background text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>

        {/* Cost Breakdown */}
        <div className="lg:col-span-6 p-5 rounded-card border border-border bg-surface space-y-3">
          <div className="space-y-2 text-xs">
            <div className="flex items-center justify-between text-muted">
              <span>{t('subtotal')}</span>
              <span className="font-semibold text-foreground">{formatUsd(subtotalCents)}</span>
            </div>

            {discountCents > 0 && (
              <div className="flex items-center justify-between text-green-800 dark:text-green-400 font-semibold">
                <span>{t('discount')}</span>
                <span>-{formatUsd(discountCents)}</span>
              </div>
            )}

            <div className="flex items-center justify-between text-muted">
              <span>{t('shippingFee')}</span>
              <span className="font-semibold text-foreground">
                {shippingCents === 0 ? t('free') : formatUsd(shippingCents)}
              </span>
            </div>

            <div className="pt-3 border-t border-border flex items-center justify-between">
              <span className="text-sm font-black text-foreground">{t('total')}</span>
              <div className="text-right">
                <span
                  data-testid="review-total-usd"
                  className="text-xl sm:text-2xl font-black text-primary block"
                >
                  {formatUsd(totalCents)}
                </span>
                <span
                  data-testid="review-total-khr"
                  className="text-xs font-bold text-muted block"
                >
                  {formatKhr(totalCents)}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Place Order CTA */}
      <div className="pt-4 border-t border-border flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          disabled={placeOrderMutation.isPending}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-btn border border-border bg-surface text-foreground font-semibold text-xs hover:bg-muted/10 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>{t('back')}</span>
        </button>

        <button
          type="button"
          data-testid="place-order-btn"
          disabled={placeOrderMutation.isPending}
          onClick={handlePlaceOrder}
          className="inline-flex items-center gap-2 px-8 py-3 rounded-btn bg-primary text-primary-foreground font-black text-sm shadow-md hover:bg-primary/90 transition-all cursor-pointer disabled:opacity-50"
        >
          {placeOrderMutation.isPending ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>{t('placingOrder')}...</span>
            </>
          ) : (
            <>
              <ShieldCheck className="w-4 h-4" />
              <span>{t('placeOrder')}</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}
