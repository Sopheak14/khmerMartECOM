'use client';

import { useTranslations } from 'next-intl';
import { Link, usePathname } from '@/navigation';
import { Home, Grid, ShoppingCart, Package, User } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient } from '@/lib/apiClient';
import { CartDTO } from '@/types/api';
import { cn } from '@/lib/utils';

export function MobileNav() {
  const t = useTranslations('nav');
  const pathname = usePathname();

  const { data: cart } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
  });

  const cartCount = cart?.itemCount || 0;

  const links = [
    { href: '/', label: t('home'), icon: Home },
    { href: '/products', label: t('products'), icon: Grid },
    { href: '/cart', label: t('cart'), icon: ShoppingCart, badge: cartCount },
    { href: '/account/orders', label: t('orders'), icon: Package },
    { href: '/account', label: t('account'), icon: User },
  ];

  return (
    <nav className="sm:hidden fixed bottom-0 left-0 right-0 z-40 bg-surface border-t border-border shadow-lg">
      <div className="grid grid-cols-5 h-14">
        {links.map((link) => {
          const Icon = link.icon;
          const isActive = pathname === link.href || (link.href !== '/' && pathname.startsWith(link.href));

          return (
            <Link
              key={link.href}
              href={link.href}
              className={cn(
                'flex flex-col items-center justify-center relative text-[10px] font-medium transition-colors',
                isActive ? 'text-primary' : 'text-muted hover:text-foreground'
              )}
            >
              <div className="relative">
                <Icon className="w-5 h-5 mb-0.5" />
                {link.badge && link.badge > 0 ? (
                  <span className="absolute -top-1.5 -right-2 flex items-center justify-center min-w-[16px] h-[16px] px-1 text-[9px] font-bold text-primary-foreground bg-primary rounded-chip">
                    {link.badge > 99 ? '99+' : link.badge}
                  </span>
                ) : null}
              </div>
              <span className="truncate max-w-[50px]">{link.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
