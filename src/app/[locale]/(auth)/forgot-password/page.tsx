'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient, ApiError } from '@/lib/apiClient';
import { ArrowLeft, CheckCircle2 } from 'lucide-react';

export default function ForgotPasswordPage() {
  const t = useTranslations('auth');
  const errT = useTranslations('errors');

  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      await apiClient('/auth/forgot-password', {
        method: 'POST',
        body: { email: email.trim() },
        auth: false,
      });
      setIsSuccess(true);
    } catch (err: any) {
      if (err instanceof ApiError) {
        setError(err.message || errT('INTERNAL_ERROR'));
      } else {
        setError(err?.message || errT('INTERNAL_ERROR'));
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
            {t('forgotPasswordTitle')}
          </h1>
          <p className="text-xs sm:text-sm text-muted">
            {t('forgotPasswordSubtitle')}
          </p>
        </div>

        {isSuccess ? (
          <div className="space-y-4 text-center py-4">
            <div className="w-12 h-12 bg-success/10 text-success rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <p className="text-xs sm:text-sm text-foreground font-medium">
              {t('resetLinkSent')}
            </p>
            <div className="pt-2">
              <Link
                href="/login"
                className="inline-flex items-center gap-1.5 text-xs font-bold text-primary hover:underline"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t('backToLogin')}</span>
              </Link>
            </div>
          </div>
        ) : (
          <>
            {error && (
              <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                id="forgot-email"
                type="email"
                label={t('email')}
                placeholder={t('emailPlaceholder')}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />

              <Button
                type="submit"
                variant="primary"
                className="w-full font-bold shadow-sm"
                isLoading={isLoading}
              >
                {isLoading ? t('sending') : t('sendResetLink')}
              </Button>
            </form>

            <div className="text-center pt-2">
              <Link
                href="/login"
                className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-foreground font-semibold"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>{t('backToLogin')}</span>
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
