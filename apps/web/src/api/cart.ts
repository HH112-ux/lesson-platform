/**
 * Cart endpoints of the gateway.
 *
 * <p>The cart is server side and scoped to the signed-in user; prices are the current course
 * prices returned by the trade service, never a local snapshot.</p>
 *
 * @author Caspian
 * @since 0.5.0
 */
import { apiRequest } from './client'

/** One course in the cart, aligned with {@code CartItemView}. */
export interface CartItemView {
  courseId: number
  title: string
  coverUrl?: string | null
  /** Current price in yuan, recalculated by the trade service on every read. */
  price: number
}

/** Cart payload returned by {@code GET /api/v1/cart}. */
export interface CartView {
  items: CartItemView[]
}

/** Result of {@code POST /api/v1/cart/items}. */
export interface CartAddResult {
  added: boolean
}

/** Result of {@code DELETE /api/v1/cart/items/{courseId}}. */
export interface CartRemoveResult {
  removed: boolean
}

/**
 * Loads the cart of the signed-in user.
 *
 * @returns cart lines with the current course price
 * @author Caspian
 * @since 0.5.0
 */
export function getCart(): Promise<CartView> {
  return apiRequest<CartView>('/api/v1/cart')
}

/**
 * Adds a course to the cart; repeated adds keep a single line.
 *
 * @param courseId course to add
 * @returns {@code {added:true}} on success
 * @author Caspian
 * @since 0.5.0
 */
export function addCartItem(courseId: number): Promise<CartAddResult> {
  return apiRequest<CartAddResult>('/api/v1/cart/items', {
    method: 'POST',
    body: JSON.stringify({ courseId }),
  })
}

/**
 * Removes a course from the cart; removing an absent line also succeeds.
 *
 * @param courseId course to remove
 * @returns {@code {removed:true}} on success
 * @author Caspian
 * @since 0.5.0
 */
export function removeCartItem(courseId: number): Promise<CartRemoveResult> {
  return apiRequest<CartRemoveResult>(`/api/v1/cart/items/${courseId}`, {
    method: 'DELETE',
  })
}
