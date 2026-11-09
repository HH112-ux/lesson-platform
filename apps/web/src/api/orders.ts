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

/** Item of an order, aligned with {@code OrderItemView}. */
export interface OrderItemView {
  courseId: number
  title: string
  price: number
}

/** Refund state embedded in an order row. */
export interface RefundSummaryView {
  refundId: number
  refundNo: string
  /** 0 pending review, 1 approved, 2 refunded, 3 rejected, 4 failed. */
  status: number
  /** Refund amount in yuan. */
  amount: number
  reason: string
  reviewReason?: string | null
  failReason?: string | null
  refundedAt?: string | null
}

/** Refund result returned by {@code POST /api/v1/orders/{orderId}/refund}. */
export interface RefundView {
  refundId: number
  refundNo: string
  orderId: number
  orderNo: string
  channel: number
  amount: number
  status: number
  reason: string
  reviewReason?: string | null
  failReason?: string | null
  refundedAt?: string | null
}

/** Order row of {@code GET /api/v1/orders}, aligned with {@code OrderSummaryView}. */
export interface OrderSummaryView {
  orderId: number
  orderNo: string
  status: number
  channel: number
  /** Amount in yuan. */
  payAmount: number
  createdAt: string
  items: OrderItemView[]
  /** Latest refund request, {@code null} when never applied. */
  refund?: RefundSummaryView | null
}

/** Page of orders, aligned with {@code OrderPageView}. */
export interface OrderPage {
  pageNum: number
  pageSize: number
  total: number
  records: OrderSummaryView[]
}

/**
 * Loads one page of the signed-in buyer's orders, newest first.
 *
 * @param pageNum 1-based page number
 * @param pageSize page size, the backend caps it at 100
 * @param status optional status filter
 * @returns the page of order rows
 * @author Caspian
 * @since 0.2.0
 */
export function listOrders(
  pageNum: number,
  pageSize: number,
  status?: number,
): Promise<OrderPage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  if (typeof status === 'number') {
    params.set('status', String(status))
  }
  return apiRequest<OrderPage>(`/api/v1/orders?${params.toString()}`)
}

/** Order status meaning "paid"; aligned with the backend {@code OrderStatus}. */
export const ORDER_STATUS_PAID = 1

/**
 * Applies for a full refund of a paid order.
 *
 * <p>The request is admitted only within seven days of payment and while course progress is at
 * most twenty percent. An admitted request waits for operations; it never refunds immediately.
 *
 * @param orderId order identifier
 * @param reason buyer supplied reason, required
 * @returns the created or already in-progress refund
 * @author Caspian
 * @since 0.1.0
 */
export function applyRefund(orderId: number, reason: string): Promise<RefundView> {
  return apiRequest<RefundView>(`/api/v1/orders/${orderId}/refund`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
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
