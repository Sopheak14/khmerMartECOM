'use client';

import React from 'react';
import { SafeImage } from '@/components/common/SafeImage';
import { Link } from '@/navigation';
import { Badge } from '@/components/ui/badge';
import { Price } from '@/components/common/Price';
import { ProductCardDTO } from '@/types/api';
import { Star } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { getImageUrl } from '@/lib/images';

export interface ProductCardProps {
  product: ProductCardDTO;
  khrRate?: number;
  locale?: string;
}

export function ProductCard({ product, khrRate = 4100, locale = 'en' }: ProductCardProps) {
  const common = useTranslations('common');
  const t = useTranslations('product');
  const name = locale === 'km' && product.nameKm ? product.nameKm : product.name;

  return (
    <div
      data-testid="product-card"
      className="group rounded-card bg-surface border border-border p-3.5 sm:p-4 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
    >
      <div className="space-y-3">
        {/* Top Badges & Stock */}
        <div className="flex items-center justify-between gap-1 min-h-[22px]">
          <div className="flex items-center gap-1 flex-wrap">
            {product.badges && product.badges.length > 0 ? (
              product.badges.map((badge) => {
                const isFlash = badge === 'FLASH_SALE';
                const isNew = badge === 'NEW';
                return (
                  <Badge
                    key={badge}
                    variant={isFlash ? 'accent' : isNew ? 'default' : 'secondary'}
                    className="text-[10px] uppercase font-bold py-0.5 px-1.5"
                  >
                    {badge.replace('_', ' ')}
                  </Badge>
                );
              })
            ) : (
              <span />
            )}
          </div>
          <span className={`text-[10px] font-semibold ${product.inStock ? 'text-success' : 'text-destructive'}`}>
            {product.inStock ? common('inStock') : common('outOfStock')}
          </span>
        </div>

        {/* Product Image */}
        <Link href={`/products/${product.slug}`} className="block relative aspect-square w-full rounded-btn overflow-hidden bg-muted/10 group-hover:opacity-95 transition-opacity">
          {product.imageUrl ? (
            <SafeImage
              src={getImageUrl(product.imageUrl)}
              alt={name}
              fill
              sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
              className="object-cover group-hover:scale-105 transition-transform duration-300"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-muted text-xs">
              {name}
            </div>
          )}
        </Link>

        {/* Brand & Name */}
        <div className="space-y-1">
          <p className="text-[11px] font-medium text-muted uppercase tracking-wider">
            {product.brand}
          </p>
          <Link href={`/products/${product.slug}`} className="block">
            <h3 className="text-xs sm:text-sm font-bold text-foreground line-clamp-2 hover:text-primary transition-colors min-h-[2rem]">
              {name}
            </h3>
          </Link>
        </div>

        {/* Rating & Reviews */}
        {(!product.reviewCount || product.reviewCount === 0) ? (
          <div className="text-[11px] text-muted py-0.5">
            {t('noReviewsYet')}
          </div>
        ) : (
          <div className="flex items-center gap-1.5 text-xs text-muted">
            <div className="flex items-center text-amber-500">
              <Star className="w-3.5 h-3.5 fill-current" />
              <span className="ml-1 text-[11px] font-bold text-foreground">
                {product.avgRating && product.avgRating > 0 ? Number(product.avgRating).toFixed(1) : '5.0'}
              </span>
            </div>
            <span className="text-[10px] text-muted">
              ({product.reviewCount})
            </span>
          </div>
        )}
      </div>

      {/* Pricing */}
      <div className="pt-3 border-t border-border/60 mt-3">
        <Price
          cents={product.priceCents}
          compareAtCents={product.compareAtCents}
          salePriceCents={product.salePriceCents}
          khrRate={khrRate}
          size="sm"
        />
      </div>
    </div>
  );
}
