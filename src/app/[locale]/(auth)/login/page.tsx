'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { useSearchParams } from 'next/navigation';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient, ApiError } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { UserDTO } from '@/types/api';
import { toast } from 'sonner';

export default function LoginPage() {
  const t = useTranslations('auth');
  const errT = useTranslations('errors');
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextUrl = searchParams.get('next') || '/';

  const { setAuth } = useAuthStore();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setGeneralError(null);
    setIsLoading(true);

    try {
      const res = await apiClient<{ accessToken: string; user: UserDTO }>('/auth/login', {
        method: 'POST',
        body: { email: email.trim(), password },
        auth: false,
      });

      if (res.data?.accessToken) {
        setAuth(res.data.accessToken, res.data.user);
        toast.success(t('signIn'));
        
        // Handle redirect to nextUrl properly (stripping locale if needed)
        const target = nextUrl.replace(/^\/(en|km)/, '') || '/';
        router.push(target);
      }
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.code === 'RATE_LIMITED' || err.status === 429) {
          setGeneralError(errT('RATE_LIMITED'));
        } else if (err.status === 401 || err.code === 'UNAUTHORIZED') {
          setGeneralError(t('invalidCredentials'));
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
    <div className="min-h-[70vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md space-y-6 bg-surface border border-border p-6 sm:p-8 rounded-card shadow-sm">
        <div className="text-center space-y-1.5">
          <h1 className="text-2xl font-black tracking-tight text-foreground">
            {t('signInTitle')}
          </h1>
          <p className="text-xs sm:text-sm text-muted">
            {t('signInSubtitle')}
          </p>
        </div>

        {generalError && (
          <div
            data-testid="login-error-alert"
            className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium"
          >
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            id="login-email"
            type="email"
            label={t('email')}
            placeholder={t('emailPlaceholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />

          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <label htmlFor="login-password" className="text-sm font-medium text-foreground">
                {t('password')}
              </label>
              <Link
                href="/forgot-password"
                className="text-xs text-primary hover:underline font-semibold"
              >
                {t('forgotPassword')}
              </Link>
            </div>
            <Input
              id="login-password"
              type="password"
              placeholder={t('passwordPlaceholder')}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            className="w-full font-bold shadow-sm"
            isLoading={isLoading}
          >
            {isLoading ? t('signingIn') : t('signIn')}
          </Button>
        </form>

        <div className="text-center text-xs text-muted pt-2 border-t border-border/80">
          <span>{t('noAccount')} </span>
          <Link href="/register" className="font-bold text-primary hover:underline">
            {t('register')}
          </Link>
        </div>
      </div>
    </div>
  );
}
