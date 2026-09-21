import * as React from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import {
  ArrowLeft,
  Clock,
  Maximize2,
  Minimize2,
  ShieldAlert,
  ShieldCheck,
  WifiOff,
} from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import { CardAccountError, getResidentCard } from "@/api/profile"
import type { ResidentCard } from "@/api/types"
import { PkLogo } from "@/components/brand/PkLogo"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
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
        <CardTitle className="text-base text-destructive">Brak sieci</CardTitle>
        <CardDescription>
          Weryfikacja dynamiczna wymaga połączenia z serwerem. Okazanie karty offline
          jest nieważne.
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
      ? "Konto zablokowane administracyjnie — karta nie uprawnia do wejścia."
      : code === "ACCOUNT_CHECKED_OUT"
        ? "Konto wymeldowane — karta nie uprawnia do wejścia."
        : "Konto nie jest aktywne."

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

function ActiveCard({
  card,
  clock,
  ripples,
  onPointer,
  onTouch,
}: {
  card: ResidentCard
  clock: string
  ripples: { id: number; x: number; y: number }[]
  onPointer: (e: React.MouseEvent<HTMLDivElement>) => void
  onTouch: (e: React.TouchEvent<HTMLDivElement>) => void
}) {
  const initials =
    `${card.firstName?.[0] ?? ""}${card.lastName?.[0] ?? ""}`.toUpperCase() || "M"

  return (
    <Card
      onClick={onPointer}
      onTouchStart={onTouch}
      className={cn(
        "relative w-full max-w-xl overflow-hidden shadow-none select-none cursor-pointer rounded-lg",
        "border-border/70 hover:border-primary/30 transition-colors",
        "gap-0 py-0",
      )}
    >
      <div
        className="h-1.5 w-full shrink-0"
        style={{ backgroundColor: card.dayColorHex }}
        title={`Kolor dnia: ${card.dayColorName}`}
      />

      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.06] bg-gradient-to-br from-primary via-transparent to-muted animate-pulse"
      />

      {ripples.map((ripple) => (
        <span
          key={ripple.id}
          style={{ left: ripple.x - 32, top: ripple.y - 32 }}
          className="absolute size-16 rounded-full bg-primary/25 pointer-events-none animate-ping"
        />
      ))}

      <CardHeader className="relative border-b border-border/60 py-3 px-5">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <PkLogo variant="icon" className="size-8 shrink-0 rounded-md" />
            <div className="min-w-0">
              <CardTitle className="text-xs font-semibold tracking-tight">
                PKampus · karta mieszkańca
              </CardTitle>
            </div>
          </div>
          <Badge variant="outline" className="shrink-0 gap-1.5 font-mono tabular-nums text-xs">
            <Clock className="size-3 text-muted-foreground" />
            {clock || "—:—:—"}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="relative px-5 py-5 sm:py-6">
        <div className="flex flex-row items-stretch gap-5 sm:gap-6">
          <Avatar className="size-28 sm:size-32 rounded-md ring-1 ring-border shrink-0 self-center">
            {card.avatarUrl ? (
              <AvatarImage src={card.avatarUrl} alt={card.firstName} className="object-cover" />
            ) : null}
            <AvatarFallback className="rounded-md text-2xl font-semibold bg-primary/10 text-primary">
              {initials}
            </AvatarFallback>
          </Avatar>

          <div className="flex flex-col min-w-0 flex-1 justify-center gap-2">
            <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Mieszkaniec
            </p>
            <p className="text-2xl sm:text-3xl font-semibold tracking-tight leading-tight break-words">
              {card.firstName} {card.lastName}
            </p>
            <p className="text-base sm:text-lg text-foreground/90 font-medium leading-snug">
              {card.dormitoryName ?? "Dom Studencki PK"}
            </p>
            <p className="text-lg sm:text-xl font-semibold tabular-nums tracking-tight pt-0.5">
              Pokój {card.roomNumber ?? "—"}
              <span className="text-sm font-normal text-muted-foreground ml-2">
                {card.academicYear}
              </span>
            </p>
          </div>
        </div>

        <div className="mt-5 flex items-center gap-3 rounded-md border border-border/70 bg-muted/40 px-3 py-2.5">
          <span
            className="size-5 rounded-sm ring-1 ring-border shrink-0"
            style={{ backgroundColor: card.dayColorHex }}
            title={card.dayColorName}
          />
          <div className="min-w-0 flex-1 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
            <p className="text-xs text-muted-foreground">
              Kod dnia · {card.dayColorName}
            </p>
            <p className="font-mono text-base font-semibold tracking-wider tabular-nums">
              {card.dayCode}
            </p>
          </div>
        </div>
      </CardContent>

      <CardFooter className="relative border-t border-border/60 bg-muted/30 px-5 py-2.5 justify-between">
        <div className="flex items-center gap-1.5 text-sm font-medium">
          <ShieldCheck className="size-4 text-primary" />
          <span>Aktywna / Mieszkaniec</span>
        </div>
        <Badge variant="secondary" className="text-[10px] font-mono">
          ACTIVE
        </Badge>
      </CardFooter>
    </Card>
  )
}

export function CardPage() {
  const online = useOnline()
  const [fullscreen, setFullscreen] = React.useState(false)
  const [ripples, setRipples] = React.useState<{ id: number; x: number; y: number }[]>([])

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
      <div className="w-full max-w-xl flex items-center justify-between gap-2">
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
            onPointer={(e) => triggerRippleAt(e.clientX, e.clientY, e.currentTarget)}
            onTouch={(e) => {
              const touch = e.touches[0]
              if (touch) triggerRippleAt(touch.clientX, touch.clientY, e.currentTarget)
            }}
          />
          {!fullscreen && (
            <p className="text-xs text-muted-foreground text-center max-w-sm">
              Okazuj kartę przy wejściu i odbiorze kluczy. Kod oraz kolor dnia muszą
              zgadzać się z pulpitem portiera.
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
