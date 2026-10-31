'use client';

import React, { useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { usePathname, useRouter } from '@/navigation';
import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { User, MapPin, Shield, Package, Loader2, Bell } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isInitialized } = useAuthStore();
  const router = useRouter();
  const pathname = usePathname();
  const t = useTranslations('account');

  useEffect(() => {
    if (isInitialized && !user) {
      const fullTarget = pathname.startsWith('/account') ? pathname : '/account';
      router.replace(`/login?next=${encodeURIComponent(fullTarget)}`);
    }
  }, [isInitialized, user, router, pathname]);

  if (!isInitialized || !user) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center gap-3">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
        <p className="text-xs text-muted">Checking authentication...</p>
      </div>
    );
  }

  const tabs = [
    { href: '/account', label: t('profileTab'), icon: User, exact: true },
    { href: '/account/orders', label: t('ordersTab'), icon: Package },
    { href: '/account/addresses', label: t('addressesTab'), icon: MapPin },
    { href: '/account/notifications', label: t('notificationsTab'), icon: Bell },
    { href: '/account/security', label: t('securityTab'), icon: Shield },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 space-y-8">
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-foreground tracking-tight">
          {t('title')}
        </h1>
        <p className="text-xs sm:text-sm text-muted mt-1">
          {user.fullName} ({user.email})
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6 items-start">
        {/* Sidebar Tabs */}
        <aside className="bg-surface border border-border rounded-card p-2 md:p-3 space-y-1">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = tab.exact ? pathname === tab.href : pathname.startsWith(tab.href);

            return (
              <Link
                key={tab.href}
                href={tab.href}
                className={cn(
                  'flex items-center gap-2.5 px-3 py-2.5 rounded-btn text-xs sm:text-sm font-semibold transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'text-muted hover:text-foreground hover:bg-muted/10'
                )}
              >
                <Icon className="w-4 h-4" />
                <span>{tab.label}</span>
              </Link>
            );
          })}
        </aside>

        {/* Tab Content */}
        <div className="md:col-span-3">
          {children}
        </div>
      </div>
    </div>
  );
}
