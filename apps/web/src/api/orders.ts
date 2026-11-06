/**
 * Order and payment endpoints of the gateway.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest } from './client'

/** Order projection returned by the trade service; amounts are in yuan. */
export interface OrderView {
  orderNo: string
  status: number
  channel: number
  totalAmount: number
  discountAmount: number
  payAmount: number
  courseId: number
  payExpireAt?: string | null
  paidAt?: string | null
}

/** Prepay payload returned by {@code POST /api/v1/payments/{orderNo}/pay}. */
export interface PayView {
  payNo: string
  channel: number
  payParams: Record<string, string>
}

/** Order status meaning "paid"; aligned with the backend {@code OrderStatus}. */
export const ORDER_STATUS_PAID = 2

/** Page of orders; the list endpoint is not live yet, the shape follows the platform page envelope. */
export interface OrderPage {
  pageNum: number
  pageSize: number
  total: number
  records: OrderView[]
}

/**
 * Loads one page of the signed-in user's orders.
 *
 * @param pageNum 1-based page number
 * @param pageSize page size
 * @returns the page of orders
 * @author Caspian
 * @since 0.2.0
 */
export function listOrders(pageNum: number, pageSize: number): Promise<OrderPage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  return apiRequest<OrderPage>(`/api/v1/orders?${params.toString()}`)
}

/**
 * Creates an order for a course.
 *
 * @param courseId course to buy
 * @param couponId optional coupon
 * @returns the created order
 * @author Caspian
 * @since 0.2.0
 */
export function createOrder(courseId: number, couponId?: number): Promise<OrderView> {
  const body: Record<string, unknown> = {
    courseId,
    idempotencyKey: crypto.randomUUID(),
  }
  if (typeof couponId === 'number') {
    body.couponId = couponId
  }
  return apiRequest<OrderView>('/api/v1/orders', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/**
 * Starts the payment of an order.
 *
 * @param orderNo order to pay
 * @returns the channel payload, usually carrying a {@code payUrl}
 * @author Caspian
 * @since 0.2.0
 */
export function payOrder(orderNo: string): Promise<PayView> {
  return apiRequest<PayView>(`/api/v1/payments/${encodeURIComponent(orderNo)}/pay`, {
    method: 'POST',
  })
}

/**
 * Tells whether an order needs no further channel round trip.
 *
 * @param order order returned by {@link createOrder}
 * @returns true when the course is already granted
 * @author Caspian
 * @since 0.2.0
 */
export function isOrderPaid(order: OrderView): boolean {
  return order.status === ORDER_STATUS_PAID || order.payAmount <= 0
}
