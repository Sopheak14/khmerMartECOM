'use client';

import { useTranslations } from 'next-intl';
import { Link } from '@/navigation';
import { ShieldCheck, Truck, Headphones, QrCode } from 'lucide-react';

export function Footer() {
  const common = useTranslations('common');
  const t = useTranslations('footer');
  const isMock = process.env.NEXT_PUBLIC_API_MOCK === 'true';

  return (
    <footer className="bg-surface border-t border-border mt-16 text-xs text-muted pb-14 sm:pb-0">
      {/* Trust Highlights Section */}
      <div className="border-b border-border/80 bg-background/50 py-8">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-btn bg-primary/10 text-primary flex items-center justify-center flex-shrink-0">
                <QrCode className="w-5 h-5" />
              </div>
              <div>
                <p className="font-semibold text-foreground text-sm">{t('trustKhqrTitle')}</p>
                <p className="text-muted text-xs">{t('trustKhqrDesc')}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-btn bg-success/10 text-success flex items-center justify-center flex-shrink-0">
                <Truck className="w-5 h-5" />
              </div>
              <div>
                <p className="font-semibold text-foreground text-sm">{t('trustDeliveryTitle')}</p>
                <p className="text-muted text-xs">{t('trustDeliveryDesc')}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-btn bg-info/10 text-info flex items-center justify-center flex-shrink-0">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <div>
                <p className="font-semibold text-foreground text-sm">{t('trustWarrantyTitle')}</p>
                <p className="text-muted text-xs">{t('trustWarrantyDesc')}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-btn bg-warning/10 text-warning flex items-center justify-center flex-shrink-0">
                <Headphones className="w-5 h-5" />
              </div>
              <div>
                <p className="font-semibold text-foreground text-sm">{t('trustSupportTitle')}</p>
                <p className="text-muted text-xs">{t('trustSupportDesc')}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Main Footer Links */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* About */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-btn bg-primary flex items-center justify-center text-primary-foreground font-black text-sm">
                K
              </div>
              <span className="font-extrabold text-base text-foreground">
                Khmer<span className="text-primary">Mart</span>
              </span>
            </div>
            <p className="text-xs leading-relaxed text-muted">
              {common('footerAbout')}
            </p>
          </div>

          {/* Quick links */}
          <div className="space-y-3">
            <p className="font-bold text-foreground text-sm">{t('quickLinks')}</p>
            <ul className="space-y-2">
              <li>
                <Link href="/products" className="hover:text-primary transition-colors">
                  {t('allProducts')}
                </Link>
              </li>
              <li>
                <Link href="/products?onSale=true" className="hover:text-primary transition-colors">
                  {t('flashSales')}
                </Link>
              </li>
              <li>
                <Link href="/track" className="hover:text-primary transition-colors">
                  {t('trackDelivery')}
                </Link>
              </li>
              <li>
                <Link href="/account/orders" className="hover:text-primary transition-colors">
                  {t('myPurchases')}
                </Link>
              </li>
            </ul>
          </div>

          {/* Customer Service */}
          <div className="space-y-3">
            <p className="font-bold text-foreground text-sm">{t('customerService')}</p>
            <ul className="space-y-2">
              <li>{t('emailLabel')}</li>
              <li>{t('phoneLabel')}</li>
              <li>{t('locationLabel')}</li>
              <li>{t('returnsPolicy')}</li>
            </ul>
          </div>

          {/* Payment Methods */}
          <div className="space-y-3">
            <p className="font-bold text-foreground text-sm">{t('paymentMethods')}</p>
            <div className="flex flex-wrap gap-2 text-[11px] font-semibold text-foreground">
              <span className="px-2.5 py-1 rounded bg-muted/10 border border-border">
                {t('paymentKhqr')}
              </span>
              <span className="px-2.5 py-1 rounded bg-muted/10 border border-border">
                {t('paymentCod')}
              </span>
              <span className="px-2.5 py-1 rounded bg-muted/10 border border-border">
                {t('paymentBank')}
              </span>
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="border-t border-border mt-8 pt-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-xs">
            © {new Date().getFullYear()} KhmerMart. {t('rightsReserved')}
          </p>

          <div className="flex items-center gap-3">
            {isMock && (
              <span className="inline-flex items-center px-2 py-0.5 rounded-chip text-[10px] font-bold bg-warning/20 text-warning border border-warning/30">
                MOCK API MODE
              </span>
            )}
            <span className="text-[11px] text-muted">
              {t('builtWith')}
            </span>
          </div>
        </div>
      </div>
    </footer>
  );
}
