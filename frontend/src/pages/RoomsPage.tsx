import { useEffect, useMemo, useState } from "react"
import { useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Calendar, DoorClosed, Info } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  cancelRoomBooking,
  createRoomBooking,
  getRoomAvailability,
  listMyRoomBookings,
  listThematicRooms,
} from "@/api/rooms"
import type { RoomBooking, ThematicRoom, UserProfile } from "@/api/types"
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import {
  addDaysToIsoDate,
  formatDayChipLabel,
  formatWarsawDateTime,
  formatWarsawTimeRange,
  overlapsAnyBusy,
  warsawDateOf,
  warsawDateString,
  warsawDayRange,
  warsawLocalDateTimeToIso,
} from "@/lib/laundry-dates"
import { cn } from "cn"

function formatHours(room: ThematicRoom): string {
  const open = room.openingTime.slice(0, 5)
  const close = room.closingTime.slice(0, 5)
  if (room.spansMidnight) {
    return `${open}–${close} (+1) · max ${room.maxDurationHours} h`
  }
  return `max ${room.maxDurationHours} h · ${open}–${close}`
}

function parseHour(time: string): number {
  return Number(time.slice(0, 2))
}

function formatHour(h: number): string {
  return `${String(h).padStart(2, "0")}:00`
}

/** Whole-hour start options for a room (session day for spansMidnight). */
function wholeHourStarts(room: ThematicRoom): string[] {
  const openH = parseHour(room.openingTime)
  const closeH = parseHour(room.closingTime)
  const hours: number[] = []
  if (room.spansMidnight) {
    // Session starts in the evening; overnight hours are end-only.
    for (let h = openH; h <= 23; h++) hours.push(h)
  } else {
    const lastStart = closeH > openH ? closeH - 1 : openH
    for (let h = openH; h <= lastStart; h++) hours.push(h)
  }
  return hours.map(formatHour)
}

/** Whole-hour end options after selected start (same or next calendar day for Chillout). */
function wholeHourEnds(room: ThematicRoom, startHhMm: string): string[] {
  const startH = parseHour(startHhMm)
  const closeH = parseHour(room.closingTime)
  const maxH = room.maxDurationHours
  const hours: number[] = []

  if (!room.spansMidnight) {
    for (let h = startH + 1; h <= closeH && h - startH <= maxH; h++) {
      hours.push(h)
    }
    return hours.map(formatHour)
  }

  for (let h = startH + 1; h <= 23 && h - startH <= maxH; h++) {
    hours.push(h)
  }
  const hoursAfterMidnight = maxH - (24 - startH)
  for (let h = 0; h <= closeH && h <= hoursAfterMidnight; h++) {
    hours.push(h)
  }
  return hours.map(formatHour)
}

function resolveBookingRange(
  room: ThematicRoom,
  day: string,
  startHhMm: string,
  endHhMm: string,
): { startIso: string; endIso: string } | null {
  let endDate = day
  if (room.spansMidnight && endHhMm <= startHhMm) {
    endDate = addDaysToIsoDate(day, 1)
  } else if (!room.spansMidnight && endHhMm <= startHhMm) {
    return null
  }
  return {
    startIso: warsawLocalDateTimeToIso(day, startHhMm),
    endIso: warsawLocalDateTimeToIso(endDate, endHhMm),
  }
}

/** Hours of the day that are covered by at least one busy interval (exclusive booking). */
function busyHourLabels(
  room: ThematicRoom,
  day: string,
  busy: { startTime: string; endTime: string }[],
): Set<string> {
  const labels = new Set<string>()
  for (const hh of occupancyStripHours(room)) {
    // closing hour alone is a boundary marker — skip half-open slot past closing
    if (!room.spansMidnight && hh === formatHour(parseHour(room.closingTime))) {
      continue
    }
    let date = day
    if (room.spansMidnight && parseHour(hh) < parseHour(room.openingTime)) {
      date = addDaysToIsoDate(day, 1)
    }
    const slotStart = warsawLocalDateTimeToIso(date, hh)
    const nextH = (parseHour(hh) + 1) % 24
    let nextDate = date
    if (nextH === 0) nextDate = addDaysToIsoDate(date, 1)
    const slotEnd = warsawLocalDateTimeToIso(nextDate, formatHour(nextH))
    if (overlapsAnyBusy(slotStart, slotEnd, busy)) {
      labels.add(hh)
    }
  }
  return labels
}

