'use client';

import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { buttonVariants } from '@/components/ui/button';
import { AlertCircle, Home } from 'lucide-react';

export default function NotFound() {
  const t = useTranslations('common');

  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4">
      <div className="w-16 h-16 rounded-full bg-primary/10 text-primary flex items-center justify-center mb-4">
        <AlertCircle className="w-8 h-8" />
      </div>
      <h1 className="text-3xl font-extrabold text-foreground mb-2">
        {t('notFoundTitle')}
      </h1>
      <p className="text-muted text-sm max-w-md mb-6">
        {t('notFoundDesc')}
      </p>
      <Link
        href="/"
        className={buttonVariants({ variant: 'primary', size: 'md', className: 'flex items-center gap-2' })}
      >
        <Home className="w-4 h-4" />
        <span>{t('backHome')}</span>
      </Link>
    </div>
  );
}
