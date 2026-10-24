'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { RotateCcw, AlertTriangle } from 'lucide-react';

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations('common');

  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4">
      <div className="w-16 h-16 rounded-full bg-destructive/10 text-destructive flex items-center justify-center mb-4">
        <AlertTriangle className="w-8 h-8" />
      </div>
      <h1 className="text-2xl font-bold text-foreground mb-2">
        {t('serverErrorTitle')}
      </h1>
      <p className="text-muted text-sm max-w-md mb-6">
        {t('serverErrorDesc')}
      </p>
      <Button onClick={() => reset()} variant="primary" size="md" className="flex items-center gap-2">
        <RotateCcw className="w-4 h-4" />
        <span>{t('retry')}</span>
      </Button>
    </div>
  );
}
