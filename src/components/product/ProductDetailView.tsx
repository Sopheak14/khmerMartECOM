'use client';

import React, { useState, useMemo } from 'react';
import { useTranslations, useLocale } from 'next-intl';
import { Link } from '@/navigation';
import { ProductDetailDTO, ReviewDTO, VariantDTO } from '@/types/api';
import { ProductGallery } from './ProductGallery';
import { FlashSalePriceCountdown } from './FlashSalePriceCountdown';
import { VariantSelector } from './VariantSelector';
import { RatingSummary } from './RatingSummary';
import { ReviewsList } from './ReviewsList';
import { ProductCard } from '@/components/common/ProductCard';
import {
  ChevronRight,
  Home,
  ShieldCheck,
  Truck,
  RotateCcw,
  Minus,
  Plus,
  ShoppingCart,
  AlertCircle,
} from 'lucide-react';

interface ProductDetailViewProps {
  product: ProductDetailDTO;
  reviews?: ReviewDTO[];
  khrRate: number;
}

export function ProductDetailView({ product, reviews = [], khrRate }: ProductDetailViewProps) {
  const t = useTranslations('product');
  const locale = useLocale();

  // Initial selected variant: default to first variant if exists
  const initialSelectedAttributes: Record<string, string> = useMemo(() => {
    if (product.variants && product.variants.length > 0) {
      return { ...product.variants[0].attributes };
    }
    return {};
  }, [product.variants]);

  const [selectedAttributes, setSelectedAttributes] = useState<Record<string, string>>(
    initialSelectedAttributes
  );

  const [quantity, setQuantity] = useState(1);

  // Find matching variant based on currently selected attributes
  const activeVariant: VariantDTO | undefined = useMemo(() => {
    if (!product.variants || product.variants.length === 0) return undefined;
    return product.variants.find((v) =>
      Object.entries(selectedAttributes).every(([k, val]) => v.attributes[k] === val)
    );
  }, [product.variants, selectedAttributes]);

  // Determine current effective price and available quantity
  // If flash sale is active on base product and no higher-tier variant is explicitly selected with higher price:
  // Note: selecting 32GB/1TB changes price to $1,299.00 and stock to 10
  const effectivePriceCents = useMemo(() => {
    if (activeVariant) {
      // If base product has flash sale and variant is default tier (e.g. 16GB/512GB), show sale price
      if (
        product.salePriceCents &&
        product.flashSaleEndsAt &&
        activeVariant.priceCents <= product.priceCents
      ) {
        return product.salePriceCents;
      }
      return activeVariant.priceCents;
    }
    return product.salePriceCents || product.priceCents;
  }, [activeVariant, product.salePriceCents, product.flashSaleEndsAt, product.priceCents]);

  const originalPriceCents = useMemo(() => {
    if (
      product.salePriceCents &&
      product.flashSaleEndsAt &&
      effectivePriceCents === product.salePriceCents
    ) {
      return product.priceCents;
    }
    return product.compareAtCents;
  }, [product.salePriceCents, product.flashSaleEndsAt, product.priceCents, product.compareAtCents, effectivePriceCents]);

  const maxAvailable = activeVariant ? activeVariant.available : product.available;
  const inStock = maxAvailable > 0;

  const handleSelectAttribute = (attrName: string, val: string) => {
    const next = { ...selectedAttributes, [attrName]: val };

    // If this direct selection doesn't form a valid variant, find the first variant that matches this new val
    const exactMatch = product.variants?.some((v) =>
      Object.entries(next).every(([k, vVal]) => v.attributes[k] === vVal)
    );

    if (!exactMatch && product.variants) {
      const fallbackVariant = product.variants.find((v) => v.attributes[attrName] === val);
      if (fallbackVariant) {
        setSelectedAttributes({ ...fallbackVariant.attributes });
        setQuantity(1);
        return;
      }
    }

    setSelectedAttributes(next);
    setQuantity(1);
  };

  const handleDecreaseQty = () => {
    setQuantity((prev) => Math.max(1, prev - 1));
  };

  const handleIncreaseQty = () => {
    setQuantity((prev) => Math.min(maxAvailable, prev + 1));
  };

  const displayName = locale === 'km' && product.nameKm ? product.nameKm : product.name;
  const categoryName =
    product.category &&
    (locale === 'km' && product.category.nameKm ? product.category.nameKm : product.category.name);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-12">
      {/* Breadcrumb Navigation */}
      <nav
        data-testid="product-breadcrumbs"
        aria-label="Breadcrumb"
        className="flex items-center gap-1.5 text-xs text-muted flex-wrap"
      >
        <Link
          href="/"
          className="flex items-center gap-1 hover:text-primary transition-colors font-medium"
        >
          <Home className="w-3.5 h-3.5" />
          <span>{locale === 'km' ? 'ទំព័រដើម' : 'Home'}</span>
        </Link>

        <ChevronRight className="w-3 h-3 text-muted/60" />

        <Link href="/products" className="hover:text-primary transition-colors font-medium">
          {locale === 'km' ? 'ទំនិញទាំងអស់' : 'Products'}
        </Link>

        {product.category && (
          <>
            <ChevronRight className="w-3 h-3 text-muted/60" />
            <Link
              href={`/category/${product.category.slug}`}
              className="hover:text-primary transition-colors font-medium"
            >
              {categoryName}
            </Link>
          </>
        )}

        <ChevronRight className="w-3 h-3 text-muted/60" />
        <span className="text-foreground font-bold truncate max-w-xs">{displayName}</span>
      </nav>

      {/* Main Top Section: Gallery + Product Info */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12">
        {/* Left: Gallery */}
        <div className="lg:col-span-6">
          <ProductGallery images={product.images || []} productName={displayName} />
        </div>

        {/* Right: Product Details & Actions */}
        <div className="lg:col-span-6 space-y-6">
          {/* Brand & Badges */}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-primary bg-primary/10 px-2.5 py-1 rounded-btn">
              {product.brand}
            </span>
            <div className="flex items-center gap-1.5">
              {product.badges?.map((badge) => (
                <span
                  key={badge}
                  className="text-[10px] font-bold uppercase tracking-wider bg-accent text-accent-foreground px-2 py-0.5 rounded-full"
                >
                  {badge.replace('_', ' ')}
                </span>
              ))}
            </div>
          </div>

          {/* Product Title */}
          <h1
            data-testid="product-detail-title"
            className="text-2xl sm:text-3xl font-black text-foreground tracking-tight leading-tight"
          >
            {displayName}
          </h1>

          {/* SKU & Short rating preview */}
          <div className="flex items-center gap-4 text-xs text-muted border-b border-border pb-4">
            <span>
              {t('sku')}: <strong className="text-foreground font-mono">{activeVariant?.sku || product.sku}</strong>
            </span>
            <span>•</span>
            {(!product.reviewCount || product.reviewCount === 0) ? (
              <span className="text-muted">{t('noReviewsYet')}</span>
            ) : (
              <div className="flex items-center gap-1">
                <span className="font-bold text-foreground">★ {product.avgRating?.toFixed(1) || '0.0'}</span>
                <span>({product.reviewCount} {t('reviewsList')})</span>
              </div>
            )}
          </div>

          {/* Price & Flash Sale Countdown */}
          <FlashSalePriceCountdown
            priceCents={effectivePriceCents}
            originalPriceCents={originalPriceCents}
            flashSaleEndsAt={product.flashSaleEndsAt}
            khrRate={khrRate}
          />

          {/* Variant Selector */}
          {product.variantOptions && (
            <div className="border-t border-border pt-4">
              <VariantSelector
                variantOptions={product.variantOptions}
                variants={product.variants || []}
                selectedAttributes={selectedAttributes}
                onSelectAttribute={handleSelectAttribute}
              />
            </div>
          )}

          {/* Stock Status */}
          <div className="space-y-1" data-testid="stock-status">
            {inStock ? (
              <div className="flex items-center gap-2 text-xs font-semibold text-green-600 dark:text-green-400">
                <span className="w-2 h-2 rounded-full bg-green-500 animate-ping" />
                <span>
                  {maxAvailable <= 5
                    ? t('lowStock', { count: maxAvailable })
                    : `${t('inStock')} ${t('availableCount', { count: maxAvailable })}`}
                </span>
              </div>
            ) : (
              <div className="flex items-center gap-1.5 text-xs font-semibold text-red-500">
                <AlertCircle className="w-3.5 h-3.5" />
                <span>{t('outOfStock')}</span>
              </div>
            )}
          </div>

          {/* Quantity Selector & Add to Cart */}
          <div className="space-y-3 pt-2">
            <div className="flex items-center gap-4">
              {/* Quantity counter */}
              <div
                data-testid="quantity-selector"
                className="flex items-center border border-border rounded-btn bg-surface overflow-hidden"
              >
                <button
                  type="button"
                  data-testid="qty-minus-btn"
                  onClick={handleDecreaseQty}
                  disabled={quantity <= 1 || !inStock}
                  className="w-9 h-10 flex items-center justify-center text-muted hover:text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <Minus className="w-3.5 h-3.5" />
                </button>
                <span
                  data-testid="qty-display"
                  className="w-12 text-center text-sm font-bold text-foreground"
                >
                  {quantity}
                </span>
                <button
                  type="button"
                  data-testid="qty-plus-btn"
                  onClick={handleIncreaseQty}
                  disabled={quantity >= maxAvailable || !inStock}
                  className="w-9 h-10 flex items-center justify-center text-muted hover:text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Add to Cart Disabled Placeholder */}
              <button
                type="button"
                data-testid="add-to-cart-button"
                disabled
                aria-disabled="true"
                className="flex-1 h-11 px-6 rounded-btn bg-primary/40 text-primary-foreground text-sm font-bold flex items-center justify-center gap-2 cursor-not-allowed opacity-75 shadow-xs"
              >
                <ShoppingCart className="w-4 h-4" />
                <span>{t('addToCartDisabled')}</span>
              </button>
            </div>
            <p className="text-[11px] text-muted text-center sm:text-left italic">
              {t('phaseNotice')}
            </p>
          </div>

          {/* Trust Guarantees */}
          <div className="grid grid-cols-3 gap-2 pt-4 border-t border-border text-center">
            <div className="p-2.5 rounded-btn bg-muted/10 space-y-1">
              <Truck className="w-4 h-4 mx-auto text-primary" />
              <span className="text-[10px] font-semibold text-foreground block">
                {locale === 'km' ? 'ដឹកជញ្ជូនរហ័សទូទាំង ២៥ ខេត្តក្រុង' : 'Fast 25-Province Delivery'}
              </span>
            </div>
            <div className="p-2.5 rounded-btn bg-muted/10 space-y-1">
              <ShieldCheck className="w-4 h-4 mx-auto text-primary" />
              <span className="text-[10px] font-semibold text-foreground block">
                {locale === 'km' ? 'ធានាទំនិញពិត ១០០%' : '100% Genuine Guaranteed'}
              </span>
            </div>
            <div className="p-2.5 rounded-btn bg-muted/10 space-y-1">
              <RotateCcw className="w-4 h-4 mx-auto text-primary" />
              <span className="text-[10px] font-semibold text-foreground block">
                {locale === 'km' ? 'សងប្រាក់វិញក្នុង ៧ ថ្ងៃ' : '7-Day Return Guarantee'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Description Section */}
      <div className="border-t border-border pt-8 space-y-4">
        <h2 className="text-xl font-bold text-foreground">{t('description')}</h2>
        <div className="prose dark:prose-invert max-w-none text-sm text-muted leading-relaxed">
          <p>{product.description}</p>
        </div>
      </div>

      {/* Customer Reviews & Rating Breakdown */}
      <div className="space-y-8 border-t border-border pt-8">
        <RatingSummary
          avgRating={product.avgRating}
          reviewCount={product.reviewCount}
          ratingBreakdown={product.ratingBreakdown}
        />

        <ReviewsList reviews={reviews} />
      </div>

      {/* Related Products Grid */}
      {product.related && product.related.length > 0 && (
        <div className="space-y-6 border-t border-border pt-8" data-testid="related-products-section">
          <div className="flex items-center justify-between">
            <h2 className="text-xl sm:text-2xl font-bold text-foreground">
              {t('relatedProducts')}
            </h2>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-6">
            {product.related.map((rel) => (
              <ProductCard key={rel.id} product={rel} khrRate={khrRate} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
