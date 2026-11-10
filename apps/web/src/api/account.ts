import { apiRequest } from './client'

/** Cancellation state: {@code status} is 0 cooling off, 1 withdrawn, null never applied. */
export interface AccountCancellationView {
  status: number | null
  remainingSeconds: number
}

export function getCancellationState(): Promise<AccountCancellationView> {
  return apiRequest<AccountCancellationView>('/api/v1/auth/account-cancellation')
}

export function applyCancellation(): Promise<AccountCancellationView> {
  return apiRequest<AccountCancellationView>('/api/v1/auth/account-cancellation', { method: 'POST' })
}

export function withdrawCancellation(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/account-cancellation', { method: 'DELETE' })
}
