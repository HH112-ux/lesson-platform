/**
 * Path based routing of the operations console.
 *
 * <p>The console has few screens and no nested layouts, so a tiny pathname dispatcher keeps the
 * bundle free of a router dependency. Role rules live here as plain data so both the guard and the
 * sidebar read them from one place.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { shallowRef } from 'vue'
import { readUser, type SessionUser } from './api/client'

/** Role that may open a console screen. */
export type ConsoleRole = 'ADMIN' | 'OPS' | 'TEACHER'

/** Screen of the console. */
export type RouteName =
  | 'overview'
  | 'review'
  | 'refunds'
  | 'users'
  | 'orders'
  | 'teacher-courses'
  | 'lesson-video'
  | 'login'
  | 'not-found'

/** Resolved route with its rendered screen, path parameters and admitted roles. */
export interface Route {
  name: RouteName
  params: Record<string, string>
  /** Roles admitted to the screen; empty means the screen has no role gate. */
  roles: readonly ConsoleRole[]
}

/** Sidebar entry with the roles that may see it. */
export interface ConsoleMenu {
  path: string
  label: string
  name: RouteName
  roles: readonly ConsoleRole[]
}

/** Roles that own the operations screens. */
const OPERATOR_ROLES: readonly ConsoleRole[] = ['ADMIN', 'OPS']

/**
 * Screens admitted to administrators and teachers.
 *
 * <p>The guard mirrors what {@code TeacherCourseServiceImpl#resolveActor} really accepts, so an
 * administrator can open the teacher screen by URL even though the sidebar hides it: the menu
 * targets the primary user, the guard tracks the backend capability.</p>
 */
const LESSON_VIDEO_ROLES: readonly ConsoleRole[] = ['ADMIN', 'TEACHER']

/** Sidebar entries in display order. */
export const CONSOLE_MENU: readonly ConsoleMenu[] = [
  { path: '/', label: '数据看板', name: 'overview', roles: OPERATOR_ROLES },
  { path: '/courses', label: '课程审核', name: 'review', roles: OPERATOR_ROLES },
  { path: '/users', label: '用户管理', name: 'users', roles: OPERATOR_ROLES },
  { path: '/orders', label: '订单管理', name: 'orders', roles: OPERATOR_ROLES },
  { path: '/refunds', label: '退款审核', name: 'refunds', roles: OPERATOR_ROLES },
  { path: '/teacher-courses', label: '课程管理', name: 'teacher-courses', roles: ['TEACHER'] },
  { path: '/lesson-videos', label: '课时视频', name: 'lesson-video', roles: ['TEACHER'] },
]

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
    return { name: 'overview', params: {}, roles: OPERATOR_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'login') {
    return { name: 'login', params: {}, roles: [] }
  }
  if (segments.length === 1 && segments[0] === 'courses') {
    return { name: 'review', params: {}, roles: OPERATOR_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'users') {
    return { name: 'users', params: {}, roles: OPERATOR_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'orders') {
    return { name: 'orders', params: {}, roles: OPERATOR_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'refunds') {
    return { name: 'refunds', params: {}, roles: OPERATOR_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'teacher-courses') {
    return { name: 'teacher-courses', params: {}, roles: LESSON_VIDEO_ROLES }
  }
  if (segments.length === 1 && segments[0] === 'lesson-videos') {
    return { name: 'lesson-video', params: {}, roles: LESSON_VIDEO_ROLES }
  }
  return { name: 'not-found', params: {}, roles: [] }
}

/**
 * Tells whether a role set may render a route.
 *
 * @param route resolved route
 * @param roles roles of the signed-in user
 * @returns true when the route has no gate or the roles intersect its gate
 * @author Caspian
 * @since 0.7.0
 */
export function canAccess(route: Route, roles: readonly string[]): boolean {
  if (route.roles.length === 0) {
    return true
  }
  return route.roles.some((role) => roles.includes(role))
}

/**
 * Picks the landing path of a role set.
 *
 * <p>Administrators and operators land on the dashboard even when they also teach; the teacher
 * screen stays reachable through the sidebar.</p>
 *
 * @param roles roles of the signed-in user
 * @returns path to replace the current location with
 * @author Caspian
 * @since 0.7.0
 */
export function defaultRouteFor(roles: readonly string[]): string {
  if (roles.includes('ADMIN') || roles.includes('OPS')) {
    return '/'
  }
  if (roles.includes('TEACHER')) {
    return '/teacher-courses'
  }
  return '/login'
}

/**
 * Filters the sidebar for a role set.
 *
 * @param roles roles of the signed-in user
 * @returns entries the user may see, in display order
 * @author Caspian
 * @since 0.7.0
 */
export function visibleMenu(roles: readonly string[]): ConsoleMenu[] {
  return CONSOLE_MENU.filter((item) => item.roles.some((role) => roles.includes(role)))
}

/** Current route, kept in sync with the address bar. */
export const currentRoute = shallowRef<Route>(
  typeof window === 'undefined' ? { name: 'overview', params: {}, roles: OPERATOR_ROLES } : resolveRoute(window.location.pathname),
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
