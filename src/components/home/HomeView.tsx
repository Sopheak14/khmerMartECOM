'use client';

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useLocale, useTranslations } from 'next-intl';
import Image from 'next/image';
import { Link } from '@/navigation';
import {
  ArrowRight,
  Sparkles,
  QrCode,
  Truck,
  ShieldCheck,
  HeartHandshake,
  Flame,
} from 'lucide-react';
import { buttonVariants } from '@/components/ui/button';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { HomeDTO, PublicSettingsDTO } from '@/types/api';
import { ProductCard } from '@/components/common/ProductCard';
import { FlashSaleCountdown } from '@/components/home/FlashSaleCountdown';
import { HomeSkeleton } from '@/components/home/HomeSkeleton';

export function HomeView() {
  const locale = useLocale();
  const t = useTranslations('home');
  const common = useTranslations('common');

  const { data: homeData, isLoading: isHomeLoading } = useQuery({
    queryKey: queryKeys.home.feed,
    queryFn: async () => {
      const res = await apiClient<HomeDTO>('/home');
      return res.data;
    },
  });

  const { data: settingsData } = useQuery({
    queryKey: queryKeys.settings.public,
    queryFn: async () => {
      const res = await apiClient<PublicSettingsDTO>('/settings/public');
      return res.data;
    },
  });

  if (isHomeLoading || !homeData) {
    return <HomeSkeleton />;
  }

  const khrRate = settingsData?.khrRate || 4100;
  const seasonalBanner = homeData.seasonalBanner || settingsData?.seasonalBanner;
  const flashSale = homeData.flashSale;
  const categories = homeData.categories || [];
  const bestSellers = homeData.bestSellers || [];
  const newArrivals = homeData.newArrivals || [];

  const bannerTitle =
    locale === 'km' && seasonalBanner?.titleKm
      ? seasonalBanner.titleKm
      : seasonalBanner?.title || t('heroTitle');

  return (
    <div className="space-y-12">
      {/* Hero Banner Section with real API seasonalBanner */}
      <section className="relative overflow-hidden bg-secondary text-secondary-foreground py-16 sm:py-24">
        {seasonalBanner?.imageUrl && (
          <div className="absolute inset-0 z-0">
            <Image
              src={seasonalBanner.imageUrl}
              alt={bannerTitle}
              fill
              priority
              className="object-cover object-center opacity-30"
            />
            <div className="absolute inset-0 bg-gradient-to-r from-secondary via-secondary/90 to-secondary/60" />
          </div>
        )}

        <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-chip bg-accent/20 border border-accent/30 text-accent font-semibold text-xs">
              <Sparkles className="w-3.5 h-3.5" />
              <span>{t('flashSaleTitle')}</span>
            </div>

            <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight leading-tight drop-shadow-sm text-white">
              {bannerTitle}
            </h1>

            <p className="text-sm sm:text-base text-gray-200 leading-relaxed max-w-xl">
              {t('heroSubtitle')}
            </p>

            <div className="flex flex-wrap items-center gap-4 pt-2">
              <Link
                href="/products"
                className={buttonVariants({
                  variant: 'primary',
                  size: 'lg',
                  className: 'flex items-center gap-2 font-bold shadow-md',
                })}
              >
                <span>{t('shopNow')}</span>
                <ArrowRight className="w-4 h-4" />
              </Link>

              {/* Requirement 2: Outline button with transparent background, white border, white text, AA contrast */}
              <Link
                href="#flash-sale"
                className="inline-flex items-center justify-center rounded-btn px-6 py-3 text-base font-semibold bg-transparent border-2 border-white text-white hover:bg-white/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white transition-colors shadow-sm"
              >
                {t('exploreDeals')}
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Trust & Guarantee Cards */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="p-5 rounded-card bg-surface border border-border flex items-start gap-3.5 shadow-sm hover:shadow transition-shadow">
            <div className="w-10 h-10 rounded-btn bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
              <QrCode className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-foreground">{t('trustKhqr')}</h3>
              <p className="text-xs text-muted mt-0.5">{t('trustKhqrDesc')}</p>
            </div>
          </div>

          <div className="p-5 rounded-card bg-surface border border-border flex items-start gap-3.5 shadow-sm hover:shadow transition-shadow">
            <div className="w-10 h-10 rounded-btn bg-success/10 text-success flex items-center justify-center flex-shrink-0">
              <Truck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-foreground">{t('trustDelivery')}</h3>
              <p className="text-xs text-muted mt-0.5">{t('trustDeliveryDesc')}</p>
            </div>
          </div>

          <div className="p-5 rounded-card bg-surface border border-border flex items-start gap-3.5 shadow-sm hover:shadow transition-shadow">
            <div className="w-10 h-10 rounded-btn bg-info/10 text-info flex items-center justify-center flex-shrink-0">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-foreground">{t('trustWarranty')}</h3>
              <p className="text-xs text-muted mt-0.5">{t('trustWarrantyDesc')}</p>
            </div>
          </div>

          <div className="p-5 rounded-card bg-surface border border-border flex items-start gap-3.5 shadow-sm hover:shadow transition-shadow">
            <div className="w-10 h-10 rounded-btn bg-warning/10 text-warning flex items-center justify-center flex-shrink-0">
              <HeartHandshake className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-foreground">{t('trustSupport')}</h3>
              <p className="text-xs text-muted mt-0.5">{t('trustSupportDesc')}</p>
            </div>
          </div>
        </div>
      </section>

      {/* Featured Categories from API */}
      {categories.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                {t('featuredCategories')}
              </h2>
              <p className="text-xs text-muted mt-1">
                {t('featuredCategoriesSubtitle')}
              </p>
            </div>
            <Link
              href="/products"
              className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
            >
              <span>{common('viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3 sm:gap-4">
            {categories.map((cat) => {
              const catName =
                locale === 'km' && cat.nameKm ? cat.nameKm : cat.name;
              return (
                <Link
                  key={cat.id}
                  href={`/products?category=${cat.slug}`}
                  className="group rounded-card bg-surface border border-border p-3 text-center hover:border-primary hover:shadow-md transition-all flex flex-col items-center justify-center"
                >
                  <div className="relative w-14 h-14 rounded-full overflow-hidden bg-muted/20 mb-2 border border-border/80 group-hover:scale-105 transition-transform">
                    {cat.imageUrl ? (
                      <Image
                        src={cat.imageUrl}
                        alt={catName}
                        fill
                        sizes="56px"
                        className="object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-xs font-bold text-primary">
                        {catName.charAt(0)}
                      </div>
                    )}
                  </div>
                  <h3 className="text-xs font-bold text-foreground group-hover:text-primary transition-colors line-clamp-1">
                    {catName}
                  </h3>
                  <span className="text-[10px] text-muted mt-0.5">
                    {t('itemsCount', { count: cat.productCount })}
                  </span>
                </Link>
              );
            })}
          </div>
        </section>
      )}

      {/* Flash Sale Section with Countdown to endsAt */}
      {flashSale && flashSale.products && flashSale.products.length > 0 && (
        <section id="flash-sale" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
          <div className="rounded-card bg-gradient-to-r from-primary/10 via-accent/10 to-primary/5 border border-primary/20 p-4 sm:p-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <Flame className="w-5 h-5 text-primary animate-pulse" />
                  <h2 className="text-xl sm:text-2xl font-black text-foreground tracking-tight">
                    {t('flashSaleTitle')}
                  </h2>
                </div>
                <div className="flex items-center gap-2 pt-1">
                  <span className="text-xs font-semibold text-muted">
                    {t('flashSaleEndsIn')}
                  </span>
                  <FlashSaleCountdown endsAt={flashSale.endsAt} />
                </div>
              </div>

              <Link
                href="/products?onSale=true"
                className="text-xs font-bold text-primary hover:underline flex items-center gap-1 self-start sm:self-auto"
              >
                <span>{common('viewAll')}</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {flashSale.products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  khrRate={khrRate}
                  locale={locale}
                />
              ))}
            </div>
          </div>
        </section>
      )}

      {/* Best Sellers Section */}
      {bestSellers.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                {t('bestSellersTitle')}
              </h2>
              <p className="text-xs text-muted mt-1">
                {t('bestSellersSubtitle')}
              </p>
            </div>
            <Link
              href="/products"
              className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
            >
              <span>{common('viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {bestSellers.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                khrRate={khrRate}
                locale={locale}
              />
            ))}
          </div>
        </section>
      )}

      {/* New Arrivals Section */}
      {newArrivals.length > 0 && (
        <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl sm:text-2xl font-bold text-foreground">
                {t('newArrivalsTitle')}
              </h2>
              <p className="text-xs text-muted mt-1">
                {t('newArrivalsSubtitle')}
              </p>
            </div>
            <Link
              href="/products"
              className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
            >
              <span>{common('viewAll')}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {newArrivals.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                khrRate={khrRate}
                locale={locale}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
