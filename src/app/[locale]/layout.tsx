import type { Metadata } from 'next';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages, setRequestLocale } from 'next-intl/server';
import { notFound } from 'next/navigation';
import { kantumruy, inter } from '../fonts';
import '../globals.css';
import { QueryProvider } from '@/components/providers/QueryProvider';
import { AuthProvider } from '@/components/providers/AuthProvider';
import { CartDrawer } from '@/components/cart/CartDrawer';
import { Toaster } from 'sonner';
import { locales } from '@/i18n/request';

export const metadata: Metadata = {
  title: 'KhmerMart — Modern Cambodian Marketplace with KHQR',
  description: 'Shop authentic Cambodian silk, Kampot pepper, rice, coffee and handicrafts with instant KHQR payments.',
};

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export default async function RootLayout({
  children,
  params: { locale },
}: {
  children: React.ReactNode;
  params: { locale: string };
}) {
  if (!locales.includes(locale as any)) {
    notFound();
  }

  setRequestLocale(locale);
  const messages = await getMessages();

  return (
    <html lang={locale} className={`${kantumruy.variable} ${inter.variable}`}>
      <body className="min-h-screen flex flex-col font-sans antialiased bg-background text-foreground">
        <NextIntlClientProvider messages={messages} locale={locale}>
          <QueryProvider>
            <AuthProvider>
              {children}
              <CartDrawer />
              <Toaster position="top-right" richColors closeButton />
            </AuthProvider>
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
