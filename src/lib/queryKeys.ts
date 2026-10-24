// Centralized TanStack Query Keys

export const queryKeys = {
  auth: {
    me: ['auth', 'me'] as const,
  },
  settings: {
    public: ['settings', 'public'] as const,
  },
  home: {
    feed: ['home', 'feed'] as const,
  },
  categories: {
    all: ['categories'] as const,
    detail: (slug: string) => ['categories', slug] as const,
  },
  products: {
    list: (params?: Record<string, any>) => ['products', 'list', params] as const,
    detail: (idOrSlug: string | number) => ['products', 'detail', String(idOrSlug)] as const,
    suggestions: (q: string) => ['products', 'suggestions', q] as const,
    reviews: (productId: number, params?: Record<string, any>) =>
      ['products', productId, 'reviews', params] as const,
  },
  cart: {
    current: ['cart'] as const,
  },
  shipping: {
    methods: ['shipping', 'methods'] as const,
    provinces: ['shipping', 'provinces'] as const,
  },
  tracking: {
    detail: (trackingNumber: string) => ['tracking', trackingNumber] as const,
  },
  checkout: {
    preview: (params: Record<string, any>) => ['checkout', 'preview', params] as const,
  },
  orders: {
    list: (params?: Record<string, any>) => ['orders', 'list', params] as const,
    detail: (orderNumber: string) => ['orders', 'detail', orderNumber] as const,
  },
  payments: {
    khqr: (orderNumber: string) => ['payments', 'khqr', orderNumber] as const,
    status: (paymentId: number) => ['payments', 'status', paymentId] as const,
  },
  notifications: {
    list: (params?: Record<string, any>) => ['notifications', 'list', params] as const,
    unreadCount: ['notifications', 'unread-count'] as const,
  },
  admin: {
    dashboard: (range?: string) => ['admin', 'dashboard', range] as const,
    users: (params?: Record<string, any>) => ['admin', 'users', params] as const,
    products: (params?: Record<string, any>) => ['admin', 'products', params] as const,
    productDetail: (id: number) => ['admin', 'products', id] as const,
    categories: ['admin', 'categories'] as const,
    inventory: (params?: Record<string, any>) => ['admin', 'inventory', params] as const,
    inventoryMovements: (id: number) => ['admin', 'inventory', id, 'movements'] as const,
    orders: (params?: Record<string, any>) => ['admin', 'orders', params] as const,
    orderDetail: (orderNumber: string) => ['admin', 'orders', orderNumber] as const,
    payments: (params?: Record<string, any>) => ['admin', 'payments', params] as const,
    coupons: ['admin', 'coupons'] as const,
    promotions: ['admin', 'promotions'] as const,
    reviews: (params?: Record<string, any>) => ['admin', 'reviews', params] as const,
    reports: (params?: Record<string, any>) => ['admin', 'reports', params] as const,
    settings: ['admin', 'settings'] as const,
    auditLogs: (params?: Record<string, any>) => ['admin', 'audit-logs', params] as const,
  },
};
