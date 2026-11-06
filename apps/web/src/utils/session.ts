/**
 * Reactive mirror of the stored browser session.
 *
 * <p>Local storage stays the source of truth for reloads; this module only keeps the header
 * and the pages in sync within a single tab.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { computed, ref } from 'vue'
import {
  clearSession,
  readUser,
  storeUser,
  storeTokens,
  type SessionUser,
} from '../api/client'

/** User currently signed in, or {@code null} for a visitor. */
export const currentUser = ref<SessionUser | null>(readUser())

/** True when a user is signed in. */
export const isSignedIn = computed(() => currentUser.value !== null)

/**
 * Stores a freshly issued session and notifies the shell.
 *
 * @param accessToken signed access token
 * @param refreshToken opaque refresh token
 * @param user profile returned by the auth endpoint
 * @author Caspian
 * @since 0.2.0
 */
export function signIn(
  accessToken: string,
  refreshToken: string,
  user: SessionUser,
): void {
  storeTokens(accessToken, refreshToken)
  storeUser(user)
  currentUser.value = user
}

/** Clears the stored session and notifies the shell. */
export function signOut(): void {
  clearSession()
  currentUser.value = null
}
