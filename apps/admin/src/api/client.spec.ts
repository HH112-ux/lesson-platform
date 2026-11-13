import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ACCESS_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  accessToken,
  apiRequest,
  clearSession,
  storeTokens,
} from './client'

/** Minimal Storage backed by a Map so the client can run outside a browser. */
function stubStorage(): void {
  const store = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
    key: (index: number) => [...store.keys()][index] ?? null,
    get length() {
      return store.size
    },
  })
}

/** Builds a unified envelope response the client understands. */
function envelope(code: number, message: string): Response {
  return new Response(JSON.stringify({ code, message, data: null, traceId: 'trace' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  stubStorage()
  clearSession()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiRequest session handling', () => {
  it('clears the session when another endpoint rejects an invalid token', async () => {
    storeTokens('access', 'refresh')
    vi.stubGlobal('fetch', vi.fn(async () => envelope(40101, 'missing credentials or identity')))

    await expect(apiRequest('/api/v1/admin/orders')).rejects.toMatchObject({ code: 40101 })

    expect(accessToken.value).toBeNull()
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('keeps the refresh path and clears the session when renewal fails', async () => {
    storeTokens('access', 'refresh')
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    const fetchMock = vi.fn(async () => envelope(40102, 'credential expired'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/api/v1/admin/orders')).rejects.toMatchObject({ code: 40102 })

    expect(accessToken.value).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('leaves the stored session alone when the anonymous login endpoint rejects credentials', async () => {
    storeTokens('access', 'refresh')
    vi.stubGlobal('fetch', vi.fn(async () => envelope(40101, 'missing credentials or identity')))

    await expect(
      apiRequest('/api/v1/auth/login', { method: 'POST', body: '{}' }, false),
    ).rejects.toMatchObject({ code: 40101 })

    expect(accessToken.value).toBe('access')
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('access')
  })
})
