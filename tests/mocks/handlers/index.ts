import { http, HttpResponse } from 'msw';
import { mockPublicSettings, mockShippingMethods, mockProvinces } from '../data/settings';
import { mockCategories } from '../data/categories';
import { mockProducts, mockProductDetail1001 } from '../data/products';
import { mockUsers, mockAddresses } from '../data/users';

const API_BASE = 'http://localhost:4000/api';

export const handlers = [
  // Health
  http.get(`${API_BASE}/health`, () => {
    return HttpResponse.json({
      success: true,
      data: {
        status: 'ok',
        db: 'ok',
        redis: 'ok',
        khqrMode: 'MOCK',
      },
    });
  }),

  // Settings
  http.get(`${API_BASE}/settings/public`, () => {
    return HttpResponse.json({
      success: true,
      data: mockPublicSettings,
    });
  }),

  // Home
  http.get(`${API_BASE}/home`, () => {
    const flashProducts = mockProducts.filter((p) => p.badges.includes('FLASH_SALE')).slice(0, 6);
    const bestSellers = [...mockProducts].sort((a, b) => b.reviewCount - a.reviewCount).slice(0, 8);
    const newArrivals = mockProducts.filter((p) => p.badges.includes('NEW')).slice(0, 8);

    return HttpResponse.json({
      success: true,
      data: {
        seasonalBanner: mockPublicSettings.seasonalBanner,
        flashSale: {
          name: 'Midnight Tech Madness',
          endsAt: mockPublicSettings.flashSale?.endsAt,
          products: flashProducts,
        },
        categories: mockCategories,
        bestSellers,
        newArrivals,
      },
    });
  }),

  // Categories
  http.get(`${API_BASE}/categories`, () => {
    return HttpResponse.json({
      success: true,
      data: mockCategories,
    });
  }),

  http.get(`${API_BASE}/categories/:slug`, ({ params }) => {
    const cat = mockCategories.find((c) => c.slug === params.slug);
    if (!cat) {
      return HttpResponse.json(
        { success: false, error: { code: 'NOT_FOUND', message: 'Category not found' } },
        { status: 404 }
      );
    }
    return HttpResponse.json({ success: true, data: cat });
  }),

  // Products
  http.get(`${API_BASE}/products`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase() || '';
    const brand = url.searchParams.get('brand');
    const minPrice = url.searchParams.get('minPrice');
    const maxPrice = url.searchParams.get('maxPrice');
    const onSale = url.searchParams.get('onSale') === 'true';
    const page = parseInt(url.searchParams.get('page') || '1', 10);
    const limit = parseInt(url.searchParams.get('limit') || '20', 10);

    let filtered = [...mockProducts];

    if (q) {
      filtered = filtered.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.brand.toLowerCase().includes(q) ||
          (p.nameKm && p.nameKm.includes(q))
      );
    }

    if (brand) {
      filtered = filtered.filter((p) => p.brand.toLowerCase() === brand.toLowerCase());
    }

    if (minPrice) {
      filtered = filtered.filter((p) => p.priceCents >= parseInt(minPrice, 10));
    }

    if (maxPrice) {
      filtered = filtered.filter((p) => p.priceCents <= parseInt(maxPrice, 10));
    }

    if (onSale) {
      filtered = filtered.filter((p) => p.salePriceCents || p.badges.includes('FLASH_SALE'));
    }

    const total = filtered.length;
    const totalPages = Math.ceil(total / limit) || 1;
    const start = (page - 1) * limit;
    const paginated = filtered.slice(start, start + limit);

    return HttpResponse.json({
      success: true,
      data: paginated,
      meta: {
        page,
        limit,
        total,
        totalPages,
      },
      facets: {
        brands: [
          { value: 'Dell', count: 4 },
          { value: 'Apple', count: 1 },
          { value: 'Samsung', count: 1 },
          { value: 'KhmerTech', count: 1 },
        ],
        attributes: [
          {
            name: 'ram',
            values: [
              { value: '8GB', count: 1 },
              { value: '16GB', count: 3 },
              { value: '32GB', count: 1 },
            ],
          },
        ],
        priceRange: {
          minCents: 1200,
          maxCents: 159900,
        },
      },
    });
  }),

  // Product Detail
  http.get(`${API_BASE}/products/:idOrSlug`, ({ params }) => {
    const { idOrSlug } = params;
    if (idOrSlug === '1001' || idOrSlug === 'laptop-pro-15') {
      return HttpResponse.json({
        success: true,
        data: mockProductDetail1001,
      });
    }

    const p = mockProducts.find(
      (item) => String(item.id) === idOrSlug || item.slug === idOrSlug
    );

    if (!p) {
      return HttpResponse.json(
        { success: false, error: { code: 'NOT_FOUND', message: 'Product not found' } },
        { status: 404 }
      );
    }

    const detail = {
      ...p,
      description: `High quality ${p.name} from ${p.brand}. Guaranteed authentic with KhmerMart warranty.`,
      category: mockCategories[0],
      images: [{ id: 1, url: p.imageUrl, sortOrder: 1 }],
      variants: [],
      variantOptions: {},
      ratingBreakdown: { '5': 10, '4': 5, '3': 1, '2': 0, '1': 0 },
      related: mockProducts.slice(0, 4),
    };

    return HttpResponse.json({
      success: true,
      data: detail,
    });
  }),

  // Search autocomplete suggest
  http.get(`${API_BASE}/search/suggest`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase() || '';

    const matchedProducts = mockProducts
      .filter((p) => p.name.toLowerCase().includes(q) || p.brand.toLowerCase().includes(q))
      .slice(0, 5)
      .map((p) => ({
        slug: p.slug,
        name: p.name,
        imageUrl: p.imageUrl,
        priceCents: p.priceCents,
      }));

    const matchedCategories = mockCategories
      .filter((c) => c.name.toLowerCase().includes(q))
      .slice(0, 3)
      .map((c) => ({
        slug: c.slug,
        name: c.name,
      }));

    return HttpResponse.json({
      success: true,
      data: {
        products: matchedProducts,
        categories: matchedCategories,
      },
    });
  }),

  // Shipping
  http.get(`${API_BASE}/shipping/methods`, () => {
    return HttpResponse.json({
      success: true,
      data: mockShippingMethods,
    });
  }),

  http.get(`${API_BASE}/shipping/provinces`, () => {
    return HttpResponse.json({
      success: true,
      data: mockProvinces,
    });
  }),

  // Auth: Login
  http.post(`${API_BASE}/auth/login`, async ({ request }) => {
    const body: any = await request.json();
    const user = mockUsers.find(
      (u) => u.email === body.email && u.password === body.password
    );

    if (!user) {
      return HttpResponse.json(
        {
          success: false,
          error: {
            code: 'UNAUTHORIZED',
            message: 'Invalid email or password',
          },
        },
        { status: 401 }
      );
    }

    const { password, ...safeUser } = user;
    return HttpResponse.json({
      success: true,
      data: {
        accessToken: `mock-jwt-token-for-${safeUser.id}`,
        user: safeUser,
      },
    });
  }),

  // Auth: Refresh
  http.post(`${API_BASE}/auth/refresh`, () => {
    // If not authenticated in cookie, return 401
    return HttpResponse.json(
      {
        success: false,
        error: {
          code: 'UNAUTHORIZED',
          message: 'No refresh token provided',
        },
      },
      { status: 401 }
    );
  }),

  // Auth: Logout
  http.post(`${API_BASE}/auth/logout`, () => {
    return HttpResponse.json({
      success: true,
      data: {},
    });
  }),

  // Notifications
  http.get(`${API_BASE}/notifications/unread-count`, () => {
    return HttpResponse.json({
      success: true,
      data: {
        count: 2,
      },
    });
  }),

  // Cart
  http.get(`${API_BASE}/cart`, () => {
    return HttpResponse.json({
      success: true,
      data: {
        id: 1,
        items: [],
        itemCount: 0,
        subtotalCents: 0,
        discountCents: 0,
        promotionDiscounts: [],
        shippingEstimateCents: 150,
        totalCents: 150,
      },
    });
  }),
];
