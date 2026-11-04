'use client';

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocale, useTranslations } from 'next-intl';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { AddressDTO, ProvinceDTO } from '@/types/api';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import { AddressLocationField, useAddressAutofill } from '@/components/map/AddressLocationField';
import type { LatLng } from '@/components/map/LocationPicker';
import { Plus, Edit2, Trash2, Check, MapPin, Loader2 } from 'lucide-react';

export default function AddressesPage() {
  const locale = useLocale();
  const t = useTranslations('address');
  const errT = useTranslations('errors');
  const queryClient = useQueryClient();

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState<AddressDTO | null>(null);

  // Form states
  const [label, setLabel] = useState('');
  const [recipientName, setRecipientName] = useState('');
  const [phone, setPhone] = useState('');
  const [line1, setLine1] = useState('');
  const [line2, setLine2] = useState('');
  const [district, setDistrict] = useState('');
  const [province, setProvince] = useState('');
  const [coords, setCoords] = useState<LatLng | null>(null);
  const [isDefault, setIsDefault] = useState(false);

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Fetch addresses
  const { data: addresses = [], isLoading: isAddressesLoading } = useQuery({
    queryKey: ['users', 'me', 'addresses'],
    queryFn: async () => {
      const res = await apiClient<AddressDTO[]>('/users/me/addresses');
      return res.data || [];
    },
  });

  // Fetch 25 Cambodian provinces
  const { data: provinces = [] } = useQuery({
    queryKey: queryKeys.shipping.provinces,
    queryFn: async () => {
      const res = await apiClient<ProvinceDTO[]>('/shipping/provinces');
      return res.data || [];
    },
  });

  const provinceMap = React.useMemo(() => new Map(provinces.map((p) => [p.name, p.nameKm])), [provinces]);

  const resetForm = () => {
    setEditingAddress(null);
    setLabel('');
    setRecipientName('');
    setPhone('');
    setLine1('');
    setLine2('');
    setDistrict('');
    setProvince(provinces[0]?.name || 'Phnom Penh');
    setCoords(null);
    setIsDefault(false);
    setFieldErrors({});
    setGeneralError(null);
    setIsFormOpen(false);
  };

  const openCreateForm = () => {
    resetForm();
    setProvince(provinces[0]?.name || 'Phnom Penh');
    setIsFormOpen(true);
  };

  const openEditForm = (addr: AddressDTO) => {
    setEditingAddress(addr);
    setLabel(addr.label || '');
    setRecipientName(addr.recipientName);
    setPhone(addr.phone);
    setLine1(addr.line1);
    setLine2(addr.line2 || '');
    setDistrict(addr.district);
    setProvince(addr.province);
    setCoords(
      addr.latitude != null && addr.longitude != null
        ? { lat: addr.latitude, lng: addr.longitude }
        : null
    );
    setIsDefault(addr.isDefault);
    setFieldErrors({});
    setGeneralError(null);
    setIsFormOpen(true);
  };

  // Create mutation
  const createMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient<AddressDTO>('/users/me/addresses', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'me', 'addresses'] });
      toast.success(t('addressCreated'));
      resetForm();
    },
    onError: (err: any) => {
      if (err instanceof ApiError && err.details) {
        setFieldErrors(err.details);
      } else {
        setGeneralError(err?.message || errT('VALIDATION_ERROR'));
      }
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: async ({ id, payload }: { id: number; payload: any }) => {
      const res = await apiClient<AddressDTO>(`/users/me/addresses/${id}`, {
        method: 'PATCH',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'me', 'addresses'] });
      toast.success(t('addressUpdated'));
      resetForm();
    },
    onError: (err: any) => {
      if (err instanceof ApiError && err.details) {
        setFieldErrors(err.details);
      } else {
        setGeneralError(err?.message || errT('VALIDATION_ERROR'));
      }
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient(`/users/me/addresses/${id}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'me', 'addresses'] });
      toast.success(t('addressDeleted'));
    },
  });

  // Set default mutation
  const setDefaultMutation = useMutation({
    mutationFn: async (addr: AddressDTO) => {
      await apiClient<AddressDTO>(`/users/me/addresses/${addr.id}`, {
        method: 'PATCH',
        body: {
          label: addr.label || 'Home',
          recipientName: addr.recipientName,
          phone: addr.phone,
          line1: addr.line1,
          line2: addr.line2,
          district: addr.district,
          province: addr.province,
          postalCode: addr.postalCode,
          ...(addr.latitude != null && addr.longitude != null
            ? { latitude: addr.latitude, longitude: addr.longitude }
            : {}),
          isDefault: true,
        },
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users', 'me', 'addresses'] });
      toast.success(t('addressUpdated'));
    },
  });

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    const payload = {
      label: label.trim(),
      recipientName: recipientName.trim(),
      phone: phone.trim(),
      line1: line1.trim(),
      line2: line2.trim() || undefined,
      district: district.trim(),
      province: province.trim(),
      ...(coords ? { latitude: coords.lat, longitude: coords.lng } : {}),
      isDefault,
    };

    if (editingAddress) {
      updateMutation.mutate({ id: editingAddress.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const onSuggest = useAddressAutofill(line1, setLine1, district, setDistrict);

  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-base sm:text-lg font-bold text-foreground">
            {t('title')}
          </h2>
          <p className="text-xs text-muted mt-0.5">
            {t('subtitle')}
          </p>
        </div>

        {!isFormOpen && (
          <Button
            onClick={openCreateForm}
            variant="primary"
            size="sm"
            className="flex items-center gap-1.5 self-start sm:self-auto font-bold shadow-sm"
          >
            <Plus className="w-4 h-4" />
            <span>{t('addNew')}</span>
          </Button>
        )}
      </div>

      {/* Address Form (Modal / Inline card) */}
      {isFormOpen && (
        <div className="bg-surface border border-primary/30 rounded-card p-6 shadow-md space-y-4 animate-in fade-in-50">
          <div className="flex items-center justify-between border-b border-border/80 pb-3">
            <h3 className="text-sm font-bold text-foreground">
              {editingAddress ? t('edit') : t('addNew')}
            </h3>
            <button
              onClick={resetForm}
              className="text-xs text-muted hover:text-foreground font-semibold"
            >
              {t('cancel')}
            </button>
          </div>

          {generalError && (
            <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
              {generalError}
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="addr-label"
                type="text"
                label={t('label')}
                placeholder={t('labelPlaceholder')}
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                error={fieldErrors.label}
                required
              />

              <Input
                id="addr-recipient"
                type="text"
                label={t('recipient')}
                value={recipientName}
                onChange={(e) => setRecipientName(e.target.value)}
                error={fieldErrors.recipientName}
                required
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="addr-phone"
                type="tel"
                label={t('phone')}
                placeholder="+85512345678"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                error={fieldErrors.phone}
                required
              />

              {/* Province Dropdown loaded from GET /api/shipping/provinces */}
              <div className="space-y-1">
                <label htmlFor="addr-province" className="block text-sm font-medium text-foreground">
                  {t('province')}
                </label>
                <select
                  id="addr-province"
                  value={province}
                  onChange={(e) => setProvince(e.target.value)}
                  className="flex h-10 w-full rounded-btn border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary shadow-sm"
                  required
                >
                  {provinces.map((prov) => {
                    const provLabel = locale === 'km' && prov.nameKm ? prov.nameKm : prov.name;
                    return (
                      <option key={prov.code} value={prov.name}>
                        {provLabel}
                      </option>
                    );
                  })}
                </select>
                {fieldErrors.province && (
                  <p className="text-xs text-destructive">{fieldErrors.province}</p>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="addr-district"
                type="text"
                label={t('district')}
                placeholder={t('districtPlaceholder')}
                value={district}
                onChange={(e) => setDistrict(e.target.value)}
                error={fieldErrors.district}
                required
              />

            </div>

            <Input
              id="addr-line1"
              type="text"
              label={t('addressLine1')}
              placeholder={t('addressLine1Placeholder')}
              value={line1}
              onChange={(e) => setLine1(e.target.value)}
              error={fieldErrors.line1}
              required
            />

            <Input
              id="addr-line2"
              type="text"
              label={t('addressLine2')}
              value={line2}
              onChange={(e) => setLine2(e.target.value)}
              error={fieldErrors.line2}
            />

            <AddressLocationField value={coords} onChange={setCoords} onSuggest={onSuggest} />
            {(fieldErrors.latitude || fieldErrors.longitude) && (
              <p className="text-xs text-destructive">{fieldErrors.latitude || fieldErrors.longitude}</p>
            )}

            <div className="flex items-center gap-2 pt-1">
              <input
                id="addr-is-default"
                type="checkbox"
                checked={isDefault}
                onChange={(e) => setIsDefault(e.target.checked)}
                className="w-4 h-4 rounded text-primary border-input focus:ring-primary"
              />
              <label htmlFor="addr-is-default" className="text-xs font-semibold text-foreground cursor-pointer">
                {t('setAsDefault')}
              </label>
            </div>

            <div className="flex items-center gap-3 pt-2">
              <Button
                type="submit"
                variant="primary"
                isLoading={isSaving}
                className="font-bold shadow-sm"
              >
                {t('save')}
              </Button>
              <Button
                type="button"
                variant="ghost"
                onClick={resetForm}
              >
                {t('cancel')}
              </Button>
            </div>
          </form>
        </div>
      )}

      {/* Address Cards List */}
      {isAddressesLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-pulse">
          <div className="h-40 bg-surface border border-border rounded-card" />
          <div className="h-40 bg-surface border border-border rounded-card" />
        </div>
      ) : addresses.length === 0 ? (
        <div className="p-8 text-center bg-surface border border-border rounded-card space-y-3">
          <MapPin className="w-8 h-8 text-muted mx-auto" />
          <p className="text-xs sm:text-sm text-muted">
            {t('noAddresses')}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {addresses.map((addr) => (
            <div
              key={addr.id}
              data-testid="address-card"
              className={`p-5 rounded-card bg-surface border transition-all space-y-3 flex flex-col justify-between ${
                addr.isDefault ? 'border-primary shadow-sm ring-1 ring-primary/20' : 'border-border'
              }`}
            >
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm text-foreground">
                      {addr.label || 'Address'}
                    </span>
                    {addr.isDefault && (
                      <Badge
                        data-testid="default-badge"
                        variant="default"
                        className="text-[10px] bg-primary text-primary-foreground font-black"
                      >
                        {t('defaultBadge')}
                      </Badge>
                    )}
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      data-testid="edit-address-button"
                      onClick={() => openEditForm(addr)}
                      aria-label="Edit address"
                      className="p-1.5 rounded hover:bg-muted/10 text-muted hover:text-foreground transition-colors"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      data-testid="delete-address-button"
                      onClick={() => {
                        if (confirm(t('deleteConfirm'))) {
                          deleteMutation.mutate(addr.id);
                        }
                      }}
                      aria-label="Delete address"
                      className="p-1.5 rounded hover:bg-destructive/10 text-muted hover:text-destructive transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                <div className="text-xs text-muted space-y-0.5">
                  <p className="font-semibold text-foreground">{addr.recipientName}</p>
                  <p>{addr.phone}</p>
                  <p>{addr.line1}{addr.line2 ? `, ${addr.line2}` : ''}</p>
                  <p>
                    {addr.district}, {locale === 'km' && provinceMap.get(addr.province) ? provinceMap.get(addr.province) : addr.province} {addr.postalCode || ''}
                  </p>
                </div>
              </div>

              {!addr.isDefault && (
                <div className="pt-2 border-t border-border/60">
                  <button
                    data-testid="set-default-button"
                    onClick={() => setDefaultMutation.mutate(addr)}
                    className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
                  >
                    <Check className="w-3.5 h-3.5" />
                    <span>{t('setAsDefault')}</span>
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
