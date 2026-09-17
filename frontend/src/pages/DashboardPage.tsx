import * as React from "react"
import { Link, useOutletContext } from "react-router-dom"
import {
  ArrowRight,
  CheckCircle2,
  Clock,
  DoorClosed,
  Megaphone,
  Waves,
  Wrench,
} from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

interface ActiveLaundry {
  id: string
  machineName: string
  slotTime: string
  date: string
  status: "CONFIRMED" | "KEY_ISSUED"
  pickupDeadline: string
}

interface ActiveRoom {
  id: string
  roomName: string
  timeRange: string
  date: string
  participants: number
  status: "CONFIRMED" | "KEY_ISSUED"
}

interface ActiveIssue {
  id: string
  title: string
  category: string
  location: string
  status: "NEW" | "ASSIGNED" | "IN_PROGRESS"
  statusLabel: string
  lastNote?: string
}

interface ActiveAnnouncement {
  id: string
  title: string
  author: string
  date: string
  content: string
  isUrgent?: boolean
}

export function DashboardPage() {
  const user = useOutletContext<UserProfile>()

  // In production, these will be populated from TanStack queries (useQuery).
  // Default mock state illustrates active items; user can also test the empty state.
  const [activeLaundry, setActiveLaundry] = React.useState<ActiveLaundry | null>({
    id: "laundry-1",
    machineName: "Pralka #2 (Samsung EcoBubble)",
    slotTime: "18:00 – 19:30",
    date: "Dzisiaj",
    status: "CONFIRMED",
    pickupDeadline: "18:15",
  })

  const [activeRoom, setActiveRoom] = React.useState<ActiveRoom | null>({
    id: "room-1",
    roomName: "Salka Cichej Nauki „Kujon”",
    timeRange: "14:00 – 17:00",
    date: "Jutro",
    participants: 4,
    status: "CONFIRMED",
  })

  const [activeIssue, setActiveIssue] = React.useState<ActiveIssue | null>({
    id: "issue-1",
    title: "Nieszczelna uszczelka baterii umywalkowej",
    category: "Hydraulika",
    location: user.roomNumber ? `Pokój ${user.roomNumber}` : "Mój pokój",
    status: "IN_PROGRESS",
    statusLabel: "W trakcie realizacji (Konserwator)",
    lastNote: "Części zamienne pobrane z magazynu. Wymiana jutro do 12:00.",
  })

  const [activeAnnouncement, setActiveAnnouncement] = React.useState<ActiveAnnouncement | null>({
    id: "ann-1",
    title: "Przegląd okresowy czujników dymu i wentylacji",
    author: "Administracja DS",
    date: "Dzisiaj, 09:30",
    content: "W najbliższy czwartek w godz. 10:00–14:00 odbędzie się obowiązkowy przegląd instalacji ppoż.",
    isUrgent: false,
  })

  const hasAnyActive = Boolean(
    activeLaundry || activeRoom || activeIssue || activeAnnouncement
  )

  const clearAllForTesting = () => {
    setActiveLaundry(null)
    setActiveRoom(null)
    setActiveIssue(null)
    setActiveAnnouncement(null)
  }

  const restoreAllForTesting = () => {
    setActiveLaundry({
      id: "laundry-1",
      machineName: "Pralka #2 (Samsung EcoBubble)",
      slotTime: "18:00 – 19:30",
      date: "Dzisiaj",
      status: "CONFIRMED",
      pickupDeadline: "18:15",
    })
    setActiveRoom({
      id: "room-1",
      roomName: "Salka Cichej Nauki „Kujon”",
      timeRange: "14:00 – 17:00",
      date: "Jutro",
      participants: 4,
      status: "CONFIRMED",
    })
    setActiveIssue({
      id: "issue-1",
      title: "Nieszczelna uszczelka baterii umywalkowej",
      category: "Hydraulika",
      location: user.roomNumber ? `Pokój ${user.roomNumber}` : "Mój pokój",
      status: "IN_PROGRESS",
      statusLabel: "W trakcie realizacji (Konserwator)",
      lastNote: "Części zamienne pobrane z magazynu. Wymiana jutro do 12:00.",
    })
    setActiveAnnouncement({
      id: "ann-1",
      title: "Przegląd okresowy czujników dymu i wentylacji",
      author: "Administracja DS",
      date: "Dzisiaj, 09:30",
      content: "W najbliższy czwartek w godz. 10:00–14:00 odbędzie się obowiązkowy przegląd instalacji ppoż.",
      isUrgent: false,
    })
  }

  return (
    <div className="w-full flex flex-col items-center py-2 md:py-6 max-w-5xl mx-auto space-y-6">
      {/* Dev / Preview toggle switch */}
      <div className="w-full flex items-center justify-end text-xs text-muted-foreground">
        <button
          type="button"
          onClick={hasAnyActive ? clearAllForTesting : restoreAllForTesting}
          className="underline hover:text-foreground cursor-pointer transition-colors"
        >
          {hasAnyActive ? "Podgląd: symuluj brak aktywnych spraw" : "Podgląd: przywróć aktywne sprawy"}
        </button>
      </div>

      {/* STATE 1: HAS ACTIVE ITEMS -> ONLY ACTIVE CARDS SHOWN */}
      {hasAnyActive && (
        <div className="w-full grid grid-cols-1 md:grid-cols-2 gap-5 animate-in fade-in-50 duration-300">
          {/* Active Laundry Reservation */}
          {activeLaundry && (
            <Link
              to="/laundry"
              className="block group select-none focus:outline-hidden"
            >
              <Card className="h-full border-sky-500/30 hover:border-sky-500/60 shadow-xs flex flex-col justify-between transition-all duration-200 group-hover:shadow-md group-hover:-translate-y-0.5 cursor-pointer bg-gradient-to-br from-card via-card to-sky-500/5">
                <CardHeader className="pb-2">
                  <CardTitle className="text-base font-semibold flex items-center gap-2.5 group-hover:text-primary transition-colors">
                    <div className="size-8 rounded-lg bg-sky-500/10 text-sky-600 dark:text-sky-400 flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform">
                      <Waves className="size-4.5" />
                    </div>
                    <span className="truncate">Aktywne Pranie: {activeLaundry.date}</span>
                  </CardTitle>
                  <CardDescription className="truncate">
                    {activeLaundry.machineName}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-3 flex-1">
                  <div className="rounded-xl p-3 bg-sky-500/10 border border-sky-500/20 space-y-1.5">
                    <div className="flex items-center justify-between text-xs font-semibold text-sky-950 dark:text-sky-200">
                      <span className="flex items-center gap-1.5">
                        <Clock className="size-3.5 text-sky-500" />
                        {activeLaundry.slotTime}
                      </span>
                      <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded-full bg-sky-500/20 text-sky-700 dark:text-sky-300">
                        Oczekuje na klucz
                      </span>
                    </div>
                    <p className="text-[11px] text-sky-900/80 dark:text-sky-300/80">
                      <strong>Reguła 15 minut:</strong> Odbierz klucz na portierni najpóźniej do godz. <strong>{activeLaundry.pickupDeadline}</strong>.
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="pt-2">
                  <div className="w-full flex items-center justify-between text-xs font-medium text-muted-foreground group-hover:text-primary transition-colors">
                    <span>Zarządzaj rezerwacją pralni</span>
                    <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-1" />
                  </div>
                </CardFooter>
              </Card>
            </Link>
          )}

          {/* Active Thematic Room Reservation */}
          {activeRoom && (
            <Link
              to="/rooms"
              className="block group select-none focus:outline-hidden"
            >
              <Card className="h-full border-purple-500/30 hover:border-purple-500/60 shadow-xs flex flex-col justify-between transition-all duration-200 group-hover:shadow-md group-hover:-translate-y-0.5 cursor-pointer bg-gradient-to-br from-card via-card to-purple-500/5">
                <CardHeader className="pb-2">
                  <CardTitle className="text-base font-semibold flex items-center gap-2.5 group-hover:text-primary transition-colors">
                    <div className="size-8 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform">
                      <DoorClosed className="size-4.5" />
                    </div>
                    <span className="truncate">Rezerwacja Salki: {activeRoom.date}</span>
                  </CardTitle>
                  <CardDescription className="truncate">
                    {activeRoom.roomName}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-3 flex-1">
                  <div className="rounded-xl p-3 bg-purple-500/10 border border-purple-500/20 space-y-1.5">
                    <div className="flex items-center justify-between text-xs font-semibold text-purple-950 dark:text-purple-200">
                      <span className="flex items-center gap-1.5">
                        <Clock className="size-3.5 text-purple-500" />
                        {activeRoom.timeRange}
                      </span>
                      <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded-full bg-purple-500/20 text-purple-700 dark:text-purple-300">
                        Potwierdzona
                      </span>
                    </div>
                    <p className="text-[11px] text-purple-900/80 dark:text-purple-300/80">
                      Rola: <strong>Organizator</strong> ({activeRoom.participants} osoby). Pamiętaj o odbiorze klucza w 15 min.
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="pt-2">
                  <div className="w-full flex items-center justify-between text-xs font-medium text-muted-foreground group-hover:text-primary transition-colors">
                    <span>Szczegóły rezerwacji salki</span>
                    <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-1" />
                  </div>
                </CardFooter>
              </Card>
            </Link>
          )}

          {/* Active Issue Report */}
          {activeIssue && (
            <Link
              to="/issues"
              className="block group select-none focus:outline-hidden"
            >
              <Card className="h-full border-amber-500/30 hover:border-amber-500/60 shadow-xs flex flex-col justify-between transition-all duration-200 group-hover:shadow-md group-hover:-translate-y-0.5 cursor-pointer bg-gradient-to-br from-card via-card to-amber-500/5">
                <CardHeader className="pb-2">
                  <CardTitle className="text-base font-semibold flex items-center gap-2.5 group-hover:text-primary transition-colors">
                    <div className="size-8 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform">
                      <Wrench className="size-4.5" />
                    </div>
                    <span className="truncate">Zgłoszona Awaria: {activeIssue.category}</span>
                  </CardTitle>
                  <CardDescription className="truncate">
                    {activeIssue.location} • {activeIssue.title}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-3 flex-1">
                  <div className="rounded-xl p-3 bg-amber-500/10 border border-amber-500/20 space-y-1.5">
                    <div className="flex items-center justify-between text-xs font-semibold text-amber-950 dark:text-amber-200">
                      <span className="flex items-center gap-1">
                        <Wrench className="size-3 text-amber-600" />
                        {activeIssue.statusLabel}
                      </span>
                    </div>
                    {activeIssue.lastNote && (
                      <p className="text-[11px] text-amber-900/90 dark:text-amber-200/90">
                        <strong>Notatka konserwatora:</strong> „{activeIssue.lastNote}”
                      </p>
                    )}
                  </div>
                </CardContent>

                <CardFooter className="pt-2">
                  <div className="w-full flex items-center justify-between text-xs font-medium text-muted-foreground group-hover:text-primary transition-colors">
                    <span>Śledź stan zgłoszenia</span>
                    <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-1" />
                  </div>
                </CardFooter>
              </Card>
            </Link>
          )}

          {/* Active Administration Announcement */}
          {activeAnnouncement && (
            <Link
              to="/board"
              className="block group select-none focus:outline-hidden"
            >
              <Card className="h-full border-rose-500/30 hover:border-rose-500/60 shadow-xs flex flex-col justify-between transition-all duration-200 group-hover:shadow-md group-hover:-translate-y-0.5 cursor-pointer bg-gradient-to-br from-card via-card to-rose-500/5">
                <CardHeader className="pb-2">
                  <CardTitle className="text-base font-semibold flex items-center gap-2.5 group-hover:text-primary transition-colors">
                    <div className="size-8 rounded-lg bg-rose-500/10 text-rose-600 dark:text-rose-400 flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform">
                      <Megaphone className="size-4.5" />
                    </div>
                    <span className="truncate">Komunikat Administracji DS</span>
                  </CardTitle>
                  <CardDescription className="truncate">
                    {activeAnnouncement.author} • {activeAnnouncement.date}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-3 flex-1">
                  <div className="rounded-xl p-3 bg-rose-500/10 border border-rose-500/20 space-y-1">
                    <p className="text-xs font-semibold text-rose-950 dark:text-rose-200">
                      {activeAnnouncement.title}
                    </p>
                    <p className="text-[11px] text-rose-900/80 dark:text-rose-300/80 line-clamp-2">
                      {activeAnnouncement.content}
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="pt-2">
                  <div className="w-full flex items-center justify-between text-xs font-medium text-muted-foreground group-hover:text-primary transition-colors">
                    <span>Zobacz całe ogłoszenie na tablicy</span>
                    <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-1" />
                  </div>
                </CardFooter>
              </Card>
            </Link>
          )}
        </div>
      )}

      {/* STATE 2: EMPTY STATE -> ALL CLEAR, QUICK ACTIONS */}
      {!hasAnyActive && (
        <div className="flex flex-col items-center justify-center text-center p-8 md:p-12 rounded-2xl border border-dashed border-border bg-card/50 max-w-lg mx-auto animate-in fade-in-50 duration-300">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 mb-4">
            <CheckCircle2 className="size-7" />
          </div>

          <h3 className="text-lg font-bold text-foreground">
            Brak aktywnych spraw
          </h3>
          <p className="text-xs md:text-sm text-muted-foreground mt-1 max-w-sm">
            Nie masz w tej chwili zaplanowanych prań, rezerwacji salek ani otwartych zgłoszeń usterek. Wszystko działa jak należy!
          </p>

          <div className="flex flex-wrap items-center justify-center gap-2.5 mt-6 w-full">
            <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
              <Link to="/laundry">
                <Waves className="size-3.5 text-sky-500" />
                <span>Zarezerwuj pralkę</span>
              </Link>
            </Button>

            <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
              <Link to="/rooms">
                <DoorClosed className="size-3.5 text-purple-500" />
                <span>Rezerwuj salkę</span>
              </Link>
            </Button>

            <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
              <Link to="/issues">
                <Wrench className="size-3.5 text-amber-500" />
                <span>Zgłoś usterkę</span>
              </Link>
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
