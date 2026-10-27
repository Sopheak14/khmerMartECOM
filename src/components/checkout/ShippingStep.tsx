'use client';

import React from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { ShippingMethodDTO, AddressDTO } from '@/types/api';
import { Truck, Check, ArrowRight, ArrowLeft, Clock, AlertTriangle } from 'lucide-react';
import { formatUsd } from '@/lib/money';

interface ShippingStepProps {
  selectedAddress: AddressDTO | null;
  selectedShippingCode: string;
  cartSubtotalCents: number;
  onSelectShippingCode: (code: string) => void;
  onNext: () => void;
  onBack: () => void;
}

export function ShippingStep({
  selectedAddress,
  selectedShippingCode,
  cartSubtotalCents,
  onSelectShippingCode,
  onNext,
  onBack,
}: ShippingStepProps) {
  const t = useTranslations('checkout');
  const locale = useLocale();

  const { data: methods = [], isLoading } = useQuery({
    queryKey: queryKeys.shipping.methods,
    queryFn: async () => {
      const res = await apiClient<ShippingMethodDTO[]>('/shipping/methods');
      return res.data || [];
    },
  });

  const isPhnomPenh = Boolean(
    selectedAddress &&
      (selectedAddress.province.toLowerCase().includes('phnom penh') ||
        selectedAddress.province.includes('ភ្នំពេញ'))
  );

  // Auto-select STANDARD if current selected is invalid or none selected
  React.useEffect(() => {
    if (!selectedShippingCode && methods.length > 0) {
      onSelectShippingCode('STANDARD');
    }
  }, [methods, selectedShippingCode, onSelectShippingCode]);

  if (isLoading) {
    return (
      <div className="space-y-4 animate-pulse">
        <div className="h-20 bg-muted/15 rounded-card" />
        <div className="h-20 bg-muted/15 rounded-card" />
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="step-shipping">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-foreground">{t('selectShippingMethod')}</h2>
        {selectedAddress && (
          <span className="text-xs text-muted">
            {t('deliveringTo')}: <strong className="text-foreground">{selectedAddress.province}</strong>
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {methods.map((method) => {
          const isSelected = selectedShippingCode === method.code;
          const isSameDay = method.code === 'SAME_DAY';
          const isSameDayDisabled = isSameDay && !isPhnomPenh;
          const isFreeStandard =
            method.code === 'STANDARD' && cartSubtotalCents >= (method.freeOverCents || 5000);
          const feeDisplay = isFreeStandard ? t('free') : formatUsd(method.feeCents);

          const name = locale === 'km' && method.nameKm ? method.nameKm : method.name;

          return (
            <div
              key={method.code}
              data-testid={`shipping-method-${method.code}`}
              onClick={() => {
                if (!isSameDayDisabled) {
                  onSelectShippingCode(method.code);
                }
              }}
              className={`p-4 rounded-card border transition-all flex flex-col justify-between ${
                isSameDayDisabled
                  ? 'border-border/50 bg-muted/5 opacity-60 cursor-not-allowed'
                  : isSelected
                  ? 'border-primary bg-primary/5 ring-1 ring-primary shadow-xs cursor-pointer'
                  : 'border-border bg-surface hover:border-primary/50 cursor-pointer'
              }`}
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Truck
                      className={`w-5 h-5 ${
                        isSelected ? 'text-primary' : 'text-muted'
                      }`}
                    />
                    <h3 className="font-bold text-sm text-foreground">{name}</h3>
                  </div>
                  {isSelected && (
                    <div className="w-5 h-5 rounded-full bg-primary text-primary-foreground flex items-center justify-center shrink-0">
                      <Check className="w-3.5 h-3.5" />
                    </div>
                  )}
                </div>

                <div className="space-y-1 text-xs">
                  <div className="flex items-center gap-1.5 text-muted">
                    <Clock className="w-3.5 h-3.5 text-muted/70" />
                    <span>
                      {method.etaDaysMin === 0
                        ? t('etaSameDay')
                        : t('etaDays', { min: method.etaDaysMin, max: method.etaDaysMax })}
                    </span>
                  </div>

                  {method.code === 'STANDARD' && (
                    <p className="text-[11px] text-muted italic">
                      {t('freeOver50Notice')}
                    </p>
                  )}
                </div>

                {/* Same-day disabled reason for non-Phnom Penh (e.g. Siem Reap) */}
                {isSameDayDisabled && (
                  <div
                    data-testid="sameday-disabled-reason"
                    className="p-2 rounded bg-amber-500/10 border border-amber-500/20 text-amber-800 dark:text-amber-400 text-[11px] flex items-start gap-1.5"
                  >
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                    <span>
                      {locale === 'km'
                        ? 'សេវាដឹកជញ្ជូនក្នុងថ្ងៃតែមួយមានសម្រាប់តែរាជធានីភ្នំពេញប៉ុណ្ណោះ'
                        : 'Same-day delivery is only available in Phnom Penh'}
                    </span>
                  </div>
                )}
              </div>

              <div className="pt-3 border-t border-border/60 mt-3 flex items-center justify-between">
                <span className="text-xs text-muted">{t('deliveryFee')}</span>
                <span
                  data-testid={`fee-${method.code}`}
                  className={`text-sm font-black ${
                    isFreeStandard ? 'text-green-800 dark:text-green-400' : 'text-foreground'
                  }`}
                >
                  {feeDisplay}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      {/* Navigation CTA */}
      <div className="pt-4 border-t border-border flex items-center justify-between">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-btn border border-border bg-surface text-foreground font-semibold text-xs hover:bg-muted/10 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>{t('back')}</span>
        </button>

        <button
          type="button"
          data-testid="next-to-payment-btn"
          disabled={!selectedShippingCode}
          onClick={onNext}
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-sm shadow-xs hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <span>{t('continueToPayment')}</span>
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
