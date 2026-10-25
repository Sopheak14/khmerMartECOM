'use client';

import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { ShoppingCart } from 'lucide-react';
import { Link } from '@/navigation';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient } from '@/lib/apiClient';
import { CartDTO } from '@/types/api';
import { useCartDrawerStore } from '@/lib/store/useCartDrawerStore';

export function CartButton() {
  const t = useTranslations('common');
  const openDrawer = useCartDrawerStore((state) => state.open);
  const { data: cart } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
  });

  const count = cart?.itemCount || 0;

  const handleClick = (e: React.MouseEvent) => {
    if (!e.metaKey && !e.ctrlKey) {
      e.preventDefault();
      openDrawer();
    }
  };

  return (
    <Link
      href="/cart"
      onClick={handleClick}
      aria-label="View shopping cart"
      data-testid="header-cart-button"
      className="relative flex items-center gap-2 p-2 rounded-btn hover:bg-muted/10 text-foreground transition-colors"
    >
      <div className="relative">
        <ShoppingCart className="w-5 h-5 text-muted hover:text-foreground transition-colors" />
        {count > 0 && (
          <span
            data-testid="header-cart-count"
            className="absolute -top-1.5 -right-1.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-primary-foreground bg-primary rounded-chip ring-2 ring-surface animate-in zoom-in-75"
          >
            {count > 99 ? '99+' : count}
          </span>
        )}
      </div>
      <span className="hidden md:inline-block text-xs font-semibold">
        {t('cart')}
      </span>
    </Link>
  );
}
