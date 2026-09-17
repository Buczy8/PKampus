import * as React from "react"
import { useOutletContext, Link } from "react-router-dom"
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  Clock,
  Fingerprint,
  Maximize2,
  Minimize2,
  ShieldCheck,
} from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Button } from "@/components/ui/button"
import { cn } from "cn"

export function CardPage() {
  const user = useOutletContext<UserProfile>()
  const [currentTime, setCurrentTime] = React.useState<string>("")
  const [fullscreen, setFullscreen] = React.useState<boolean>(false)
  const [ripples, setRipples] = React.useState<{ id: number; x: number; y: number }[]>([])

  // Live real-time clock (FR-CARD-02)
  React.useEffect(() => {
    const update = () => {
      const now = new Date()
      setCurrentTime(
        now.toLocaleTimeString("pl-PL", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        })
      )
    }
    update()
    const timer = setInterval(update, 1000)
    return () => clearInterval(timer)
  }, [])

  // Touch Challenge / Ripple effect (FR-CARD-02)
  const triggerRippleAt = (clientX: number, clientY: number, currentTarget: HTMLElement) => {
    const rect = currentTarget.getBoundingClientRect()
    const x = clientX - rect.left
    const y = clientY - rect.top
    const newRipple = { id: Date.now(), x, y }

    setRipples((prev) => [...prev.slice(-4), newRipple])
    setTimeout(() => {
      setRipples((prev) => prev.filter((r) => r.id !== newRipple.id))
    }, 1000)
  }

  const handleTouchChallenge = (e: React.MouseEvent<HTMLDivElement>) => {
    triggerRippleAt(e.clientX, e.clientY, e.currentTarget)
  }

  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    const touch = e.touches[0]
    if (touch) {
      triggerRippleAt(touch.clientX, touch.clientY, e.currentTarget)
    }
  }

  const initials = `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() || "M"

  // Generated dynamic day code for visual verification on reception
  const todayStr = new Date().toISOString().slice(0, 10).replace(/-/g, "")
  const dynamicCode = `PK-${todayStr.slice(4)}-${(user.roomNumber || "000").padStart(3, "0")}`

  return (
    <div className={cn(
      "flex flex-col items-center justify-center transition-all",
      fullscreen
        ? "fixed inset-0 z-50 bg-background/95 backdrop-blur-xl p-4 overflow-y-auto"
        : "max-w-md mx-auto py-2"
    )}>
      {/* Top action bar */}
      <div className="w-full flex items-center justify-between mb-4">
        {!fullscreen ? (
          <Button variant="ghost" size="sm" asChild className="gap-1.5 text-muted-foreground">
            <Link to="/dashboard">
              <ArrowLeft className="size-4" />
              <span>Wróć do pulpitu</span>
            </Link>
          </Button>
        ) : (
          <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
            Tryb Okazania Portierowi
          </span>
        )}

        <Button
          variant="outline"
          size="sm"
          onClick={() => setFullscreen((v) => !v)}
          className="gap-1.5 text-xs ml-auto"
        >
          {fullscreen ? (
            <>
              <Minimize2 className="size-3.5" />
              Zamknij pełny ekran
            </>
          ) : (
            <>
              <Maximize2 className="size-3.5" />
              Pełny ekran (Portiernia)
            </>
          )}
        </Button>
      </div>

      {/* Main Virtual Card Document (mObywatel style) */}
      <div
        onClick={handleTouchChallenge}
        onTouchStart={handleTouchStart}
        className={cn(
          "relative w-full max-w-sm rounded-3xl overflow-hidden border border-border/80 shadow-2xl transition-all select-none cursor-pointer group",
          "bg-gradient-to-br from-slate-900 via-slate-800 to-indigo-950 text-white"
        )}
      >
        {/* Animated Holographic Shimmer / Gradient (FR-CARD-02) */}
        <div
          aria-hidden="true"
          className="absolute inset-0 opacity-25 pointer-events-none bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-sky-400 via-indigo-400 to-transparent animate-pulse"
        />

        {/* Dynamic touch challenge ripples */}
        {ripples.map((ripple) => (
          <span
            key={ripple.id}
            style={{ left: ripple.x - 40, top: ripple.y - 40 }}
            className="absolute size-20 rounded-full bg-cyan-400/40 pointer-events-none animate-ping"
          />
        ))}

        <div className="relative p-6 flex flex-col gap-5">
          {/* Card Header: PKampus crest & real-time clock */}
          <div className="flex items-center justify-between border-b border-white/10 pb-4">
            <div className="flex items-center gap-2">
              <div className="flex size-9 items-center justify-center rounded-xl bg-white/15 backdrop-blur-md border border-white/20">
                <Building2 className="size-5 text-cyan-300" />
              </div>
              <div>
                <h2 className="text-xs font-bold tracking-wider uppercase text-cyan-300">
                  Politechnika Krakowska
                </h2>
                <p className="text-[11px] text-white/70 font-medium">
                  Osiedle Studenckie • Wirtualna Karta
                </p>
              </div>
            </div>

            {/* Live Clock */}
            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-black/40 border border-white/15 text-[11px] font-mono text-emerald-300">
              <Clock className="size-3 animate-spin" style={{ animationDuration: "8s" }} />
              <span>{currentTime || "00:00:00"}</span>
            </div>
          </div>

          {/* Card Body: Photo & Identity */}
          <div className="flex items-start gap-4">
            <div className="relative shrink-0">
              <div className="size-24 rounded-2xl overflow-hidden ring-2 ring-cyan-400/50 shadow-inner bg-black/40 flex items-center justify-center">
                {user.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt={user.firstName}
                    className="size-full object-cover"
                  />
                ) : (
                  <div className="text-2xl font-bold text-white/60">
                    {initials}
                  </div>
                )}
              </div>
              <div className="absolute -bottom-1 -right-1 size-5 rounded-full bg-emerald-500 border-2 border-slate-900 flex items-center justify-center text-white" title="Konto aktywne">
                <CheckCircle2 className="size-3.5" />
              </div>
            </div>

            <div className="flex flex-col min-w-0 flex-1">
              <span className="text-[10px] font-semibold text-cyan-300 uppercase tracking-wider">
                Mieszkaniec
              </span>
              <h3 className="text-lg font-bold text-white tracking-tight truncate leading-tight">
                {user.firstName} {user.lastName}
              </h3>
              <p className="text-xs text-white/80 font-medium mt-1 truncate">
                {user.dormitoryName ?? "Dom Studencki PK"}
              </p>
              <div className="flex items-center gap-2 mt-2">
                <span className="text-xs px-2 py-0.5 rounded-md bg-white/15 text-white font-mono font-semibold">
                  Pokój {user.roomNumber ?? "—"}
                </span>
                <span className="text-[11px] text-white/60">
                  2025/2026
                </span>
              </div>
            </div>
          </div>

          {/* Verification Bar: Dynamic Day Code & Hologram Touch Hint */}
          <div className="pt-2 border-t border-white/10 flex items-center justify-between">
            <div>
              <span className="text-[9px] uppercase tracking-wider text-white/50 block">
                Kod Dnia (Portiernia)
              </span>
              <span className="font-mono text-xs font-bold text-cyan-200 tracking-wider">
                {dynamicCode}
              </span>
            </div>

            <div className="flex items-center gap-1.5 text-[10px] text-white/60 bg-white/5 px-2 py-1 rounded-lg border border-white/10">
              <Fingerprint className="size-3.5 text-cyan-400" />
              <span>Dotknij, aby zweryfikować</span>
            </div>
          </div>
        </div>

        {/* Dynamic bottom status bar */}
        <div className="bg-emerald-600/30 border-t border-emerald-500/30 px-6 py-2 flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-xs text-emerald-300 font-semibold">
            <ShieldCheck className="size-4" />
            <span>Karta Ważna • Mieszkaniec Aktywny</span>
          </div>
          <span className="text-[10px] text-emerald-300/80 font-mono">
            STATUS: ACTIVE
          </span>
        </div>
      </div>

      <p className="text-xs text-muted-foreground text-center mt-4 max-w-xs">
        Okazuj wirtualną kartę podczas wchodzenia do akademika oraz przy odbiorze kluczy na portierni (zgodnie z FR-CARD-02).
      </p>
    </div>
  )
}
