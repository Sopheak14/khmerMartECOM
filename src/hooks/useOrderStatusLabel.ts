import { useTranslations } from 'next-intl';
import { OrderStatus } from '@/types/api';

const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING_PAYMENT',
  'CONFIRMED',
  'PROCESSING',
  'SHIPPED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'PAYMENT_EXPIRED',
  'REFUNDED',
];

/** One label per OrderStatus (orders.status.*), shared by the customer, admin and tracking views. */
export function useOrderStatusLabel() {
  const t = useTranslations('orders');
  return (status: string): string => (ORDER_STATUSES.includes(status as OrderStatus) ? t(`status.${status}`) : status);
}
