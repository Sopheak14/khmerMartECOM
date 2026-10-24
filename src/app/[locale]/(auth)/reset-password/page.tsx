'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { useSearchParams } from 'next/navigation';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient, ApiError } from '@/lib/apiClient';
import { CheckCircle2 } from 'lucide-react';

export default function ResetPasswordPage() {
  const t = useTranslations('auth');
  const errT = useTranslations('errors');
  const searchParams = useSearchParams();
  const token = searchParams.get('token') || '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    if (newPassword !== confirmPassword) {
      setFieldErrors({ confirmPassword: t('passwordsDoNotMatch') });
      return;
    }

    if (!token) {
      setGeneralError('Invalid or missing reset token.');
      return;
    }

    setIsLoading(true);

    try {
      await apiClient('/auth/reset-password', {
        method: 'POST',
        body: { token, newPassword },
        auth: false,
      });
      setIsSuccess(true);
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.details && Object.keys(err.details).length > 0) {
          setFieldErrors(err.details);
        } else {
          setGeneralError(err.message || errT('INTERNAL_ERROR'));
        }
      } else {
        setGeneralError(err?.message || errT('INTERNAL_ERROR'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-[65vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md space-y-6 bg-surface border border-border p-6 sm:p-8 rounded-card shadow-sm">
        <div className="text-center space-y-1.5">
          <h1 className="text-2xl font-black tracking-tight text-foreground">
            {t('resetPasswordTitle')}
          </h1>
          <p className="text-xs sm:text-sm text-muted">
            {t('resetPasswordSubtitle')}
          </p>
        </div>

        {isSuccess ? (
          <div className="space-y-4 text-center py-4">
            <div className="w-12 h-12 bg-success/10 text-success rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <p className="text-xs sm:text-sm text-foreground font-medium">
              {t('passwordResetSuccess')}
            </p>
            <div className="pt-2">
              <Link
                href="/login"
                className="inline-flex items-center justify-center rounded-btn px-6 py-2.5 text-xs sm:text-sm font-bold bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm"
              >
                {t('signIn')}
              </Link>
            </div>
          </div>
        ) : (
          <>
            {generalError && (
              <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
                {generalError}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                id="reset-new-password"
                type="password"
                label={t('newPassword')}
                placeholder={t('passwordPlaceholder')}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                error={fieldErrors.newPassword}
                required
                autoComplete="new-password"
              />

              <Input
                id="reset-confirm-password"
                type="password"
                label={t('confirmPassword')}
                placeholder={t('passwordPlaceholder')}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                error={fieldErrors.confirmPassword}
                required
                autoComplete="new-password"
              />

              <Button
                type="submit"
                variant="primary"
                className="w-full font-bold shadow-sm"
                isLoading={isLoading}
              >
                {isLoading ? t('sending') : t('resetPassword')}
              </Button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
