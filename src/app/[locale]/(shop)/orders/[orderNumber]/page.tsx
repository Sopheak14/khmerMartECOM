'use client';

import React from 'react';
import Image from 'next/image';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useTranslations, useLocale } from 'next-intl';
import { Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { OrderDTO } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import { Package, MapPin, CreditCard, ArrowLeft, Loader2, CheckCircle2 } from 'lucide-react';

export default function OrderDetailPage() {
  const t = useTranslations('checkout');
  const common = useTranslations('common');
  const locale = useLocale();
  const params = useParams();
  const orderNumber = params?.orderNumber as string;

  const { data: order, isLoading } = useQuery({
    queryKey: ['orders', orderNumber],
    queryFn: async () => {
      const res = await apiClient<OrderDTO>(`/orders/${orderNumber}`);
      return res.data;
    },
    enabled: Boolean(orderNumber),
  });

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-16 max-w-2xl text-center space-y-4">
        <Loader2 className="w-8 h-8 mx-auto animate-spin text-primary" />
        <p className="text-xs text-muted">Loading order {orderNumber}...</p>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-3xl space-y-6" data-testid="order-detail-page">
      <div className="flex items-center justify-between">
        <Link
          href="/products"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>{t('continueShopping')}</span>
        </Link>
        <span className="text-xs font-mono font-bold bg-muted/15 px-2.5 py-1 rounded">
          {orderNumber}
        </span>
      </div>

      <div className="bg-surface border border-border rounded-card p-6 space-y-6 shadow-xs">
        <div className="flex items-center justify-between pb-4 border-b border-border flex-wrap gap-2">
          <div className="space-y-1">
            <h1 className="text-xl font-bold text-foreground">
              {t('orderNumber')}: {orderNumber}
            </h1>
            <p className="text-xs text-muted">
              {order?.status ? `Status: ${order.status}` : t('orderPlacedPendingShipment')}
            </p>
          </div>
          <span className="px-3 py-1 rounded-full text-xs font-bold uppercase bg-green-500/10 text-green-600 dark:text-green-400 border border-green-500/20">
            {order?.paymentStatus || 'PENDING'}
          </span>
        </div>

        {/* Order Items */}
        {order?.items && order.items.length > 0 && (
          <div className="space-y-3">
            <h3 className="font-bold text-xs uppercase tracking-wider text-muted">
              {t('itemsOrdered')} ({order.items.length})
            </h3>
            <div className="divide-y divide-border border border-border rounded-btn overflow-hidden">
              {order.items.map((item) => (
                <div key={item.id} className="p-3 flex items-center justify-between gap-3 text-xs">
                  <div className="flex items-center gap-3">
                    <div className="relative w-12 h-12 rounded bg-muted/10 overflow-hidden shrink-0">
                      {item.imageUrl ? (
                        <Image src={item.imageUrl} alt={item.name} fill className="object-cover" sizes="48px" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-[10px] text-muted">
                          Item
                        </div>
                      )}
                    </div>
                    <div>
                      <h4 className="font-bold text-foreground line-clamp-1">{item.name}</h4>
                      <p className="text-muted text-[11px]">
                        {formatUsd(item.unitPriceCents)} × {item.quantity}
                      </p>
                    </div>
                  </div>
                  <span className="font-bold text-foreground">
                    {formatUsd(item.lineTotalCents)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Total breakdown */}
        {order && (
          <div className="space-y-2 pt-3 border-t border-border text-xs">
            <div className="flex justify-between text-muted">
              <span>{t('subtotal')}</span>
              <span>{formatUsd(order.subtotalCents)}</span>
            </div>
            {order.discountCents > 0 && (
              <div className="flex justify-between text-green-600 dark:text-green-400 font-medium">
                <span>{t('discount')}</span>
                <span>-{formatUsd(order.discountCents)}</span>
              </div>
            )}
            <div className="flex justify-between text-muted">
              <span>{t('shippingFee')}</span>
              <span>{order.shippingCents === 0 ? t('free') : formatUsd(order.shippingCents)}</span>
            </div>
            <div className="pt-2 border-t border-border flex justify-between font-bold text-base text-foreground">
              <span>{t('total')}</span>
              <div className="text-right">
                <span className="text-primary">{formatUsd(order.totalCents)}</span>
                <span className="block text-[11px] font-normal text-muted">{formatKhr(order.totalCents)}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
