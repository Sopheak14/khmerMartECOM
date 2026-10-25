'use client';

import React, { useState, useRef, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Link, useRouter } from '@/navigation';
import { LocaleSwitcher } from '@/components/layout/LocaleSwitcher';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { apiClient } from '@/lib/apiClient';
import {
  Menu,
  Search,
  ExternalLink,
  LogOut,
  ChevronDown,
  Shield,
  User as UserIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface AdminTopBarProps {
  onToggleMobileSidebar: () => void;
}

export function AdminTopBar({ onToggleMobileSidebar }: AdminTopBarProps) {
  const t = useTranslations('admin');
  const { user, clearAuth } = useAuthStore();
  const router = useRouter();
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
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
      setDropdownOpen(false);
      router.replace('/login');
    }
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    router.push(`/admin/orders?q=${encodeURIComponent(searchQuery.trim())}`);
  };

  const roleColor =
    user?.role === 'ADMIN'
      ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20'
      : 'bg-purple-500/10 text-purple-600 border-purple-500/20';

  return (
    <header className="h-16 bg-surface border-b border-border px-4 sm:px-6 flex items-center justify-between gap-4 sticky top-0 z-30 shadow-2xs">
      {/* Left: Mobile hamburger & Global Search */}
      <div className="flex items-center gap-3 flex-1 max-w-md">
        <button
          type="button"
          onClick={onToggleMobileSidebar}
          aria-label="Toggle navigation menu"
          className="p-2 rounded-lg text-muted hover:text-foreground hover:bg-muted/10 lg:hidden"
        >
          <Menu className="w-5 h-5" />
        </button>

        <form onSubmit={handleSearchSubmit} className="relative w-full hidden sm:block">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            type="search"
            data-testid="admin-topbar-search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('searchPlaceholder')}
            className="w-full pl-9 pr-4 py-1.5 rounded-lg border border-border bg-background text-xs text-foreground placeholder:text-muted/60 focus:outline-hidden focus:ring-2 focus:ring-primary/20"
          />
        </form>
      </div>

      {/* Right: Language switch & User menu */}
      <div className="flex items-center gap-2 sm:gap-3">
        <LocaleSwitcher />

        {/* User Account Menu */}
        <div ref={menuRef} className="relative">
          <button
            type="button"
            data-testid="admin-user-menu-button"
            onClick={() => setDropdownOpen(!dropdownOpen)}
            className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-muted/10 transition-colors"
          >
            <div className="w-7 h-7 rounded-full bg-primary/10 text-primary font-bold text-xs flex items-center justify-center">
              {user?.fullName?.charAt(0).toUpperCase() || 'A'}
            </div>
            <div className="hidden md:block text-left">
              <span className="text-xs font-semibold text-foreground block leading-tight">
                {user?.fullName || 'Backoffice User'}
              </span>
              <span
                className={cn(
                  'text-[10px] font-bold px-1.5 py-0.2 rounded border inline-block mt-0.5',
                  roleColor
                )}
              >
                {user?.role === 'ADMIN' ? t('roleAdmin') : t('roleStaff')}
              </span>
            </div>
            <ChevronDown className="w-3.5 h-3.5 text-muted" />
          </button>

          {dropdownOpen && (
            <div
              data-testid="admin-user-dropdown"
              className="absolute right-0 mt-2 w-56 bg-surface border border-border rounded-xl shadow-lg py-1.5 z-50 text-xs animate-in zoom-in-95"
            >
              <div className="px-3.5 py-2 border-b border-border space-y-0.5">
                <p className="font-semibold text-foreground truncate">{user?.fullName}</p>
                <p className="text-[11px] text-muted truncate">{user?.email}</p>
              </div>

              <div className="py-1">
                <Link
                  href="/"
                  target="_blank"
                  onClick={() => setDropdownOpen(false)}
                  className="flex items-center gap-2.5 px-3.5 py-2 text-foreground hover:bg-muted/10 transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5 text-muted" />
                  <span>{t('viewStorefront')}</span>
                </Link>
              </div>

              <div className="border-t border-border pt-1">
                <button
                  type="button"
                  data-testid="admin-logout-btn"
                  onClick={handleLogout}
                  className="w-full flex items-center gap-2.5 px-3.5 py-2 text-destructive hover:bg-destructive/10 transition-colors cursor-pointer"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>{t('logout')}</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
