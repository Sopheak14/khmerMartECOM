'use client';

import React, { useState } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { AddressDTO, ProvinceDTO } from '@/types/api';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { X, MapPin } from 'lucide-react';
import { toast } from 'sonner';

interface AddAddressDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onAddressCreated: (address: AddressDTO) => void;
}

export function AddAddressDialog({ isOpen, onClose, onAddressCreated }: AddAddressDialogProps) {
  const t = useTranslations('address');
  const errT = useTranslations('errors');
  const locale = useLocale();
  const queryClient = useQueryClient();

  // Fetch 25 Cambodian provinces
  const { data: provinces = [] } = useQuery({
    queryKey: queryKeys.shipping.provinces,
    queryFn: async () => {
      const res = await apiClient<ProvinceDTO[]>('/shipping/provinces');
      return res.data || [];
    },
  });

  const [label, setLabel] = useState('Home');
  const [recipientName, setRecipientName] = useState('');
  const [phone, setPhone] = useState('+855');
  const [line1, setLine1] = useState('');
  const [line2, setLine2] = useState('');
  const [district, setDistrict] = useState('');
  const [province, setProvince] = useState(provinces[0]?.name || 'Phnom Penh');
  const [postalCode, setPostalCode] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Sync initial province
  React.useEffect(() => {
    if (provinces.length > 0 && !province) {
      setProvince(provinces[0].name);
    }
  }, [provinces, province]);

  const createMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient<AddressDTO>('/users/me/addresses', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: (newAddress) => {
      queryClient.invalidateQueries({ queryKey: ['users', 'me', 'addresses'] });
      toast.success(t('addressCreatedSuccess'));
      onAddressCreated(newAddress);
      onClose();
    },
    onError: (err: any) => {
      if (err instanceof ApiError && err.details) {
        setFieldErrors(err.details);
      } else {
        setGeneralError(err.message || 'Failed to save address');
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    createMutation.mutate({
      label: label.trim() || 'Home',
      recipientName: recipientName.trim(),
      phone: phone.trim(),
      line1: line1.trim(),
      line2: line2.trim() || undefined,
      district: district.trim(),
      province,
      postalCode: postalCode.trim() || undefined,
      isDefault: false,
    });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 animate-in fade-in">
      {/* Backdrop overlay */}
      <div className="fixed inset-0 bg-black/50" onClick={onClose} aria-hidden="true" />

      {/* Modal Dialog Panel - Opaque white/surface with higher z-index */}
      <div
        data-testid="add-address-dialog"
        role="dialog"
        aria-modal="true"
        className="w-full max-w-lg bg-white dark:bg-card border border-border rounded-card shadow-2xl p-6 relative z-10 max-h-[90vh] overflow-y-auto space-y-4 opacity-100"
      >
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <div className="flex items-center gap-2">
            <MapPin className="w-5 h-5 text-primary" />
            <h3 className="font-bold text-base text-foreground">{t('addNewAddress')}</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="p-1 rounded-full text-muted hover:text-foreground hover:bg-muted/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {generalError && (
          <div className="p-3 text-xs bg-destructive/10 border border-destructive/20 text-destructive rounded">
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label={t('recipientName')}
              data-testid="address-recipient-name-input"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              error={fieldErrors.recipientName}
              required
            />
            <Input
              label={t('phone')}
              data-testid="address-phone-input"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+85512345678"
              error={fieldErrors.phone}
              required
            />
          </div>

          <Input
            label={t('line1')}
            data-testid="address-line1-input"
            value={line1}
            onChange={(e) => setLine1(e.target.value)}
            placeholder={t('line1Placeholder')}
            error={fieldErrors.line1}
            required
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label={t('district')}
              data-testid="address-district-input"
              value={district}
              onChange={(e) => setDistrict(e.target.value)}
              placeholder={t('districtPlaceholder')}
              error={fieldErrors.district}
              required
            />

            {/* Province Dropdown: 25 Cambodian Provinces */}
            <div className="space-y-1">
              <label className="text-xs font-semibold text-foreground block">
                {t('province')} *
              </label>
              <select
                data-testid="address-province-select"
                value={province}
                onChange={(e) => setProvince(e.target.value)}
                className="w-full h-10 px-3 rounded-btn border border-border bg-background text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
                required
              >
                {provinces.map((prov) => (
                  <option key={prov.code} value={prov.name}>
                    {locale === 'km' && prov.nameKm ? prov.nameKm : prov.name}
                  </option>
                ))}
              </select>
              {fieldErrors.province && (
                <p className="text-xs text-destructive">{fieldErrors.province}</p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              label={t('postalCode')}
              data-testid="address-postal-input"
              value={postalCode}
              onChange={(e) => setPostalCode(e.target.value)}
              placeholder="12000"
              error={fieldErrors.postalCode}
            />
            <Input
              label={t('label')}
              data-testid="address-label-input"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="Home, Office..."
              error={fieldErrors.label}
            />
          </div>

          <div className="pt-3 border-t border-border flex items-center justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              {t('cancel')}
            </Button>
            <Button
              type="submit"
              data-testid="save-address-btn"
              isLoading={createMutation.isPending}
            >
              {t('saveAddress')}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
