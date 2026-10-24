'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient, ApiError } from '@/lib/apiClient';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { UserDTO } from '@/types/api';
import { toast } from 'sonner';

export default function RegisterPage() {
  const t = useTranslations('auth');
  const errT = useTranslations('errors');
  const router = useRouter();
  const { setAuth } = useAuthStore();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    if (password !== confirmPassword) {
      setFieldErrors({ confirmPassword: t('passwordsDoNotMatch') });
      return;
    }

    setIsLoading(true);

    try {
      const res = await apiClient<{ accessToken: string; user: UserDTO }>('/auth/register', {
        method: 'POST',
        body: {
          fullName: fullName.trim(),
          email: email.trim(),
          phone: phone.trim(),
          password,
        },
        auth: false,
      });

      if (res.data?.accessToken) {
        setAuth(res.data.accessToken, res.data.user);
        toast.success(t('createAccount'));
        router.push('/');
      }
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
    <div className="min-h-[75vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md space-y-6 bg-surface border border-border p-6 sm:p-8 rounded-card shadow-sm">
        <div className="text-center space-y-1.5">
          <h1 className="text-2xl font-black tracking-tight text-foreground">
            {t('registerTitle')}
          </h1>
          <p className="text-xs sm:text-sm text-muted">
            {t('registerSubtitle')}
          </p>
        </div>

        {generalError && (
          <div
            data-testid="register-error-alert"
            className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium"
          >
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Input
            id="register-fullname"
            type="text"
            label={t('fullName')}
            placeholder={t('fullNamePlaceholder')}
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            error={fieldErrors.fullName}
            required
            autoComplete="name"
          />

          <Input
            id="register-email"
            type="email"
            label={t('email')}
            placeholder={t('emailPlaceholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            required
            autoComplete="email"
          />

          <Input
            id="register-phone"
            type="tel"
            label={t('phone')}
            placeholder={t('phonePlaceholder')}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            error={fieldErrors.phone}
            required
            autoComplete="tel"
          />

          <Input
            id="register-password"
            type="password"
            label={t('password')}
            placeholder={t('passwordPlaceholder')}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            required
            autoComplete="new-password"
          />

          <Input
            id="register-confirm-password"
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
            {isLoading ? t('creatingAccount') : t('createAccount')}
          </Button>
        </form>

        <div className="text-center text-xs text-muted pt-2 border-t border-border/80">
          <span>{t('alreadyHaveAccount')} </span>
          <Link href="/login" className="font-bold text-primary hover:underline">
            {t('signIn')}
          </Link>
        </div>
      </div>
    </div>
  );
}
