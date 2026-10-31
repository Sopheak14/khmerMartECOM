'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { apiClient, ApiError } from '@/lib/apiClient';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { Bell, BellRing, Loader2, ShieldCheck, AlertCircle } from 'lucide-react';
import {
  subscribeToWebPush,
  unsubscribeFromWebPush,
  getExistingPushSubscription,
  isPushNotificationSupported,
} from '@/lib/push';

export default function SecurityPage() {
  const t = useTranslations('account');
  const authT = useTranslations('auth');
  const errT = useTranslations('errors');
  const pushT = useTranslations('push');

  // Password state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Web Push state
  const [isPushSupported, setIsPushSupported] = useState(true);
  const [isPushSubscribed, setIsPushSubscribed] = useState(false);
  const [isPushLoading, setIsPushLoading] = useState(false);
  const [pushStatusMessage, setPushStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    async function checkPushState() {
      const supported = await isPushNotificationSupported();
      setIsPushSupported(supported);
      if (supported) {
        if (typeof Notification !== 'undefined' && Notification.permission === 'denied') {
          setPushStatusMessage(pushT('permissionDenied'));
        }
        const sub = await getExistingPushSubscription();
        setIsPushSubscribed(Boolean(sub));
      } else {
        setPushStatusMessage(pushT('unsupported'));
      }
    }
    checkPushState();
  }, [pushT]);

  const handleTogglePush = async () => {
    setPushStatusMessage(null);
    setIsPushLoading(true);

    try {
      if (isPushSubscribed) {
        await unsubscribeFromWebPush();
        setIsPushSubscribed(false);
        toast.info(pushT('disabled'));
      } else {
        const res = await subscribeToWebPush();
        if (res.success) {
          setIsPushSubscribed(true);
          toast.success(pushT('subscribeSuccess'));
        } else {
          if (res.error === 'UNSUPPORTED') {
            setPushStatusMessage(pushT('unsupported'));
            toast.error(pushT('unsupported'));
          } else if (res.error === 'PERMISSION_DENIED') {
            setPushStatusMessage(pushT('permissionDenied'));
            toast.error(pushT('permissionDenied'));
          } else {
            setPushStatusMessage(res.message || 'Failed to enable push notifications');
            toast.error(res.message || 'Failed to enable push notifications');
          }
        }
      }
    } catch (err: any) {
      setPushStatusMessage(err.message || 'Error toggling push notifications');
    } finally {
      setIsPushLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    if (newPassword !== confirmPassword) {
      setFieldErrors({ confirmPassword: authT('passwordsDoNotMatch') });
      return;
    }

    setIsLoading(true);

    try {
      await apiClient('/users/me/password', {
        method: 'PATCH',
        body: { currentPassword, newPassword },
      });

      toast.success(t('passwordChanged'));
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.details && Object.keys(err.details).length > 0) {
          setFieldErrors(err.details);
        } else {
          setGeneralError(err.message || errT('VALIDATION_ERROR'));
        }
      } else {
        setGeneralError(err?.message || errT('INTERNAL_ERROR'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Web Push Notification Settings Card */}
      <div
        className="bg-surface border border-border rounded-card p-6 shadow-xs space-y-4"
        data-testid="web-push-section"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <BellRing className="w-5 h-5 text-primary" />
              <h2 className="text-base sm:text-lg font-bold text-foreground">
                {pushT('title')}
              </h2>
            </div>
            <p className="text-xs text-muted leading-relaxed max-w-lg">
              {pushT('description')}
            </p>
          </div>

          {/* Toggle Switch */}
          <div className="flex items-center gap-3 shrink-0">
            <span className="text-xs font-semibold text-foreground hidden sm:inline">
              {isPushSubscribed ? pushT('enabled') : pushT('disabled')}
            </span>

            <button
              type="button"
              role="switch"
              aria-checked={isPushSubscribed}
              data-testid="web-push-toggle"
              onClick={handleTogglePush}
              disabled={isPushLoading || (!isPushSupported && !isPushSubscribed)}
              className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden focus-visible:ring-2 focus-visible:ring-primary ${
                isPushSubscribed ? 'bg-primary' : 'bg-muted/30'
              } disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              <span className="sr-only">{pushT('title')}</span>
              <span
                className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-md ring-0 transition duration-200 ease-in-out ${
                  isPushSubscribed ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </div>

        {pushStatusMessage && (
          <div
            data-testid="web-push-message"
            className="p-3 text-xs rounded-btn bg-amber-500/10 border border-amber-500/20 text-amber-800 dark:text-amber-400 font-medium flex items-center gap-2"
          >
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{pushStatusMessage}</span>
          </div>
        )}
      </div>

      {/* Password Change Card */}
      <div className="bg-surface border border-border rounded-card p-6 shadow-xs space-y-6">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-primary" />
            <h2 className="text-base sm:text-lg font-bold text-foreground">
              {t('securityTab')}
            </h2>
          </div>
          <p className="text-xs text-muted mt-0.5">
            {t('passwordRequirements')}
          </p>
        </div>

        {generalError && (
          <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
            {generalError}
          </div>
        )}

        <form onSubmit={handlePasswordSubmit} className="space-y-4 max-w-xl">
          <Input
            id="current-password"
            type="password"
            label={t('currentPassword')}
            placeholder="••••••••"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            error={fieldErrors.currentPassword}
            required
            autoComplete="current-password"
          />

          <Input
            id="new-password"
            type="password"
            label={authT('newPassword')}
            placeholder="••••••••"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            error={fieldErrors.newPassword}
            required
            autoComplete="new-password"
          />

          <Input
            id="confirm-password"
            type="password"
            label={authT('confirmPassword')}
            placeholder="••••••••"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            error={fieldErrors.confirmPassword}
            required
            autoComplete="new-password"
          />

          <div className="pt-2">
            <Button
              type="submit"
              variant="primary"
              isLoading={isLoading}
              className="font-bold shadow-xs cursor-pointer"
            >
              {isLoading ? t('saving') : t('changePassword')}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
