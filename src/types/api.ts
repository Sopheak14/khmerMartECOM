// Shared API Contract Types for KhmerMart

export type Role = 'CUSTOMER' | 'STAFF' | 'ADMIN';

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'PAYMENT_EXPIRED'
  | 'REFUNDED';

export type PaymentMethod = 'KHQR' | 'COD' | 'BANK_TRANSFER' | 'CARD' | 'WALLET';

export type PaymentStatus =
  | 'PENDING'
  | 'PAID'
  | 'FAILED'
  | 'EXPIRED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export type ShipmentStatus =
  | 'PREPARING'
  | 'DISPATCHED'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED';

export type ShippingMethodCode = 'STANDARD' | 'EXPRESS' | 'SAME_DAY';

export type CouponType = 'PERCENT' | 'FIXED' | 'FREE_SHIPPING' | 'BOGO';

export type PromotionType = 'FLASH_SALE' | 'SEASONAL' | 'BOGO' | 'FREE_SHIPPING_THRESHOLD';

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH' | 'IN_APP';

export type KhqrMode = 'MOCK' | 'LIVE';

export interface PaginationMeta {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  details?: Record<string, any>;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  meta?: PaginationMeta;
  error?: ApiErrorResponse;
}

export interface UserDTO {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: Role;
  isActive: boolean;
  emailVerified: boolean;
  createdAt: string;
}

export interface AddressDTO {
  id: number;
  label?: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2?: string;
  district: string;
  province: string;
  provinceKm?: string;
  postalCode?: string;
  isDefault: boolean;
}

export interface CategoryDTO {
  id: number;
  name: string;
  nameKm?: string | null;
  slug: string;
  imageUrl?: string;
  parentId?: number | null;
  children?: CategoryDTO[];
  productCount: number;
}

export interface ImageDTO {
  id: number;
  url: string;
  sortOrder: number;
}

export interface VariantDTO {
  id: number;
  sku: string;
  attributes: Record<string, string>;
  priceCents: number;
  available: number;
  salePriceCents?: number;
}

export interface ProductCardDTO {
  id: number;
  sku: string;
  slug: string;
  name: string;
  nameKm?: string | null;
  brand: string;
  priceCents: number;
  compareAtCents?: number;
  salePriceCents?: number;
  imageUrl: string;
  avgRating: number;
  reviewCount: number;
  available: number;
  inStock: boolean;
  badges: string[];
}

export interface ProductDetailDTO extends ProductCardDTO {
  description: string;
  category: CategoryDTO;
  images: ImageDTO[];
  variants: VariantDTO[];
  variantOptions: Record<string, string[]>;
  ratingBreakdown: {
    '5': number;
    '4': number;
    '3': number;
    '2': number;
    '1': number;
  };
  flashSaleEndsAt?: string;
  related: ProductCardDTO[];
}

export type CartItemIssue = 'OUT_OF_STOCK' | 'PRICE_CHANGED' | 'QTY_REDUCED';

export interface CartItemDTO {
  id: number;
  productId: number;
  variantId?: number | null;
  name: string;
  nameKm?: string | null;
  imageUrl: string;
  slug: string;
  attributes?: Record<string, string>;
  unitPriceCents: number;
  quantity: number;
  lineTotalCents: number;
  available: number;
  issues: CartItemIssue[];
}

export interface PromotionDiscount {
  label: string;
  amountCents: number;
}

export interface CartCoupon {
  code: string;
  type: CouponType;
  value: number;
  description?: string;
}

export interface CartDTO {
  id: number;
  items: CartItemDTO[];
  itemCount: number;
  subtotalCents: number;
  discountCents: number;
  promotionDiscounts: PromotionDiscount[];
  coupon?: CartCoupon | null;
  shippingEstimateCents: number;
  totalCents: number;
}

export interface ShippingMethodDTO {
  code: ShippingMethodCode;
  name: string;
  nameKm: string;
  feeCents: number;
  freeOverCents?: number;
  etaDaysMin: number;
  etaDaysMax: number;
  phnomPenhOnly: boolean;
}

export interface ProvinceDTO {
  code: string;
  name: string;
  nameKm: string;
}

export interface CheckoutPreviewDTO {
  items: CartItemDTO[];
  subtotalCents: number;
  discountCents: number;
  shippingCents: number;
  totalCents: number;
  totalKhr: number;
  shippingMethod: ShippingMethodDTO;
  address: AddressDTO;
  availablePaymentMethods: PaymentMethod[];
}

