'use client';

import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { LocaleSwitcher } from './LocaleSwitcher';
import { SearchBar } from './SearchBar';
import { NotificationBell } from './NotificationBell';
import { CartButton } from './CartButton';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { User, ShieldCheck, Package, LogOut, ChevronDown, Sparkles } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { PublicSettingsDTO } from '@/types/api';
import { formatUsd } from '@/lib/money';

export function Header() {
  const t = useTranslations('common');
  const nav = useTranslations('nav');
  const { user, clearAuth } = useAuthStore();
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const accountRef = useRef<HTMLDivElement>(null);

  const { data: settings } = useQuery({
    queryKey: queryKeys.settings.public,
    queryFn: async () => {
      const res = await apiClient<PublicSettingsDTO>('/settings/public');
      return res.data;
    },
  });

  const freeShippingThreshold = settings?.freeShippingOverCents
    ? formatUsd(settings.freeShippingOverCents)
    : '$50.00';

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (accountRef.current && !accountRef.current.contains(e.target as Node)) {
        setAccountMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = async () => {
    try {
      await apiClient('/auth/logout', { method: 'POST' });
    } catch {
      // ignore
    } finally {
      clearAuth();
      setAccountMenuOpen(false);
    }
  };

  return (
    <header className="sticky top-0 z-40 w-full shadow-sm">
      {/* Top Announcement Bar */}
      <div className="bg-secondary text-secondary-foreground text-xs font-medium py-1.5 px-4 text-center flex items-center justify-center gap-2">
        <Sparkles className="w-3.5 h-3.5 text-accent" />
        <span>{t('freeShippingNotice', { amount: freeShippingThreshold })}</span>
      </div>

      {/* Main Navigation Bar */}
      <div className="bg-surface border-b border-border">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
          {/* Logo & Brand */}
          <Link href="/" className="flex items-center gap-2 flex-shrink-0">
            <div className="w-9 h-9 rounded-btn bg-primary flex items-center justify-center text-primary-foreground font-black text-lg shadow-sm">
              K
            </div>
            <div className="flex flex-col">
              <span className="font-extrabold text-lg tracking-tight text-foreground leading-none">
                Khmer<span className="text-primary">Mart</span>
              </span>
              <span className="text-[10px] text-muted font-medium tracking-wide">
                កម្ពុជា · CAMBODIA
              </span>
            </div>
          </Link>

          {/* Search Bar (Desktop / Tablet) */}
          <div className="hidden sm:flex flex-1 max-w-xl mx-2">
            <SearchBar />
          </div>

          {/* Right Action Icons */}
          <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
            {/* Language Switcher */}
            <LocaleSwitcher />

            {/* Notification Bell */}
            <NotificationBell />

            {/* Cart Button */}
            <CartButton />

            {/* User Account Menu */}
            <div ref={accountRef} className="relative">
              {user ? (
                <div>
                  <button
                    data-testid="account-menu-button"
                    onClick={() => setAccountMenuOpen(!accountMenuOpen)}
                    aria-expanded={accountMenuOpen}
                    className="flex items-center gap-1.5 p-1.5 rounded-btn hover:bg-muted/10 text-foreground transition-colors"
                  >
                    <div className="w-7 h-7 rounded-chip bg-primary/10 text-primary font-bold text-xs flex items-center justify-center">
                      {user.fullName.charAt(0).toUpperCase()}
                    </div>
                    <span
                      data-testid="header-user-name"
                      className="hidden sm:inline-block text-xs font-semibold max-w-[120px] truncate"
                    >
                      {user.fullName}
                    </span>
                    <ChevronDown className="w-3.5 h-3.5 text-muted" />
                  </button>

                  {accountMenuOpen && (
                    <div className="absolute right-0 mt-2 w-52 bg-surface border border-border rounded-card shadow-lg py-1.5 z-50">
                      <div className="px-3 py-2 border-b border-border">
                        <p className="text-xs font-semibold text-foreground truncate">{user.fullName}</p>
                        <p className="text-[11px] text-muted truncate">{user.email}</p>
                      </div>

                      {(user.role === 'ADMIN' || user.role === 'STAFF') && (
                        <Link
                          href="/admin/dashboard"
                          onClick={() => setAccountMenuOpen(false)}
                          className="flex items-center gap-2 px-3 py-2 text-xs text-foreground hover:bg-muted/10"
                        >
                          <ShieldCheck className="w-4 h-4 text-primary" />
                          <span>{nav('admin')}</span>
                        </Link>
                      )}

                      <Link
                        href="/account/orders"
                        onClick={() => setAccountMenuOpen(false)}
                        className="flex items-center gap-2 px-3 py-2 text-xs text-foreground hover:bg-muted/10"
                      >
                        <Package className="w-4 h-4 text-muted" />
                        <span>{t('orders')}</span>
                      </Link>

                      <Link
                        href="/account"
                        onClick={() => setAccountMenuOpen(false)}
                        className="flex items-center gap-2 px-3 py-2 text-xs text-foreground hover:bg-muted/10"
                      >
                        <User className="w-4 h-4 text-muted" />
                        <span>{t('account')}</span>
                      </Link>

                      <button
                        data-testid="logout-button"
                        onClick={handleLogout}
                        className="w-full flex items-center gap-2 px-3 py-2 text-xs text-destructive hover:bg-destructive/10 border-t border-border mt-1"
                      >
                        <LogOut className="w-4 h-4" />
                        <span>{t('logout')}</span>
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <Link
                    href="/login"
                    className="text-xs font-semibold px-3 py-1.5 rounded-btn bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm"
                  >
                    {t('login')}
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Mobile Search Bar Row */}
        <div className="sm:hidden px-4 pb-2.5">
          <SearchBar />
        </div>

        {/* Secondary Category / Quick Links Strip */}
        <nav className="border-t border-border/60 bg-surface/50 text-xs hidden md:block">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-10 flex items-center justify-between">
            <div className="flex items-center gap-6 font-medium text-muted hover:text-foreground">
              <Link href="/products" className="hover:text-primary transition-colors">
                {nav('products')}
              </Link>
              <Link href="/products?onSale=true" className="text-primary font-semibold hover:underline flex items-center gap-1">
                <span>{nav('flashSale')}</span>
                <span className="bg-accent text-accent-foreground text-[10px] px-1.5 py-0.2 rounded-chip font-bold">
                  HOT
                </span>
              </Link>
              <Link href="/track" className="hover:text-primary transition-colors">
                {nav('trackOrder')}
              </Link>
            </div>

            <div className="flex items-center gap-4 text-muted">
              <span className="text-[11px]">{nav('khqrBanner')}</span>
            </div>
          </div>
        </nav>
      </div>
    </header>
  );
}
