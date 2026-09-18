const ACCESS_TOKEN_KEY = 'pkampus.accessToken'
const REFRESH_TOKEN_KEY = 'pkampus.refreshToken'

/** Bumped on login / token replace so stale refresh failures cannot wipe a newer session. */
let authSessionEpoch = 0

export function getAuthSessionEpoch(): number {
  return authSessionEpoch
}

export function bumpAuthSessionEpoch(): number {
  authSessionEpoch += 1
  return authSessionEpoch
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setAuthTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  bumpAuthSessionEpoch()
}

export function clearAuthTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  bumpAuthSessionEpoch()
}
