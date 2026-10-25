'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { AdminSettingsDTO, ShippingMethodDTO } from '@/types/api';
import { toCents } from '@/lib/money';
import {
  Settings,
  AlertTriangle,
  Loader2,
  CheckCircle2,
  RotateCcw,
  Truck,
  DollarSign,
  Store,
  ShieldAlert,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminSettingsPage() {
  const t = useTranslations('admin.settings');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const queryClient = useQueryClient();

  const [storeName, setStoreName] = useState('');
  const [khrRate, setKhrRate] = useState<number>(4100);
  const [khqrMode, setKhqrMode] = useState<'MOCK' | 'LIVE'>('MOCK');
  const [shippingMethods, setShippingMethods] = useState<ShippingMethodDTO[]>([]);
  const [shippingFeeInputs, setShippingFeeInputs] = useState<Record<string, { fee: string; freeOver: string }>>({});

  const [showLiveWarning, setShowLiveWarning] = useState(false);
  const [pendingKhqrMode, setPendingKhqrMode] = useState<'LIVE' | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-settings'],
    queryFn: async () => {
      const res = await apiClient.get<AdminSettingsDTO>('/admin/settings');
      return res.data;
    },
  });

  useEffect(() => {
    if (data) {
      setStoreName(data.storeName || '');
      setKhrRate(data.khrRate || 4100);
      setKhqrMode((data.khqrMode as 'MOCK' | 'LIVE') || 'MOCK');
      setShippingMethods(data.shippingMethods || []);

      const feeMap: Record<string, { fee: string; freeOver: string }> = {};
      (data.shippingMethods || []).forEach((m: ShippingMethodDTO) => {
        feeMap[m.code] = {
          fee: (m.feeCents / 100).toFixed(2),
          freeOver: m.freeOverCents ? (m.freeOverCents / 100).toFixed(2) : '',
        };
      });
      setShippingFeeInputs(feeMap);
    }
  }, [data]);

  const updateSettingsMutation = useMutation({
    mutationFn: async () => {
      const updatedMethods: ShippingMethodDTO[] = shippingMethods.map((m) => {
        const input = shippingFeeInputs[m.code] || {
          fee: (m.feeCents / 100).toFixed(2),
          freeOver: m.freeOverCents ? (m.freeOverCents / 100).toFixed(2) : '',
        };
        const feeCents = toCents(parseFloat(input.fee) || 0);
        const freeOverCents = input.freeOver ? toCents(parseFloat(input.freeOver) || 0) : undefined;
        return {
          ...m,
          feeCents,
          freeOverCents,
        };
      });

      return apiClient.put<AdminSettingsDTO>('/admin/settings', {
        storeName,
        khrRate: Number(khrRate),
        khqrMode,
        shippingMethods: updatedMethods,
      });
    },
    onSuccess: (res) => {
      queryClient.setQueryData(['admin-settings'], res.data);
      setSaveSuccess(true);
      setSaveError(null);
      setTimeout(() => setSaveSuccess(false), 4000);
    },
    onError: (err: any) => {
      setSaveError(err.message || 'Failed to save settings');
      setSaveSuccess(false);
    },
  });

  const handleKhqrModeClick = (mode: 'MOCK' | 'LIVE') => {
    if (mode === 'LIVE' && khqrMode !== 'LIVE') {
      setPendingKhqrMode('LIVE');
      setShowLiveWarning(true);
    } else {
      setKhqrMode(mode);
    }
  };

  const handleConfirmLive = () => {
    setKhqrMode('LIVE');
    setShowLiveWarning(false);
    setPendingKhqrMode(null);
  };

  const handleFeeInputChange = (code: string, field: 'fee' | 'freeOver', value: string) => {
    setShippingFeeInputs((prev) => ({
      ...prev,
      [code]: {
        ...prev[code],
        [field]: value,
      },
    }));
  };

  return (
    <div className="space-y-6 max-w-4xl" data-testid="admin-settings-page">
      {/* Header */}
      <div>
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <Settings className="w-6 h-6 text-primary" />
          <span>{t('title')}</span>
        </h1>
        <p className="text-sm text-muted mt-1">{t('subtitle')}</p>
      </div>

      {saveSuccess && (
        <div data-testid="settings-save-success" className="bg-success/10 border border-success/20 text-success text-sm p-4 rounded-xl flex items-center gap-2 animate-in fade-in">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span>{t('saveSuccess')}</span>
        </div>
      )}

      {saveError && (
        <div className="bg-destructive/10 border border-destructive/20 text-destructive text-sm p-4 rounded-xl flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 flex-shrink-0" />
            <span>{saveError}</span>
          </div>
          <button onClick={() => setSaveError(null)} className="text-xs underline hover:opacity-80">
            Dismiss
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="bg-surface border border-border rounded-xl p-12 text-center">
          <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto mb-3" />
          <p className="text-sm text-muted">{commonT('loading')}</p>
        </div>
      ) : isError ? (
        <div className="bg-surface border border-border rounded-xl p-12 text-center">
          <AlertTriangle className="w-8 h-8 text-destructive mx-auto mb-3" />
          <p className="text-sm text-destructive mb-3">{commonT('error')}</p>
          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-surface border border-border rounded-lg hover:bg-muted/10 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>{commonT('retry')}</span>
          </button>
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateSettingsMutation.mutate();
          }}
          className="space-y-6"
        >
          {/* Section: Store Profile */}
          <div className="bg-surface border border-border rounded-xl p-5 space-y-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 border-b border-border pb-3">
              <Store className="w-4 h-4 text-primary" />
              <span>General Store Profile</span>
            </h2>

            <div>
              <label className="block text-xs font-medium text-foreground mb-1.5">
                {t('storeName')}
              </label>
              <input
                type="text"
                data-testid="input-store-name"
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
                required
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          {/* Section: Currency & Exchange Rate */}
          <div className="bg-surface border border-border rounded-xl p-5 space-y-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 border-b border-border pb-3">
              <DollarSign className="w-4 h-4 text-primary" />
              <span>Currency & Exchange Rate</span>
            </h2>

            <div>
              <label className="block text-xs font-medium text-foreground mb-1.5">
                {t('khrRate')}
              </label>
              <div className="relative max-w-xs">
                <input
                  type="number"
                  data-testid="input-khr-rate"
                  min="1"
                  step="1"
                  value={khrRate}
                  onChange={(e) => setKhrRate(parseInt(e.target.value, 10) || 0)}
                  required
                  className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary pr-16"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-semibold text-muted">
                  KHR / $1
                </span>
              </div>
              <p className="text-xs text-muted mt-1.5">
                {t('khrRateHelp', { rate: khrRate.toLocaleString() })}
              </p>
            </div>
          </div>

          {/* Section: Shipping Methods */}
          <div className="bg-surface border border-border rounded-xl p-5 space-y-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 border-b border-border pb-3">
              <Truck className="w-4 h-4 text-primary" />
              <span>{t('shippingFees')}</span>
            </h2>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border bg-background/50 text-muted text-xs uppercase tracking-wider font-semibold">
                    <th className="py-2.5 px-3">{t('shippingCode')}</th>
                    <th className="py-2.5 px-3">{t('shippingName')}</th>
                    <th className="py-2.5 px-3">{t('shippingFee')} ($)</th>
                    <th className="py-2.5 px-3">{t('shippingFreeOver')} ($)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {shippingMethods.map((m) => {
                    const input = shippingFeeInputs[m.code] || {
                      fee: (m.feeCents / 100).toFixed(2),
                      freeOver: m.freeOverCents ? (m.freeOverCents / 100).toFixed(2) : '',
                    };
                    return (
                      <tr key={m.code} className="hover:bg-muted/5 transition-colors">
                        <td className="py-2.5 px-3 font-mono text-xs font-semibold text-muted">
                          {m.code}
                        </td>
                        <td className="py-2.5 px-3 font-medium text-foreground">
                          {locale === 'km' && m.nameKm ? m.nameKm : m.name}
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="relative w-28">
                            <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted text-xs">
                              $
                            </span>
                            <input
                              type="number"
                              step="0.01"
                              min="0"
                              value={input.fee}
                              onChange={(e) =>
                                handleFeeInputChange(m.code, 'fee', e.target.value)
                              }
                              className="w-full pl-6 pr-2 py-1 text-xs bg-background border border-border rounded text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                          </div>
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="relative w-32">
                            <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted text-xs">
                              $
                            </span>
                            <input
                              type="number"
                              step="0.01"
                              min="0"
                              placeholder="No free threshold"
                              value={input.freeOver}
                              onChange={(e) =>
                                handleFeeInputChange(m.code, 'freeOver', e.target.value)
                              }
                              className="w-full pl-6 pr-2 py-1 text-xs bg-background border border-border rounded text-foreground focus:outline-none focus:ring-1 focus:ring-primary placeholder:text-muted/60"
                            />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Section: KHQR Payment Mode */}
          <div className="bg-surface border border-border rounded-xl p-5 space-y-4 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2 border-b border-border pb-3">
              <ShieldAlert className="w-4 h-4 text-primary" />
              <span>{t('khqrMode')}</span>
            </h2>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <label
                onClick={() => handleKhqrModeClick('MOCK')}
                className={cn(
                  'cursor-pointer border rounded-xl p-4 flex items-start gap-3 transition-colors',
                  khqrMode === 'MOCK'
                    ? 'border-primary bg-primary/5 ring-1 ring-primary'
                    : 'border-border bg-background hover:bg-muted/5'
                )}
              >
                <input
                  type="radio"
                  data-testid="mode-mock-radio"
                  name="khqrMode"
                  value="MOCK"
                  checked={khqrMode === 'MOCK'}
                  onChange={() => handleKhqrModeClick('MOCK')}
                  className="mt-1"
                />
                <div>
                  <div className="font-semibold text-sm text-foreground flex items-center gap-2">
                    <span>{t('modeMock')}</span>
                    <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-600">
                      Safe Test Mode
                    </span>
                  </div>
                  <p className="text-xs text-muted mt-1">
                    Generates mock QR codes and allows one-click sandbox simulation. No real funds debited.
                  </p>
                </div>
              </label>

              <label
                onClick={() => handleKhqrModeClick('LIVE')}
                className={cn(
                  'cursor-pointer border rounded-xl p-4 flex items-start gap-3 transition-colors',
                  khqrMode === 'LIVE'
                    ? 'border-destructive bg-destructive/5 ring-1 ring-destructive'
                    : 'border-border bg-background hover:bg-muted/5'
                )}
              >
                <input
                  type="radio"
                  data-testid="mode-live-radio"
                  name="khqrMode"
                  value="LIVE"
                  checked={khqrMode === 'LIVE'}
                  onChange={() => handleKhqrModeClick('LIVE')}
                  className="mt-1"
                />
                <div>
                  <div className="font-semibold text-sm text-foreground flex items-center gap-2">
                    <span>{t('modeLive')}</span>
                    <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded bg-destructive/10 text-destructive">
                      Real NBC Bakong
                    </span>
                  </div>
                  <p className="text-xs text-muted mt-1">
                    Connects directly to NBC Bakong Open API. Requires live Bakong token and credentials.
                  </p>
                </div>
              </label>
            </div>
          </div>

          {/* Submit Button */}
          <div className="flex justify-end pt-2">
            <button
              type="submit"
              data-testid="save-settings-btn"
              disabled={updateSettingsMutation.isPending}
              className="px-5 py-2.5 text-xs sm:text-sm font-semibold bg-primary text-primary-foreground rounded-lg hover:bg-primary-hover transition-colors shadow-sm inline-flex items-center gap-2 disabled:opacity-50"
            >
              {updateSettingsMutation.isPending && (
                <Loader2 className="w-4 h-4 animate-spin" />
              )}
              <span>{t('saveSettings')}</span>
            </button>
          </div>
        </form>
      )}

      {/* Red Warning Dialog for LIVE KHQR Mode */}
      {showLiveWarning && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div data-testid="live-warning-modal" className="bg-surface border-2 border-destructive rounded-xl max-w-lg w-full p-6 space-y-4 shadow-2xl animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3 text-destructive">
              <div className="p-3 rounded-full bg-destructive/10 border border-destructive/20">
                <AlertTriangle className="w-8 h-8 text-destructive animate-pulse" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-destructive">
                  {t('liveWarningTitle')}
                </h2>
                <p className="text-xs text-muted">High-risk production setting</p>
              </div>
            </div>

            <p className="text-sm text-foreground bg-destructive/5 p-4 rounded-lg border border-destructive/20 leading-relaxed">
              {t('liveWarningDesc')}
            </p>

            <div className="flex justify-end gap-3 pt-3">
              <button
                type="button"
                data-testid="cancel-live-btn"
                onClick={() => {
                  setShowLiveWarning(false);
                  setPendingKhqrMode(null);
                }}
                className="px-4 py-2 text-xs font-semibold border border-border rounded-lg hover:bg-muted/10 transition-colors"
              >
                {commonT('cancel')}
              </button>
              <button
                type="button"
                data-testid="confirm-live-btn"
                onClick={handleConfirmLive}
                className="px-4 py-2 text-xs font-semibold bg-destructive text-destructive-foreground rounded-lg hover:bg-destructive/90 transition-colors shadow-sm"
              >
                {t('liveConfirmBtn')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
