/**
 * Path based routing of the operations console.
 *
 * <p>The console has few screens and no nested layouts, so a tiny pathname dispatcher keeps the
 * bundle free of a router dependency.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { shallowRef } from 'vue'
import { readUser, type SessionUser } from './api/client'

/** Screen of the console. */
export type RouteName = 'overview' | 'review' | 'users' | 'orders' | 'login' | 'not-found'

/** Resolved route with its rendered screen and path parameters. */
export interface Route {
  name: RouteName
  params: Record<string, string>
}

/**
 * Resolves a pathname into a screen.
 *
 * @param pathname {@code window.location.pathname}
 * @returns route to render
 * @author Caspian
 * @since 0.2.0
 */
export function resolveRoute(pathname: string): Route {
  const segments = pathname.split('/').filter(Boolean)
  if (segments.length === 0) {
    return { name: 'overview', params: {} }
  }
  if (segments.length === 1 && segments[0] === 'login') {
    return { name: 'login', params: {} }
  }
  if (segments.length === 1 && segments[0] === 'courses') {
    return { name: 'review', params: {} }
  }
  if (segments.length === 1 && segments[0] === 'users') {
    return { name: 'users', params: {} }
  }
  if (segments.length === 1 && segments[0] === 'orders') {
    return { name: 'orders', params: {} }
  }
  return { name: 'not-found', params: {} }
}

/** Current route, kept in sync with the address bar. */
export const currentRoute = shallowRef<Route>(
  typeof window === 'undefined' ? { name: 'overview', params: {} } : resolveRoute(window.location.pathname),
)

/** Signed-in operator, or {@code null} when the session is gone. */
export const currentUser = shallowRef<SessionUser | null>(readUser())

/** Re-reads the address bar into {@link currentRoute}. */
export function syncRoute(): void {
  currentRoute.value = resolveRoute(window.location.pathname)
}

/**
 * Pushes a new path and renders its screen.
 *
 * @param path absolute path such as {@code /login}
 * @param replace whether to replace the history entry instead of pushing one
 * @author Caspian
 * @since 0.2.0
 */
export function navigate(path: string, replace = false): void {
  if (replace) {
    window.history.replaceState(null, '', path)
  } else {
    window.history.pushState(null, '', path)
  }
  syncRoute()
}
