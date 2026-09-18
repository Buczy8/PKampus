import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import {
  clearAuthTokens,
  getAccessToken,
  getAuthSessionEpoch,
  getRefreshToken,
  setAuthTokens,
} from '@/lib/auth-storage'
import type { ApiResponse, AuthResponse } from '@/api/types'

const API_BASE_URL = '/api/v1'
const REFRESH_LOCK_NAME = 'pkampus-token-refresh'

type RetriableConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
})

/** Bare client without interceptors — used only for token refresh. */
const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

let refreshInFlight: Promise<string> | null = null

function isPublicAuthRequest(url?: string): boolean {
  if (!url) return false
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/register') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/verify-email') ||
    url.includes('/auth/logout')
  )
}

async function rotateTokensUnlocked(): Promise<string> {
  const epochAtStart = getAuthSessionEpoch()
  // Re-read after lock — another tab / login may have already rotated.
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }

  try {
    const { data } = await refreshClient.post<ApiResponse<AuthResponse>>(
      '/auth/refresh',
      { refreshToken },
    )

    if (!data.success || !data.data?.token || !data.data.refreshToken) {
      throw new Error(data.message ?? 'Token refresh failed')
    }

    // Newer login/session won the race — keep its tokens.
    if (epochAtStart !== getAuthSessionEpoch()) {
      const current = getAccessToken()
      if (current) return current
    }

    setAuthTokens(data.data.token, data.data.refreshToken)
    return data.data.token
  } catch (error) {
    // If tokens changed (e.g. user logged in again), do not treat this as logout.
    if (epochAtStart !== getAuthSessionEpoch()) {
      const current = getAccessToken()
      if (current) return current
    }
    // Another tab may have rotated successfully while we held a stale RT.
    if (getRefreshToken() && getRefreshToken() !== refreshToken) {
      const current = getAccessToken()
      if (current) return current
    }
    throw error
  }
}

async function rotateTokens(): Promise<string> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  if (locks?.request) {
    return locks.request(REFRESH_LOCK_NAME, () => rotateTokensUnlocked())
  }
  return rotateTokensUnlocked()
}

function redirectToLogin() {
  clearAuthTokens()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

apiClient.interceptors.request.use((config) => {
  const isFormData = typeof FormData !== 'undefined' && config.data instanceof FormData

  if (!isFormData && !config.headers['Content-Type']) {
    config.headers['Content-Type'] = 'application/json'
  }

  if (isFormData) {
    delete config.headers['Content-Type']
  }

  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined
    const status = error.response?.status

    if (
      status !== 401 ||
      !original ||
      original._retry ||
      isPublicAuthRequest(original.url)
    ) {
      return Promise.reject(error)
    }

    original._retry = true
    const epochAtStart = getAuthSessionEpoch()

    try {
      if (!refreshInFlight) {
        refreshInFlight = rotateTokens().finally(() => {
          refreshInFlight = null
        })
      }

      const accessToken = await refreshInFlight
      original.headers.Authorization = `Bearer ${accessToken}`
      return apiClient(original)
    } catch {
      // Only wipe session if nothing newer replaced tokens during the attempt.
      if (epochAtStart === getAuthSessionEpoch()) {
        redirectToLogin()
      }
      return Promise.reject(error)
    }
  },
)

/** Explicit refresh (e.g. before long-lived screens). */
export async function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = rotateTokens().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}
