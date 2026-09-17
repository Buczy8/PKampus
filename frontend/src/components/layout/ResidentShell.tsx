import * as React from "react"
import { Outlet, useOutletContext, useNavigate } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"

import { logout } from "@/api/auth"
import type { UserProfile } from "@/api/types"
import { clearAuthTokens, getRefreshToken } from "@/lib/auth-storage"
import { NetworkStatusBanner } from "./NetworkStatusBanner"
import { ResidentBottomNav } from "./ResidentBottomNav"
import { ResidentHeader } from "./ResidentHeader"
import { ResidentSidebar } from "./ResidentSidebar"

const SIDEBAR_COLLAPSED_KEY = "pkampus.sidebar.collapsed"

export function ResidentShell() {
  const user = useOutletContext<UserProfile>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [sidebarCollapsed, setSidebarCollapsed] = React.useState<boolean>(() => {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "true"
    } catch {
      return false
    }
  })

  const handleToggleCollapse = React.useCallback(() => {
    setSidebarCollapsed((prev) => {
      const next = !prev
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(next))
      } catch {
        // ignore
      }
      return next
    })
  }, [])

  const handleLogout = React.useCallback(async () => {
    try {
      const refreshToken = getRefreshToken()
      await logout(refreshToken)
    } catch {
      // ignore network errors on logout
    } finally {
      clearAuthTokens()
      queryClient.clear()
      navigate("/login", { replace: true })
    }
  }, [navigate, queryClient])

  if (!user) {
    return null
  }

  return (
    <div className="flex min-h-screen bg-background text-foreground antialiased selection:bg-primary selection:text-primary-foreground">
      {/* Desktop Collapsible Sidebar */}
      <ResidentSidebar
        user={user}
        collapsed={sidebarCollapsed}
        onToggleCollapse={handleToggleCollapse}
        onLogout={handleLogout}
      />

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col min-w-0 pb-24 md:pb-6">
        {/* Top Header */}
        <ResidentHeader user={user} onLogout={handleLogout} />

        {/* Global System Alert / Network Banner */}
        <NetworkStatusBanner />

        {/* Page Content Viewport */}
        <main className="flex-1 w-full max-w-7xl mx-auto p-4 md:p-6 lg:p-8 animate-in fade-in-50 duration-200">
          <Outlet context={user} />
        </main>

        {/* Mobile Bottom Navigation Bar */}
        <ResidentBottomNav />
      </div>
    </div>
  )
}
