import { Navigate, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

import { getCurrentUser } from '@/api/auth'
import { isAdminRole } from '@/api/types'
import { getAccessToken } from '@/lib/auth-storage'

type ProtectedRouteProps = {
  adminOnly?: boolean
}

function isAuthFailure(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  // Only 401 ends the session. 403 is used for business/account rules (e.g. laundry).
  return error.response?.status === 401
}

export function ProtectedRoute({ adminOnly = false }: ProtectedRouteProps) {
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

  // Wait for in-flight /me (incl. refetch after a cached error from a prior session).
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

    return (
      <main className="flex min-h-svh flex-col items-center justify-center gap-3 p-6">
        <p className="text-sm text-muted-foreground">
          Nie udało się wczytać sesji. Sprawdź połączenie i spróbuj ponownie.
        </p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void meQuery.refetch()}
        >
          Spróbuj ponownie
        </button>
      </main>
    )
  }

  if (adminOnly && !isAdminRole(meQuery.data.role)) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet context={meQuery.data} />
}
