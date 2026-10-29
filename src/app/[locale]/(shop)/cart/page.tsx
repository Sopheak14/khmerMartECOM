'use client';

import React, { useState } from 'react';
import { SafeImage } from '@/components/common/SafeImage';
import { getImageUrl } from '@/lib/images';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ShoppingBag, Plus, Minus, Trash2, ArrowRight, Tag, AlertCircle, Check, Undo2 } from 'lucide-react';
import { Link, useRouter } from '@/navigation';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient, ApiError } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { CartDTO } from '@/types/api';
import { formatUsd, formatKhr, calculateCouponDiscountCents, translatePromotionLabel } from '@/lib/money';
import { toast } from 'sonner';

export default function CartPage() {
  const t = useTranslations('cart');
  const common = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const queryClient = useQueryClient();

  const { isInitialized } = useAuthStore();

  const [mounted, setMounted] = useState(false);
  const [couponCode, setCouponCode] = useState('');
  const [couponError, setCouponError] = useState<string | null>(null);
  const [stockError, setStockError] = useState<{ itemId: number; message: string } | null>(null);
  const [pendingRemovalId, setPendingRemovalId] = useState<number | null>(null);

  React.useEffect(() => {
    setMounted(true);
  }, []);

  // Fetch cart
  const { data: cart, isLoading } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
    enabled: isInitialized,
  });

  // Quantity mutation
  const updateQtyMutation = useMutation({
    mutationFn: async ({ itemId, quantity }: { itemId: number; quantity: number }) => {
      const res = await apiClient<CartDTO>(`/cart/items/${itemId}`, {
        method: 'PATCH',
        body: { quantity },
      });
      return res.data;
    },
    onSuccess: (data) => {
      setStockError(null);
      queryClient.setQueryData(queryKeys.cart.current, data);
    },
    onError: (err: any, variables) => {
      if (err instanceof ApiError && err.code === 'OUT_OF_STOCK') {
        const available = err.details?.available ?? '0';
        const msg =
          locale === 'km'
            ? `នៅសល់តែ ${available} ទៀតប៉ុណ្ណោះក្នុងស្តុក (OUT_OF_STOCK)`
            : `Only ${available} left in stock (OUT_OF_STOCK)`;
        setStockError({ itemId: variables.itemId, message: msg });
        toast.error(msg);
      } else {
        toast.error(err.message || common('somethingWentWrong'));
      }
    },
  });

  // Remove mutation
  const removeItemMutation = useMutation({
    mutationFn: async (itemId: number) => {
      const res = await apiClient<CartDTO>(`/cart/items/${itemId}`, {
        method: 'DELETE',
      });
      return res.data;
    },
    onSuccess: (data) => {
      setPendingRemovalId(null);
      queryClient.setQueryData(queryKeys.cart.current, data);
      toast.success(t('itemRemoved'));
    },
    onError: (err: any) => {
      toast.error(err.message || common('somethingWentWrong'));
    },
  });

  // Apply coupon mutation
  const applyCouponMutation = useMutation({
    mutationFn: async (code: string) => {
      const res = await apiClient<CartDTO>('/cart/coupon', {
        method: 'POST',
        body: { code },
      });
      return res.data;
    },
    onSuccess: (data) => {
      setCouponError(null);
      setCouponCode('');
      queryClient.setQueryData(queryKeys.cart.current, data);
      toast.success(t('couponAppliedSuccess'));
    },
    onError: (err: any) => {
      if (err instanceof ApiError) {
        if (err.code === 'COUPON_MIN_ORDER') {
          setCouponError(
            locale === 'km'
              ? 'ការបញ្ជាទិញអប្បបរមា $35.00 ត្រូវបានទាមទារ (Minimum order $35.00)'
              : 'Minimum order $35.00 is required for coupon SAVE5'
          );
        } else if (err.code === 'COUPON_INVALID') {
          setCouponError(
            locale === 'km'
              ? 'លេខកូដប័ណ្ណមិនត្រឹមត្រូវ (COUPON_INVALID)'
              : 'Coupon code is invalid (COUPON_INVALID)'
          );
        } else {
          setCouponError(err.message);
        }
      } else {
        setCouponError(err?.message || 'Failed to apply coupon');
      }
    },
  });

  // Remove coupon mutation
  const removeCouponMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient<CartDTO>('/cart/coupon', {
        method: 'DELETE',
      });
      return res.data;
    },
    onSuccess: (data) => {
      setCouponError(null);
      queryClient.setQueryData(queryKeys.cart.current, data);
      toast.success(t('couponRemoved'));
    },
  });

  const handleApplyCoupon = (e: React.FormEvent) => {
    e.preventDefault();
    if (!couponCode.trim()) return;
    setCouponError(null);
    applyCouponMutation.mutate(couponCode.trim());
  };

  const items = cart?.items || [];
  const itemCount = cart?.itemCount || 0;

  if (!mounted || !isInitialized || isLoading) {
    return (
      <div className="container mx-auto px-4 py-12 max-w-6xl animate-pulse space-y-6" data-testid="cart-loading-skeleton">
        <div className="h-8 w-48 bg-muted/20 rounded" />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-8 h-96 bg-muted/10 rounded-card" />
          <div className="lg:col-span-4 h-80 bg-muted/10 rounded-card" />
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl" data-testid="cart-page">
      <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight mb-8">
        {t('shoppingCart')} ({itemCount})
      </h1>

      {items.length === 0 ? (
        <div
          data-testid="cart-empty-state"
          className="p-12 text-center bg-surface border border-border rounded-card space-y-4 max-w-xl mx-auto my-8 shadow-xs"
        >
          <div className="w-16 h-16 mx-auto rounded-full bg-muted/10 flex items-center justify-center text-muted">
            <ShoppingBag className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-foreground">{t('emptyTitle')}</h2>
          <p className="text-sm text-muted max-w-md mx-auto">{t('emptyDescription')}</p>
          <Link
            href="/products"
            className="inline-flex items-center gap-2 px-6 py-3 rounded-btn bg-primary text-primary-foreground text-sm font-bold shadow-xs hover:bg-primary/90 transition-colors"
          >
            <span>{t('continueShopping')}</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Left: Cart Items List */}
          <div className="lg:col-span-8 space-y-4" data-testid="cart-items-list">
            <div className="bg-surface rounded-card border border-border overflow-hidden shadow-xs divide-y divide-border">
              {items.map((item) => {
                const name = locale === 'km' && item.nameKm ? item.nameKm : item.name;
                const isPendingDelete = pendingRemovalId === item.id;

                return (
                  <div
                    key={item.id}
                    data-testid="cart-item"
                    className="p-4 sm:p-6 transition-colors hover:bg-muted/5 flex flex-col sm:flex-row gap-4 justify-between"
                  >
                    <div className="flex gap-4">
                      {/* Image */}
                      <div className="relative w-20 h-20 sm:w-24 sm:h-24 rounded-card overflow-hidden bg-muted/20 shrink-0 border border-border/50">
                        {item.imageUrl ? (
                          <SafeImage
                            src={getImageUrl(item.imageUrl)}
                            alt={name}
                            fill
                            className="object-cover"
                            sizes="96px"
                          />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-xs text-muted">
                            No image
                          </div>
                        )}
                      </div>

                      {/* Info */}
                      <div className="space-y-1.5 flex-1 min-w-0">
                        <Link
                          href={`/products/${item.slug}`}
                          data-testid="cart-item-name"
                          className="font-bold text-sm sm:text-base text-foreground hover:text-primary transition-colors line-clamp-2"
                        >
                          {name}
                        </Link>

                        {/* Variant Attributes */}
                        {item.attributes && Object.keys(item.attributes).length > 0 && (
                          <div className="flex flex-wrap gap-1.5 pt-0.5">
                            {Object.entries(item.attributes).map(([attr, val]) => (
                              <span
                                key={attr}
                                className="text-[11px] bg-muted/15 border border-border px-2 py-0.5 rounded text-muted font-medium"
                              >
                                {val}
                              </span>
                            ))}
                          </div>
                        )}

                        {/* Unit price */}
                        <p className="text-xs text-muted">
                          {t('unitPrice')}: <span className="font-semibold text-foreground">{formatUsd(item.unitPriceCents)}</span>
                        </p>

                        {/* Stock error message */}
                        {stockError?.itemId === item.id && (
                          <div
                            data-testid="stock-error"
                            className="flex items-center gap-1.5 text-xs font-semibold text-red-700 bg-red-500/10 px-2 py-1 rounded"
                          >
                            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                            <span>{stockError.message}</span>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Right: Quantity Stepper & Price & Actions */}
                    <div className="flex sm:flex-col items-center sm:items-end justify-between sm:justify-between border-t sm:border-t-0 pt-3 sm:pt-0 border-border/60">
                      {/* Line total */}
                      <div className="text-left sm:text-right">
                        <span className="font-black text-base sm:text-lg text-foreground block">
                          {formatUsd(item.lineTotalCents)}
                        </span>
                        <span className="text-[11px] text-muted block">
                          {formatKhr(item.lineTotalCents)}
                        </span>
                      </div>

                      {/* Stepper & Remove */}
                      <div className="flex items-center gap-3">
                        {/* Stepper */}
                        <div className="flex items-center border border-border rounded-btn bg-surface">
                          <button
                            type="button"
                            data-testid="qty-minus"
                            aria-label="Decrease quantity"
                            disabled={item.quantity <= 1 || updateQtyMutation.isPending}
                            onClick={() =>
                              updateQtyMutation.mutate({
                                itemId: item.id,
                                quantity: item.quantity - 1,
                              })
                            }
                            className="w-8 h-8 flex items-center justify-center text-muted hover:text-foreground disabled:opacity-40 transition-colors"
                          >
                            <Minus className="w-3.5 h-3.5" />
                          </button>
                          <span
                            data-testid="qty-value"
                            className="w-8 text-center text-xs font-bold text-foreground"
                          >
                            {item.quantity}
                          </span>
                          <button
                            type="button"
                            data-testid="qty-plus"
                            aria-label="Increase quantity"
                            disabled={updateQtyMutation.isPending}
                            onClick={() => {
                              // If clicked exceeding available, trigger OUT_OF_STOCK error logic
                              if (item.quantity >= item.available) {
                                const msg =
                                  locale === 'km'
                                    ? `នៅសល់តែ ${item.available} ទៀតប៉ុណ្ណោះក្នុងស្តុក (OUT_OF_STOCK)`
                                    : `Only ${item.available} left in stock (OUT_OF_STOCK)`;
                                setStockError({ itemId: item.id, message: msg });
                                toast.error(msg);
                                return;
                              }
                              updateQtyMutation.mutate({
                                itemId: item.id,
                                quantity: item.quantity + 1,
                              });
                            }}
                            className="w-8 h-8 flex items-center justify-center text-muted hover:text-foreground disabled:opacity-40 transition-colors"
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>

                        {/* Remove item with confirmation */}
                        {isPendingDelete ? (
                          <div className="flex items-center gap-1.5 animate-in fade-in">
                            <button
                              type="button"
                              data-testid="confirm-remove-btn"
                              onClick={() => removeItemMutation.mutate(item.id)}
                              className="px-2 py-1 rounded bg-destructive text-destructive-foreground text-[11px] font-bold hover:bg-destructive/90 transition-colors"
                            >
                              {t('confirmRemove')}
                            </button>
                            <button
                              type="button"
                              onClick={() => setPendingRemovalId(null)}
                              className="p-1 rounded text-muted hover:text-foreground transition-colors"
                            >
                              <Undo2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            data-testid="remove-cart-item"
                            onClick={() => setPendingRemovalId(item.id)}
                            aria-label="Remove item"
                            className="p-2 rounded-btn hover:bg-destructive/10 text-muted hover:text-destructive transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right: Order Summary */}
          <div className="lg:col-span-4 space-y-4" data-testid="cart-summary">
            {/* Coupon Card */}
            <div className="p-5 bg-surface rounded-card border border-border space-y-3 shadow-xs">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted">
                <Tag className="w-3.5 h-3.5 text-primary" />
                <span>{t('couponCode')}</span>
              </div>

              {cart?.coupon ? (
                <div
                  data-testid="applied-coupon-badge"
                  className="p-3 rounded-btn bg-primary/10 border border-primary/20 flex items-center justify-between gap-2"
                >
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-1.5 font-bold text-xs text-primary">
                      <Check className="w-3.5 h-3.5" />
                      <span>{cart.coupon.code}</span>
                    </div>
                    {cart.coupon.description && (
                      <p className="text-[11px] text-muted">{cart.coupon.description}</p>
                    )}
                  </div>
                  <button
                    type="button"
                    data-testid="remove-coupon-btn"
                    onClick={() => removeCouponMutation.mutate()}
                    className="text-xs font-bold text-destructive hover:underline"
                  >
                    {t('removeCoupon')}
                  </button>
                </div>
              ) : (
                <form onSubmit={handleApplyCoupon} className="space-y-2">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      data-testid="coupon-input"
                      value={couponCode}
                      onChange={(e) => {
                        setCouponCode(e.target.value);
                        setCouponError(null);
                      }}
                      placeholder={t('couponPlaceholder')}
                      className="flex-1 h-9 px-3 rounded-btn bg-background border border-border text-xs text-foreground placeholder:text-muted focus:outline-none focus:ring-1 focus:ring-primary"
                    />
                    <button
                      type="submit"
                      data-testid="apply-coupon-btn"
                      disabled={!couponCode.trim() || applyCouponMutation.isPending}
                      className="h-9 px-4 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 disabled:opacity-50 transition-colors shadow-xs"
                    >
                      {applyCouponMutation.isPending ? '...' : t('apply')}
                    </button>
                  </div>
                  {couponError && (
                    <div
                      data-testid="coupon-error"
                      className="text-xs font-medium text-destructive flex items-center gap-1 mt-1"
                    >
                      <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                      <span>{couponError}</span>
                    </div>
                  )}
                </form>
              )}
            </div>

            {/* Financial Summary */}
            {cart && (
              <div className="p-5 bg-surface rounded-card border border-border space-y-4 shadow-xs">
                <h3 className="font-bold text-sm uppercase tracking-wider text-muted border-b border-border pb-3">
                  {t('orderSummary')}
                </h3>

                <div className="space-y-2.5 text-xs">
                  {/* Subtotal */}
                  <div className="flex items-center justify-between text-muted">
                    <span>{t('subtotal')}</span>
                    <span data-testid="cart-subtotal" className="font-semibold text-foreground">
                      {formatUsd(cart.subtotalCents)}
                    </span>
                  </div>

                  {/* Promotion Discounts */}
                  {cart.promotionDiscounts && cart.promotionDiscounts.length > 0 && (
                    <div data-testid="promotion-discounts" className="space-y-1.5 py-1 border-t border-dashed border-border">
                      {cart.promotionDiscounts.map((promo, idx) => (
                        <div
                          key={idx}
                          data-testid="promotion-discount-item"
                          className="flex items-center justify-between text-amber-800 font-medium text-[11px]"
                        >
                          <span>{translatePromotionLabel(promo.label, locale)}</span>
                          <span>-{formatUsd(promo.amountCents)}</span>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Coupon Discount (Real coupon line = discountCents - sum(promotionDiscounts.amountCents)) */}
                  {cart.coupon && calculateCouponDiscountCents(cart.discountCents, cart.promotionDiscounts) > 0 && (
                    <div
                      data-testid="coupon-discount-line"
                      className="flex items-center justify-between text-green-800 dark:text-green-400 font-semibold"
                    >
                      <span>{t('couponDiscount')} ({cart.coupon.code})</span>
                      <span data-testid="coupon-discount-amount">
                        -{formatUsd(calculateCouponDiscountCents(cart.discountCents, cart.promotionDiscounts))}
                      </span>
                    </div>
                  )}

                  {/* Shipping Estimate */}
                  <div className="flex items-center justify-between text-muted border-t border-dashed border-border pt-2">
                    <span>{t('shippingEstimate')}</span>
                    <span data-testid="cart-shipping" className="font-semibold text-foreground">
                      {cart.shippingEstimateCents === 0
                        ? t('free')
                        : formatUsd(cart.shippingEstimateCents)}
                    </span>
                  </div>

                  {/* Total */}
                  <div className="pt-3 border-t border-border flex items-center justify-between">
                    <div>
                      <span className="font-bold text-base text-foreground block">{t('total')}</span>
                      <span className="text-[10px] text-muted">{t('vatIncluded')}</span>
                    </div>
                    <div className="text-right">
                      <span
                        data-testid="cart-total-usd"
                        className="text-xl sm:text-2xl font-black text-primary block"
                      >
                        {formatUsd(cart.totalCents)}
                      </span>
                      <span
                        data-testid="cart-total-khr"
                        className="text-xs font-bold text-muted block"
                      >
                        {formatKhr(cart.totalCents)}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Checkout CTA */}
                <Link
                  href="/checkout"
                  data-testid="proceed-to-checkout-btn"
                  className="w-full h-11 flex items-center justify-center gap-2 rounded-btn bg-primary text-primary-foreground font-bold text-sm shadow-md hover:bg-primary/90 transition-all cursor-pointer"
                >
                  <span>{t('proceedToCheckout')}</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
