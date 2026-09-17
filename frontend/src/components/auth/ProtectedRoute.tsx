import { Navigate, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { getCurrentUser } from '@/api/auth'
import { isAdminRole } from '@/api/types'
import { getAccessToken } from '@/lib/auth-storage'

type ProtectedRouteProps = {
  adminOnly?: boolean
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

  if (meQuery.isLoading) {
    return (
      <main className="flex min-h-svh items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </main>
    )
  }

  if (meQuery.isError || !meQuery.data) {
    return <Navigate to="/login" replace />
  }

  if (adminOnly && !isAdminRole(meQuery.data.role)) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet context={meQuery.data} />
}
