import { NavLink, useOutletContext } from "react-router-dom"
import {
  DoorClosed,
  IdCard,
  LayoutDashboard,
  Megaphone,
  Settings,
  Waves,
  Wrench,
} from "lucide-react"

import type { UserProfile } from "@/api/types"
import { isReceptionistRole } from "@/api/types"
import { cn } from "cn"

interface BottomNavItem {
  title: string
  to: string
  icon: React.ComponentType<{ className?: string }>
}

const residentBottomNavItems: BottomNavItem[] = [
  {
    title: "Pulpit",
    to: "/dashboard",
    icon: LayoutDashboard,
  },
  {
    title: "Karta",
    to: "/card",
    icon: IdCard,
  },
  {
    title: "Pralnia",
    to: "/laundry",
    icon: Waves,
  },
  {
    title: "Salki",
    to: "/rooms",
    icon: DoorClosed,
  },
  {
    title: "Usterki",
    to: "/issues",
    icon: Wrench,
  },
  {
    title: "Ogłoszenia",
    to: "/board",
    icon: Megaphone,
  },
]

const receptionistBottomNavItems: BottomNavItem[] = [
  {
    title: "Pulpit",
    to: "/receptionist",
    icon: LayoutDashboard,
  },
  {
    title: "Pralnia",
    to: "/receptionist/laundry",
    icon: Waves,
  },
  {
    title: "Ustawienia",
    to: "/settings",
    icon: Settings,
  },
]

export function ResidentBottomNav() {
  const user = useOutletContext<UserProfile>()
  const items = isReceptionistRole(user?.role)
    ? receptionistBottomNavItems
    : residentBottomNavItems

  return (
    <nav
      className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-background/95 backdrop-blur-md border-t border-border/80 px-1 pt-1.5 pb-[calc(env(safe-area-inset-bottom,0px)+8px)] shadow-lg select-none"
      aria-label="Nawigacja mobilna"
    >
      <div
        className={cn(
          "grid items-center max-w-md mx-auto",
          items.length <= 3 ? "grid-cols-3" : "grid-cols-6",
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
