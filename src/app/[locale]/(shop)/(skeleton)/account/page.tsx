'use client';

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { UserDTO } from '@/types/api';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import { User, CheckCircle2, AlertCircle } from 'lucide-react';

export default function ProfilePage() {
  const t = useTranslations('account');
  const authT = useTranslations('auth');
  const errT = useTranslations('errors');
  const queryClient = useQueryClient();
  const { setUser } = useAuthStore();

  const { data: profile, isLoading } = useQuery({
    queryKey: queryKeys.auth.me,
    queryFn: async () => {
      const res = await apiClient<UserDTO>('/users/me');
      return res.data;
    },
  });

  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName || '');
      setPhone(profile.phone || '');
    }
  }, [profile]);

  const updateMutation = useMutation({
    mutationFn: async (data: { fullName: string; phone: string }) => {
      const res = await apiClient<UserDTO>('/users/me', {
        method: 'PATCH',
        body: data,
      });
      return res.data;
    },
    onSuccess: (updatedUser) => {
      setUser(updatedUser);
      queryClient.setQueryData(queryKeys.auth.me, updatedUser);
      queryClient.invalidateQueries({ queryKey: queryKeys.auth.me });
      toast.success(t('profileUpdated'));
      setFieldErrors({});
      setGeneralError(null);
    },
    onError: (err: any) => {
      if (err instanceof ApiError && err.details) {
        setFieldErrors(err.details);
      } else {
        setGeneralError(err?.message || errT('VALIDATION_ERROR'));
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    updateMutation.mutate({
      fullName: fullName.trim(),
      phone: phone.trim(),
    });
  };

  if (isLoading || !profile) {
    return (
      <div className="bg-surface border border-border rounded-card p-6 space-y-4 animate-pulse">
        <div className="h-6 w-36 bg-muted/20 rounded" />
        <div className="h-10 w-full bg-muted/20 rounded" />
        <div className="h-10 w-full bg-muted/20 rounded" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Profile Overview Card */}
      <div className="bg-surface border border-border rounded-card p-6 shadow-sm space-y-6">
        <div className="flex items-center justify-between flex-wrap gap-4 border-b border-border/80 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary font-black text-lg flex items-center justify-center">
              {profile.fullName?.charAt(0).toUpperCase()}
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-foreground">
                {profile.fullName}
              </h2>
              <p className="text-xs text-muted">{profile.email}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Badge variant="outline" className="text-xs">
              {profile.role}
            </Badge>
            {profile.emailVerified ? (
              <Badge variant="default" className="text-xs bg-success text-success-foreground gap-1">
                <CheckCircle2 className="w-3 h-3" />
                <span>{t('verified')}</span>
              </Badge>
            ) : (
              <Badge variant="secondary" className="text-xs gap-1 text-warning">
                <AlertCircle className="w-3 h-3" />
                <span>{t('unverified')}</span>
              </Badge>
            )}
          </div>
        </div>

        {/* Edit Form */}
        <form onSubmit={handleSubmit} className="space-y-4 max-w-xl">
          <h3 className="text-sm font-bold text-foreground">
            {t('profileTab')}
          </h3>

          {generalError && (
            <div className="p-3 text-xs sm:text-sm rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium">
              {generalError}
            </div>
          )}

          <div className="space-y-1">
            <label className="text-sm font-medium text-foreground">
              {authT('email')}
            </label>
            <input
              type="email"
              value={profile.email}
              disabled
              className="flex h-10 w-full rounded-btn border border-input bg-muted/10 px-3 py-2 text-sm text-muted cursor-not-allowed"
            />
          </div>

          <Input
            id="profile-fullname"
            type="text"
            label={authT('fullName')}
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            error={fieldErrors.fullName}
            required
          />

          <Input
            id="profile-phone"
            type="tel"
            label={authT('phone')}
            placeholder="+85512345678"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            error={fieldErrors.phone}
            required
          />

          <div className="pt-2">
            <Button
              type="submit"
              variant="primary"
              isLoading={updateMutation.isPending}
              className="font-bold shadow-sm"
            >
              {updateMutation.isPending ? t('saving') : t('saveChanges')}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
