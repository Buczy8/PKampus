import * as React from "react"
import { AlertTriangle, WifiOff, X } from "lucide-react"

interface NetworkStatusBannerProps {
  announcement?: {
    id: string
    title: string
    message: string
    type?: "info" | "warning" | "alert"
  } | null
}

export function NetworkStatusBanner({ announcement }: NetworkStatusBannerProps) {
  const [isOnline, setIsOnline] = React.useState<boolean>(
    typeof navigator !== "undefined" ? navigator.onLine : true
  )
  const [dismissedAnnouncements, setDismissedAnnouncements] = React.useState<string[]>([])

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

  const showAnnouncement =
    announcement && !dismissedAnnouncements.includes(announcement.id)

  if (isOnline && !showAnnouncement) {
    return null
  }

  return (
    <div className="flex flex-col w-full z-40">
      {!isOnline && (
        <div className="flex items-center justify-between gap-3 px-4 py-2 text-xs md:text-sm bg-destructive text-destructive-foreground font-medium animate-in fade-in-0 duration-200">
          <div className="flex items-center gap-2 mx-auto max-w-7xl">
            <WifiOff className="size-4 shrink-0 animate-pulse" />
            <span>
              <strong>Brak połączenia z siecią:</strong> Weryfikacja dynamiczna karty mieszkańca (kod dnia) oraz składanie rezerwacji są niedostępne w trybie offline.
            </span>
          </div>
        </div>
      )}

      {isOnline && showAnnouncement && (
        <div
          className={`flex items-center justify-between gap-3 px-4 py-2 text-xs md:text-sm font-medium border-b ${
            announcement.type === "alert"
              ? "bg-destructive/15 text-destructive border-destructive/20"
              : announcement.type === "warning"
              ? "bg-amber-500/15 text-amber-900 dark:text-amber-200 border-amber-500/25"
              : "bg-primary/10 text-primary border-primary/15"
          }`}
        >
          <div className="flex items-center gap-2 max-w-7xl mx-auto flex-1">
            <AlertTriangle className="size-4 shrink-0" />
            <span>
              <strong>{announcement.title}:</strong> {announcement.message}
            </span>
          </div>
          <button
            type="button"
            onClick={() => setDismissedAnnouncements((prev) => [...prev, announcement.id])}
            className="p-1 rounded-sm hover:bg-black/5 dark:hover:bg-white/10"
            aria-label="Zamknij powiadomienie"
          >
            <X className="size-3.5" />
          </button>
        </div>
      )}
    </div>
  )
}
