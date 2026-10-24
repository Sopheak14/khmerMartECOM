'use client';

import React, { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { useSearchParams } from 'next/navigation';
import { apiClient, ApiError } from '@/lib/apiClient';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';

export default function VerifyEmailPage() {
  const t = useTranslations('auth');
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setErrorMessage(t('emailVerificationFailed'));
      return;
    }

    async function verify() {
      try {
        await apiClient('/auth/verify-email', {
          method: 'POST',
          body: { token },
          auth: false,
        });
        setStatus('success');
      } catch (err: any) {
        setStatus('error');
        if (err instanceof ApiError) {
          setErrorMessage(err.message || t('emailVerificationFailed'));
        } else {
          setErrorMessage(t('emailVerificationFailed'));
        }
      }
    }

    verify();
  }, [token, t]);

  return (
    <div className="min-h-[65vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md space-y-6 bg-surface border border-border p-6 sm:p-8 rounded-card shadow-sm text-center">
        <h1 className="text-2xl font-black tracking-tight text-foreground">
          {t('verifyEmailTitle')}
        </h1>

        {status === 'loading' && (
          <div className="space-y-4 py-8">
            <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto" />
            <p className="text-xs sm:text-sm text-muted">
              {t('verifyingEmail')}
            </p>
          </div>
        )}

        {status === 'success' && (
          <div className="space-y-4 py-6">
            <div className="w-14 h-14 bg-success/10 text-success rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-7 h-7" />
            </div>
            <p className="text-sm font-semibold text-foreground">
              {t('emailVerifiedSuccess')}
            </p>
            <div className="pt-2">
              <Link
                href="/login"
                className="inline-flex items-center justify-center rounded-btn px-6 py-2.5 text-xs sm:text-sm font-bold bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm"
              >
                {t('goToLogin')}
              </Link>
            </div>
          </div>
        )}

        {status === 'error' && (
          <div className="space-y-4 py-6">
            <div className="w-14 h-14 bg-destructive/10 text-destructive rounded-full flex items-center justify-center mx-auto">
              <XCircle className="w-7 h-7" />
            </div>
            <p className="text-sm font-semibold text-destructive">
              {errorMessage || t('emailVerificationFailed')}
            </p>
            <div className="pt-2">
              <Link
                href="/login"
                className="inline-flex items-center justify-center rounded-btn px-6 py-2.5 text-xs sm:text-sm font-bold bg-secondary text-secondary-foreground hover:bg-secondary/90 transition-colors shadow-sm"
              >
                {t('goToLogin')}
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
