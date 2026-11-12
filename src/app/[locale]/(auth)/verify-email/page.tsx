'use client';

import React, { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { useSearchParams } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient, ApiError } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { queryKeys } from '@/lib/queryKeys';
import { UserDTO } from '@/types/api';
import { toast } from 'sonner';

export default function VerifyEmailPage() {
  const t = useTranslations('auth');
  const errT = useTranslations('errors');
  const router = useRouter();
  const queryClient = useQueryClient();
  const { setAuth } = useAuthStore();
  const email = (useSearchParams().get('email') || '').trim();

  const [code, setCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [cooldown, setCooldown] = useState(60);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(id);
  }, [cooldown]);

  const fail = (err: any) => {
    if (err instanceof ApiError) {
      if (err.code === 'OTP_INVALID' && err.details?.attemptsLeft !== undefined) {
        setError(`${err.message} (${err.details.attemptsLeft})`);
      } else {
        setError(err.message || t('emailVerificationFailed'));
      }
      if (err.code === 'OTP_RESEND_COOLDOWN' && err.details?.retryInSeconds) {
        setCooldown(Number(err.details.retryInSeconds));
      }
    } else {
      setError(err?.message || errT('INTERNAL_ERROR'));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!/^\d{6}$/.test(code)) {
      setError(t('otpInvalidFormat'));
      return;
    }
    setIsLoading(true);
    try {
      const res = await apiClient<{ accessToken: string; user: UserDTO }>('/auth/verify-email', {
        method: 'POST',
        body: { email, code },
        auth: false,
      });
      if (res.data?.accessToken) {
        setAuth(res.data.accessToken, res.data.user);
        toast.success(t('emailVerifiedSuccess'));
        try {
          await apiClient('/cart/merge', { method: 'POST' });
        } catch {
          // ignore
        }
        queryClient.invalidateQueries({ queryKey: queryKeys.cart.current });
        router.push('/');
      }
    } catch (err) {
      fail(err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    setError(null);
    setIsResending(true);
    try {
      await apiClient('/auth/resend-verification', {
        method: 'POST',
        body: { email },
        auth: false,
      });
      setCooldown(60);
      setCode('');
      toast.success(t('otpResent'));
    } catch (err) {
      fail(err);
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="min-h-[65vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md space-y-6 bg-surface border border-border p-6 sm:p-8 rounded-card shadow-sm">
        <div className="text-center space-y-1.5">
          <h1 className="text-2xl font-black tracking-tight text-foreground">
            {t('verifyEmailTitle')}
          </h1>
          {email ? (
            <p className="text-xs sm:text-sm text-muted">
              {t('otpSubtitle', { email })}
            </p>
          ) : null}
        </div>

        {!email ? (
          <div className="text-center space-y-4">
            <p className="text-sm text-destructive font-semibold">{t('otpMissingEmail')}</p>
            <Link href="/register" className="font-bold text-primary hover:underline text-sm">
              {t('registerTitle')}
            </Link>
          </div>
        ) : (
          <>
            {error && (
              <div
                data-testid="verify-error-alert"
                className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium"
              >
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <Input
                id="verify-code"
                type="text"
                label={t('otpLabel')}
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                required
              />
              <Button
                type="submit"
                variant="primary"
                className="w-full font-bold shadow-sm"
                isLoading={isLoading}
              >
                {t('otpVerify')}
              </Button>
            </form>

            <div className="text-center text-xs text-muted pt-2 border-t border-border/80 space-y-2">
              <button
                type="button"
                data-testid="verify-resend"
                onClick={handleResend}
                disabled={cooldown > 0 || isResending}
                className="font-bold text-primary hover:underline disabled:opacity-50 disabled:no-underline"
              >
                {cooldown > 0 ? t('otpResendIn', { seconds: cooldown }) : t('otpResend')}
              </button>
              <div>
                <Link href="/login" className="font-bold text-primary hover:underline">
                  {t('backToLogin')}
                </Link>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
