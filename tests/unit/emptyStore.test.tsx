import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import enMessages from '../../messages/en.json';
import kmMessages from '../../messages/km.json';

// ---- mocks -----------------------------------------------------------------------------------------------------
let searchParams = new URLSearchParams();
vi.mock('next/navigation', () => ({
  useSearchParams: () => searchParams,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/',
}));
vi.mock('@/navigation', () => ({
  Link: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => '/',
}));
vi.mock('next/image', () => ({
  default: ({ fill, priority, ...props }: any) => <img {...props} />,
}));

// An empty store: every list is empty, every counter is zero.
const emptyDashboard = {
  revenueTodayCents: 0,
  revenue7dCents: 0,
  revenue30dCents: 0,
  ordersToday: 0,
  ordersPending: 0,
  avgOrderValueCents: 0,
  pendingPayments: 0,
  salesChart: [],
  topProducts: [],
  paymentMethodBreakdown: [],
  lowStock: [],
  recentOrders: [],
};
let storeCounts = { products: 0, categories: 0 };

const respond = (path: string) => {
  if (path.startsWith('/admin/dashboard')) return { data: emptyDashboard };
  if (path.startsWith('/admin/products')) {
    return { data: [], meta: { page: 1, limit: 1, total: storeCounts.products, totalPages: 1 } };
  }
  if (path.startsWith('/admin/categories')) {
    return { data: Array.from({ length: storeCounts.categories }, (_, i) => ({ id: i + 1, name: `Cat ${i}` })) };
  }
  if (path.startsWith('/admin/payments/reconciliation')) return { data: { mismatches: [] } };
  if (path.startsWith('/home')) return { data: {} };
  if (path.startsWith('/settings/public')) return { data: { khrRate: 4100 } };
  // orders, payments, reviews, reports, products...: empty list
  return { data: [], meta: { page: 1, limit: 15, total: 0, totalPages: 1 } };
};

vi.mock('@/lib/apiClient', () => {
  const fn: any = vi.fn(async (path: string) => respond(path));
  fn.get = vi.fn(async (path: string) => respond(path));
  class ApiError extends Error {}
  return { apiClient: fn, ApiError };
});

import { apiClient } from '@/lib/apiClient';
import { HomeView } from '@/components/home/HomeView';
import { ProductsCatalogView } from '@/components/catalog/ProductsCatalogView';
import { EmptyStoreCta } from '@/components/admin/EmptyStoreCta';
import AdminDashboardPage from '@/app/[locale]/admin/page';
import AdminReportsPage from '@/app/[locale]/admin/reports/page';
import AdminOrdersPage from '@/app/[locale]/admin/orders/page';
import AdminPaymentsPage from '@/app/[locale]/admin/payments/page';
import AdminReviewsPage from '@/app/[locale]/admin/reviews/page';
import AdminProductsPage from '@/app/[locale]/admin/products/page';
import AdminCategoriesPage from '@/app/[locale]/admin/categories/page';

function renderIn(ui: React.ReactElement, locale: 'en' | 'km' = 'en') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <NextIntlClientProvider locale={locale} messages={locale === 'en' ? enMessages : kmMessages}>
        {ui}
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

const emptyHome = {
  seasonalBanner: undefined,
  flashSale: null,
  categories: [],
  bestSellers: [],
  newArrivals: [],
};

beforeEach(() => {
  vi.mocked(apiClient).mockClear();
  searchParams = new URLSearchParams();
  storeCounts = { products: 0, categories: 0 };
});

describe('Storefront with an empty store', () => {
  it.each([
    ['en', enMessages.home.emptyTitle],
    ['km', kmMessages.home.emptyTitle],
  ] as const)('home page shows a friendly empty state without broken sections (%s)', async (locale, title) => {
    renderIn(<HomeView initialHomeData={emptyHome as any} initialSettingsData={{ khrRate: 4100 } as any} />, locale);

    expect(await screen.findByTestId('home-empty-state')).toHaveTextContent(title);
    // No flash-sale / category / product sections and no flash-sale call-to-action in the hero
    expect(document.getElementById('flash-sale')).toBeNull();
    expect(screen.queryByText(enMessages.home.featuredCategories)).toBeNull();
    expect(screen.queryByText(enMessages.home.bestSellersTitle)).toBeNull();
    expect(screen.queryByText(enMessages.home.newArrivalsTitle)).toBeNull();
    expect(screen.queryByText(enMessages.home.exploreDeals)).toBeNull();
    // Hero and trust cards still render
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });

  it('home page renders the empty state when the API returns no feed at all', async () => {
    renderIn(<HomeView initialHomeData={undefined} />);
    expect(await screen.findByTestId('home-empty-state')).toBeInTheDocument();
  });

  it.each([
    ['en', enMessages.catalog.noProductsYet],
    ['km', kmMessages.catalog.noProductsYet],
  ] as const)('products page says "No products yet" and hides filters/sort (%s)', async (locale, text) => {
    renderIn(<ProductsCatalogView initialProducts={[]} khrRate={4100} />, locale);
    const empty = await screen.findByTestId('products-empty-state');
    expect(empty).toHaveAttribute('data-variant', 'store-empty');
    expect(empty).toHaveTextContent(text);
    expect(screen.queryByTestId('sort-select')).toBeNull();
  });

  it('a search with no matches still shows the "no results" state with a clear-filters button', async () => {
    searchParams = new URLSearchParams('q=nothing');
    renderIn(<ProductsCatalogView initialProducts={[]} khrRate={4100} />);
    const empty = await screen.findByTestId('products-empty-state');
    expect(empty).not.toHaveAttribute('data-variant', 'store-empty');
    expect(empty).toHaveTextContent(enMessages.catalog.noProducts);
    expect(within(empty).getByRole('button', { name: enMessages.catalog.clearAll })).toBeInTheDocument();
  });

  it('an empty category says so instead of offering to clear filters', async () => {
    renderIn(<ProductsCatalogView initialProducts={[]} categoryTitle="Spices" khrRate={4100} />);
    const empty = await screen.findByTestId('products-empty-state');
    expect(empty).toHaveTextContent(enMessages.category.noProductsInCategory);
  });
});

