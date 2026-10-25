'use client';

import React from 'react';
import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/navigation';
import { cn } from '@/lib/utils';
import {
  LayoutDashboard,
  ShoppingBag,
  CreditCard,
  Boxes,
  Package,
  FolderTree,
  Ticket,
  Sparkles,
  Star,
  Users,
  BarChart3,
  Settings,
  History,
  X,
  Shield,
} from 'lucide-react';

interface AdminSidebarProps {
  userRole?: string;
  isOpenMobile: boolean;
  onCloseMobile: () => void;
}

export function AdminSidebar({ userRole, isOpenMobile, onCloseMobile }: AdminSidebarProps) {
  const t = useTranslations('admin.nav');
  const pathname = usePathname();
  const isAdmin = userRole === 'ADMIN';

  // Section 1: Main
  const mainNav = [
    { href: '/admin', label: t('dashboard'), icon: LayoutDashboard, testId: 'admin-nav-dashboard' },
    { href: '/admin/orders', label: t('orders'), icon: ShoppingBag, testId: 'admin-nav-orders' },
    ...(isAdmin
      ? [{ href: '/admin/payments', label: t('payments'), icon: CreditCard, testId: 'admin-nav-payments', adminOnly: true }]
      : []),
    { href: '/admin/inventory', label: t('inventory'), icon: Boxes, testId: 'admin-nav-inventory' },
  ];

  // Section 2: Catalog & Marketing
  const catalogNav = [
    { href: '/admin/products', label: t('products'), icon: Package, testId: 'admin-nav-products' },
    { href: '/admin/categories', label: t('categories'), icon: FolderTree, testId: 'admin-nav-categories' },
    { href: '/admin/coupons', label: t('coupons'), icon: Ticket, testId: 'admin-nav-coupons' },
    { href: '/admin/promotions', label: t('promotions'), icon: Sparkles, testId: 'admin-nav-promotions' },
    { href: '/admin/reviews', label: t('reviews'), icon: Star, testId: 'admin-nav-reviews' },
  ];

  // Section 3: Administration (Admin Only)
  const adminNav = isAdmin
    ? [
        { href: '/admin/users', label: t('users'), icon: Users, testId: 'admin-nav-users', adminOnly: true },
        { href: '/admin/reports', label: t('reports'), icon: BarChart3, testId: 'admin-nav-reports', adminOnly: true },
        { href: '/admin/settings', label: t('settings'), icon: Settings, testId: 'admin-nav-settings', adminOnly: true },
        { href: '/admin/audit-logs', label: t('auditLogs'), icon: History, testId: 'admin-nav-audit-logs', adminOnly: true },
      ]
    : [];

  const isActive = (href: string) => {
    if (href === '/admin') {
      return pathname === '/admin';
    }
    return pathname.startsWith(href);
  };

  return (
    <>
      {/* Mobile backdrop */}
      {isOpenMobile && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs lg:hidden"
          onClick={onCloseMobile}
        />
      )}

      {/* Sidebar container */}
      <aside
        data-testid="admin-sidebar"
        className={cn(
          'fixed top-0 bottom-0 left-0 z-50 w-64 bg-slate-900 border-r border-slate-800 flex flex-col transition-transform duration-200 ease-in-out lg:translate-x-0 lg:static lg:z-auto',
          isOpenMobile ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Brand header */}
        <div className="h-16 px-6 border-b border-slate-800 flex items-center justify-between">
          <Link href="/admin" className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center text-primary-foreground font-black text-sm shadow-sm">
              K
            </div>
            <div>
              <span className="font-extrabold text-white text-sm tracking-tight block">
                Khmer<span className="text-primary">Mart</span>
              </span>
              <span className="text-[10px] uppercase font-bold tracking-widest text-slate-400 block -mt-0.5">
                Backoffice
              </span>
            </div>
          </Link>

          <button
            type="button"
            onClick={onCloseMobile}
            className="p-1 rounded-md text-slate-400 hover:text-white lg:hidden"
            aria-label="Close sidebar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation list */}
        <div className="flex-1 overflow-y-auto px-4 py-5 space-y-6">
          {/* Main Group */}
          <div className="space-y-1">
            <span className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              {t('main')}
            </span>
            {mainNav.map((item) => {
              const active = isActive(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  data-testid={item.testId}
                  onClick={onCloseMobile}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-semibold transition-colors',
                    active
                      ? 'bg-primary text-white shadow-xs'
                      : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                  )}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </div>

          {/* Catalog Group */}
          <div className="space-y-1">
            <span className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400">
              {t('catalogMarketing')}
            </span>
            {catalogNav.map((item) => {
              const active = isActive(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  data-testid={item.testId}
                  onClick={onCloseMobile}
                  className={cn(
                    'flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-semibold transition-colors',
                    active
                      ? 'bg-primary text-white shadow-xs'
                      : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                  )}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </div>

          {/* Administration Group (Admin Only) */}
          {adminNav.length > 0 && (
            <div className="space-y-1">
              <span className="px-3 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                {t('administration')}
              </span>
              {adminNav.map((item) => {
                const active = isActive(item.href);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    data-testid={item.testId}
                    onClick={onCloseMobile}
                    className={cn(
                      'flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-semibold transition-colors',
                      active
                        ? 'bg-primary text-white shadow-xs'
                        : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                    )}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                    <Shield className="w-3 h-3 ml-auto text-amber-400/80" />
                  </Link>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer info */}
        <div className="p-4 border-t border-slate-800 text-[11px] text-slate-400 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="font-mono text-[10px]">API :4000 ONLINE</span>
          </div>
          <span className="text-[10px] font-mono opacity-60">v1.0.0</span>
        </div>
      </aside>
    </>
  );
}
