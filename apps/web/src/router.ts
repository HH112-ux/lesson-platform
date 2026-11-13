/**
 * Pathname router of the browser app.
 *
 * <p>The app keeps a dependency free dispatcher: {@link resolveRoute} turns the current
 * {@code window.location} into a typed route, while {@link navigate} changes it without a
 * full reload. Direct anchors still work because the dev server falls back to
 * {@code index.html} for unknown paths.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { shallowRef } from 'vue'

/** Pages the shell knows how to render. */
export type RouteName =
  | 'home'
  | 'course-detail'
  | 'checkout'
  | 'order-result'
  | 'my-courses'
  | 'player'
  | 'login'
  | 'register'
  | 'orders'
  | 'cart'
  | 'account'
  | 'purchase'
  | 'health'
  | 'not-found'

/** Resolved location consumed by the shell and its views. */
export interface RouteLocation {
  /** Page identifier. */
  name: RouteName
  /** Normalized path without trailing slash. */
  path: string
  /** Path parameters such as {@code courseId}. */
  params: Record<string, string>
  /** Raw query string parsed lazily by consumers. */
  query: URLSearchParams
}

const NUMERIC = /^\d+$/

const ORDER_NO = /^[A-Za-z0-9_-]+$/

/**
 * Resolves a pathname into a route.
 *
 * @param pathname path such as {@code /courses/12}; a full href is accepted as well
 * @param search raw query string such as {@code ?page=2}
 * @returns the resolved route, {@code not-found} when nothing matches
 * @author Caspian
 * @since 0.2.0
 */
export function resolveRoute(pathname: string, search = ''): RouteLocation {
  const rawPath = pathname.split('?')[0] ?? '/'
  const segments = rawPath.split('/').filter((segment) => segment.length > 0)
  const query = new URLSearchParams(search)
  const path = `/${segments.join('/')}`

  const route = (name: RouteName, params: Record<string, string> = {}): RouteLocation => ({
    name,
    path,
    params,
    query,
  })

  if (segments.length === 0) {
    return route('home')
  }
  if (segments.length === 1) {
    switch (segments[0]) {
      case 'login':
        return route('login')
      case 'register':
        return route('register')
      case 'orders':
        return route('orders')
      case 'cart':
        return route('cart')
      case 'account':
        return route('account')
      case 'purchase':
        return route('purchase')
      case 'health':
        return route('health')
    }
  }
  if (segments.length === 2) {
    if (segments[0] === 'courses' && NUMERIC.test(segments[1])) {
      return route('course-detail', { courseId: segments[1] })
    }
    if (segments[0] === 'checkout' && NUMERIC.test(segments[1])) {
      return route('checkout', { courseId: segments[1] })
    }
    if (segments[0] === 'learning' && segments[1] === 'my-courses') {
      return route('my-courses')
    }
  }
  if (
    segments.length === 3
    && segments[0] === 'orders'
    && segments[2] === 'result'
    && ORDER_NO.test(segments[1])
  ) {
    return route('order-result', { orderNo: segments[1] })
  }
  if (
    segments.length === 3
    && segments[0] === 'learning'
    && segments[1] === 'lessons'
    && NUMERIC.test(segments[2])
  ) {
    return route('player', { lessonId: segments[2] })
  }
  return route('not-found')
}

const initialPath = typeof window === 'undefined' ? '/' : window.location.pathname
const initialSearch = typeof window === 'undefined' ? '' : window.location.search

/** Route currently rendered by the shell. */
export const currentRoute = shallowRef(resolveRoute(initialPath, initialSearch))

/** Re-reads the address bar, used on {@code popstate} and after programmatic navigation. */
export function syncRoute(): void {
  if (typeof window === 'undefined') {
    return
  }
  currentRoute.value = resolveRoute(window.location.pathname, window.location.search)
}

/**
 * Changes the address bar and re-renders the matching page.
 *
 * @param to absolute in-app path such as {@code /login?next=/orders}
 * @param replace whether the current history entry should be replaced instead of pushed
 * @author Caspian
 * @since 0.2.0
 */
export function navigate(to: string, replace = false): void {
  if (typeof window === 'undefined') {
    return
  }
  if (replace) {
    window.history.replaceState({}, '', to)
  } else {
    window.history.pushState({}, '', to)
  }
  syncRoute()
  window.scrollTo(0, 0)
}
