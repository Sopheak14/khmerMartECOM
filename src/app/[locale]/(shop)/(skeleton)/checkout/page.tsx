'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { AddressDTO, CartDTO, CheckoutPreviewDTO, PaymentMethod } from '@/types/api';
import { CheckoutStepper } from '@/components/checkout/CheckoutStepper';
import { AddressStep } from '@/components/checkout/AddressStep';
import { ShippingStep } from '@/components/checkout/ShippingStep';
import { PaymentStep } from '@/components/checkout/PaymentStep';
import { ReviewStep } from '@/components/checkout/ReviewStep';
import { formatUsd } from '@/lib/money';
import { ShieldCheck, ArrowRight, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

export default function CheckoutPage() {
  const t = useTranslations('checkout');
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, isInitialized } = useAuthStore();

  // Load persisted checkout state from localStorage after mount
  const [mounted, setMounted] = useState(false);
  const [currentStep, setCurrentStep] = useState<number>(1);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [selectedShippingCode, setSelectedShippingCode] = useState<string>('STANDARD');
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<PaymentMethod>('KHQR');
  const [notes, setNotes] = useState<string>('');

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const savedStep = localStorage.getItem('km_checkout_step');
      if (savedStep) setCurrentStep(Number(savedStep));

      const savedAddressId = localStorage.getItem('km_checkout_address_id');
      if (savedAddressId) setSelectedAddressId(Number(savedAddressId));

      const savedShippingCode = localStorage.getItem('km_checkout_shipping_code');
      if (savedShippingCode) setSelectedShippingCode(savedShippingCode);

      const savedPaymentMethod = localStorage.getItem('km_checkout_payment_method');
      if (savedPaymentMethod) setSelectedPaymentMethod(savedPaymentMethod as PaymentMethod);

      const savedNotes = localStorage.getItem('km_checkout_notes');
      if (savedNotes) setNotes(savedNotes);

      setMounted(true);
    }
  }, []);

  // Save changes to localStorage so state survives page refresh
  useEffect(() => {
    if (mounted && typeof window !== 'undefined') {
      localStorage.setItem('km_checkout_step', String(currentStep));
      if (selectedAddressId) {
        localStorage.setItem('km_checkout_address_id', String(selectedAddressId));
      }
      localStorage.setItem('km_checkout_shipping_code', selectedShippingCode);
      localStorage.setItem('km_checkout_payment_method', selectedPaymentMethod);
      localStorage.setItem('km_checkout_notes', notes);
    }
  }, [mounted, currentStep, selectedAddressId, selectedShippingCode, selectedPaymentMethod, notes]);

  // Auth guard: redirect to login if not authenticated
  useEffect(() => {
    if (isInitialized && !user) {
      router.push('/login?next=/checkout');
    }
  }, [isInitialized, user, router]);

  // Fetch cart
  const { data: cart, isLoading: isCartLoading } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
    enabled: isInitialized && Boolean(user),
  });

  // Redirect to /cart if cart is empty on entry (not after placing an order)
  useEffect(() => {
    if (cart && cart.items.length === 0 && currentStep < 4) {
      router.push('/cart');
    }
  }, [cart, currentStep, router]);

  // Fetch user addresses
  const { data: addresses = [] } = useQuery({
    queryKey: ['users', 'me', 'addresses'],
    queryFn: async () => {
      const res = await apiClient<AddressDTO[]>('/users/me/addresses');
      return res.data || [];
    },
    enabled: isInitialized && Boolean(user),
  });

  const selectedAddress =
    addresses.find((a) => a.id === selectedAddressId) || addresses[0] || null;

  // Checkout Preview Query
  const {
    data: preview,
    isLoading: isPreviewLoading,
    error: previewError,
  } = useQuery({
    queryKey: [
      'checkout',
      'preview',
      selectedAddressId,
      selectedShippingCode,
      selectedPaymentMethod,
    ],
    queryFn: async () => {
      if (!selectedAddressId) return null;
      try {
        const res = await apiClient<CheckoutPreviewDTO>('/checkout/preview', {
          method: 'POST',
          body: {
            addressId: selectedAddressId,
            shippingMethodCode: selectedShippingCode,
            paymentMethod: selectedPaymentMethod,
          },
        });
        return res.data;
      } catch (err: any) {
        if (err instanceof ApiError) {
          if (err.code === 'PRICE_CHANGED' || err.code === 'OUT_OF_STOCK') {
            toast.error(err.message);
            router.push(`/cart?error=${err.code}`);
            return null;
          }
        }
        throw err;
      }
    },
    enabled:
      isInitialized &&
      Boolean(user) &&
      Boolean(selectedAddressId) &&
      (currentStep >= 3 || currentStep === 2),
  });

  if (!mounted || !isInitialized || isCartLoading) {
    return (
      <div className="container mx-auto px-4 py-16 flex flex-col items-center justify-center space-y-4">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
        <p className="text-xs text-muted">{t('loadingCheckout')}</p>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-5xl" data-testid="checkout-page">
      <div className="mb-6">
        <h1 className="text-2xl sm:text-3xl font-black text-foreground tracking-tight text-center sm:text-left">
          {t('checkout')}
        </h1>
        <CheckoutStepper
          currentStep={currentStep}
          onStepClick={(step) => setCurrentStep(step)}
        />
      </div>

      <div className="bg-surface rounded-card border border-border p-6 shadow-xs">
        {currentStep === 1 && (
          <AddressStep
            selectedAddressId={selectedAddressId}
            onSelectAddress={(id) => setSelectedAddressId(id)}
            onNext={() => setCurrentStep(2)}
          />
        )}

        {currentStep === 2 && (
          <ShippingStep
            selectedAddress={selectedAddress}
            selectedShippingCode={selectedShippingCode}
            cartSubtotalCents={cart?.subtotalCents || 0}
            onSelectShippingCode={(code) => setSelectedShippingCode(code)}
            onNext={() => setCurrentStep(3)}
            onBack={() => setCurrentStep(1)}
          />
        )}

        {currentStep === 3 && (
          <PaymentStep
            selectedPaymentMethod={selectedPaymentMethod}
            availablePaymentMethods={preview?.availablePaymentMethods || []}
            onSelectPaymentMethod={(method) => setSelectedPaymentMethod(method)}
            onNext={() => setCurrentStep(4)}
            onBack={() => setCurrentStep(2)}
          />
        )}

        {currentStep === 4 && (
          <>
            {isPreviewLoading ? (
              <div className="py-12 flex flex-col items-center justify-center space-y-3">
                <Loader2 className="w-6 h-6 animate-spin text-primary" />
                <p className="text-xs text-muted">{t('preparingOrderPreview')}</p>
              </div>
            ) : preview ? (
              <ReviewStep
                preview={preview}
                selectedAddressId={selectedAddressId!}
                selectedShippingCode={selectedShippingCode}
                selectedPaymentMethod={selectedPaymentMethod}
                notes={notes}
                onNotesChange={setNotes}
                onBack={() => setCurrentStep(3)}
              />
            ) : (
              <div className="p-8 text-center space-y-3">
                <p className="text-xs text-destructive">
                  {t('previewErrorFallback')}
                </p>
                <button
                  type="button"
                  onClick={() => setCurrentStep(1)}
                  className="px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold"
                >
                  {t('startOver')}
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* Mobile Sticky CTA Bar (390x844 layout) */}
      <div
        data-testid="checkout-mobile-sticky-bar"
        className="sm:hidden fixed bottom-14 left-0 right-0 z-40 bg-surface/95 backdrop-blur-md border-t border-border p-3 flex items-center justify-between shadow-lg"
      >
        <div>
          <span className="text-[10px] text-muted uppercase font-bold block">{t('total')}</span>
          <span className="text-base font-black text-primary">
            {formatUsd(preview?.totalCents ?? cart?.totalCents ?? 0)}
          </span>
        </div>

        {currentStep < 4 ? (
          <button
            type="button"
            data-testid="mobile-next-btn"
            disabled={currentStep === 1 && !selectedAddressId}
            onClick={() => setCurrentStep((prev) => Math.min(4, prev + 1))}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs disabled:opacity-50"
          >
            <span>{t('continue')}</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        ) : (
          <button
            type="button"
            data-testid="mobile-place-order-trigger"
            onClick={() => {
              const placeBtn = document.querySelector('[data-testid="place-order-btn"]') as HTMLButtonElement;
              if (placeBtn) placeBtn.click();
            }}
            className="inline-flex items-center gap-1.5 px-5 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs"
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>{t('placeOrder')}</span>
          </button>
        )}
      </div>
    </div>
  );
}
