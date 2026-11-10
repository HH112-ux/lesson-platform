import { describe, expect, it } from 'vitest'
import {
  canAccess,
  defaultRouteFor,
  resolveRoute,
  visibleMenu,
  type ConsoleRole,
} from './router'

/** Paths the console knows, paired with the roles admitted by the guard. */
const GUARD_MATRIX: [string, ConsoleRole[]][] = [
  ['/', ['ADMIN', 'OPS']],
  ['/courses', ['ADMIN', 'OPS']],
  ['/users', ['ADMIN', 'OPS']],
  ['/orders', ['ADMIN', 'OPS']],
  ['/refunds', ['ADMIN', 'OPS']],
  ['/lesson-videos', ['ADMIN', 'TEACHER']],
]

describe('resolveRoute', () => {
  it('tags every operator screen with the operator roles', () => {
    for (const [path, roles] of GUARD_MATRIX) {
      expect(resolveRoute(path).roles).toEqual(roles)
    }
  })

  it('leaves login and unknown paths without a role gate', () => {
    expect(resolveRoute('/login').name).toBe('login')
    expect(resolveRoute('/login').roles).toEqual([])
    expect(resolveRoute('/nope').name).toBe('not-found')
    expect(resolveRoute('/nope').roles).toEqual([])
  })
})

describe('canAccess', () => {
  it('admits teachers to the lesson video screen only', () => {
    const teacher = ['TEACHER'] as const
    expect(canAccess(resolveRoute('/lesson-videos'), teacher)).toBe(true)
    expect(canAccess(resolveRoute('/orders'), teacher)).toBe(false)
    expect(canAccess(resolveRoute('/'), teacher)).toBe(false)
  })

  it('admits administrators to the teacher screen by URL', () => {
    expect(canAccess(resolveRoute('/lesson-videos'), ['ADMIN'])).toBe(true)
    expect(canAccess(resolveRoute('/orders'), ['ADMIN'])).toBe(true)
  })

  it('keeps operators out of the teacher screen', () => {
    expect(canAccess(resolveRoute('/lesson-videos'), ['OPS'])).toBe(false)
  })

  it('rejects users without a console role', () => {
    expect(canAccess(resolveRoute('/orders'), ['STUDENT'])).toBe(false)
    expect(canAccess(resolveRoute('/orders'), [])).toBe(false)
  })

  it('never gates a route without roles', () => {
    expect(canAccess(resolveRoute('/nope'), [])).toBe(true)
    expect(canAccess(resolveRoute('/login'), [])).toBe(true)
  })
})

describe('defaultRouteFor', () => {
  it('lands operators on the dashboard', () => {
    expect(defaultRouteFor(['ADMIN'])).toBe('/')
    expect(defaultRouteFor(['OPS'])).toBe('/')
  })

  it('lands a pure teacher on the lesson video screen', () => {
    expect(defaultRouteFor(['TEACHER'])).toBe('/lesson-videos')
  })

  it('prefers the dashboard when an account is both admin and teacher', () => {
    expect(defaultRouteFor(['TEACHER', 'ADMIN'])).toBe('/')
  })

  it('falls back to login without a console role', () => {
    expect(defaultRouteFor(['STUDENT'])).toBe('/login')
    expect(defaultRouteFor([])).toBe('/login')
  })
})

describe('visibleMenu', () => {
  it('shows teachers only the lesson video entry', () => {
    expect(visibleMenu(['TEACHER']).map((item) => item.path)).toEqual(['/lesson-videos'])
  })

  it('shows operators the five operations entries and hides the teacher entry', () => {
    for (const roles of [['ADMIN'], ['OPS']]) {
      expect(visibleMenu(roles).map((item) => item.path)).toEqual([
        '/',
        '/courses',
        '/users',
        '/orders',
        '/refunds',
      ])
    }
  })

  it('shows both entries to an account that is admin and teacher', () => {
    const paths = visibleMenu(['TEACHER', 'ADMIN']).map((item) => item.path)
    expect(paths).toContain('/')
    expect(paths).toContain('/lesson-videos')
  })

  it('shows nothing to users without a console role', () => {
    expect(visibleMenu(['STUDENT'])).toEqual([])
  })
})
