/**
 * HTTP client of the operations console.
 *
 * <p>Every backend endpoint answers with {@code {code,message,data,traceId}}. The client unwraps
 * the envelope, attaches the access token, renews an expired token once and raises
 * {@link ApiError} for non-zero codes so views never parse the envelope themselves.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { shallowRef } from 'vue'

/** Local storage key of the console access token. */
export const ACCESS_TOKEN_KEY = 'lp.admin.accessToken'

/** Local storage key of the console refresh token. */
export const REFRESH_TOKEN_KEY = 'lp.admin.refreshToken'

/** Local storage key of the signed-in operator profile. */
export const USER_KEY = 'lp.admin.user'

/** Operator profile embedded in the login response. */
export interface SessionUser {
  id: number
  nickname: string
  avatarUrl?: string | null
  roles?: string[]
}

/** Token pair issued by login and refresh. */
export interface TokenPair {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

interface Envelope<T> {
  code: number
  message: string
  data: T
  traceId: string | null
}

/** Failure raised for a non-zero envelope code or a transport error. */
export class ApiError extends Error {
  /** Backend error code, {@code 0} when the request never reached the backend. */
  readonly code: number

  /**
   * Creates an error carrying the backend code.
   *
   * @param code backend error code or {@code 0} for a transport failure
   * @param message message safe to display
   * @author Caspian
   * @since 0.2.0
   */
  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

function storage(): Storage | null {
  return typeof localStorage === 'undefined' ? null : localStorage
}

/** Returns the stored access token, or {@code null} when signed out. */
export function readAccessToken(): string | null {
  return storage()?.getItem(ACCESS_TOKEN_KEY) ?? null
}

/** Reactive mirror of the stored access token, so views react to sign-in and sign-out. */
export const accessToken = shallowRef<string | null>(readAccessToken())

/** Returns the cached operator profile, or {@code null} when absent or unreadable. */
export function readUser(): SessionUser | null {
  const raw = storage()?.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as SessionUser
  } catch {
    return null
  }
}

/**
 * Stores a freshly issued token pair.
 *
 * @param token signed access token
 * @param refreshToken opaque refresh token
 * @author Caspian
 * @since 0.2.0
 */
export function storeTokens(token: string, refreshToken: string): void {
  storage()?.setItem(ACCESS_TOKEN_KEY, token)
  storage()?.setItem(REFRESH_TOKEN_KEY, refreshToken)
  accessToken.value = token
}

/**
 * Caches the operator profile.
 *
 * @param user profile returned by the auth endpoints
 * @author Caspian
 * @since 0.2.0
 */
export function storeUser(user: SessionUser): void {
  storage()?.setItem(USER_KEY, JSON.stringify(user))
}

/** Removes the stored console session. */
export function clearSession(): void {
  storage()?.removeItem(ACCESS_TOKEN_KEY)
  storage()?.removeItem(REFRESH_TOKEN_KEY)
  storage()?.removeItem(USER_KEY)
  accessToken.value = null
}

let refreshInFlight: Promise<boolean> | null = null

/**
 * Renews the access token; concurrent callers share one request so the refresh token is never
 * spent twice.
 *
 * @returns true when a new token pair was stored
 * @author Caspian
 * @since 0.2.0
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = requestRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function requestRefresh(): Promise<boolean> {
  const refreshToken = storage()?.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) {
    return false
  }
  try {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    const envelope = (await response.json()) as Envelope<TokenPair> | null
    if (!envelope || envelope.code !== 0 || !envelope.data) {
      return false
    }
    storeTokens(envelope.data.accessToken, envelope.data.refreshToken)
    return true
  } catch {
    return false
  }
}

/**
 * Calls a backend endpoint and unwraps the envelope.
 *
 * @param path gateway path such as {@code /api/v1/admin/courses/pending}
 * @param init fetch options; a JSON body is serialized by the caller
 * @param authenticated whether the access token should be attached
 * @returns the envelope payload
 * @throws ApiError when the transport fails or the envelope code is not zero
 * @author Caspian
 * @since 0.2.0
 */
export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  authenticated = true,
): Promise<T> {
  return send<T>(path, init, authenticated, false)
}

async function send<T>(
  path: string,
  init: RequestInit,
  authenticated: boolean,
  retried: boolean,
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (authenticated) {
    const token = readAccessToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, '网络连接失败，请检查网络后重试')
  }

  let envelope: Envelope<T> | null = null
  try {
    envelope = (await response.json()) as Envelope<T>
  } catch {
    envelope = null
  }

  if (!envelope || typeof envelope.code !== 'number') {
    throw new ApiError(response.status, '服务暂时不可用，请稍后重试')
  }
  if (envelope.code !== 0) {
    if (authenticated && (envelope.code === 40101 || envelope.code === 40102)) {
      if (envelope.code === 40102 && !retried) {
        const renewed = await refreshSession()
        if (renewed) {
          return send<T>(path, init, authenticated, true)
        }
      }
      clearSession()
    }
    throw new ApiError(envelope.code, envelope.message || '操作失败，请稍后重试')
  }
  return envelope.data
}
