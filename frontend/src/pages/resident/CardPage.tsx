import * as React from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import {
  ArrowLeft,
  Maximize2,
  Minimize2,
  ShieldAlert,
  ShieldCheck,
  WifiOff,
  Sparkles,
} from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import { CardAccountError, getResidentCard } from "@/api/profile"
import type { ResidentCard } from "@/api/types"
import { PkLogo } from "@/components/brand/PkLogo"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { cn } from "cn"

const CARD_REFETCH_MS = 20_000

function useOnline(): boolean {
  const [online, setOnline] = React.useState(
    typeof navigator !== "undefined" ? navigator.onLine : true,
  )
  React.useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener("online", on)
    window.addEventListener("offline", off)
    return () => {
      window.removeEventListener("online", on)
      window.removeEventListener("offline", off)
    }
  }, [])
  return online
}

function useServerClock(serverTimeIso: string | undefined): string {
  const offsetRef = React.useRef(0)
  const [display, setDisplay] = React.useState("")

  React.useEffect(() => {
    if (!serverTimeIso) return
    const serverMs = new Date(serverTimeIso).getTime()
    if (!Number.isNaN(serverMs)) {
      offsetRef.current = serverMs - Date.now()
    }
  }, [serverTimeIso])

  React.useEffect(() => {
    const tick = () => {
      const now = new Date(Date.now() + offsetRef.current)
      setDisplay(
        now.toLocaleTimeString("pl-PL", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          timeZone: "Europe/Warsaw",
        }),
      )
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [serverTimeIso])

  return display
}

function OfflineBoard() {
  return (
    <Card className="w-full max-w-md border-destructive/40 shadow-none">
      <CardHeader className="items-center text-center pb-2">
        <WifiOff className="size-8 text-destructive mb-1" />
        <CardTitle className="text-base text-destructive">Brak połączenia sieciowego</CardTitle>
        <CardDescription>
          Weryfikacja dynamiczna wymaga połączenia z serwerem. Okazanie karty offline jest nieważne.
        </CardDescription>
      </CardHeader>
    </Card>
  )
}

function BlockedBoard({ code }: { code: CardAccountError["code"] }) {
  const title =
    code === "ACCOUNT_BLOCKED"
      ? "Konto zablokowane"
      : code === "ACCOUNT_CHECKED_OUT"
        ? "Konto wygasłe / wymeldowane"
        : "Karta nieważna"
  const subtitle =
    code === "ACCOUNT_BLOCKED"
      ? "Konto zablokowane administracyjnie — karta nie uprawnia do wstępu do budynku."
      : code === "ACCOUNT_CHECKED_OUT"
        ? "Konto formalnie wymeldowane — karta utraciła ważność."
        : "Konto mieszkańca nie jest aktywne w systemie."

  return (
    <Card className="w-full max-w-md border-destructive shadow-none bg-destructive/5">
      <CardHeader className="items-center text-center pb-2">
        <ShieldAlert className="size-8 text-destructive mb-1" />
        <CardTitle className="text-base text-destructive">{title}</CardTitle>
        <CardDescription>{subtitle}</CardDescription>
      </CardHeader>
    </Card>
  )
}

export interface DormitoryVisualTheme {
  code: string
  fullName: string
  headerBg: string
  topBarAccent: string
  badgeBg: string
  badgeBorder: string
  badgeText: string
  hologramColor: string
  cardBorder: string
  accentColor: string
  subAccent: string
  tagline: string
}

export function getDormitoryTheme(
  dormitoryCode?: string | null,
  dormitoryName?: string | null,
): DormitoryVisualTheme {
  const code = (dormitoryCode ?? "").toUpperCase().trim()
  const name = (dormitoryName ?? "").toUpperCase().trim()

  // DS-1: Klasyczny błękit szafirowy Politechniki Krakowskiej
  if (
    code.includes("DS-1") ||
    code.includes("DS1") ||
    name.includes("RUMCAJS") ||
    name.includes("OLIMP") ||
    name.includes("DS-1")
  ) {
    return {
      code: "DS-1",
      fullName: dormitoryName || "DS-1 Rumcajs",
      headerBg: "from-[#022851] via-[#004785] to-[#0284c7]",
      topBarAccent: "#004785",
      badgeBg: "bg-sky-500/15 border-sky-400/30 text-sky-200",
      badgeBorder: "border-sky-400/30",
      badgeText: "text-sky-300",
      hologramColor: "#38bdf8",
      cardBorder: "border-[#004785]/40 hover:border-[#004785]/70",
      accentColor: "#0284c7",
      subAccent: "text-sky-700 dark:text-sky-300",
      tagline: "Osiedle Studenckie Politechniki Krakowskiej",
    }
  }

  // DS-2: Szlachetny szmaragd / leśna zieleń (Leon)
  if (
    code.includes("DS-2") ||
    code.includes("DS2") ||
    name.includes("LEON") ||
    name.includes("DS-2")
  ) {
    return {
      code: "DS-2",
      fullName: dormitoryName || "DS-2 Leon",
      headerBg: "from-[#022c22] via-[#065f46] to-[#059669]",
      topBarAccent: "#065f46",
      badgeBg: "bg-emerald-500/15 border-emerald-400/30 text-emerald-200",
      badgeBorder: "border-emerald-400/30",
      badgeText: "text-emerald-300",
      hologramColor: "#34d399",
      cardBorder: "border-[#065f46]/40 hover:border-[#065f46]/70",
      accentColor: "#059669",
      subAccent: "text-emerald-700 dark:text-emerald-300",
      tagline: "Osiedle Studenckie Politechniki Krakowskiej",
    }
  }

  // DS-3: Głęboki bordowy karmin / rubinowy (Akropol)
  if (
    code.includes("DS-3") ||
    code.includes("DS3") ||
    name.includes("AKROPOL") ||
    name.includes("DS-3")
  ) {
    return {
      code: "DS-3",
      fullName: dormitoryName || "DS-3 Akropol",
      headerBg: "from-[#4c0519] via-[#881337] to-[#e11d48]",
      topBarAccent: "#881337",
      badgeBg: "bg-rose-500/15 border-rose-400/30 text-rose-200",
      badgeBorder: "border-rose-400/30",
      badgeText: "text-rose-300",
      hologramColor: "#fb7185",
      cardBorder: "border-[#881337]/40 hover:border-[#881337]/70",
      accentColor: "#e11d48",
      subAccent: "text-rose-700 dark:text-rose-300",
      tagline: "Osiedle Studenckie Politechniki Krakowskiej",
    }
  }

  // DS-4: Królewski indygo / ametystowy fiolet (Bydgoska)
  if (
    code.includes("DS-4") ||
    code.includes("DS4") ||
    name.includes("BYDGOSKA") ||
    name.includes("DS-4")
  ) {
    return {
      code: "DS-4",
      fullName: dormitoryName || "DS-4 Bydgoska",
      headerBg: "from-[#1e1b4b] via-[#3730a3] to-[#6366f1]",
      topBarAccent: "#3730a3",
      badgeBg: "bg-indigo-500/15 border-indigo-400/30 text-indigo-200",
      badgeBorder: "border-indigo-400/30",
      badgeText: "text-indigo-300",
      hologramColor: "#818cf8",
      cardBorder: "border-[#3730a3]/40 hover:border-[#3730a3]/70",
      accentColor: "#6366f1",
      subAccent: "text-indigo-700 dark:text-indigo-300",
      tagline: "Osiedle Studenckie Politechniki Krakowskiej",
    }
  }

  // Domyślny uniwersalny motyw PK
  return {
    code: code || "DS",
    fullName: dormitoryName || "Dom Studencki PK",
    headerBg: "from-[#0f172a] via-[#1e293b] to-[#334155]",
    topBarAccent: "#1e293b",
    badgeBg: "bg-slate-500/15 border-slate-400/30 text-slate-200",
    badgeBorder: "border-slate-400/30",
    badgeText: "text-slate-300",
    hologramColor: "#94a3b8",
    cardBorder: "border-slate-400/30 hover:border-slate-400/60",
    accentColor: "#0284c7",
    subAccent: "text-slate-700 dark:text-slate-300",
    tagline: "Politechnika Krakowska im. Tadeusza Kościuszki",
  }
}

/**
 * Wektorowy wzór giloszowy (antifałszerski) stosowany w dokumentach tożsamości.
 */
function GuillochePattern() {
  return (
    <svg
      className="absolute inset-0 size-full pointer-events-none opacity-[0.045] dark:opacity-[0.06] select-none"
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 600 380"
      preserveAspectRatio="none"
    >
      <defs>
        <pattern id="guilloche" width="60" height="60" patternUnits="userSpaceOnUse">
          <circle cx="30" cy="30" r="28" fill="none" stroke="currentColor" strokeWidth="0.75" />
          <circle cx="30" cy="30" r="20" fill="none" stroke="currentColor" strokeWidth="0.5" />
          <circle cx="30" cy="30" r="12" fill="none" stroke="currentColor" strokeWidth="0.5" />
          <path d="M 0 30 Q 15 15 30 30 T 60 30" fill="none" stroke="currentColor" strokeWidth="0.5" />
          <path d="M 30 0 Q 15 15 30 30 T 30 60" fill="none" stroke="currentColor" strokeWidth="0.5" />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#guilloche)" />
    </svg>
  )
}

function ActiveCard({
  card,
  clock,
  ripples,
  lastTouchTime,
  onPointer,
  onTouch,
}: {
  card: ResidentCard
  clock: string
  ripples: { id: number; x: number; y: number }[]
  lastTouchTime: string | null
  onPointer: (e: React.MouseEvent<HTMLDivElement>) => void
  onTouch: (e: React.TouchEvent<HTMLDivElement>) => void
}) {
  const initials =
    `${card.firstName?.[0] ?? ""}${card.lastName?.[0] ?? ""}`.toUpperCase() || "M"

  const theme = getDormitoryTheme(card.dormitoryCode, card.dormitoryName)

  return (
    <div
      onClick={onPointer}
      onTouchStart={onTouch}
      className={cn(
        "relative w-full max-w-lg select-none cursor-pointer overflow-hidden rounded-2xl",
        "bg-card text-card-foreground shadow-2xl transition-all duration-300",
        "border ring-1 ring-black/5 dark:ring-white/10",
        theme.cardBorder,
      )}
    >
      {/* 1. Prestiżowy pasek nagłówkowy charakterystyczny dla danego akademika */}
      <div
        className={cn(
          "relative px-4 py-3 sm:px-5 sm:py-3.5 bg-gradient-to-r text-white flex items-center justify-between gap-3 shadow-md",
          theme.headerBg,
        )}
      >
        <div className="flex items-center gap-2.5 min-w-0">
          <PkLogo
            variant="icon"
            transparent
            className="size-8 sm:size-9 shrink-0 drop-shadow-sm brightness-110"
          />
          <div className="min-w-0">
            <p className="text-[10px] sm:text-[11px] font-bold tracking-[0.16em] uppercase text-white/95 leading-tight">
              Politechnika Krakowska
            </p>
            <p className="text-[9px] sm:text-[10px] font-medium tracking-wide text-white/80 uppercase">
              Elektroniczna Karta Mieszkańca
            </p>
          </div>
        </div>

        {/* Etykieta / Identyfikator konkretnego akademika */}
        <div className="flex flex-col items-end shrink-0">
          <span
            className={cn(
              "px-2.5 py-0.5 rounded-full text-[11px] sm:text-xs font-bold tracking-wider uppercase border backdrop-blur-sm shadow-sm",
              theme.badgeBg,
            )}
          >
            {theme.code}
          </span>
          <span className="text-[8.5px] text-white/70 font-mono tracking-tight mt-0.5">
            {theme.fullName}
          </span>
        </div>
      </div>

      {/* 2. Ciało dokumentu z tłem giloszowym i hologramem */}
      <div className="relative p-5 sm:p-6 bg-gradient-to-b from-card via-card to-muted/20">
        <GuillochePattern />

        {/* Dynamiczne fale touch ripple */}
        {ripples.map((ripple) => (
          <span
            key={ripple.id}
            className="absolute size-24 rounded-full pointer-events-none animate-ping"
            style={{
              left: ripple.x - 48,
              top: ripple.y - 48,
              backgroundColor: `${theme.accentColor}33`,
            }}
          />
        ))}

        <div className="flex flex-row items-stretch gap-4 sm:gap-6">
          {/* Ramka ze zdjęciem mieszkańca w formacie paszportowym (3:4) */}
          <div className="relative shrink-0 flex flex-col items-center">
            <div className="relative size-28 sm:size-32 rounded-xl overflow-hidden border-2 border-border/80 shadow-md bg-muted/30">
              {card.avatarUrl ? (
                <img
                  src={card.avatarUrl}
                  alt={`${card.firstName} ${card.lastName}`}
                  className="size-full object-cover object-top"
                />
              ) : (
                <div
                  className="size-full flex flex-col items-center justify-center font-bold text-2xl"
                  style={{
                    backgroundColor: `${theme.accentColor}15`,
                    color: theme.accentColor,
                  }}
                >
                  <PkLogo variant="icon" transparent className="size-8 opacity-30 mb-1" />
                  {initials}
                </div>
              )}

              {/* Holograficzny mikro-znak w rogu zdjęcia */}
              <div
                className="absolute bottom-1 right-1 px-1 py-0.5 rounded text-[8px] font-mono font-bold tracking-tighter shadow-sm backdrop-blur-md border border-white/40 text-white"
                style={{ backgroundColor: `${theme.accentColor}bb` }}
              >
                PK
              </div>
            </div>
          </div>

          {/* Oficjalne dane mieszkańca */}
          <div className="flex flex-col min-w-0 flex-1 justify-between py-0.5">
            <div>
              <p className="text-[10px] font-semibold tracking-wider uppercase text-muted-foreground">
                Nazwisko i imię
              </p>
              <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground leading-tight truncate">
                {card.lastName} {card.firstName}
              </h2>
            </div>

            <div>
              <p className="text-[10px] font-semibold tracking-wider uppercase text-muted-foreground">
                Dom Studencki
              </p>
              <p className="text-sm sm:text-base font-semibold text-foreground/90 truncate">
                {theme.fullName}
              </p>
            </div>

            <div className="flex items-baseline justify-between gap-2 pt-1 border-t border-border/40">
              <div>
                <p className="text-[10px] font-semibold tracking-wider uppercase text-muted-foreground">
                  Pokój
                </p>
                <p className="text-lg sm:text-xl font-bold tabular-nums tracking-tight text-foreground">
                  {card.roomNumber ?? "—"}
                </p>
              </div>

              <div className="text-right">
                <p className="text-[10px] font-semibold tracking-wider uppercase text-muted-foreground">
                  Rok akademicki
                </p>
                <p className="text-xs sm:text-sm font-medium tabular-nums text-foreground/90">
                  {card.academicYear}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 3. Panel weryfikacji portierskiej (Zabezpieczenie dobowe i zegar on-line) */}
        <div className="mt-5 rounded-xl border border-border/80 bg-muted/40 p-3 flex flex-col gap-2.5 shadow-inner">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span
                className="size-4 rounded-full ring-2 ring-white/50 shadow-sm shrink-0"
                style={{ backgroundColor: card.dayColorHex }}
              />
              <span className="text-xs font-medium text-foreground">
                Kolor dnia: <strong className="font-semibold">{card.dayColorName}</strong>
              </span>
            </div>

            <div className="flex items-center gap-1.5">
              <span className="text-[11px] text-muted-foreground">Kod:</span>
              <span className="font-mono text-sm font-bold tracking-widest px-2 py-0.5 rounded bg-background border shadow-xs">
                {card.dayCode}
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between text-xs pt-1 border-t border-border/40">
            <div className="flex items-center gap-1 text-muted-foreground">
              <span className="size-1.5 rounded-full bg-emerald-500 animate-pulse" />
              <span>Czas serwera:</span>
              <span className="font-mono font-semibold text-foreground tabular-nums ml-1">
                {clock || "—:—:—"}
              </span>
            </div>

            {lastTouchTime ? (
              <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                <Sparkles className="size-3" />
                Dotyk: {lastTouchTime}
              </span>
            ) : (
              <span className="text-[10px] text-muted-foreground/80 italic">
                Dotknij, aby zweryfikować
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 4. Dolny pasek stanu i certyfikatu tożsamości */}
      <div className="px-5 py-2.5 bg-muted/40 border-t border-border/70 flex items-center justify-between text-xs">
        <div className="flex items-center gap-1.5 font-medium text-emerald-700 dark:text-emerald-400">
          <ShieldCheck className="size-4 shrink-0" />
          <span className="font-semibold tracking-wide text-[11px] uppercase">
            Aktywna · Ważny mieszkaniec DS
          </span>
        </div>

        <span className="text-[10px] font-mono text-muted-foreground/70 uppercase">
          POLITECHNIKA KRAKOWSKA
        </span>
      </div>
    </div>
  )
}

export function CardPage() {
  const online = useOnline()
  const [fullscreen, setFullscreen] = React.useState(false)
  const [ripples, setRipples] = React.useState<{ id: number; x: number; y: number }[]>([])
  const [lastTouchTime, setLastTouchTime] = React.useState<string | null>(null)

  const cardQuery = useQuery({
    queryKey: ["profile", "card"],
    queryFn: getResidentCard,
    enabled: online,
    refetchInterval: online ? CARD_REFETCH_MS : false,
    retry: (count, error) => {
      if (error instanceof CardAccountError) return false
      return count < 2
    },
  })

  const clock = useServerClock(cardQuery.data?.serverTime)

  const triggerRippleAt = (
    clientX: number,
    clientY: number,
    currentTarget: HTMLElement,
  ) => {
    const rect = currentTarget.getBoundingClientRect()
    const x = clientX - rect.left
    const y = clientY - rect.top
    const newRipple = { id: Date.now(), x, y }
    setRipples((prev) => [...prev.slice(-4), newRipple])

    const now = new Date()
    setLastTouchTime(
      now.toLocaleTimeString("pl-PL", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        fractionalSecondDigits: 3,
      }),
    )

    setTimeout(() => {
      setRipples((prev) => prev.filter((r) => r.id !== newRipple.id))
    }, 1000)
  }

  const accountError =
    cardQuery.error instanceof CardAccountError ? cardQuery.error : null
  const networkBlocked =
    !online || (cardQuery.isError && !accountError && !cardQuery.data)

  return (
    <div
      className={cn(
        "flex flex-col items-center transition-all",
        fullscreen
          ? "fixed inset-0 z-50 bg-background p-4 md:p-6 overflow-y-auto justify-center"
          : "w-full max-w-xl mx-auto space-y-4 py-2",
      )}
    >
      <div className="w-full max-w-lg flex items-center justify-between gap-2">
        {!fullscreen ? (
          <Button variant="ghost" size="sm" asChild className="-ml-2 text-muted-foreground">
            <Link to="/dashboard">
              <ArrowLeft className="size-3.5 mr-1" />
              Pulpit
            </Link>
          </Button>
        ) : (
          <span className="text-xs font-medium text-muted-foreground">
            Tryb okazania portierowi
          </span>
        )}

        <Button
          variant="outline"
          size="sm"
          onClick={() => setFullscreen((v) => !v)}
          className="ml-auto"
          disabled={!!accountError || networkBlocked}
        >
          {fullscreen ? (
            <>
              <Minimize2 className="size-3.5 mr-1.5" />
              Zamknij
            </>
          ) : (
            <>
              <Maximize2 className="size-3.5 mr-1.5" />
              Pełny ekran
            </>
          )}
        </Button>
      </div>

      {!online ? (
        <OfflineBoard />
      ) : accountError ? (
        <BlockedBoard code={accountError.code} />
      ) : cardQuery.isLoading && !cardQuery.data ? (
        <p className="text-sm text-muted-foreground">Ładowanie karty…</p>
      ) : cardQuery.isError && !cardQuery.data ? (
        <OfflineBoard />
      ) : cardQuery.data ? (
        <>
          <ActiveCard
            card={cardQuery.data}
            clock={clock}
            ripples={ripples}
            lastTouchTime={lastTouchTime}
            onPointer={(e) => triggerRippleAt(e.clientX, e.clientY, e.currentTarget)}
            onTouch={(e) => {
              const touch = e.touches[0]
              if (touch) triggerRippleAt(touch.clientX, touch.clientY, e.currentTarget)
            }}
          />
          {!fullscreen && (
            <p className="text-xs text-muted-foreground text-center max-w-sm">
              Okazuj kartę przy wejściu do akademika i odbiorze kluczy na portierni. Kod oraz kolor dnia muszą zgadzać się z ekranem dyżurnym.
            </p>
          )}
        </>
      ) : null}

      {cardQuery.isError && cardQuery.data && !accountError && (
        <p className="text-xs text-destructive">
          {getApiErrorMessage(cardQuery.error, "Nie udało się odświeżyć karty")}
        </p>
      )}
    </div>
  )
}
