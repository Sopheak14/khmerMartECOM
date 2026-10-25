'use client';

import React from 'react';
import { useParams } from 'next/navigation';
import { KhqrCard } from '@/components/payment/KhqrCard';

export default function CheckoutPayPage() {
  const params = useParams();
  const orderNumber = params?.orderNumber as string;

  return (
    <div className="container mx-auto px-4 py-8 sm:py-12" data-testid="checkout-pay-page">
      <KhqrCard orderNumber={orderNumber} />
    </div>
  );
}
