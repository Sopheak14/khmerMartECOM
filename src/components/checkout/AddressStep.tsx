'use client';

import React, { useState } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { AddressDTO, ProvinceDTO } from '@/types/api';
import { Plus, Check, MapPin, ArrowRight } from 'lucide-react';
import { AddAddressDialog } from './AddAddressDialog';

interface AddressStepProps {
  selectedAddressId: number | null;
  onSelectAddress: (addressId: number) => void;
  onNext: () => void;
}

export function AddressStep({ selectedAddressId, onSelectAddress, onNext }: AddressStepProps) {
  const t = useTranslations('checkout');
  const addrT = useTranslations('address');
  const locale = useLocale();
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);

  // Fetch addresses
  const { data: addresses = [], isLoading } = useQuery({
    queryKey: ['users', 'me', 'addresses'],
    queryFn: async () => {
      const res = await apiClient<AddressDTO[]>('/users/me/addresses');
      return res.data || [];
    },
  });

  // Fetch provinces for mapping
  const { data: provinces = [] } = useQuery({
    queryKey: queryKeys.shipping.provinces,
    queryFn: async () => {
      const res = await apiClient<ProvinceDTO[]>('/shipping/provinces');
      return res.data || [];
    },
  });

  const provinceMap = React.useMemo(
    () => new Map(provinces.map((p) => [p.name, p.nameKm])),
    [provinces]
  );

  // Auto-select first or default address if none selected
  React.useEffect(() => {
    if (!selectedAddressId && addresses.length > 0) {
      const defaultAddr = addresses.find((a) => a.isDefault) || addresses[0];
      onSelectAddress(defaultAddr.id);
    }
  }, [addresses, selectedAddressId, onSelectAddress]);

  if (isLoading) {
    return (
      <div className="space-y-4 animate-pulse">
        <div className="h-24 bg-muted/15 rounded-card" />
        <div className="h-24 bg-muted/15 rounded-card" />
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="step-address">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h2 className="text-lg font-bold text-foreground">{t('selectDeliveryAddress')}</h2>
        <button
          type="button"
          data-testid="add-new-address-btn"
          onClick={() => setIsAddDialogOpen(true)}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-primary hover:underline"
        >
          <Plus className="w-4 h-4" />
          <span>{addrT('addNewAddress')}</span>
        </button>
      </div>

      {addresses.length === 0 ? (
        <div className="p-8 text-center bg-muted/10 border border-dashed border-border rounded-card space-y-3">
          <MapPin className="w-8 h-8 mx-auto text-muted" />
          <p className="text-sm text-muted">{t('noAddressYet')}</p>
          <button
            type="button"
            data-testid="add-first-address-btn"
            onClick={() => setIsAddDialogOpen(true)}
            className="px-4 py-2 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs hover:bg-primary/90 transition-colors"
          >
            {addrT('addNewAddress')}
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {addresses.map((addr) => {
            const isSelected = selectedAddressId === addr.id;
            const provinceName =
              locale === 'km' && provinceMap.get(addr.province)
                ? provinceMap.get(addr.province)
                : addr.province;

            return (
              <div
                key={addr.id}
                data-testid={`address-card-${addr.id}`}
                onClick={() => onSelectAddress(addr.id)}
                className={`p-4 rounded-card border transition-all cursor-pointer flex flex-col justify-between ${
                  isSelected
                    ? 'border-primary bg-primary/5 ring-1 ring-primary shadow-xs'
                    : 'border-border bg-surface hover:border-primary/50'
                }`}
              >
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm text-foreground">
                        {addr.recipientName}
                      </span>
                      {addr.label && (
                        <span className="text-[10px] uppercase font-bold px-2 py-0.5 rounded-full bg-muted/15 text-muted">
                          {addr.label}
                        </span>
                      )}
                    </div>
                    {isSelected && (
                      <div className="w-5 h-5 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
                        <Check className="w-3.5 h-3.5" />
                      </div>
                    )}
                  </div>

                  <div className="text-xs text-muted space-y-0.5">
                    <p className="font-medium text-foreground">{addr.phone}</p>
                    <p>
                      {addr.line1}
                      {addr.line2 ? `, ${addr.line2}` : ''}
                    </p>
                    <p>
                      {addr.district}, {provinceName} {addr.postalCode || ''}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Continue CTA */}
      <div className="pt-4 border-t border-border flex justify-end">
        <button
          type="button"
          data-testid="next-to-shipping-btn"
          disabled={!selectedAddressId}
          onClick={onNext}
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-sm shadow-xs hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <span>{t('continueToShipping')}</span>
          <ArrowRight className="w-4 h-4" />
        </button>
      </div>

      {/* Add Address Modal Dialog */}
      <AddAddressDialog
        isOpen={isAddDialogOpen}
        onClose={() => setIsAddDialogOpen(false)}
        onAddressCreated={(newAddr) => {
          onSelectAddress(newAddr.id);
        }}
      />
    </div>
  );
}
