import * as React from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import {
  CheckCircle2,
  ChevronRight,
  DoorClosed,
  Waves,
  Wrench,
} from "lucide-react"

import { getActiveBanner } from "@/api/events"
import type { UserProfile } from "@/api/types"
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

import {
  type ActiveAnnouncement,
  type ActiveIssue,
  type ActiveLaundry,
  type ActiveRoom,
  type AgendaReservation,
  groupDashboardAgenda,
  hasAgendaItems,
  leadingTime,
} from "./dashboard-agenda"

function QuickActions() {
  return (
    <div className="flex flex-wrap items-center justify-center gap-2.5 w-full">
      <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
        <Link to="/laundry">
          <Waves className="size-3.5 text-muted-foreground" />
          <span>Zarezerwuj pralkę</span>
        </Link>
      </Button>
      <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
        <Link to="/rooms">
          <DoorClosed className="size-3.5 text-muted-foreground" />
          <span>Rezerwuj salkę</span>
        </Link>
      </Button>
      <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
        <Link to="/issues">
          <Wrench className="size-3.5 text-muted-foreground" />
          <span>Zgłoś usterkę</span>
        </Link>
      </Button>
    </div>
  )
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="text-xs font-medium text-muted-foreground mb-2">{children}</h2>
  )
}

function reservationRoute(entry: AgendaReservation): string {
  return entry.kind === "laundry" ? "/laundry" : "/rooms"
}

function reservationTitle(entry: AgendaReservation): string {
  return entry.kind === "laundry" ? entry.item.machineName : entry.item.roomName
}

function reservationTimeRange(entry: AgendaReservation): string {
  return entry.kind === "laundry" ? entry.item.slotTime : entry.item.timeRange
}

function reservationMeta(entry: AgendaReservation): string {
  if (entry.kind === "laundry") {
    return entry.item.pickupDeadline
      ? `Klucz do ${entry.item.pickupDeadline}`
      : "Pralnia"
  }
  return `Organizator · ${entry.item.participants} os.`
}

function TodayHeroCard({ entry }: { entry: AgendaReservation }) {
  const to = reservationRoute(entry)
  const time = reservationTimeRange(entry)
  const title = reservationTitle(entry)
  const isLaundry = entry.kind === "laundry"
  const deadline = isLaundry ? entry.item.pickupDeadline : undefined

  return (
    <Link to={to} className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl">
      <Card className="transition-colors hover:bg-muted/30">
        <CardHeader className="border-b">
          <div className="flex items-start justify-between gap-3">
            <CardTitle className="text-lg font-medium tracking-tight">
              {time}
            </CardTitle>
            {deadline ? (
              <Badge variant="outline">Klucz do {deadline}</Badge>
            ) : (
              <Badge variant="outline">Potwierdzona</Badge>
            )}
          </div>
          <CardDescription>{title}</CardDescription>
        </CardHeader>
        {isLaundry && deadline ? (
          <CardContent>
            <p className="text-sm text-foreground">
              Reguła 15 minut: odbierz klucz na portierni najpóźniej do{" "}
              <span className="font-medium">{deadline}</span>.
            </p>
          </CardContent>
        ) : entry.kind === "room" ? (
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Rola: organizator ({entry.item.participants} os.). Pamiętaj o
              odbiorze klucza w 15 min.
            </p>
          </CardContent>
        ) : null}
        <CardFooter className="justify-between text-sm">
          <span className="text-muted-foreground">
            {isLaundry ? "Pralnia" : "Salki"}
          </span>
          <span className="inline-flex items-center gap-1 font-medium">
            Otwórz zakładkę
            <ChevronRight className="size-4 text-muted-foreground" />
          </span>
        </CardFooter>
      </Card>
    </Link>
  )
}

