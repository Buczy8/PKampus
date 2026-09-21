import type { ReactNode } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { DoorClosed, KeyRound, RefreshCw, Waves, Wrench } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  getReceptionistDesk,
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

export function ReceptionistDeskPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const deskQuery = useQuery({
    queryKey: ["receptionist", "desk"],
    queryFn: getReceptionistDesk,
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
  const roomIssue = useMutation({
    mutationFn: issueRoomKey,
    onSuccess: () => void invalidateDesk(),
  })
  const roomReturn = useMutation({
    mutationFn: returnRoomKey,
    onSuccess: () => void invalidateDesk(),
  })

  const actionError =
    laundryIssue.error ||
    laundryReturn.error ||
    roomIssue.error ||
    roomReturn.error

  const desk = deskQuery.data
  const busy =
    laundryIssue.isPending ||
    laundryReturn.isPending ||
    roomIssue.isPending ||
    roomReturn.isPending

  const laundryAwaiting =
    desk?.laundry.filter((b) => b.status === "CONFIRMED") ?? []
  const laundryIssued =
    desk?.laundry.filter((b) => b.status === "KEY_ISSUED") ?? []
  const roomsAwaiting =
    desk?.rooms.filter((b) => b.status === "CONFIRMED") ?? []
  const roomsIssued =
    desk?.rooms.filter((b) => b.status === "KEY_ISSUED") ?? []

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Pulpit portiera</h1>
          <p className="text-base text-muted-foreground mt-2">
            {user.dormitoryName ?? "Twój DS"} — rezerwacje na dziś i otwarte usterki
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="lg"
          onClick={() => void deskQuery.refetch()}
          disabled={deskQuery.isFetching}
        >
          <RefreshCw
            className={`size-4 mr-2 ${deskQuery.isFetching ? "animate-spin" : ""}`}
          />
          Odśwież
        </Button>
      </div>

      {deskQuery.isError && (
        <p className="text-base text-destructive">
          {getApiErrorMessage(deskQuery.error, "Nie udało się wczytać pulpitu")}
        </p>
      )}

      {actionError && (
        <p className="text-base text-destructive">
          {getApiErrorMessage(actionError, "Nie udało się wykonać akcji")}
        </p>
      )}

      {deskQuery.isPending && !desk && (
        <p className="text-base text-muted-foreground">Ładowanie pulpitu…</p>
      )}

      {desk && (
        <div className="flex flex-col gap-6">
          {desk.laundry.length === 0 &&
            desk.rooms.length === 0 &&
            desk.openIssues.length === 0 && (
              <p className="text-base text-muted-foreground">
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
              <CardHeader className="pb-4 px-6 pt-6">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-xl flex items-center gap-2.5">
                      <Waves className="size-5 text-primary" />
                      Pralnia — dziś
                    </CardTitle>
                    <CardDescription className="mt-2 text-base">
                      {laundryAwaiting.length} do wydania · {laundryIssued.length}{" "}
                      klucz wydany
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="lg"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/laundry">Grafik →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-6 px-6 pb-6">
                {laundryAwaiting.length > 0 && (
                  <BookingGroup title="Do wydania klucza">
                    {laundryAwaiting.map((b) => (
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
              <CardHeader className="pb-4 px-6 pt-6">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-xl flex items-center gap-2.5">
                      <DoorClosed className="size-5 text-primary" />
                      Salki — dziś
                    </CardTitle>
                    <CardDescription className="mt-2 text-base">
                      {roomsAwaiting.length} do wydania · {roomsIssued.length} klucz
                      wydany
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="lg"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/rooms">Grafik →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-6 px-6 pb-6">
                {roomsAwaiting.length > 0 && (
                  <BookingGroup title="Do wydania klucza">
                    {roomsAwaiting.map((b) => (
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
              <CardHeader className="pb-4 px-6 pt-6">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-xl flex items-center gap-2.5">
                      <Wrench className="size-5 text-primary" />
                      Usterki
                    </CardTitle>
                    <CardDescription className="mt-2 text-base">
                      {desk.openIssuesCount} otwartych
                    </CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="lg"
                    asChild
                    className="shrink-0"
                  >
                    <Link to="/receptionist/issues">Rejestr →</Link>
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3 px-6 pb-6">
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
}: {
  booking: DeskLaundryBooking
  busy: boolean
  onIssue: () => void
  onReturn: () => void
}) {
  return (
    <div className="rounded-2xl border border-border/70 bg-card p-5 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xl font-semibold truncate">
              {booking.machineIdentifier}
            </p>
            <Badge
              variant={booking.status === "KEY_ISSUED" ? "default" : "secondary"}
              className="text-xs"
            >
              {booking.status === "KEY_ISSUED" ? "Klucz wydany" : "Do wydania"}
            </Badge>
          </div>
          <p className="text-base text-foreground/90">{residentLine(booking)}</p>
          <p className="text-lg font-medium tabular-nums">
            {formatWarsawTimeRange(booking.startTime, booking.endTime)}
          </p>
        </div>

        <div className="flex flex-wrap gap-2 shrink-0">
          {booking.status === "CONFIRMED" && (
            <Button
              size="lg"
              className="min-w-40 h-12 text-base"
              disabled={busy}
              onClick={onIssue}
            >
              <KeyRound className="size-5 mr-2" />
              Wydaj klucz
            </Button>
          )}
          {booking.status === "KEY_ISSUED" && (
            <Button
              size="lg"
              variant="secondary"
              className="min-w-40 h-12 text-base"
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
}: {
  booking: DeskRoomBooking
  busy: boolean
  onIssue: () => void
  onReturn: () => void
}) {
  return (
    <div className="rounded-2xl border border-border/70 bg-card p-5 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xl font-semibold truncate">{booking.roomName}</p>
            <Badge
              variant={booking.status === "KEY_ISSUED" ? "default" : "secondary"}
              className="text-xs"
            >
              {booking.status === "KEY_ISSUED" ? "Klucz wydany" : "Do wydania"}
            </Badge>
          </div>
          <p className="text-base text-foreground/90">{residentLine(booking)}</p>
          <p className="text-lg font-medium tabular-nums">
            {formatWarsawTimeRange(booking.startTime, booking.endTime)}
            <span className="text-muted-foreground font-normal text-base">
              {" "}
              · {booking.participantsCount} os.
            </span>
          </p>
        </div>

        <div className="flex flex-wrap gap-2 shrink-0">
          {booking.status === "CONFIRMED" && (
            <Button
              size="lg"
              className="min-w-40 h-12 text-base"
              disabled={busy}
              onClick={onIssue}
            >
              <KeyRound className="size-5 mr-2" />
              Wydaj klucz
            </Button>
          )}
          {booking.status === "KEY_ISSUED" && (
            <Button
              size="lg"
              variant="secondary"
              className="min-w-40 h-12 text-base"
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
    <div className="rounded-2xl border border-border/60 p-5 space-y-2">
      <div className="flex items-start justify-between gap-3">
        <p className="text-lg font-semibold truncate">{issue.locationLabel}</p>
        <Badge variant="outline" className="shrink-0">
          {issueStatusLabel(issue.status)}
        </Badge>
      </div>
      <p className="text-sm text-muted-foreground">
        {categoryLabel(issue.category)}
        {issue.urgency === "URGENT" ? " · pilne" : ""}
      </p>
      <p className="text-base leading-relaxed line-clamp-3">{issue.description}</p>
      <p className="text-sm text-muted-foreground">
        {formatWarsawDateTime(issue.createdAt)}
      </p>
    </div>
  )
}
