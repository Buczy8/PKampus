import { Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

import { getCurrentUser } from '@/api/auth'
import { homePathForRole } from '@/api/types'
import { getAccessToken } from '@/lib/auth-storage'

function isAuthFailure(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  return error.response?.status === 401
}

/**
 * Catch-all / root redirect: login if anonymous, otherwise role home
 * (or forced password change).
 */
export function FallbackRedirect() {
  const token = getAccessToken()

  const meQuery = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    enabled: Boolean(token),
    retry: false,
  })

  if (!token) {
    return <Navigate to="/login" replace />
  }

  if (meQuery.isPending || (meQuery.isFetching && !meQuery.data)) {
    return (
      <main className="flex min-h-svh items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </main>
    )
  }

  if (meQuery.isError || !meQuery.data) {
    if (!getAccessToken() || isAuthFailure(meQuery.error)) {
      return <Navigate to="/login" replace />
    }
    return <Navigate to="/login" replace />
  }

  if (meQuery.data.status === 'MUST_CHANGE_PASSWORD') {
    return <Navigate to="/change-password" replace />
  }

  return <Navigate to={homePathForRole(meQuery.data.role)} replace />
}