/** End times that would overlap an existing booking if chosen with this start. */
function freeEndHours(
  room: ThematicRoom,
  day: string,
  startHhMm: string,
  busy: { startTime: string; endTime: string }[],
): string[] {
  const ends = wholeHourEnds(room, startHhMm)
  const free: string[] = []
  for (const end of ends) {
    const range = resolveBookingRange(room, day, startHhMm, end)
    if (!range) continue
    if (overlapsAnyBusy(range.startIso, range.endIso, busy)) break
    free.push(end)
  }
  return free
}

function isStartHourFree(
  room: ThematicRoom,
  day: string,
  startHhMm: string,
  busy: { startTime: string; endTime: string }[],
): boolean {
  return freeEndHours(room, day, startHhMm, busy).length > 0
}

function occupancyStripHours(room: ThematicRoom): string[] {
  const starts = wholeHourStarts(room)
  if (!room.spansMidnight) {
    const close = formatHour(parseHour(room.closingTime))
    return starts.includes(close) ? starts : [...starts, close]
  }
  const closeH = parseHour(room.closingTime)
  const overnight = Array.from({ length: closeH + 1 }, (_, h) => formatHour(h))
  return [...starts, ...overnight]
}

function isRoomBookingCancellable(booking: RoomBooking): boolean {
  return booking.status === "CONFIRMED" && new Date(booking.startTime) > new Date()
}

function bookingStatusLabel(status: RoomBooking["status"]): string {
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
    case "CANCELLED_ROOM_MAINTENANCE":
      return "Anulowana (remont)"
    default:
      return status
  }
}

