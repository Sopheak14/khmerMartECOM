'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { MapPin, Truck, CreditCard, CheckCircle2 } from 'lucide-react';

interface CheckoutStepperProps {
  currentStep: number; // 1, 2, 3, 4
  onStepClick?: (step: number) => void;
}

export function CheckoutStepper({ currentStep, onStepClick }: CheckoutStepperProps) {
  const t = useTranslations('checkout');

  const steps = [
    { number: 1, label: t('stepAddress'), icon: MapPin },
    { number: 2, label: t('stepShipping'), icon: Truck },
    { number: 3, label: t('stepPayment'), icon: CreditCard },
    { number: 4, label: t('stepReview'), icon: CheckCircle2 },
  ];

  return (
    <div className="w-full py-4" data-testid="checkout-stepper">
      <div className="flex items-center justify-between relative max-w-2xl mx-auto">
        {/* Connecting line */}
        <div className="absolute top-1/2 left-0 right-0 -translate-y-1/2 h-0.5 bg-border -z-0" />
        <div
          className="absolute top-1/2 left-0 -translate-y-1/2 h-0.5 bg-primary transition-all duration-300 -z-0"
          style={{ width: `${((currentStep - 1) / (steps.length - 1)) * 100}%` }}
        />

        {steps.map((step) => {
          const isCompleted = currentStep > step.number;
          const isCurrent = currentStep === step.number;
          const Icon = step.icon;

          return (
            <div
              key={step.number}
              onClick={() => {
                if (isCompleted && onStepClick) {
                  onStepClick(step.number);
                }
              }}
              className={`flex flex-col items-center gap-1.5 relative z-10 ${
                isCompleted ? 'cursor-pointer' : ''
              }`}
            >
              <div
                className={`w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold transition-all shadow-xs ${
                  isCurrent
                    ? 'bg-primary text-primary-foreground ring-4 ring-primary/20 scale-105'
                    : isCompleted
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-surface border-2 border-border text-muted'
                }`}
              >
                <Icon className="w-4 h-4" />
              </div>
              <span
                className={`text-[11px] font-bold tracking-tight whitespace-nowrap ${
                  isCurrent
                    ? 'text-primary'
                    : isCompleted
                    ? 'text-foreground'
                    : 'text-muted'
                }`}
              >
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
