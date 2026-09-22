import type { ReactNode } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  ChevronRight,
  DoorClosed,
  KeyRound,
  RefreshCw,
  Waves,
  Wrench,
} from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  cancelReceptionistLaundryBooking,
  cancelReceptionistRoomBooking,
  getReceptionistDesk,
  getReceptionistCardDay,
  issueLaundryKey,
  issueRoomKey,
  returnLaundryKey,
  returnRoomKey,
} from "@/api/receptionist"
import type {
  DeskLaundryBooking,
  DeskOpenIssue,
  DeskRoomBooking,
  IssueCategory,
  IssueStatus,
  UserProfile,
} from "@/api/types"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { formatWarsawDateTime, formatWarsawTimeRange } from "@/lib/laundry-dates"

const DESK_REFRESH_MS = 45_000

function categoryLabel(category: IssueCategory): string {
  switch (category) {
    case "PLUMBING":
      return "Hydraulika"
    case "ELECTRICAL":
      return "Elektryka"
    case "FURNITURE":
      return "Meble"
    case "LOCKSMITH":
      return "Ślusarstwo"
    default:
      return "Inne"
  }
}

function issueStatusLabel(status: IssueStatus): string {
  switch (status) {
    case "NEW":
      return "Nowe"
    case "ASSIGNED_TO_MAINTENANCE":
      return "Przekazane"
    case "IN_PROGRESS":
      return "W trakcie"
    case "PARTS_REQUIRED":
      return "Części"
    default:
      return status
  }
}

function residentLine(b: {
  residentFirstName: string
  residentLastName: string
  residentRoomNumber: string | null
}): string {
  const room = b.residentRoomNumber ? ` · pok. ${b.residentRoomNumber}` : ""
  return `${b.residentFirstName} ${b.residentLastName}${room}`
}

function isBookingOverdue(startTimeIso: string): boolean {
  const startMs = new Date(startTimeIso).getTime()
  return Date.now() > startMs + 15 * 60 * 1000
}

