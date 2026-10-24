'use client';

import React, { useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { useRouter } from '@/navigation';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { ShieldAlert, ArrowLeft, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function AdminPage() {
  const { user, isInitialized } = useAuthStore();
  const router = useRouter();
  const t = useTranslations('admin');

  useEffect(() => {
    if (isInitialized && !user) {
      router.replace('/login?next=/admin');
    }
  }, [isInitialized, user, router]);

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </div>
    );
  }

  // If customer (not ADMIN and not STAFF) -> render 403 Forbidden page
  if (user && user.role !== 'ADMIN' && user.role !== 'STAFF') {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-background">
        <div className="w-full max-w-lg bg-surface border border-destructive/30 rounded-card p-8 shadow-lg text-center space-y-6 animate-in zoom-in-95">
          <div className="w-16 h-16 bg-destructive/10 text-destructive rounded-full flex items-center justify-center mx-auto">
            <ShieldAlert className="w-8 h-8" />
          </div>

          <div className="space-y-2">
            <span className="inline-block px-3 py-1 rounded-chip text-xs font-black bg-destructive/10 text-destructive border border-destructive/20 uppercase tracking-widest">
              HTTP 403 FORBIDDEN
            </span>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-foreground">
              {t('forbiddenTitle')}
            </h1>
            <p className="text-xs sm:text-sm text-muted leading-relaxed max-w-md mx-auto">
              {t('forbiddenMessage')}
            </p>
          </div>

          <div className="pt-2">
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground text-xs sm:text-sm font-bold hover:bg-primary/90 transition-colors shadow-sm"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>{t('backHome')}</span>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // For Admin / Staff
  return (
    <div className="min-h-screen p-8 max-w-7xl mx-auto space-y-6">
      <h1 className="text-2xl font-black text-foreground">KhmerMart Backoffice Dashboard</h1>
      <p className="text-sm text-muted">Welcome, {user?.fullName} ({user?.role})</p>
    </div>
  );
}
