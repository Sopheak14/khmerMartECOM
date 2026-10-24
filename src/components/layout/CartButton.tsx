'use client';

import { useTranslations } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { ShoppingCart } from 'lucide-react';
import { Link } from '@/navigation';
import { queryKeys } from '@/lib/queryKeys';
import { apiClient } from '@/lib/apiClient';
import { CartDTO } from '@/types/api';

export function CartButton() {
  const t = useTranslations('common');
  const { data: cart } = useQuery({
    queryKey: queryKeys.cart.current,
    queryFn: async () => {
      const res = await apiClient<CartDTO>('/cart');
      return res.data;
    },
  });

  const count = cart?.itemCount || 0;

  return (
    <Link
      href="/cart"
      aria-label="View shopping cart"
      className="relative flex items-center gap-2 p-2 rounded-btn hover:bg-muted/10 text-foreground transition-colors"
    >
      <div className="relative">
        <ShoppingCart className="w-5 h-5 text-muted hover:text-foreground transition-colors" />
        {count > 0 && (
          <span className="absolute -top-1.5 -right-1.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-primary-foreground bg-primary rounded-chip ring-2 ring-surface animate-in zoom-in-75">
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