export function ReceptionistDeskPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const deskQuery = useQuery({
    queryKey: ["receptionist", "desk"],
    queryFn: getReceptionistDesk,
    refetchInterval: DESK_REFRESH_MS,
  })

  const cardDayQuery = useQuery({
    queryKey: ["receptionist", "card-day"],
    queryFn: getReceptionistCardDay,
    refetchInterval: DESK_REFRESH_MS,
  })

  const invalidateDesk = () =>
    queryClient.invalidateQueries({ queryKey: ["receptionist", "desk"] })

  const laundryIssue = useMutation({
    mutationFn: issueLaundryKey,
    onSuccess: () => void invalidateDesk(),
  })
  const laundryReturn = useMutation({
    mutationFn: returnLaundryKey,
    onSuccess: () => void invalidateDesk(),
  })
  const laundryCancel = useMutation({
    mutationFn: cancelReceptionistLaundryBooking,
    onSuccess: () => void invalidateDesk(),
  })
  const roomIssue = useMutation({
    mutationFn: issueRoomKey,
    onSuccess: () => void invalidateDesk(),
  })
  const roomReturn = useMutation({
    mutationFn: returnRoomKey,
    onSuccess: () => void invalidateDesk(),
  })
  const roomCancel = useMutation({
    mutationFn: cancelReceptionistRoomBooking,
    onSuccess: () => void invalidateDesk(),
  })

  const actionError =
    laundryIssue.error ||
    laundryReturn.error ||
    laundryCancel.error ||
    roomIssue.error ||
    roomReturn.error ||
    roomCancel.error

  const desk = deskQuery.data
  const busy =
    laundryIssue.isPending ||
    laundryReturn.isPending ||
    laundryCancel.isPending ||
    roomIssue.isPending ||
    roomReturn.isPending ||
    roomCancel.isPending

  const laundryAwaiting =
    desk?.laundry.filter((b) => b.status === "CONFIRMED") ?? []
  const laundryIssued =
    desk?.laundry.filter((b) => b.status === "KEY_ISSUED") ?? []
  const roomsAwaiting =
    desk?.rooms.filter((b) => b.status === "CONFIRMED") ?? []
  const roomsIssued =
    desk?.rooms.filter((b) => b.status === "KEY_ISSUED") ?? []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Pulpit portiera</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {user.dormitoryName ?? "Twój DS"} — rezerwacje na dziś i otwarte usterki
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => void deskQuery.refetch()}
          disabled={deskQuery.isFetching}
        >
          <RefreshCw
            className={`size-3.5 mr-1.5 ${deskQuery.isFetching ? "animate-spin" : ""}`}
          />
          Odśwież
        </Button>
      </div>

      {cardDayQuery.data && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border/70 bg-card px-4 py-3 text-sm">
          <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Kod dnia karty
          </span>
          <span
            className="size-4 rounded-full ring-1 ring-border shrink-0"
            style={{ backgroundColor: cardDayQuery.data.dayColorHex }}
            title={cardDayQuery.data.dayColorName}
          />
          <span className="font-medium">{cardDayQuery.data.dayColorName}</span>
          <span className="font-mono font-semibold tracking-wider">
            {cardDayQuery.data.dayCode}
          </span>
          <span className="text-xs text-muted-foreground ml-auto tabular-nums">
            {formatWarsawDateTime(cardDayQuery.data.serverTime)}
          </span>
        </div>
      )}

      {deskQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(deskQuery.error, "Nie udało się wczytać pulpitu")}
        </p>
      )}

      {actionError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(actionError, "Nie udało się wykonać akcji")}
        </p>
      )}

      {deskQuery.isPending && !desk && (
        <p className="text-sm text-muted-foreground">Ładowanie pulpitu…</p>
      )}

      {desk && (
        <div className="flex flex-col gap-6">
          {desk.laundry.length === 0 &&
            desk.rooms.length === 0 &&
            desk.openIssues.length === 0 && (
              <p className="text-sm text-muted-foreground">
                Brak aktywnych rezerwacji i otwartych usterek na dziś.{" "}
                <Link
                  to="/receptionist/laundry"
                  className="text-primary underline-offset-4 hover:underline"
                >
                  Grafik pralni
                </Link>
                {" · "}
                <Link
                  to="/receptionist/rooms"
                  className="text-primary underline-offset-4 hover:underline"
                >
                  grafik salek
                </Link>
                {" · "}
                <Link
                  to="/receptionist/issues"
                  className="text-primary underline-offset-4 hover:underline"
                >
                  rejestr usterek
                </Link>
              </p>
            )}

          {desk.laundry.length > 0 && (
            <Card className="border-border/70 shadow-none">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base flex items-center gap-2">
                      <Waves className="size-4 text-primary" />
                      Pralnia — dziś
                    </CardTitle>
                    <CardDescription className="mt-1">
                      {laundryAwaiting.length} do wydania · {laundryIssued.length}{" "}
                      klucz wydany
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/laundry">Grafik →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                {laundryAwaiting.length > 0 && (
                  <BookingGroup title="Do wydania klucza">
                    {laundryAwaiting.map((b) => (
                      <LaundryCard
                        key={b.id}
                        booking={b}
                        busy={busy}
                        onIssue={() => laundryIssue.mutate(b.id)}
                        onReturn={() => laundryReturn.mutate(b.id)}
                        onCancel={() => laundryCancel.mutate(b.id)}
                      />
                    ))}
                  </BookingGroup>
                )}
                {laundryIssued.length > 0 && (
                  <BookingGroup title="Klucz wydany — oczekuje zwrotu">
                    {laundryIssued.map((b) => (
                      <LaundryCard
                        key={b.id}
                        booking={b}
                        busy={busy}
                        onIssue={() => laundryIssue.mutate(b.id)}
                        onReturn={() => laundryReturn.mutate(b.id)}
                      />
                    ))}
                  </BookingGroup>
                )}
              </CardContent>
            </Card>
          )}

          {desk.rooms.length > 0 && (
            <Card className="border-border/70 shadow-none">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base flex items-center gap-2">
                      <DoorClosed className="size-4 text-primary" />
                      Salki — dziś
                    </CardTitle>
                    <CardDescription className="mt-1">
                      {roomsAwaiting.length} do wydania · {roomsIssued.length} klucz
                      wydany
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/rooms">Grafik →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                {roomsAwaiting.length > 0 && (
                  <BookingGroup title="Do wydania klucza">
                    {roomsAwaiting.map((b) => (
                      <RoomCard
                        key={b.id}
                        booking={b}
                        busy={busy}
                        onIssue={() => roomIssue.mutate(b.id)}
                        onReturn={() => roomReturn.mutate(b.id)}
                        onCancel={() => roomCancel.mutate(b.id)}
                      />
                    ))}
                  </BookingGroup>
                )}
                {roomsIssued.length > 0 && (
                  <BookingGroup title="Klucz wydany — oczekuje zwrotu">
                    {roomsIssued.map((b) => (
                      <RoomCard
                        key={b.id}
                        booking={b}
                        busy={busy}
                        onIssue={() => roomIssue.mutate(b.id)}
                        onReturn={() => roomReturn.mutate(b.id)}
                      />
                    ))}
                  </BookingGroup>
                )}
              </CardContent>
            </Card>
          )}

          {desk.openIssues.length > 0 && (
            <Card className="border-border/70 shadow-none">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base flex items-center gap-2">
                      <Wrench className="size-4 text-primary" />
                      Usterki
                    </CardTitle>
                    <CardDescription className="mt-1">
                      {desk.openIssuesCount} otwartych
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/issues">Rejestr →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {desk.openIssues.map((issue) => (
                  <IssueCard key={issue.id} issue={issue} />
                ))}
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  )
}

function BookingGroup({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
        {title}
      </h3>
      <div className="space-y-3">{children}</div>
    </div>
  )
}

