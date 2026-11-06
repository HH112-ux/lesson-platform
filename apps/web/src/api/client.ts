/**
 * Minimal HTTP client for the browser side of the course platform.
 *
 * <p>Every backend endpoint answers with the unified envelope {@code {code,message,data,traceId}};
 * this module unwraps it, attaches the access token and turns non-zero codes into {@link ApiError}
 * so views never read the envelope themselves.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */

/** Local storage keys of the browser session, shared by every page. */
export const ACCESS_TOKEN_KEY = 'lp.accessToken'

/** Local storage key of the refresh token. */
export const REFRESH_TOKEN_KEY = 'lp.refreshToken'

/** Backend envelope shared by all endpoints. */
interface Envelope<T> {
  code: number
  message: string
  data: T
  traceId: string | null
}

/** Failure raised for a non-zero envelope code or a network error. */
export class ApiError extends Error {
  /** Backend error code, {@code 0} when the request never reached the backend. */
  readonly code: number

  /**
   * Creates an error carrying the backend code.
   *
   * @param code backend error code or {@code 0} for a transport failure
   * @param message message safe to display
   * @author Caspian
   * @since 0.1.0
   */
  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/** Returns the stored access token, or {@code null} when the visitor is anonymous. */
export function readAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

/**
 * Stores a freshly issued token pair.
 *
 * @param accessToken signed access token
 * @param refreshToken opaque refresh token
 * @author Caspian
 * @since 0.1.0
 */
export function storeTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

/** Removes the stored session, used when a token is rejected. */
export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * Calls a backend endpoint and unwraps the envelope.
 *
 * @param path gateway path such as {@code /api/v1/orders}
 * @param init fetch options; a JSON body is serialized by the caller
 * @param authenticated whether the access token should be attached
 * @returns the envelope payload
 * @throws ApiError when the transport fails or the envelope code is not zero
 * @author Caspian
 * @since 0.1.0
 */
export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  authenticated = true,
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
    throw new ApiError(envelope.code, envelope.message || '操作失败，请稍后重试')
  }
  return envelope.data
}
