/**
 * Auth endpoints used by the operations console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest, type SessionUser } from './client'

/** Token pair plus the operator profile issued by login. */
export interface AuthResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: SessionUser
}

/** Full profile returned by {@code GET /api/v1/auth/me}. */
export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatarUrl?: string | null
  roles: string[]
  permissions: string[]
}

/**
 * Signs an operator in with an account name or phone number.
 *
 * @param account username or phone
 * @param password plain password sent over HTTPS
 * @returns the issued token pair and profile
 * @author Caspian
 * @since 0.2.0
 */
export function login(account: string, password: string): Promise<AuthResult> {
  return apiRequest<AuthResult>(
    '/api/v1/auth/login',
    { method: 'POST', body: JSON.stringify({ account, password }) },
    false,
  )
}

/**
 * Loads the profile of the signed-in operator.
 *
 * @returns the profile with roles and permission codes
 * @author Caspian
 * @since 0.2.0
 */
export function loadProfile(): Promise<UserProfile> {
  return apiRequest<UserProfile>('/api/v1/auth/me')
}
