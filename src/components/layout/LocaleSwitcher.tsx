'use client';

import { useLocale } from 'next-intl';
import { usePathname, useRouter } from '@/navigation';
import { Globe } from 'lucide-react';
import { useTransition } from 'react';

export function LocaleSwitcher() {
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [isPending, startTransition] = useTransition();

  const toggleLocale = () => {
    const nextLocale = locale === 'en' ? 'km' : 'en';
    startTransition(() => {
      // Set NEXT_LOCALE cookie so next-intl middleware remembers it
      document.cookie = `NEXT_LOCALE=${nextLocale}; path=/; max-age=31536000; SameSite=Lax`;
      router.replace(pathname, { locale: nextLocale });
    });
  };

  return (
    <button
      onClick={toggleLocale}
      disabled={isPending}
      aria-label="Switch language"
      className="flex items-center gap-1 sm:gap-1.5 px-2 sm:px-2.5 py-1.5 text-xs font-semibold rounded-btn border border-border/80 bg-surface/50 hover:bg-surface text-foreground transition-all duration-150"
    >
      <Globe className="w-3.5 h-3.5 text-primary shrink-0" />
      <span className="hidden sm:inline">{locale === 'en' ? 'ខ្មែរ (KM)' : 'English (EN)'}</span>
      <span className="sm:hidden font-bold">{locale === 'en' ? 'KM' : 'EN'}</span>
    </button>
  );
}
