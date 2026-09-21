import { NavLink, useOutletContext } from "react-router-dom"

import type { UserProfile } from "@/api/types"
import { cn } from "cn"

import { bottomNavForRole } from "./nav-config"

export function AppBottomNav() {
  const user = useOutletContext<UserProfile>()
  const items = bottomNavForRole(user?.role)

  return (
    <nav
      className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-background/95 backdrop-blur-md border-t border-border/80 px-1 pt-1.5 pb-[calc(env(safe-area-inset-bottom,0px)+8px)] shadow-lg select-none"
      aria-label="Nawigacja mobilna"
    >
      <div
        className={cn(
          "grid items-center max-w-md mx-auto",
          items.length === 5
            ? "grid-cols-5"
            : items.length === 4
              ? "grid-cols-4"
              : items.length <= 3
                ? "grid-cols-3"
                : items.length === 7
                  ? "grid-cols-7"
                  : "grid-cols-6",
        )}
      >
        {items.map((item) => {
          const Icon = item.icon

          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center justify-center py-1 rounded-lg text-[10px] font-medium transition-all relative group",
                  isActive
                    ? "text-primary font-semibold"
                    : "text-muted-foreground hover:text-foreground active:scale-95"
                )
              }
            >
              {({ isActive }) => (
                <>
                  <div
                    className={cn(
                      "flex items-center justify-center size-7 rounded-md transition-all",
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "group-hover:bg-muted"
                    )}
                  >
                    <Icon className="size-4.5 transition-transform" />
                  </div>
                  <span className="mt-0.5 truncate max-w-full text-center leading-tight">
                    {item.title}
                  </span>
                  {isActive && (
                    <span className="absolute -bottom-1 size-1 rounded-full bg-primary" />
                  )}
                </>
              )}
            </NavLink>
          )
        })}
      </div>
    </nav>
  )
}