describe('Admin pages with an empty store', () => {
  it('EmptyStoreCta offers both first steps when there is no product and no category', async () => {
    renderIn(<EmptyStoreCta />);
    expect(await screen.findByTestId('cta-add-product')).toHaveAttribute('href', '/admin/products');
    expect(screen.getByTestId('cta-add-category')).toHaveAttribute('href', '/admin/categories');
  });

  it('EmptyStoreCta only asks for what is missing, and disappears for a populated store', async () => {
    storeCounts = { products: 0, categories: 3 };
    const { unmount } = renderIn(<EmptyStoreCta />);
    expect(await screen.findByTestId('cta-add-product')).toBeInTheDocument();
    expect(screen.queryByTestId('cta-add-category')).toBeNull();
    unmount();

    storeCounts = { products: 5, categories: 3 };
    vi.mocked(apiClient).mockClear();
    renderIn(<EmptyStoreCta />);
    // wait until both count queries have been answered, then let React flush
    await waitFor(() => expect(vi.mocked(apiClient)).toHaveBeenCalledTimes(2));
    await act(async () => {});
    expect(screen.queryByTestId('empty-store-cta')).toBeNull();
  });

  it.each([
    ['en', enMessages.admin.emptyStore.addProduct],
    ['km', kmMessages.admin.emptyStore.addProduct],
  ] as const)('dashboard shows zeros, empty tables and the setup call to action (%s)', async (locale, cta) => {
    renderIn(<AdminDashboardPage />, locale);
    expect(await screen.findByTestId('kpi-orders-count')).toHaveTextContent('0');
    expect(screen.getByTestId('kpi-pending-payments')).toHaveTextContent('0');
    expect(screen.getByTestId('kpi-revenue-usd')).toHaveTextContent('$0.00');
    expect(screen.getByTestId('kpi-avg-order-usd')).toHaveTextContent('$0.00');
    expect(screen.getByTestId('dashboard-low-stock-card')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-recent-orders-card')).toBeInTheDocument();
    // all three charts fall back to "no data" instead of rendering an empty chart
    expect(await screen.findAllByText(locale === 'en' ? enMessages.admin.dashboard.noChartData : kmMessages.admin.dashboard.noChartData)).toHaveLength(3);
    expect(await screen.findByTestId('cta-add-product')).toHaveTextContent(cta);
  });

  it('reports page shows zero totals and a "no sales yet" message without a chart', async () => {
    renderIn(<AdminReportsPage />);
    expect(await screen.findByTestId('reports-empty-chart')).toHaveTextContent(enMessages.admin.reports.noSalesYet);
    expect(screen.getByTestId('admin-reports-page')).toHaveTextContent('$0.00');
    expect(await screen.findByTestId('cta-add-product')).toBeInTheDocument();
  });

  it('orders page: empty table message plus setup call to action', async () => {
    renderIn(<AdminOrdersPage />);
    const empty = await screen.findByTestId('orders-empty-state');
    expect(empty).toHaveTextContent(enMessages.admin.orders.noOrdersYet);
    expect(await screen.findByTestId('cta-add-product')).toBeInTheDocument();
  });

  it('orders page: a filtered search keeps the plain "no orders match" message', async () => {
    searchParams = new URLSearchParams('status=SHIPPED');
    renderIn(<AdminOrdersPage />);
    const empty = await screen.findByTestId('orders-empty-state');
    expect(empty).toHaveTextContent(enMessages.admin.orders.noOrders);
    expect(screen.queryByTestId('empty-store-cta')).toBeNull();
  });

  it('payments page: empty state in Khmer', async () => {
    renderIn(<AdminPaymentsPage />, 'km');
    const empty = await screen.findByTestId('payments-empty-state');
    expect(empty).toHaveTextContent(kmMessages.admin.payments.noPaymentsYet);
  });

  it('reviews page: translated "all caught up" state', async () => {
    renderIn(<AdminReviewsPage />, 'km');
    expect(await screen.findByText(kmMessages.admin.reviews.allCaughtUp)).toBeInTheDocument();
  });

  it('products admin page: "Add your first product" opens the create form', async () => {
    renderIn(<AdminProductsPage />);
    const empty = await screen.findByTestId('admin-products-empty-state');
    expect(empty).toHaveTextContent(enMessages.admin.products.noProductsYet);
    expect(screen.getByTestId('add-first-product-btn')).toHaveTextContent(enMessages.admin.products.addFirstProduct);
  });

  it('categories admin page: "Add a category" call to action', async () => {
    renderIn(<AdminCategoriesPage />);
    const empty = await screen.findByTestId('admin-categories-empty-state');
    expect(empty).toHaveTextContent(enMessages.admin.categories.noCategoriesYet);
    expect(screen.getByTestId('add-first-category-btn')).toBeInTheDocument();
  });
});