export function RoomsPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const dayOptions = useMemo(() => warsawDayRange(14), [])

  const [bookRoom, setBookRoom] = useState<ThematicRoom | null>(null)
  const [day, setDay] = useState(() => dayOptions[1] ?? warsawDateString())
  const [startTime, setStartTime] = useState("10:00")
  const [endTime, setEndTime] = useState("12:00")
  const [participants, setParticipants] = useState(2)
  const [purpose, setPurpose] = useState("")
  const [termsAccepted, setTermsAccepted] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [cancelTarget, setCancelTarget] = useState<RoomBooking | null>(null)
  const [isBooking, setIsBooking] = useState(false)

  const availabilityTo = bookRoom?.spansMidnight
    ? addDaysToIsoDate(day, 1)
    : day

  const roomsQuery = useQuery({
    queryKey: ["rooms", "catalog"],
    queryFn: listThematicRooms,
  })

  const bookingsQuery = useQuery({
    queryKey: ["rooms", "bookings", "me"],
    queryFn: listMyRoomBookings,
  })

  const availabilityQuery = useQuery({
    queryKey: ["rooms", "availability", bookRoom?.id, day, availabilityTo],
    queryFn: () => getRoomAvailability(bookRoom!.id, day, availabilityTo),
    enabled: Boolean(bookRoom),
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["rooms", "bookings"] }),
      queryClient.invalidateQueries({ queryKey: ["rooms", "availability"] }),
    ])
  }

  const cancelMutation = useMutation({
    mutationFn: (id: string) => cancelRoomBooking(id),
    onSuccess: async () => {
      setCancelTarget(null)
      await invalidate()
    },
  })

  const rooms = roomsQuery.data ?? []
  const bookings = bookingsQuery.data ?? []
  const busy = availabilityQuery.data?.busy ?? []

  const busyHours = useMemo(
    () => (bookRoom ? busyHourLabels(bookRoom, day, busy) : new Set<string>()),
    [bookRoom, day, busy],
  )

  const startOptions = useMemo(() => {
    if (!bookRoom) return []
    return wholeHourStarts(bookRoom).filter((h) =>
      isStartHourFree(bookRoom, day, h, busy),
    )
  }, [bookRoom, day, busy])

  const endOptions = useMemo(() => {
    if (!bookRoom) return []
    return freeEndHours(bookRoom, day, startTime, busy)
  }, [bookRoom, day, startTime, busy])

  const selectedRange = useMemo(() => {
    if (!bookRoom) return null
    return resolveBookingRange(bookRoom, day, startTime, endTime)
  }, [bookRoom, day, startTime, endTime])

  const selectionConflicts =
    Boolean(selectedRange) &&
    overlapsAnyBusy(selectedRange!.startIso, selectedRange!.endIso, busy)

  const myActiveSameDay = useMemo(() => {
    if (!selectedRange) return null
    const now = Date.now()
    const daysCovered = new Set<string>()
    daysCovered.add(warsawDateOf(selectedRange.startIso))
    daysCovered.add(warsawDateOf(selectedRange.endIso))
    // if end is midnight, end date is exclusive boundary
    return (
      bookings.find((b) => {
        if (b.status !== "CONFIRMED" && b.status !== "KEY_ISSUED") return false
        if (new Date(b.endTime).getTime() <= now) return false
        const bDays = [warsawDateOf(b.startTime), warsawDateOf(b.endTime)]
        return bDays.some((d) => daysCovered.has(d))
      }) ?? null
    )
  }, [bookings, selectedRange])

  const stripHours = bookRoom ? occupancyStripHours(bookRoom) : []

  function openBook(room: ThematicRoom) {
    setBookRoom(room)
    setFormError(null)
    setTermsAccepted(false)
    setParticipants(Math.min(2, room.maxCapacity))
    setPurpose("")
    const nextDay = dayOptions[1] ?? warsawDateString()
    setDay(nextDay)
    const starts = wholeHourStarts(room)
    const defaultStart = starts[0] ?? "10:00"
    setStartTime(defaultStart)
    const ends = freeEndHours(room, nextDay, defaultStart, [])
    setEndTime(ends[Math.min(1, ends.length - 1)] ?? ends[0] ?? "12:00")
  }

  // Keep start/end in sync when day or availability changes
  useEffect(() => {
    if (!bookRoom) return
    if (startOptions.length > 0 && !startOptions.includes(startTime)) {
      setStartTime(startOptions[0])
    }
  }, [bookRoom, startOptions, startTime])

  useEffect(() => {
    if (!bookRoom) return
    if (endOptions.length > 0 && !endOptions.includes(endTime)) {
      setEndTime(endOptions[0])
    }
  }, [bookRoom, endOptions, endTime])

  async function submitBooking() {
    if (!bookRoom) return
    setFormError(null)

    if (!purpose.trim()) {
      setFormError("Podaj cel rezerwacji")
      return
    }
    if (!termsAccepted) {
      setFormError("Musisz zaakceptować regulamin")
      return
    }
    if (participants < 1 || participants > bookRoom.maxCapacity) {
      setFormError("Nieprawidłowa liczba uczestników")
      return
    }

    const range = resolveBookingRange(bookRoom, day, startTime, endTime)
    if (!range) {
      setFormError("Godzina zakończenia musi być późniejsza niż rozpoczęcia")
      return
    }
    if (overlapsAnyBusy(range.startIso, range.endIso, busy)) {
      setFormError(
        "Wybrany przedział jest już zajęty — salka jest rezerwowana na wyłączność",
      )
      return
    }
    const now = Date.now()
    const daysCovered = new Set([
      warsawDateOf(range.startIso),
      warsawDateOf(range.endIso),
    ])
    const blocking = bookings.find((b) => {
      if (b.status !== "CONFIRMED" && b.status !== "KEY_ISSUED") return false
      if (new Date(b.endTime).getTime() <= now) return false
      return [warsawDateOf(b.startTime), warsawDateOf(b.endTime)].some((d) =>
        daysCovered.has(d),
      )
    })
    if (blocking) {
      setFormError(
        `Masz już aktywną rezerwację tego dnia (${blocking.roomName}, ${formatWarsawTimeRange(blocking.startTime, blocking.endTime)}). Kolejna możliwa dopiero po jej zakończeniu.`,
      )
      return
    }

    setIsBooking(true)
    try {
      await createRoomBooking({
        roomId: bookRoom.id,
        startTime: range.startIso,
        endTime: range.endIso,
        participantsCount: participants,
        purpose: purpose.trim(),
        termsAccepted: true,
      })
      setBookRoom(null)
      setPurpose("")
      setTermsAccepted(false)
      await invalidate()
    } catch (error) {
      setFormError(getApiErrorMessage(error, "Nie udało się zarezerwować salki"))
      await queryClient.invalidateQueries({ queryKey: ["rooms", "availability"] })
    } finally {
      setIsBooking(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <DoorClosed className="size-5 text-muted-foreground" />
            Salki Tematyczne — {user.dormitoryName ?? "Twój akademik"}
          </h2>
          <p className="text-sm text-muted-foreground">
            Jako Organizator odpowiadasz za porządek i stan wyposażenia (Regulamin OS PK).
          </p>
        </div>
      </div>

      <div className="flex items-start gap-3 p-4 rounded-xl border border-border bg-muted/40 text-sm">
        <Info className="size-5 text-muted-foreground shrink-0 mt-0.5" />
        <div>
          <strong>Odpowiedzialność Organizatora:</strong> Deklarujesz liczbę uczestników, cel
          oraz akceptujesz regulamin. Odbiór klucza na portierni w ciągu 15 minut od startu.
        </div>
      </div>

      {roomsQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">Ładowanie katalogu…</p>
      ) : roomsQuery.isError ? (
        <p className="text-sm text-destructive">{getApiErrorMessage(roomsQuery.error)}</p>
      ) : rooms.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Brak dostępnych salek w Twoim akademiku.
        </p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {rooms.map((room) => (
            <Card key={room.id} className="flex flex-col justify-between">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="size-9 rounded-lg bg-muted text-muted-foreground flex items-center justify-center">
                    <DoorClosed className="size-5" />
                  </div>
                  <Badge variant="outline" className="text-xs">
                    Do {room.maxCapacity} osób
                  </Badge>
                </div>
                <CardTitle className="text-base mt-2">{room.name}</CardTitle>
                <CardDescription className="line-clamp-2">
                  {room.description || formatHours(room)}
                </CardDescription>
                <p className="text-xs text-muted-foreground pt-1">{formatHours(room)}</p>
              </CardHeader>
              <CardFooter className="pt-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full gap-2"
                  type="button"
                  onClick={() => openBook(room)}
                >
                  <Calendar className="size-3.5" />
                  <span>Zarezerwuj</span>
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Moje rezerwacje salek</CardTitle>
          <CardDescription>Aktywne rezerwacje (potwierdzone / klucz wydany)</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {bookingsQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Ładowanie…</p>
          )}
          {bookingsQuery.isError && (
            <p className="text-sm text-destructive">
              {getApiErrorMessage(bookingsQuery.error, "Nie udało się pobrać rezerwacji")}
            </p>
          )}
          {!bookingsQuery.isLoading && bookings.length === 0 && (
            <p className="text-sm text-muted-foreground">Brak aktywnych rezerwacji salek.</p>
          )}
          {bookings.map((booking) => (
            <div
              key={booking.id}
              className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg border border-border p-3"
            >
              <div>
                <p className="font-medium text-sm">{booking.roomName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatWarsawDateTime(booking.startTime)} ·{" "}
                  {formatWarsawTimeRange(booking.startTime, booking.endTime)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {booking.participantsCount} os. · {booking.purpose}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="secondary">{bookingStatusLabel(booking.status)}</Badge>
                {isRoomBookingCancellable(booking) && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => setCancelTarget(booking)}
                  >
                    Anuluj
                  </Button>
                )}
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Dialog
        open={Boolean(bookRoom)}
        onOpenChange={(open) => {
          if (!open) {
            setBookRoom(null)
            setFormError(null)
          }
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Rezerwacja: {bookRoom?.name}</DialogTitle>
            <DialogDescription>
              {bookRoom ? formatHours(bookRoom) : null}. Max {bookRoom?.maxCapacity}{" "}
              osób. Salka na wyłączność — w jednym czasie tylko jedna rezerwacja.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>Dzień</FieldLabel>
              <Select
                value={day}
                onValueChange={(value) => {
                  setDay(value)
                  setFormError(null)
                }}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {dayOptions.map((d) => (
                    <SelectItem key={d} value={d}>
                      {formatDayChipLabel(d)} · {d}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field>
              <FieldLabel>Zajętość w wybranym dniu</FieldLabel>
              {availabilityQuery.isLoading ? (
                <p className="text-xs text-muted-foreground">Ładowanie grafiku…</p>
              ) : (
                <div className="space-y-2">
                  <div className="flex flex-wrap gap-1">
                    {stripHours.map((h) => {
                      const taken = busyHours.has(h)
                      const inSelection =
                        selectedRange != null &&
                        (() => {
                          let date = day
                          if (
                            bookRoom?.spansMidnight &&
                            parseHour(h) < parseHour(bookRoom.openingTime)
                          ) {
                            date = addDaysToIsoDate(day, 1)
                          }
                          const slotStart = warsawLocalDateTimeToIso(date, h)
                          const nextH = (parseHour(h) + 1) % 24
                          let nextDate = date
                          if (nextH === 0) nextDate = addDaysToIsoDate(date, 1)
                          const slotEnd = warsawLocalDateTimeToIso(
                            nextDate,
                            formatHour(nextH),
                          )
                          return overlapsAnyBusy(
                            slotStart,
                            slotEnd,
                            [
                              {
                                startTime: selectedRange.startIso,
                                endTime: selectedRange.endIso,
                              },
                            ],
                          )
                        })()
                      return (
                        <span
                          key={h}
                          title={taken ? "Zajęte" : "Wolne"}
                          className={cn(
                            "inline-flex min-w-10 flex-col items-center rounded-md border px-1.5 py-1 text-[10px] leading-tight",
                            taken
                              ? "border-destructive/40 bg-destructive/15 text-destructive"
                              : inSelection
                                ? "border-primary/50 bg-primary/15 text-foreground"
                                : "border-border bg-muted/40 text-muted-foreground",
                          )}
                        >
                          <span className="font-medium">{h.slice(0, 2)}</span>
                          <span>{taken ? "zajęte" : "wolne"}</span>
                        </span>
                      )
                    })}
                  </div>
                  {busy.length > 0 ? (
                    <ul className="text-xs text-muted-foreground space-y-0.5">
                      {busy.map((b) => (
                        <li key={`${b.startTime}-${b.endTime}`}>
                          Zarezerwowane: {formatWarsawTimeRange(b.startTime, b.endTime)}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-muted-foreground">
                      Brak rezerwacji w tym dniu — wszystkie godziny wolne.
                    </p>
                  )}
                </div>
              )}
            </Field>

            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel>Od</FieldLabel>
                <Select
                  value={startTime}
                  onValueChange={(value) => {
                    setStartTime(value)
                    setFormError(null)
                    if (bookRoom) {
                      const ends = freeEndHours(bookRoom, day, value, busy)
                      if (!ends.includes(endTime)) {
                        setEndTime(ends[0] ?? endTime)
                      }
                    }
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {startOptions.length === 0 ? (
                      <SelectItem value={startTime} disabled>
                        Brak wolnych godzin
                      </SelectItem>
                    ) : (
                      startOptions.map((h) => (
                        <SelectItem key={h} value={h}>
                          {h}
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>Do</FieldLabel>
                <Select
                  value={endTime}
                  onValueChange={(value) => {
                    setEndTime(value)
                    setFormError(null)
                  }}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {endOptions.length === 0 ? (
                      <SelectItem value={endTime} disabled>
                        Brak wolnego końca
                      </SelectItem>
                    ) : (
                      endOptions.map((h) => (
                        <SelectItem key={`end-${h}`} value={h}>
                          {h}
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <p className="text-xs text-muted-foreground">
              Tylko pełne godziny. Lista „Do” kończy się przed pierwszą zajętą godziną.
              {bookRoom?.spansMidnight
                ? " Gdy „Do” jest wcześniejsze niż „Od”, koniec jest następnego dnia (Chillout)."
                : null}
            </p>
            {selectionConflicts ? (
              <p className="text-sm text-destructive">
                Ten przedział nachodzi na istniejącą rezerwację — wybierz inne godziny.
              </p>
            ) : null}
            {myActiveSameDay ? (
              <p className="text-sm text-destructive">
                Masz już aktywną rezerwację tego dnia ({myActiveSameDay.roomName},{" "}
                {formatWarsawTimeRange(
                  myActiveSameDay.startTime,
                  myActiveSameDay.endTime,
                )}
                ). Drugą możesz złożyć dopiero po zakończeniu pierwszej.
              </p>
            ) : null}
            <Field>
              <FieldLabel htmlFor="rb-part">Liczba uczestników</FieldLabel>
              <Input
                id="rb-part"
                type="number"
                min={1}
                max={bookRoom?.maxCapacity ?? 1}
                value={participants}
                onChange={(e) => setParticipants(Number(e.target.value) || 1)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="rb-purpose">Cel rezerwacji</FieldLabel>
              <Textarea
                id="rb-purpose"
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                maxLength={255}
                rows={3}
                placeholder="np. spotkanie koła naukowego"
              />
            </Field>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={termsAccepted}
                onChange={(e) => setTermsAccepted(e.target.checked)}
              />
              <span>
                Akceptuję Regulamin salek tematycznych, Regulamin OS PK oraz odpowiedzialność
                materialną Organizatora.
              </span>
            </label>
            {formError ? (
              <Field data-invalid>
                <FieldError>{formError}</FieldError>
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setBookRoom(null)}
              disabled={isBooking}
            >
              Anuluj
            </Button>
            <Button
              type="button"
              disabled={
                isBooking ||
                selectionConflicts ||
                Boolean(myActiveSameDay) ||
                startOptions.length === 0
              }
              onClick={() => void submitBooking()}
            >
              {isBooking ? "Rezerwuję…" : "Potwierdź rezerwację"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(cancelTarget)}
        onOpenChange={(open) => {
          if (!open) setCancelTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Anulować rezerwację?</DialogTitle>
            <DialogDescription>
              {cancelTarget
                ? `${cancelTarget.roomName} — ${formatWarsawDateTime(cancelTarget.startTime)}`
                : null}
            </DialogDescription>
          </DialogHeader>
          {cancelMutation.isError ? (
            <p className="text-sm text-destructive">
              {getApiErrorMessage(cancelMutation.error, "Nie udało się anulować")}
            </p>
          ) : null}
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
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate(cancelTarget!.id)}
            >
              {cancelMutation.isPending ? "Anuluję…" : "Anuluj rezerwację"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
