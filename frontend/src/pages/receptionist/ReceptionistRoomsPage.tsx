import { useMemo, useState } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, ArrowLeft, DoorClosed } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  cancelReceptionistRoomBooking,
  getReceptionistRoomSchedule,
  reportRoomMaintenance,
  restoreThematicRoom,
} from "@/api/receptionist"
import type {
  StaffRoomBookingSlot,
  StaffThematicRoom,
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "cn"
import {
  formatDayChipLabel,
  formatWarsawTimeRange,
  warsawDayRange,
} from "@/lib/laundry-dates"

export function ReceptionistRoomsPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const days = useMemo(() => warsawDayRange(7), [])
  const rangeFrom = days[0]
  const rangeTo = days[days.length - 1]
  const [selectedDay, setSelectedDay] = useState(days[0])

  const [cancelBooking, setCancelBooking] = useState<StaffRoomBookingSlot | null>(
    null,
  )
  const [maintenanceRoom, setMaintenanceRoom] =
    useState<StaffThematicRoom | null>(null)
  const [maintenanceReason, setMaintenanceReason] = useState("")
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  const scheduleQuery = useQuery({
    queryKey: ["receptionist", "rooms", "schedule", rangeFrom, rangeTo],
    queryFn: () => getReceptionistRoomSchedule(rangeFrom, rangeTo),
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ["receptionist", "rooms", "schedule"],
      }),
      queryClient.invalidateQueries({ queryKey: ["receptionist", "desk"] }),
    ])
  }

  const cancelMutation = useMutation({
    mutationFn: (id: string) => cancelReceptionistRoomBooking(id),
    onSuccess: async () => {
      setCancelBooking(null)
      setActionError(null)
      setActionInfo("Rezerwacja anulowana")
      await invalidate()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się anulować rezerwacji"),
      )
    },
  })

  const maintenanceMutation = useMutation({
    mutationFn: () =>
      reportRoomMaintenance(maintenanceRoom!.id, maintenanceReason),
    onSuccess: async (result) => {
      setMaintenanceRoom(null)
      setMaintenanceReason("")
      setActionError(null)
      setActionInfo(
        `Salka wyłączona. Anulowano ${result.cancelledCount} przyszłych rezerwacji.`,
      )
      await invalidate()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się wyłączyć salki"),
      )
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => restoreThematicRoom(id),
    onSuccess: async () => {
      setActionError(null)
      setActionInfo("Salka przywrócona do użytku")
      await invalidate()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się przywrócić salki"))
    },
  })

  const schedule = scheduleQuery.data
  const rooms = schedule?.rooms ?? []
  const dayBookings =
    schedule?.days.find((d) => d.date === selectedDay)?.bookings ?? []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Button type="button" variant="ghost" size="sm" asChild className="-ml-2 mb-1">
            <Link to="/receptionist">
              <ArrowLeft className="size-3.5 mr-1" />
              Pulpit
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight flex items-center gap-2">
            <DoorClosed className="size-5 text-primary" />
            Salki — moderacja
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            {user.dormitoryName ?? "Twój DS"} — podgląd rezerwacji, anulowanie i
            awarie salek
          </p>
        </div>
      </div>

      {actionError && (
        <p className="text-sm text-destructive">{actionError}</p>
      )}
      {actionInfo && !actionError && (
        <p className="text-sm text-primary">{actionInfo}</p>
      )}

      <div className="flex flex-wrap gap-2">
        {days.map((day) => (
          <Button
            key={day}
            type="button"
            size="sm"
            variant={selectedDay === day ? "default" : "outline"}
            onClick={() => setSelectedDay(day)}
          >
            {formatDayChipLabel(day)}
          </Button>
        ))}
      </div>

      {scheduleQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Ładowanie grafiku…</p>
      )}
      {scheduleQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(
            scheduleQuery.error,
            "Nie udało się pobrać grafiku salek",
          )}
        </p>
      )}

      {schedule && rooms.length === 0 && (
        <p className="text-sm text-muted-foreground">Brak salek w akademiku.</p>
      )}

      <div className="space-y-4">
        {rooms.map((room) => {
          const bookings = dayBookings
            .filter((b) => b.roomId === room.id)
            .sort(
              (a, b) =>
                new Date(a.startTime).getTime() - new Date(b.startTime).getTime(),
            )
          const maintenance = room.status === "MAINTENANCE"

          return (
            <Card key={room.id} className="border-border/70 shadow-none">
              <CardHeader className="border-b border-border/60 pb-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base font-medium flex items-center gap-2">
                      {room.name}
                      {maintenance && (
                        <Badge variant="destructive">Remont / awaria</Badge>
                      )}
                    </CardTitle>
                    <CardDescription>
                      {room.openingTime.slice(0, 5)}–{room.closingTime.slice(0, 5)}
                      {" · "}
                      max {room.maxCapacity} os.
                    </CardDescription>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {maintenance ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={restoreMutation.isPending}
                        onClick={() => {
                          setActionError(null)
                          setActionInfo(null)
                          restoreMutation.mutate(room.id)
                        }}
                      >
                        Przywróć do użytku
                      </Button>
                    ) : (
                      <Button
                        type="button"
                        size="sm"
                        variant="destructive"
                        onClick={() => {
                          setActionError(null)
                          setActionInfo(null)
                          setMaintenanceReason("")
                          setMaintenanceRoom(room)
                        }}
                      >
                        <AlertTriangle className="size-3.5 mr-1.5" />
                        Wyłącz salkę
                      </Button>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardContent className="pt-4">
                {maintenance ? (
                  <p className="text-sm text-muted-foreground">
                    Salka wyłączona — nowe rezerwacje zablokowane. Przywróć po
                    naprawie.
                  </p>
                ) : bookings.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    Brak rezerwacji w tym dniu.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {bookings.map((booking) => (
                      <StaffBookingButton
                        key={booking.id}
                        booking={booking}
                        onCancel={() => {
                          setActionError(null)
                          setActionInfo(null)
                          setCancelBooking(booking)
                        }}
                      />
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>

      <Dialog
        open={Boolean(cancelBooking)}
        onOpenChange={(open) => {
          if (!open) setCancelBooking(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Anulować rezerwację?</DialogTitle>
            <DialogDescription>
              {cancelBooking && (
                <>
                  {formatWarsawTimeRange(
                    cancelBooking.startTime,
                    cancelBooking.endTime,
                  )}
                  {` — ${cancelBooking.residentLabel}`}. Slot zostanie zwolniony.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setCancelBooking(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={
                cancelMutation.isPending ||
                !cancelBooking ||
                cancelBooking.status !== "CONFIRMED"
              }
              onClick={() => {
                if (cancelBooking) cancelMutation.mutate(cancelBooking.id)
              }}
            >
              Anuluj rezerwację
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(maintenanceRoom)}
        onOpenChange={(open) => {
          if (!open) {
            setMaintenanceRoom(null)
            setMaintenanceReason("")
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Wyłącz salkę</DialogTitle>
            <DialogDescription>
              {maintenanceRoom?.name} zostanie oznaczona jako remont/awaria.
              Przyszłe potwierdzone rezerwacje zostaną anulowane, a system
              utworzy zgłoszenie usterki.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="maintenance-reason">Powód</FieldLabel>
              <Textarea
                id="maintenance-reason"
                value={maintenanceReason}
                onChange={(e) => setMaintenanceReason(e.target.value)}
                placeholder="Np. awaria klimatyzacji, malowanie…"
                rows={3}
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setMaintenanceRoom(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={
                maintenanceMutation.isPending ||
                maintenanceReason.trim().length === 0
              }
              onClick={() => maintenanceMutation.mutate()}
            >
              Wyłącz salkę
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function StaffBookingButton({
  booking,
  onCancel,
}: {
  booking: StaffRoomBookingSlot
  onCancel: () => void
}) {
  const timeLabel = formatWarsawTimeRange(booking.startTime, booking.endTime)
  const canCancel = booking.status === "CONFIRMED"

  return (
    <button
      type="button"
      disabled={!canCancel}
      onClick={onCancel}
      title={
        canCancel
          ? "Kliknij, aby anulować"
          : "Klucz wydany — obsłuż na pulpicie"
      }
      className={cn(
        "inline-flex flex-col items-start rounded-lg border px-3 py-2 text-xs text-left transition-colors",
        canCancel
          ? "border-primary/40 bg-primary/5 hover:bg-primary/10 cursor-pointer"
          : "border-border/60 bg-muted/40 cursor-default",
      )}
    >
      <span className="font-medium">{timeLabel}</span>
      <span className="text-muted-foreground max-w-[12rem] truncate">
        {booking.residentLabel}
      </span>
      <span className="text-muted-foreground">
        {booking.participantsCount} os.
      </span>
      <span className="font-medium mt-0.5">
        {booking.status === "KEY_ISSUED"
          ? "Klucz wydany"
          : canCancel
            ? "Anuluj"
            : "Zajęty"}
      </span>
    </button>
  )
}
