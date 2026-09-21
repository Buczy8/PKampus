import * as React from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { LogOut, Settings } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { PkLogo } from "@/components/brand/PkLogo"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

import { titleForPath } from "./nav-config"

interface AppHeaderProps {
  user: UserProfile
  onLogout: () => void
}

export function AppHeader({ user, onLogout }: AppHeaderProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const [isOnline, setIsOnline] = React.useState<boolean>(
    typeof navigator !== "undefined" ? navigator.onLine : true
  )

  React.useEffect(() => {
    const handleOnline = () => setIsOnline(true)
    const handleOffline = () => setIsOnline(false)

    window.addEventListener("online", handleOnline)
    window.addEventListener("offline", handleOffline)

    return () => {
      window.removeEventListener("online", handleOnline)
      window.removeEventListener("offline", handleOffline)
    }
  }, [])

  const currentRouteInfo = titleForPath(location.pathname)
  const initials = `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() || "M"

  return (
    <header className="sticky top-0 z-30 flex h-16 w-full shrink-0 items-center justify-between border-b border-border/60 bg-background/90 px-4 backdrop-blur-md transition-all">
      <div className="flex items-center gap-3 min-w-0">
        <PkLogo
          variant="icon"
          className="flex md:hidden size-8 shrink-0 rounded-lg shadow-xs"
        />

        <div className="flex flex-col min-w-0">
          <h1 className="text-sm md:text-base font-semibold text-foreground tracking-tight truncate leading-tight">
            {currentRouteInfo.title}
          </h1>
          <p className="text-xs text-muted-foreground hidden sm:block truncate leading-tight">
            {currentRouteInfo.subtitle}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2 md:gap-3">
        <div
          className="flex items-center gap-2 px-3 py-1.5 rounded-full border border-border/70 bg-muted/40 text-xs text-foreground shrink-0 select-none shadow-2xs whitespace-nowrap"
          title={isOnline ? "Połączono z serwerem PKampus" : "Brak połączenia z siecią (Offline)"}
        >
          <span className="relative flex size-2 shrink-0">
            {isOnline ? (
              <>
                <span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex size-2 rounded-full bg-emerald-500" />
              </>
            ) : (
              <span className="size-2 rounded-full bg-destructive" />
            )}
          </span>

          <span className="hidden sm:inline font-medium text-foreground truncate max-w-[140px] sm:max-w-[200px]">
            {user.dormitoryName ?? "DS PK"}
          </span>

          {user.roomNumber && (
            <span className="hidden sm:inline text-muted-foreground/40 shrink-0">•</span>
          )}

          {user.roomNumber && (
            <span className="font-semibold text-primary shrink-0">
              pok. {user.roomNumber}
            </span>
          )}

          {!isOnline && (
            <span className="text-[10px] font-bold text-destructive bg-destructive/15 px-1.5 py-0.2 rounded-sm shrink-0">
              Offline
            </span>
          )}
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="relative size-8 rounded-full ring-1 ring-border/80 p-0 hover:ring-primary/50 transition-all"
            >
              <Avatar size="sm" className="size-8">
                {user.avatarUrl && (
                  <AvatarImage src={user.avatarUrl} alt={user.firstName} />
                )}
                <AvatarFallback className="text-xs bg-primary/10 text-primary font-semibold">
                  {initials}
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>

          <DropdownMenuContent align="end" className="w-56" sideOffset={8}>
            <DropdownMenuLabel className="font-normal">
              <div className="flex flex-col space-y-1">
                <p className="text-sm font-semibold leading-none truncate">
                  {user.firstName} {user.lastName}
                </p>
                <p className="text-xs text-muted-foreground leading-none truncate">
                  {user.email}
                </p>
              </div>
            </DropdownMenuLabel>

            <DropdownMenuSeparator />

            <DropdownMenuGroup>
              <DropdownMenuItem onClick={() => navigate("/settings")}>
                <Settings className="mr-2 size-4" />
                <span>Ustawienia konta</span>
              </DropdownMenuItem>
            </DropdownMenuGroup>

            <DropdownMenuSeparator />

            <DropdownMenuItem
              onClick={onLogout}
              className="text-destructive focus:bg-destructive/10 focus:text-destructive"
            >
              <LogOut className="mr-2 size-4" />
              <span>Wyloguj się</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