export interface CheckoutResponseDTO {
  order: OrderDTO;
  next: string;
}

export interface OrderItemDTO {
  id: number;
  productId: number;
  slug: string;
  name: string;
  imageUrl: string;
  sku: string;
  attributes?: Record<string, string>;
  unitPriceCents: number;
  quantity: number;
  lineTotalCents: number;
  canReview: boolean;
}

export interface OrderTimelineEvent {
  status: OrderStatus;
  note?: string;
  at: string;
}

export interface OrderDTO {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  items: OrderItemDTO[];
  subtotalCents: number;
  discountCents: number;
  shippingCents: number;
  totalCents: number;
  couponCode?: string | null;
  shippingAddress: AddressDTO;
  shippingMethod: ShippingMethodDTO;
  trackingNumber?: string | null;
  carrier?: string | null;
  paymentDeadline?: string | null;
  placedAt: string;
  confirmedAt?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancelReason?: string | null;
  timeline: OrderTimelineEvent[];
  canCancel: boolean;
  canReview?: boolean;
  invoiceUrl?: string;
  customer?: UserDTO;
}

export interface KhqrPaymentDTO {
  paymentId: number;
  orderNumber: string;
  status: PaymentStatus;
  method: 'KHQR';
  currency: 'USD' | 'KHR';
  amount: number;
  amountCents: number;
  qrString: string;
  qrImageDataUrl: string;
  md5: string;
  expiresAt: string;
  merchantName: string;
  deepLink?: string;
  mode: KhqrMode;
  streamToken: string;
}

export interface PaymentStatusDTO {
  paymentId: number;
  status: PaymentStatus;
  orderStatus: OrderStatus;
  paidAt?: string | null;
  bakongHash?: string | null;
  expiresAt: string;
}

export interface TrackingEvent {
  status: ShipmentStatus;
  location?: string;
  note?: string;
  at: string;
}

export interface TrackingDTO {
  trackingNumber: string;
  carrier: string;
  orderNumber: string;
  status: ShipmentStatus;
  events: TrackingEvent[];
  estimatedDelivery?: string;
}

export interface ReviewDTO {
  id: number;
  productId: number;
  user: {
    fullName: string;
  };
  rating: number;
  title: string;
  body: string;
  verifiedPurchase: boolean;
  status: ReviewStatus;
  adminReply?: string | null;
  createdAt: string;
}

export interface NotificationDTO {
  id: number;
  type: string;
  title: string;
  body: string;
  link?: string;
  readAt?: string | null;
  createdAt: string;
}

export interface SeasonalBannerDTO {
  title: string;
  titleKm?: string;
  imageUrl: string;
  link?: string;
}

export interface PublicSettingsDTO {
  storeName: string;
  khrRate: number;
  khqrMode: KhqrMode;
  defaultCurrency: 'USD' | 'KHR';
  freeShippingOverCents: number;
  seasonalBanner?: SeasonalBannerDTO;
  flashSale?: {
    id: number;
    name: string;
    endsAt: string;
  };
}

export interface HomeDTO {
  seasonalBanner?: SeasonalBannerDTO;
  flashSale?: {
    name: string;
    endsAt: string;
    products: ProductCardDTO[];
  };
  categories: CategoryDTO[];
  bestSellers: ProductCardDTO[];
  newArrivals: ProductCardDTO[];
}

export interface LowStockItem {
  productId: number;
  name: string;
  sku: string;
  available: number;
  threshold: number;
}

export interface SalesChartItem {
  date: string;
  revenueCents: number;
  orders: number;
}

export interface TopProductItem {
  productId: number;
  name: string;
  soldQty: number;
  revenueCents: number;
}

export interface PaymentBreakdownItem {
  method: PaymentMethod;
  count: number;
  amountCents: number;
}

export interface DashboardDTO {
  revenueTodayCents: number;
  revenue7dCents: number;
  revenue30dCents: number;
  ordersToday: number;
  ordersPending: number;
  avgOrderValueCents: number;
  pendingPayments: number;
  lowStock: LowStockItem[];
  recentOrders: OrderDTO[];
  salesChart: SalesChartItem[];
  topProducts: TopProductItem[];
  paymentMethodBreakdown: PaymentBreakdownItem[];
}

export interface ProductFacets {
  brands: { value: string; count: number }[];
  attributes: { name: string; values: { value: string; count: number }[] }[];
  priceRange: { minCents: number; maxCents: number };
}

export interface ProductListResponse {
  products: ProductCardDTO[];
  facets?: ProductFacets;
  meta: PaginationMeta;
}
