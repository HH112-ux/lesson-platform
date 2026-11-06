/**
 * Auth endpoints of the gateway.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest, type SessionUser } from './client'

/** Token pair plus the profile issued by login and register. */
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
  email?: string | null
  phone?: string | null
  roles: string[]
  permissions: string[]
}

/** Body of {@code POST /api/v1/auth/register}. */
export interface RegisterPayload {
  username: string
  password: string
  nickname: string
  email?: string
  phone?: string
}

/**
 * Signs a user in with an account name or phone number.
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
 * Creates an account and signs it in immediately.
 *
 * @param payload registration form
 * @returns the issued token pair and profile
 * @author Caspian
 * @since 0.2.0
 */
export function register(payload: RegisterPayload): Promise<AuthResult> {
  return apiRequest<AuthResult>(
    '/api/v1/auth/register',
    { method: 'POST', body: JSON.stringify(payload) },
    false,
  )
}

/** Loads the profile of the signed-in user. */
export function loadProfile(): Promise<UserProfile> {
  return apiRequest<UserProfile>('/api/v1/auth/me')
}
