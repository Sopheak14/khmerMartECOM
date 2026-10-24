'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { apiClient, ApiError } from '@/lib/apiClient';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';

export default function SecurityPage() {
  const t = useTranslations('account');
  const authT = useTranslations('auth');
  const errT = useTranslations('errors');

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
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
    <div className="bg-surface border border-border rounded-card p-6 shadow-sm space-y-6">
      <div>
        <h2 className="text-base sm:text-lg font-bold text-foreground">
          {t('securityTab')}
        </h2>
        <p className="text-xs text-muted mt-0.5">
          {t('passwordRequirements')}
        </p>
      </div>

      {generalError && (
        <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
          {generalError}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4 max-w-xl">
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
            className="font-bold shadow-sm"
          >
            {isLoading ? t('saving') : t('changePassword')}
          </Button>
        </div>
      </form>
    </div>
  );
}
