import { Link, NavLink } from "react-router-dom"
import { ChevronLeft, ChevronRight, LogOut } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { PkLogo } from "@/components/brand/PkLogo"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "cn"

import { isNavEndPath, navSectionsForRole } from "./nav-config"

interface AppSidebarProps {
  user: UserProfile
  collapsed: boolean
  onToggleCollapse: () => void
  onLogout: () => void
}

export function AppSidebar({
  user,
  collapsed,
  onToggleCollapse,
  onLogout,
}: AppSidebarProps) {
  const initials = `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() || "M"
  const sections = navSectionsForRole(user.role)

  return (
    <aside
      className={cn(
        "hidden md:flex flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-all duration-300 ease-in-out shrink-0 sticky top-0 h-dvh z-30 select-none",
        collapsed ? "w-16" : "w-64"
      )}
    >
      <div
        className={cn(
          "flex items-center h-14 border-b border-sidebar-border/60 shrink-0",
          collapsed ? "justify-center px-0 w-full" : "justify-between px-3"
        )}
      >
        {!collapsed ? (
          <>
            <div className="flex items-center gap-2.5 min-w-0">
              <PkLogo variant="icon" className="size-9 shrink-0 rounded-lg" />
              <div className="flex flex-col min-w-0">
                <span className="font-semibold text-sm tracking-tight truncate leading-tight flex items-center gap-1.5">
                  PKampus
                  <span className="text-[10px] font-normal px-1.5 py-0.2 rounded-full bg-primary/10 text-primary">
                    DS
                  </span>
                </span>
                <span className="text-[11px] text-muted-foreground truncate">
                  Politechnika Krakowska
                </span>
              </div>
            </div>

            <Button
              variant="ghost"
              size="icon-xs"
              onClick={onToggleCollapse}
              className="text-muted-foreground hover:text-foreground shrink-0"
              title="Zwiń pasek"
            >
              <ChevronLeft className="size-4" />
            </Button>
          </>
        ) : (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                onClick={onToggleCollapse}
                className="size-10 rounded-lg text-muted-foreground hover:text-foreground hover:bg-sidebar-accent"
                title="Rozwiń pasek"
              >
                <ChevronRight className="size-5" />
              </Button>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              Rozwiń pasek boczny
            </TooltipContent>
          </Tooltip>
        )}
      </div>

      <div
        className={cn(
          "flex-1 overflow-y-auto overflow-x-hidden py-3 space-y-4",
          collapsed ? "px-0 w-full flex flex-col items-center" : "px-2"
        )}
      >
        {sections.map((section) => (
          <div
            key={section.label}
            className={cn("w-full", collapsed ? "flex flex-col items-center space-y-1" : "space-y-1")}
          >
            {!collapsed && (
              <p className="px-2.5 text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
                {section.label}
              </p>
            )}
            <div className={cn("w-full", collapsed ? "flex flex-col items-center space-y-1" : "space-y-1")}>
              {section.items.map((item) => {
                const Icon = item.icon
                const linkContent = (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={isNavEndPath(item.to)}
                    className={({ isActive }) =>
                      cn(
                        "flex items-center rounded-lg text-sm transition-colors group relative",
                        collapsed
                          ? "size-10 justify-center"
                          : "gap-3 py-2 px-2.5 w-full",
                        isActive
                          ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium shadow-xs"
                          : "text-sidebar-foreground/75 hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <Icon
                          className={cn(
                            "size-4.5 shrink-0 transition-transform group-hover:scale-105",
                            isActive ? "text-primary" : "text-muted-foreground"
                          )}
                        />
                        {!collapsed && (
                          <span className="truncate flex-1">{item.title}</span>
                        )}
                      </>
                    )}
                  </NavLink>
                )

                if (collapsed) {
                  return (
                    <Tooltip key={item.to}>
                      <TooltipTrigger asChild>{linkContent}</TooltipTrigger>
                      <TooltipContent side="right" sideOffset={10}>
                        {item.title}
                      </TooltipContent>
                    </Tooltip>
                  )
                }

                return linkContent
              })}
            </div>
          </div>
        ))}
      </div>

      <div
        className={cn(
          "border-t border-sidebar-border/60 shrink-0",
          collapsed
            ? "p-0 py-3 w-full flex flex-col items-center gap-2"
            : "p-2 pb-4 space-y-1.5"
        )}
      >
        {collapsed ? (
          <>
            <Tooltip>
              <TooltipTrigger asChild>
                <Link
                  to="/settings"
                  className="flex size-10 items-center justify-center rounded-lg hover:bg-sidebar-accent/60 transition-colors"
                  aria-label="Ustawienia konta"
                >
                  <Avatar className="size-8 ring-1 ring-border">
                    {user.avatarUrl && (
                      <AvatarImage src={user.avatarUrl} alt={user.firstName} />
                    )}
                    <AvatarFallback className="text-xs bg-primary/10 text-primary font-semibold">
                      {initials}
                    </AvatarFallback>
                  </Avatar>
                </Link>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={10}>
                <div className="flex flex-col">
                  <span className="font-semibold text-xs">
                    {user.firstName} {user.lastName}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {user.dormitoryName ?? "DS PK"} {user.roomNumber ? `• pok. ${user.roomNumber}` : ""}
                  </span>
                  <span className="text-[11px] text-muted-foreground mt-0.5">
                    Ustawienia
                  </span>
                </div>
              </TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={onLogout}
                  className="size-10 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-lg"
                  title="Wyloguj się"
                >
                  <LogOut className="size-4.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent side="right" sideOffset={10}>
                Wyloguj się
              </TooltipContent>
            </Tooltip>
          </>
        ) : (
          <div className="flex items-center gap-2.5 rounded-xl p-2 bg-sidebar-accent/50 border border-sidebar-border/40">
            <Link
              to="/settings"
              className="flex items-center gap-2.5 min-w-0 flex-1 rounded-lg -m-1 p-1 hover:bg-sidebar-accent/80 transition-colors"
              aria-label="Ustawienia konta"
            >
              <Avatar className="size-8 ring-1 ring-border shrink-0">
                {user.avatarUrl && (
                  <AvatarImage src={user.avatarUrl} alt={user.firstName} />
                )}
                <AvatarFallback className="text-xs bg-primary/10 text-primary font-semibold">
                  {initials}
                </AvatarFallback>
              </Avatar>

              <div className="flex flex-col min-w-0 flex-1">
                <span className="text-xs font-semibold truncate leading-tight">
                  {user.firstName} {user.lastName}
                </span>
                <span className="text-[11px] text-muted-foreground truncate">
                  {user.dormitoryName ?? "DS PK"} {user.roomNumber ? `• pok. ${user.roomNumber}` : ""}
                </span>
              </div>
            </Link>

            <Button
              variant="ghost"
              size="icon-xs"
              onClick={onLogout}
              className="text-muted-foreground hover:text-destructive hover:bg-destructive/10 shrink-0"
              title="Wyloguj się"
            >
              <LogOut className="size-3.5" />
            </Button>
          </div>
        )}
      </div>
    </aside>
  )
}
