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
  totalElements?: number;
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
  latitude?: number | null;
  longitude?: number | null;
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
  sortOrder?: number;
}

export interface ImageDTO {
  id: number;
  url: string;
  sortOrder: number;
  productId?: number;
  isPrimary?: boolean;
}

export interface VariantDTO {
  id: number;
  sku: string;
  attributes: Record<string, string>;
  priceCents: number;
  available: number;
  salePriceCents?: number;
  stockOnHand?: number;
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
  active?: boolean;
}

export interface ProductDetailDTO extends ProductCardDTO {
  description: string;
  descriptionKm?: string | null;
  categoryId?: number;
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
  active?: boolean;
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

export interface DiscountBreakdownDTO {
  promotions: { label: string; amountCents: number }[];
  coupon: { code: string; amountCents: number } | null;
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
  discountBreakdown?: DiscountBreakdownDTO;
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

export type InventoryMovementType = 'RESERVE' | 'SALE' | 'RETURN' | 'RESTOCK' | 'ADJUST' | 'RELEASE';

export interface InventoryDTO {
  id: number;
  productId: number;
  productName: string;
  sku: string;
  variantId?: number;
  quantityOnHand: number;
  quantityReserved: number;
  available: number;
  lowStockThreshold: number;
  lowStockAlerted: boolean;
}

export interface InventoryMovementDTO {
  id: number;
  inventoryId: number;
  type: InventoryMovementType;
  quantity: number;
  reason?: string;
  refOrderId?: number;
  actorId?: number;
  createdAt: string;
}

export interface AdminPaymentDTO {
  id: number;
  orderId: number;
  orderNumber: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amountCents: number;
  currency: string;
  amountKhr?: number;
  khqrMd5?: string;
  bakongHash?: string;
  fromAccountId?: string;
  toAccountId?: string;
  failureReason?: string;
  paidAt?: string;
  rawResponse?: any;
  createdAt: string;
}

export interface ReconciliationMismatchDTO {
  type: string;
  orderId?: number;
  orderNumber?: string;
  paymentId?: number;
  description: string;
}

export interface ReconciliationReportDTO {
  status: string;
  mismatchCount: number;
  mismatches: ReconciliationMismatchDTO[];
  generatedAt: string;
}

export interface AdminVariantRequest {
  sku?: string;
  attributes?: Record<string, string>;
  priceCents?: number;
  stock?: number;
}

export interface AdminProductCreateRequest {
  name: string;
  nameKm?: string;
  sku?: string;
  slug?: string;
  description?: string;
  brand?: string;
  priceCents: number;
  compareAtCents?: number;
  categoryId?: number;
  active?: boolean;
  initialStock?: number;
  variants?: AdminVariantRequest[];
}

export interface AdminProductUpdateRequest {
  name?: string;
  nameKm?: string;
  sku?: string;
  slug?: string;
  description?: string;
  brand?: string;
  priceCents?: number;
  compareAtCents?: number;
  categoryId?: number;
  active?: boolean;
  variants?: AdminVariantRequest[];
}

export interface ProductImageDTO {
  id: number;
  productId?: number;
  url: string;
  sortOrder: number;
  isPrimary?: boolean;
  createdAt?: string;
}

export interface CsvRowError {
  row: number;
  message: string;
}

export interface CsvImportResultDTO {
  imported: number;
  errors: CsvRowError[];
}

export interface CategoryRequest {
  name: string;
  nameKm?: string;
  slug?: string;
  parentId?: number | null;
  imageUrl?: string;
  sortOrder?: number;
}

export interface AdminCouponDTO {
  id: number;
  code: string;
  type: CouponType;
  value: number;
  description?: string;
  minOrderCents?: number;
  maxDiscountCents?: number;
  maxUses?: number;
  usedCount: number;
  startsAt?: string;
  endsAt?: string;
  active: boolean;
}

export interface AdminCouponRequest {
  code: string;
  type: CouponType;
  value: number;
  description?: string;
  minOrderCents?: number;
  maxDiscountCents?: number;
  maxUses?: number;
  startsAt?: string;
  endsAt?: string;
  active?: boolean;
}

export interface AdminPromotionDTO {
  id: number;
  name: string;
  nameKm?: string;
  type: PromotionType;
  discountPercent?: number;
  thresholdCents?: number;
  startsAt: string;
  endsAt: string;
  active: boolean;
  productIds?: number[];
  items?: AdminPromotionItemDTO[];
}

export interface AdminPromotionItemDTO {
  productId: number;
  productName: string;
  variantId?: number;
  sku?: string;
  attributes?: Record<string, string>;
  priceCents: number;
  salePriceCents?: number;
}

export interface PromotionItemRequest {
  productId: number;
  variantId?: number;
  salePriceCents?: number;
}

export interface AdminPromotionRequest {
  name: string;
  nameKm?: string;
  type: PromotionType;
  discountPercent?: number;
  thresholdCents?: number;
  startsAt: string;
  endsAt: string;
  active?: boolean;
  /** @deprecated use items; the backend ignores productIds when items is sent */
  productIds?: number[];
  items?: PromotionItemRequest[];
}

export interface AdminReviewUpdateRequest {
  status: ReviewStatus;
  adminReply?: string;
}

export interface AdminUserUpdateRequest {
  role?: string;
  isActive?: boolean;
}

export interface SalesGroupDTO {
  groupKey: string;
  ordersCount: number;
  unitsSold: number;
  revenueCents: number;
  revenueUsd: string;
}

export interface AdminSettingsDTO {
  storeName: string;
  khrRate: number;
  khqrMode: string;
  shippingMethods: ShippingMethodDTO[];
}

export interface AdminSettingsUpdateRequest {
  storeName?: string;
  khrRate?: number;
  khqrMode?: string;
  shippingMethods?: ShippingMethodDTO[];
}

export interface AuditLogDTO {
  id: number;
  actorId?: number;
  action: string;
  entity: string;
  entityId?: string;
  beforeJson?: any;
  afterJson?: any;
  ip?: string;
  createdAt: string;
}
