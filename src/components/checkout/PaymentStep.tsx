'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { PaymentMethod } from '@/types/api';
import { QrCode, Banknote, Building2, CreditCard, Wallet, Check, ArrowRight, ArrowLeft } from 'lucide-react';

interface PaymentStepProps {
  selectedPaymentMethod: PaymentMethod;
  availablePaymentMethods: PaymentMethod[];
  onSelectPaymentMethod: (method: PaymentMethod) => void;
  onNext: () => void;
  onBack: () => void;
}

export function PaymentStep({
  selectedPaymentMethod,
  availablePaymentMethods,
  onSelectPaymentMethod,
  onNext,
  onBack,
}: PaymentStepProps) {
  const t = useTranslations('checkout');

  // Fallback list if availablePaymentMethods is empty
  const methods =
    availablePaymentMethods.length > 0
      ? availablePaymentMethods
      : [
          'KHQR' as PaymentMethod,
          'COD' as PaymentMethod,
          'BANK_TRANSFER' as PaymentMethod,
          'CARD' as PaymentMethod,
          'WALLET' as PaymentMethod,
        ];

  const getMethodDetails = (method: PaymentMethod) => {
    switch (method) {
      case 'KHQR':
        return {
          icon: QrCode,
          title: t('paymentKhqrTitle'),
          desc: t('paymentKhqrDesc'),
          recommended: true,
        };
      case 'COD':
        return {
          icon: Banknote,
          title: t('paymentCodTitle'),
          desc: t('paymentCodDesc'),
          recommended: false,
        };
      case 'BANK_TRANSFER':
        return {
          icon: Building2,
          title: t('paymentBankTitle'),
          desc: t('paymentBankDesc'),
          recommended: false,
        };
      case 'CARD':
        return {
          icon: CreditCard,
          title: t('paymentCardTitle'),
          desc: t('paymentCardDesc'),
          recommended: false,
        };
      case 'WALLET':
        return {
          icon: Wallet,
          title: t('paymentWalletTitle'),
          desc: t('paymentWalletDesc'),
          recommended: false,
        };
      default:
        return {
          icon: CreditCard,
          title: method,
          desc: '',
          recommended: false,
        };
    }
  };

  return (
    <div className="space-y-6" data-testid="step-payment">
      <h2 className="text-lg font-bold text-foreground">{t('selectPaymentMethod')}</h2>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {methods.map((method) => {
          const isSelected = selectedPaymentMethod === method;
          const { icon: Icon, title, desc, recommended } = getMethodDetails(method);

          return (
            <div
              key={method}
              data-testid={`payment-method-${method}`}
              onClick={() => onSelectPaymentMethod(method)}
              className={`p-4 rounded-card border transition-all cursor-pointer flex flex-col justify-between ${
                isSelected
                  ? 'border-primary bg-primary/5 ring-1 ring-primary shadow-xs'
                  : 'border-border bg-surface hover:border-primary/50'
              }`}
            >
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Icon className={`w-5 h-5 ${isSelected ? 'text-primary' : 'text-muted'}`} />
                    <h3 className="font-bold text-sm text-foreground">{title}</h3>
                  </div>

                  <div className="flex items-center gap-2">
                    {recommended && (
                      <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-accent text-accent-foreground">
                        {t('recommended')}
                      </span>
                    )}
                    {isSelected && (
                      <div className="w-5 h-5 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
                        <Check className="w-3.5 h-3.5" />
                      </div>
                    )}
                  </div>
                </div>

                {desc && <p className="text-xs text-muted leading-relaxed">{desc}</p>}
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
          data-testid="next-to-review-btn"
          disabled={!selectedPaymentMethod}
          onClick={onNext}
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-sm shadow-xs hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <span>{t('continueToReview')}</span>
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