function ReservationListCard({
  entries,
}: {
  entries: AgendaReservation[]
}) {
  if (!entries.length) return null

  return (
    <Card className="py-0 gap-0">
      <ul className="divide-y divide-border">
        {entries.map((entry) => (
          <li key={`${entry.kind}-${entry.item.id}`}>
            <Link
              to={reservationRoute(entry)}
              className="flex items-center gap-3 px-4 py-3 hover:bg-muted/40 transition-colors focus-visible:outline-none focus-visible:bg-muted/40"
            >
              <span className="w-12 shrink-0 text-sm font-medium tabular-nums">
                {leadingTime(reservationTimeRange(entry))}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium truncate">
                  {reservationTitle(entry)}
                </span>
                <span className="block text-xs text-muted-foreground truncate">
                  {reservationMeta(entry)}
                </span>
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function IssuesListCard({ issues }: { issues: ActiveIssue[] }) {
  if (!issues.length) return null

  return (
    <Card className="py-0 gap-0">
      <ul className="divide-y divide-border">
        {issues.map((issue) => (
          <li key={issue.id}>
            <Link
              to="/issues"
              className="flex items-center gap-3 px-4 py-3 hover:bg-muted/40 transition-colors focus-visible:outline-none focus-visible:bg-muted/40"
            >
              <Badge variant="outline" className="shrink-0">
                {issue.status === "IN_PROGRESS"
                  ? "W toku"
                  : issue.status === "ASSIGNED"
                    ? "Przypisana"
                    : "Nowa"}
              </Badge>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium truncate">
                  {issue.category}
                  <span className="font-normal text-muted-foreground">
                    {" "}
                    · {issue.title}
                  </span>
                </span>
                <span className="block text-xs text-muted-foreground truncate">
                  {issue.lastNote ?? issue.statusLabel}
                </span>
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function NoticeBanner({ announcement }: { announcement: ActiveAnnouncement }) {
  return (
    <Link
      to="/board"
      className={cn(
        "flex items-center gap-3 w-full px-4 py-2.5 -mx-0",
        "rounded-xl border border-border bg-muted/50",
        announcement.isUrgent && "border-destructive/40 bg-destructive/5",
        "hover:bg-muted transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      )}
    >
      <Badge variant={announcement.isUrgent ? "destructive" : "outline"} className="shrink-0">
        {announcement.isUrgent ? "ALERT" : "AOS"}
      </Badge>
      <span className="min-w-0 flex-1 text-sm">
        <span className="font-medium">{announcement.title}</span>
        <span className="text-muted-foreground"> · {announcement.date}</span>
      </span>
      <span className="shrink-0 inline-flex items-center gap-0.5 text-xs font-medium text-muted-foreground">
        Tablica
        <ChevronRight className="size-3.5" />
      </span>
    </Link>
  )
}

const defaultLaundry: ActiveLaundry = {
  id: "laundry-1",
  machineName: "Pralka #2 (Samsung EcoBubble)",
  slotTime: "18:00 – 19:30",
  date: "Dzisiaj",
  status: "CONFIRMED",
  pickupDeadline: "18:15",
}

const defaultRoom: ActiveRoom = {
  id: "room-1",
  roomName: "Salka Cichej Nauki „Kujon”",
  timeRange: "14:00 – 17:00",
  date: "Jutro",
  participants: 4,
  status: "CONFIRMED",
}

function defaultIssue(roomNumber?: string | null): ActiveIssue {
  return {
    id: "issue-1",
    title: "Nieszczelna uszczelka baterii umywalkowej",
    category: "Hydraulika",
    location: roomNumber ? `Pokój ${roomNumber}` : "Mój pokój",
    status: "IN_PROGRESS",
    statusLabel: "W trakcie realizacji (Konserwator)",
    lastNote: "Części zamienne pobrane z magazynu. Wymiana jutro do 12:00.",
  }
}

function formatBannerDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString("pl-PL", {
      dateStyle: "short",
      timeStyle: "short",
    })
  } catch {
    return iso
  }
}

export function DashboardPage() {
  const user = useOutletContext<UserProfile>()

  const [activeLaundry, setActiveLaundry] = React.useState<ActiveLaundry | null>(
    defaultLaundry
  )
  const [activeRoom, setActiveRoom] = React.useState<ActiveRoom | null>(
    defaultRoom
  )
  const [activeIssue, setActiveIssue] = React.useState<ActiveIssue | null>(() =>
    defaultIssue(user.roomNumber)
  )
  const [hideBannerPreview, setHideBannerPreview] = React.useState(false)

  const bannerQuery = useQuery({
    queryKey: ["events", "banner"],
    queryFn: getActiveBanner,
  })

  const liveAnnouncement: ActiveAnnouncement | null = React.useMemo(() => {
    const event = bannerQuery.data
    if (!event) return null
    return {
      id: event.id,
      title: event.title,
      author: event.authorName ?? "Administracja Osiedla",
      date: formatBannerDate(event.eventDate),
      content: event.description,
      isUrgent: event.priority === "CRITICAL",
    }
  }, [bannerQuery.data])

  const activeAnnouncement = hideBannerPreview ? null : liveAnnouncement

  const agenda = groupDashboardAgenda({
    laundry: activeLaundry,
    room: activeRoom,
    issue: activeIssue,
    announcement: activeAnnouncement,
  })

  const hasAgenda = hasAgendaItems(agenda)
  const hasAnything = hasAgenda || Boolean(agenda.banner)

  const clearAllForTesting = () => {
    setActiveLaundry(null)
    setActiveRoom(null)
    setActiveIssue(null)
    setHideBannerPreview(true)
  }

  const restoreAllForTesting = () => {
    setActiveLaundry(defaultLaundry)
    setActiveRoom(defaultRoom)
    setActiveIssue(defaultIssue(user.roomNumber))
    setHideBannerPreview(false)
  }

  return (
    <div className="w-full flex flex-col py-2 md:py-6 max-w-5xl mx-auto space-y-6 animate-in fade-in-50 duration-300">
      <div className="w-full flex items-center justify-end text-xs text-muted-foreground">
        <button
          type="button"
          onClick={hasAnything ? clearAllForTesting : restoreAllForTesting}
          className="underline hover:text-foreground cursor-pointer transition-colors"
        >
          {hasAnything
            ? "Podgląd: symuluj brak aktywnych spraw"
            : "Podgląd: przywróć aktywne sprawy"}
        </button>
      </div>

      {agenda.banner && <NoticeBanner announcement={agenda.banner} />}

      {hasAgenda && (
        <div className="w-full space-y-6">
          {(agenda.todayHero || agenda.todayRest.length > 0) && (
            <section>
              <SectionLabel>Dzisiaj</SectionLabel>
              <div className="space-y-2">
                {agenda.todayHero && <TodayHeroCard entry={agenda.todayHero} />}
                {agenda.todayRest.length > 0 && (
                  <ReservationListCard entries={agenda.todayRest} />
                )}
              </div>
            </section>
          )}

          {agenda.tomorrow.length > 0 && (
            <section>
              <SectionLabel>Jutro</SectionLabel>
              <ReservationListCard entries={agenda.tomorrow} />
            </section>
          )}

          {agenda.openIssues.length > 0 && (
            <section>
              <SectionLabel>Otwarte</SectionLabel>
              <IssuesListCard issues={agenda.openIssues} />
            </section>
          )}
        </div>
      )}

      {!hasAnything && (
        <div className="flex flex-col items-center justify-center text-center p-8 md:p-12 rounded-2xl border border-dashed border-border bg-card/50 max-w-lg mx-auto">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-muted text-muted-foreground mb-4">
            <CheckCircle2 className="size-7" />
          </div>
          <h3 className="text-lg font-semibold text-foreground">
            Brak aktywnych spraw
          </h3>
          <p className="text-xs md:text-sm text-muted-foreground mt-1 max-w-sm">
            Nie masz w tej chwili zaplanowanych prań, rezerwacji salek ani
            otwartych zgłoszeń usterek. Wszystko działa jak należy!
          </p>
          <div className="mt-6 w-full">
            <QuickActions />
          </div>
        </div>
      )}

      {agenda.banner && !hasAgenda && (
        <div className="flex flex-col items-center text-center gap-4 max-w-lg mx-auto py-4">
          <p className="text-sm text-muted-foreground">
            Brak zaplanowanych rezerwacji i usterek
          </p>
          <QuickActions />
        </div>
      )}
    </div>
  )
}
