import { useMemo, useState } from "react"
import { useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ChevronRight, Info, Waves } from "lucide-react"

import {
  cancelLaundryBooking,
  createLaundryBooking,
  getLaundrySchedule,
  listMyLaundryBookings,
} from "@/api/laundry"
import { getApiErrorMessage } from "@/api/errors"
import type {
  LaundryBooking,
  LaundryMachine,
  LaundrySlot,
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
import { cn } from "cn"
import {
  countActiveBookingsInRollingWindow,
  formatDayChipLabel,
  formatWarsawDateTime,
  formatWarsawTimeRange,
  isBookingCancellable,
  warsawDayRange,
} from "@/lib/laundry-dates"

type PendingSlot = {
  machine: LaundryMachine
  slot: LaundrySlot
}

export function LaundryPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const days = useMemo(() => warsawDayRange(7), [])
  const rangeFrom = days[0]
  const rangeTo = days[days.length - 1]
  const [selectedDay, setSelectedDay] = useState(days[0])

  const [bookTarget, setBookTarget] = useState<PendingSlot | null>(null)
  const [cancelTarget, setCancelTarget] = useState<LaundryBooking | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const scheduleQuery = useQuery({
    queryKey: ["laundry", "schedule", rangeFrom, rangeTo],
    queryFn: () => getLaundrySchedule(rangeFrom, rangeTo),
  })

  const bookingsQuery = useQuery({
    queryKey: ["laundry", "bookings", "me"],
    queryFn: listMyLaundryBookings,
  })

  const invalidateLaundry = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["laundry", "schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["laundry", "bookings"] }),
    ])
  }

  const bookMutation = useMutation({
    mutationFn: () =>
      createLaundryBooking({
        machineId: bookTarget!.slot.machineId,
        startTime: bookTarget!.slot.startTime,
        endTime: bookTarget!.slot.endTime,
      }),
    onSuccess: async () => {
      setBookTarget(null)
      setActionError(null)
      await invalidateLaundry()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się zarezerwować slotu"),
      )
      void queryClient.invalidateQueries({ queryKey: ["laundry", "schedule"] })
    },
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelLaundryBooking(cancelTarget!.id),
    onSuccess: async () => {
      setCancelTarget(null)
      setActionError(null)
      await invalidateLaundry()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się anulować rezerwacji"),
      )
    },
  })

  const schedule = scheduleQuery.data
  const bookings = bookingsQuery.data ?? []
  const weekCount = countActiveBookingsInRollingWindow(bookings)

  const daySlots =
    schedule?.days.find((d) => d.date === selectedDay)?.slots ?? []

  const machines = schedule?.machines ?? []

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <Waves className="size-5 text-muted-foreground" />
          Pralnia — {user.dormitoryName ?? "Twój akademik"}
        </h2>
        <p className="text-sm text-muted-foreground mt-1">
          Grafik na 7 dni. Maks. 2 aktywne rezerwacje w oknie 7 dni od daty
          rezerwacji; maksymalnie jedna rezerwacja na dzień.
        </p>
      </div>

      <div className="flex items-center gap-3 rounded-xl border border-border bg-muted/50 px-4 py-2.5 text-sm">
        <Badge variant="outline" className="shrink-0">
          15 min
        </Badge>
        <p className="text-muted-foreground min-w-0 flex-1">
          <span className="font-medium text-foreground">Reguła odbioru klucza:</span>{" "}
          odbierz klucz na portierni w ciągu 15 minut od startu slotu, inaczej
          rezerwacja zostanie zwolniona.
        </p>
        <Info className="size-4 shrink-0 text-muted-foreground" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="md:col-span-2 space-y-4">
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
            <Card>
              <CardContent className="py-12 text-center text-sm text-muted-foreground">
                Ładowanie harmonogramu…
              </CardContent>
            </Card>
          )}

          {scheduleQuery.isError && (
            <Card>
              <CardContent className="py-8 text-sm text-destructive">
                {getApiErrorMessage(
                  scheduleQuery.error,
                  "Nie udało się pobrać grafiku pralni",
                )}
              </CardContent>
            </Card>
          )}

          {schedule && machines.length === 0 && (
            <Card>
              <CardContent className="py-8 text-center text-sm text-muted-foreground">
                Brak pralek w Twoim akademiku.
              </CardContent>
            </Card>
          )}

          {schedule &&
            machines.map((machine) => {
              const slots = daySlots
                .filter((s) => s.machineId === machine.id)
                .sort(
                  (a, b) =>
                    new Date(a.startTime).getTime() -
                    new Date(b.startTime).getTime(),
                )

              return (
                <Card key={machine.id}>
                  <CardHeader className="border-b">
                    <CardTitle className="text-base font-medium">
                      {machine.identifier}
                    </CardTitle>
                    <CardDescription>
                      {machine.floorLocation}
                      {machine.status === "OUT_OF_ORDER"
                        ? " · wyłączona z użytku"
                        : ""}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="pt-4">
                    {slots.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        Brak slotów w tym dniu.
                      </p>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {slots.map((slot) => (
                          <SlotButton
                            key={`${slot.machineId}-${slot.startTime}`}
                            slot={slot}
                            onBook={() => {
                              setActionError(null)
                              setBookTarget({ machine, slot })
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

        <Card className="h-fit">
          <CardHeader>
            <CardTitle className="text-base">Moje rezerwacje</CardTitle>
            <CardDescription>
              Limit w oknie 7 dni: {weekCount} / 2
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {bookingsQuery.isLoading && (
              <p className="text-xs text-muted-foreground">Ładowanie…</p>
            )}
            {bookingsQuery.isError && (
              <p className="text-xs text-destructive">
                {getApiErrorMessage(
                  bookingsQuery.error,
                  "Nie udało się pobrać rezerwacji",
                )}
              </p>
            )}
            {!bookingsQuery.isLoading && bookings.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-4">
                Brak zaplanowanych prań
              </p>
            )}
            {bookings.map((booking) => (
              <div
                key={booking.id}
                className="rounded-lg border border-border p-3 space-y-2"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate">
                      {booking.machineIdentifier}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {formatWarsawDateTime(booking.startTime)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {formatWarsawTimeRange(
                        booking.startTime,
                        booking.endTime,
                      )}
                    </p>
                  </div>
                  <Badge variant="outline" className="shrink-0">
                    {bookingStatusLabel(booking.status)}
                  </Badge>
                </div>
                {isBookingCancellable(booking) && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="w-full"
                    onClick={() => {
                      setActionError(null)
                      setCancelTarget(booking)
                    }}
                  >
                    Anuluj
                  </Button>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      <Dialog
        open={bookTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setBookTarget(null)
            setActionError(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Potwierdź rezerwację</DialogTitle>
            <DialogDescription>
              {bookTarget && (
                <>
                  {bookTarget.machine.identifier} ·{" "}
                  {formatWarsawTimeRange(
                    bookTarget.slot.startTime,
                    bookTarget.slot.endTime,
                  )}
                  . Odbierz klucz w ciągu 15 minut od startu slotu.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          {actionError && (
            <p className="text-sm text-destructive">{actionError}</p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setBookTarget(null)}
              disabled={bookMutation.isPending}
            >
              Anuluj
            </Button>
            <Button
              type="button"
              onClick={() => bookMutation.mutate()}
              disabled={bookMutation.isPending || !bookTarget}
            >
              {bookMutation.isPending ? "Rezerwuję…" : "Potwierdź"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={cancelTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCancelTarget(null)
            setActionError(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Anulować rezerwację?</DialogTitle>
            <DialogDescription>
              {cancelTarget && (
                <>
                  {cancelTarget.machineIdentifier} ·{" "}
                  {formatWarsawDateTime(cancelTarget.startTime)}. Slot stanie się
                  dostępny dla innych mieszkańców.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          {actionError && (
            <p className="text-sm text-destructive">{actionError}</p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setCancelTarget(null)}
              disabled={cancelMutation.isPending}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => cancelMutation.mutate()}
              disabled={cancelMutation.isPending || !cancelTarget}
            >
              {cancelMutation.isPending ? "Anuluję…" : "Anuluj rezerwację"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function SlotButton({
  slot,
  onBook,
}: {
  slot: LaundrySlot
  onBook: () => void
}) {
  const label = formatWarsawTimeRange(slot.startTime, slot.endTime)
  const free = slot.state === "FREE"
  const mine = slot.state === "MINE"

  return (
    <Button
      type="button"
      size="sm"
      variant={mine ? "secondary" : "outline"}
      disabled={!free}
      onClick={onBook}
      className={cn(
        "tabular-nums",
        (slot.state === "OCCUPIED" || slot.state === "UNAVAILABLE") &&
          "opacity-50",
      )}
    >
      {mine ? (
        <span className="inline-flex items-center gap-1">
          {label}
          <span className="text-[10px] uppercase tracking-wide">Twoja</span>
        </span>
      ) : (
        <span className="inline-flex items-center gap-1">
          {label}
          {free && <ChevronRight className="size-3.5 opacity-60" />}
        </span>
      )}
    </Button>
  )
}

function bookingStatusLabel(status: LaundryBooking["status"]): string {
  switch (status) {
    case "CONFIRMED":
      return "Potwierdzona"
    case "KEY_ISSUED":
      return "Klucz wydany"
    case "COMPLETED":
      return "Zakończona"
    case "CANCELLED_USER":
      return "Anulowana"
    case "AUTO_CANCELLED_15MIN":
      return "Zwolniona (15 min)"
    case "CANCELLED_MACHINE_OUT_OF_ORDER":
      return "Awaria pralki"
    default:
      return status
  }
}