function LaundryCard({
  booking,
  busy,
  onIssue,
  onReturn,
  onCancel,
}: {
  booking: DeskLaundryBooking
  busy: boolean
  onIssue: () => void
  onReturn: () => void
  onCancel?: () => void
}) {
  const overdue = booking.status === "CONFIRMED" && isBookingOverdue(booking.startTime)

  return (
    <div className="rounded-xl border border-border/70 bg-card p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Link
          to="/receptionist/laundry"
          className="min-w-0 space-y-1 flex-1 rounded-md -m-1 p-1 hover:bg-muted/40 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold truncate">
              {booking.machineIdentifier}
            </p>
            <Badge
              variant={
                booking.status === "KEY_ISSUED"
                  ? "default"
                  : overdue
                    ? "destructive"
                    : "secondary"
              }
              className="text-xs"
            >
              {booking.status === "KEY_ISSUED"
                ? "Klucz wydany"
                : overdue
                  ? "Spóźnienie (>15 min)"
                  : "Do wydania"}
            </Badge>
          </div>
          <p className="text-sm text-foreground/90">{residentLine(booking)}</p>
          <p className="text-sm font-medium tabular-nums">
            {formatWarsawTimeRange(booking.startTime, booking.endTime)}
          </p>
        </Link>

        <div className="flex flex-wrap gap-2 shrink-0">
          {booking.status === "CONFIRMED" && overdue && onCancel && (
            <Button
              size="sm"
              variant="outline"
              className="text-destructive border-destructive/40 hover:bg-destructive/10"
              disabled={busy}
              onClick={onCancel}
            >
              Zwolnij slot (15 min)
            </Button>
          )}
          {booking.status === "CONFIRMED" && (
            <Button size="sm" disabled={busy} onClick={onIssue}>
              <KeyRound className="size-3.5 mr-1.5" />
              Wydaj klucz
            </Button>
          )}
          {booking.status === "KEY_ISSUED" && (
            <Button
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={onReturn}
            >
              Odbierz klucz
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

function RoomCard({
  booking,
  busy,
  onIssue,
  onReturn,
  onCancel,
}: {
  booking: DeskRoomBooking
  busy: boolean
  onIssue: () => void
  onReturn: () => void
  onCancel?: () => void
}) {
  const overdue = booking.status === "CONFIRMED" && isBookingOverdue(booking.startTime)

  return (
    <div className="rounded-xl border border-border/70 bg-card p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Link
          to="/receptionist/rooms"
          className="min-w-0 space-y-1 flex-1 rounded-md -m-1 p-1 hover:bg-muted/40 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-sm font-semibold truncate">{booking.roomName}</p>
            <Badge
              variant={
                booking.status === "KEY_ISSUED"
                  ? "default"
                  : overdue
                    ? "destructive"
                    : "secondary"
              }
              className="text-xs"
            >
              {booking.status === "KEY_ISSUED"
                ? "Klucz wydany"
                : overdue
                  ? "Spóźnienie (>15 min)"
                  : "Do wydania"}
            </Badge>
          </div>
          <p className="text-sm text-foreground/90">{residentLine(booking)}</p>
          <p className="text-sm font-medium tabular-nums">
            {formatWarsawTimeRange(booking.startTime, booking.endTime)}
            <span className="text-muted-foreground font-normal">
              {" "}
              · {booking.participantsCount} os.
            </span>
          </p>
        </Link>

        <div className="flex flex-wrap gap-2 shrink-0">
          {booking.status === "CONFIRMED" && overdue && onCancel && (
            <Button
              size="sm"
              variant="outline"
              className="text-destructive border-destructive/40 hover:bg-destructive/10"
              disabled={busy}
              onClick={onCancel}
            >
              Zwolnij slot (15 min)
            </Button>
          )}
          {booking.status === "CONFIRMED" && (
            <Button size="sm" disabled={busy} onClick={onIssue}>
              <KeyRound className="size-3.5 mr-1.5" />
              Wydaj klucz
            </Button>
          )}
          {booking.status === "KEY_ISSUED" && (
            <Button
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={onReturn}
            >
              Odbierz klucz
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

function IssueCard({ issue }: { issue: DeskOpenIssue }) {
  return (
    <Link
      to={`/receptionist/issues?id=${encodeURIComponent(issue.id)}`}
      className="block rounded-xl border border-border/60 p-4 space-y-1.5 hover:bg-muted/40 hover:border-primary/30 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-semibold truncate">{issue.locationLabel}</p>
        <div className="flex items-center gap-1.5 shrink-0">
          <Badge variant="outline">{issueStatusLabel(issue.status)}</Badge>
          <ChevronRight className="size-4 text-muted-foreground" />
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        {categoryLabel(issue.category)}
        {issue.urgency === "URGENT" ? " · pilne" : ""}
      </p>
      <p className="text-sm leading-relaxed line-clamp-3">{issue.description}</p>
      <p className="text-xs text-muted-foreground">
        {formatWarsawDateTime(issue.createdAt)}
      </p>
    </Link>
  )
}
