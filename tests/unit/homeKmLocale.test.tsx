import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import kmMessages from '../../messages/km.json';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { MobileNav } from '@/components/layout/MobileNav';
import { HomeView } from '@/components/home/HomeView';

// Mock navigation
vi.mock('@/navigation', () => ({
  Link: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
  }),
  usePathname: () => '/',
}));

// Mock next/image
vi.mock('next/image', () => ({
  default: ({ fill, priority, ...props }: any) => <img {...props} />,
}));

const mockHomeData = {
  seasonalBanner: {
    title: 'Pchum Ben Festival Sale',
    titleKm: 'ពិធីបុណ្យភ្ជុំបិណ្ឌ',
    imageUrl: 'https://placehold.co/1200x400/F3EBDD/5B4636/png?text=Pchum+Ben+Festival+Sale',
    link: '/products?category=spices-sauces',
  },
  flashSale: {
    id: 1,
    name: 'Flash Sale 24H',
    endsAt: new Date(Date.now() + 86400000).toISOString(),
    products: [
      {
        id: 1006,
        sku: 'SPC-1006',
        slug: 'kampot-black-pepper-100-g',
        name: 'Kampot Black Pepper 100 g',
        nameKm: 'ម្រេចខ្មៅកំពត ១០០ ក្រាម',
        brand: 'Kampot Farms',
        priceCents: 950,
        salePriceCents: 790,
        imageUrl: 'https://placehold.co/800x800/F3EBDD/5B4636/png?text=Kampot+Black+Pepper',
        avgRating: 4.7,
        reviewCount: 3,
        available: 150,
        inStock: true,
        badges: ['FLASH_SALE'],
      },
    ],
  },
  categories: [
    {
      id: 2,
      name: 'Spices & Sauces',
      nameKm: 'គ្រឿងទេស និងទឹកជ្រលក់',
      slug: 'spices-sauces',
      imageUrl: 'https://images.unsplash.com/photo-1509358271058-acd22cc93898?w=500',
      productCount: 7,
    },
  ],
  bestSellers: [
    {
      id: 1013,
      sku: 'RICE-1013',
      slug: 'phka-rumduol-jasmine-rice',
      name: 'Phka Rumduol Jasmine Rice',
      nameKm: 'អង្ករផ្កាម្លិះ ផ្កាស្រគំ',
      brand: 'Battambang Rice',
      priceCents: 1200,
      imageUrl: 'https://placehold.co/800x800/F3EBDD/5B4636/png?text=Jasmine+Rice',
      avgRating: 4.5,
      reviewCount: 12,
      available: 150,
      inStock: true,
      badges: ['POPULAR'],
    },
  ],
  newArrivals: [],
};

const mockSettingsData = {
  storeName: 'KhmerMart',
  khrRate: 4100,
  khqrMode: 'MOCK',
  defaultCurrency: 'USD',
  freeShippingOverCents: 5000,
};

describe('Khmer Locale UI Strings on Home Page', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });

    global.fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/home')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({ success: true, data: mockHomeData }),
        });
      }
      if (url.includes('/settings/public')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({ success: true, data: mockSettingsData }),
        });
      }
      if (url.includes('/cart')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: async () => ({ success: true, data: { itemCount: 2 } }),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: { get: () => 'application/json' },
        json: async () => ({ success: true, data: {} }),
      });
    });
  });

  it('renders home page and layout in km and fails if known English UI strings appear', async () => {
    const { container } = render(
      <NextIntlClientProvider locale="km" messages={kmMessages}>
        <QueryClientProvider client={queryClient}>
          <div>
            <Header />
            <main>
              <HomeView />
            </main>
            <Footer />
            <MobileNav />
          </div>
        </QueryClientProvider>
      </NextIntlClientProvider>
    );

    // Wait for async queries to resolve
    expect(await screen.findByText('ពិធីបុណ្យភ្ជុំបិណ្ឌ')).toBeInTheDocument();

    // Known English UI strings that must NOT appear when rendering in Khmer:
    const forbiddenEnglishStrings = [
      'Top trending items with instant KHQR payment',
      'KHQR Payments',
      'All Bakong apps supported',
      'Fast Delivery',
      'Phnom Penh & 24 Provinces',
      '100% Authentic',
      'Verified store warranty',
      'Customer Care',
      'Mon - Sun (8am - 8pm)',
      'Flash Sales & Discounts',
      'Track Delivery',
      'My Purchases',
      'Returns & Refunds Policy',
      'Cash on Delivery',
      'Bank Transfer',
      'Built with Next.js 14 & Spring Boot 3',
      'Free delivery in Phnom Penh on orders over $50!',
    ];

    const bodyText = container.textContent || '';

    for (const forbidden of forbiddenEnglishStrings) {
      expect(bodyText).not.toContain(forbidden);
    }

    // Verify translated Khmer strings ARE present
    expect(bodyText).toContain('កន្ត្រក');
    expect(bodyText).toContain('ទំនិញពេញនិយមបំផុត អាចទូទាត់ភ្លាមៗជាមួយ KHQR');
    expect(bodyText).toContain('ទូទាត់តាម KHQR');
    expect(bodyText).toContain('ដឹកជញ្ជូនរហ័ស');
    expect(bodyText).toContain('ទំនិញសុទ្ធ ១០០%');
    expect(bodyText).toContain('សេវាបម្រើអតិថិជន');
    expect(bodyText).toContain('ដឹកជញ្ជូនធម្មតាឥតគិតថ្លៃសម្រាប់ការបញ្ជាទិញលើសពី $50');
  });
});
