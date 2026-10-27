'use client';

import React, { useEffect } from 'react';
import Image from 'next/image';
import { getImageUrl } from '@/lib/images';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ShoppingBag, X, Plus, Minus, Trash2, ArrowRight } from 'lucide-react';
import { Link } from '@/navigation';
import { useCartDrawerStore } from '@/lib/store/useCartDrawerStore';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient } from '@/lib/apiClient';
import { CartDTO } from '@/types/api';
import { formatUsd, formatKhr, calculateCouponDiscountCents, translatePromotionLabel } from '@/lib/money';
import { toast } from 'sonner';

export function CartDrawer() {
  const t = useTranslations('cart');
  const common = useTranslations('common');
  const locale = useLocale();
  const queryClient = useQueryClient();
  const { isOpen, close } = useCartDrawerStore();

  const { data: cart, isLoading } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
  });

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, close]);

  // Update item quantity mutation
  const updateQtyMutation = useMutation({
    mutationFn: async ({ itemId, quantity }: { itemId: number; quantity: number }) => {
      const res = await apiClient<CartDTO>(`/cart/items/${itemId}`, {
        method: 'PATCH',
        body: { quantity },
      });
      return res.data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.cart.current, data);
    },
    onError: (err: any) => {
      toast.error(err.message || common('somethingWentWrong'));
    },
  });

  // Remove item mutation
  const removeItemMutation = useMutation({
    mutationFn: async (itemId: number) => {
      const res = await apiClient<CartDTO>(`/cart/items/${itemId}`, {
        method: 'DELETE',
      });
      return res.data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData(queryKeys.cart.current, data);
      toast.success(t('itemRemoved'));
    },
    onError: (err: any) => {
      toast.error(err.message || common('somethingWentWrong'));
    },
  });

  if (!isOpen) return null;

  const items = cart?.items || [];
  const itemCount = cart?.itemCount || 0;

  return (
    <div className="fixed inset-0 z-50 flex justify-end animate-in fade-in duration-200">
      {/* Backdrop */}
      <div
        data-testid="cart-drawer-overlay"
        onClick={close}
        className="fixed inset-0 bg-black/60 backdrop-blur-xs transition-opacity"
      />

      {/* Drawer content */}
      <aside
        data-testid="cart-drawer"
        role="dialog"
        aria-label="Shopping Cart"
        className="relative z-10 w-full max-w-md bg-surface border-l border-border shadow-2xl flex flex-col h-full animate-in slide-in-from-right duration-300"
      >
        {/* Header */}
        <div className="p-4 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShoppingBag className="w-5 h-5 text-primary" />
            <h2 className="font-bold text-base text-foreground">
              {t('shoppingCart')} ({itemCount})
            </h2>
          </div>
          <button
            type="button"
            data-testid="cart-drawer-close"
            onClick={close}
            aria-label="Close cart"
            className="p-1.5 rounded-full hover:bg-muted/20 text-muted hover:text-foreground transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body / Items list */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 space-y-3">
              <ShoppingBag className="w-12 h-12 text-muted/40" />
              <h3 className="font-bold text-base text-foreground">{t('emptyTitle')}</h3>
              <p className="text-xs text-muted max-w-xs">{t('emptyDescription')}</p>
              <Link
                href="/products"
                onClick={close}
                className="mt-2 inline-flex items-center gap-1.5 px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs hover:bg-primary/90 transition-colors"
              >
                <span>{t('continueShopping')}</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          ) : (
            items.map((item) => {
              const name = locale === 'km' && item.nameKm ? item.nameKm : item.name;
              return (
                <div
                  key={item.id}
                  data-testid="cart-drawer-item"
                  className="flex gap-3 p-3 rounded-card bg-muted/10 border border-border/70 hover:border-border transition-colors"
                >
                  {/* Thumbnail */}
                  <div className="relative w-16 h-16 rounded-md overflow-hidden bg-muted/20 shrink-0">
                    {item.imageUrl ? (
                      <Image
                        src={getImageUrl(item.imageUrl)}
                        alt={name}
                        fill
                        className="object-cover"
                        sizes="64px"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-[10px] text-muted">
                        No img
                      </div>
                    )}
                  </div>

                  {/* Details */}
                  <div className="flex-1 min-w-0 flex flex-col justify-between">
                    <div>
                      <div className="flex items-start justify-between gap-1">
                        <Link
                          href={`/products/${item.slug}`}
                          onClick={close}
                          className="text-xs font-bold text-foreground line-clamp-1 hover:text-primary transition-colors"
                        >
                          {name}
                        </Link>
                        <button
                          type="button"
                          data-testid="drawer-remove-item-btn"
                          onClick={() => removeItemMutation.mutate(item.id)}
                          aria-label="Remove item"
                          className="text-muted hover:text-destructive p-0.5 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      {/* Variant chips */}
                      {item.attributes && Object.keys(item.attributes).length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-1">
                          {Object.entries(item.attributes).map(([k, val]) => (
                            <span
                              key={k}
                              className="text-[10px] bg-surface px-1.5 py-0.5 rounded border border-border text-muted"
                            >
                              {val}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Stepper & Price */}
                    <div className="flex items-center justify-between mt-2 pt-1 border-t border-border/40">
                      <div className="flex items-center border border-border rounded bg-surface">
                        <button
                          type="button"
                          disabled={item.quantity <= 1 || updateQtyMutation.isPending}
                          onClick={() =>
                            updateQtyMutation.mutate({
                              itemId: item.id,
                              quantity: item.quantity - 1,
                            })
                          }
                          aria-label="Decrease quantity"
                          className="w-6 h-6 flex items-center justify-center text-muted hover:text-foreground disabled:opacity-40 transition-colors"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="w-6 text-center text-xs font-bold text-foreground">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          disabled={item.quantity >= item.available || updateQtyMutation.isPending}
                          onClick={() =>
                            updateQtyMutation.mutate({
                              itemId: item.id,
                              quantity: item.quantity + 1,
                            })
                          }
                          aria-label="Increase quantity"
                          className="w-6 h-6 flex items-center justify-center text-muted hover:text-foreground disabled:opacity-40 transition-colors"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>

                      <div className="text-right">
                        <span className="text-xs font-bold text-foreground">
                          {formatUsd(item.lineTotalCents)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer / Summary */}
        {items.length > 0 && cart && (
          <div className="p-4 border-t border-border bg-surface space-y-3">
            <div className="space-y-1.5 text-xs">
              <div className="flex items-center justify-between text-muted">
                <span>{t('subtotal')}</span>
                <span className="font-semibold text-foreground">
                  {formatUsd(cart.subtotalCents)}
                </span>
              </div>

              {/* Promotion Discounts */}
              {cart.promotionDiscounts && cart.promotionDiscounts.length > 0 && (
                <div data-testid="drawer-promotion-discounts" className="space-y-1 py-0.5">
                  {cart.promotionDiscounts.map((promo, idx) => (
                    <div
                      key={idx}
                      className="flex items-center justify-between text-accent font-medium text-[11px]"
                    >
                      <span>{translatePromotionLabel(promo.label, locale)}</span>
                      <span>-{formatUsd(promo.amountCents)}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* Coupon Discount */}
              {cart.coupon && calculateCouponDiscountCents(cart.discountCents, cart.promotionDiscounts) > 0 ? (
                <div className="flex items-center justify-between text-green-800 dark:text-green-400 font-semibold">
                  <span>{t('couponDiscount')} ({cart.coupon.code})</span>
                  <span>-{formatUsd(calculateCouponDiscountCents(cart.discountCents, cart.promotionDiscounts))}</span>
                </div>
              ) : !cart.promotionDiscounts?.length && cart.discountCents > 0 ? (
                <div className="flex items-center justify-between text-green-800 dark:text-green-400">
                  <span>{t('discount')}</span>
                  <span className="font-semibold">
                    -{formatUsd(cart.discountCents)}
                  </span>
                </div>
              ) : null}

              <div className="flex items-center justify-between text-muted">
                <span>{t('shipping')}</span>
                <span className="font-semibold text-foreground">
                  {cart.shippingEstimateCents === 0
                    ? t('free')
                    : formatUsd(cart.shippingEstimateCents)}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm font-black text-foreground pt-2 border-t border-border">
                <span>{t('total')}</span>
                <div className="text-right">
                  <span data-testid="drawer-total-usd">{formatUsd(cart.totalCents)}</span>
                  <span className="block text-[11px] font-normal text-muted" data-testid="drawer-total-khr">
                    {formatKhr(cart.totalCents)}
                  </span>
                </div>
              </div>
            </div>

            {/* Actions */}
            <div className="grid grid-cols-2 gap-2 pt-1">
              <Link
                href="/cart"
                onClick={close}
                data-testid="drawer-view-cart-btn"
                className="w-full h-10 flex items-center justify-center rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground transition-colors"
              >
                {t('viewCart')}
              </Link>
              <Link
                href="/checkout"
                onClick={close}
                data-testid="drawer-checkout-btn"
                className="w-full h-10 flex items-center justify-center gap-1.5 rounded-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-bold shadow-xs transition-colors"
              >
                <span>{t('checkout')}</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        )}
      </aside>
    </div>
  );
}
