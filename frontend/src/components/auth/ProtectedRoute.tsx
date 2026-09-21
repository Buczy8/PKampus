import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

import { getCurrentUser } from '@/api/auth'
import { isAdminRole, isSuperAdminRole, homePathForRole } from '@/api/types'
import { getAccessToken } from '@/lib/auth-storage'

type ProtectedRouteProps = {
  adminOnly?: boolean
  superAdminOnly?: boolean
  /** Allow MUST_CHANGE_PASSWORD users (change-password page only). */
  allowMustChangePassword?: boolean
}

function isAuthFailure(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  // Only 401 ends the session. 403 is used for business/account rules (e.g. laundry).
  return error.response?.status === 401
}

export function ProtectedRoute({
  adminOnly = false,
  superAdminOnly = false,
  allowMustChangePassword = false,
}: ProtectedRouteProps) {
  const token = getAccessToken()
  const location = useLocation()

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

  const user = meQuery.data

  if (user.status === 'MUST_CHANGE_PASSWORD') {
    if (allowMustChangePassword || location.pathname === '/change-password') {
      return <Outlet context={user} />
    }
    return <Navigate to="/change-password" replace />
  }

  if (allowMustChangePassword && user.status !== 'MUST_CHANGE_PASSWORD') {
    return <Navigate to={homePathForRole(user.role)} replace />
  }

  if (superAdminOnly && !isSuperAdminRole(user.role)) {
    return <Navigate to={homePathForRole(user.role)} replace />
  }

  if (adminOnly && !isAdminRole(user.role)) {
    return <Navigate to={homePathForRole(user.role)} replace />
  }

  // DORM_ADMIN stays on /admin; SUPER_ADMIN belongs on /superadmin
  if (adminOnly && isSuperAdminRole(user.role)) {
    return <Navigate to="/superadmin" replace />
  }

  return <Outlet context={user} />
}
