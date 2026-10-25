'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { ShieldAlert, ArrowLeft, LayoutDashboard } from 'lucide-react';
import { useAuthStore } from '@/lib/store/useAuthStore';

export function AdminForbidden({ message }: { message?: string }) {
  const t = useTranslations('admin');
  const { user } = useAuthStore();
  const isStaff = user?.role === 'STAFF';

  return (
    <div
      data-testid="admin-forbidden"
      className="min-h-[70vh] flex items-center justify-center p-4"
    >
      <div className="w-full max-w-lg bg-surface border border-destructive/30 rounded-card p-8 shadow-lg text-center space-y-6 animate-in zoom-in-95">
        <div className="w-16 h-16 bg-destructive/10 text-destructive rounded-full flex items-center justify-center mx-auto">
          <ShieldAlert className="w-8 h-8" />
        </div>

        <div className="space-y-2">
          <span className="inline-block px-3 py-1 rounded-chip text-xs font-black bg-destructive/10 text-destructive border border-destructive/20 uppercase tracking-widest">
            HTTP 403 FORBIDDEN
          </span>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-foreground" data-testid="admin-403-title">
            {t('forbiddenTitle')}
          </h1>
          <p className="text-xs sm:text-sm text-muted leading-relaxed max-w-md mx-auto" data-testid="admin-403-message">
            {message || t('forbiddenMessage')}
          </p>
        </div>

        <div className="pt-2 flex items-center justify-center gap-3">
          {isStaff ? (
            <Link
              href="/admin"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs"
            >
              <LayoutDashboard className="w-4 h-4" />
              <span>{t('backDashboard')}</span>
            </Link>
          ) : (
            <Link
              href="/"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>{t('backHome')}</span>
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
