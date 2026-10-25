'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { Sparkles } from 'lucide-react';

export default function AdminPromotionsPage() {
  const t = useTranslations('admin.nav');

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <Sparkles className="w-6 h-6 text-primary" />
          <span>{t('promotions')}</span>
        </h1>
      </div>
      <div className="bg-surface border border-border rounded-xl p-8 text-center text-muted">
        Promotions management module
      </div>
    </div>
  );
}
