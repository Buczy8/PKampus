import { useMemo, useState } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, ArrowLeft, Waves } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  cancelReceptionistLaundryBooking,
  getReceptionistLaundrySchedule,
  reportLaundryMachineBreakdown,
  restoreLaundryMachine,
} from "@/api/receptionist"
import type {
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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "cn"
import {
  formatDayChipLabel,
  formatWarsawTimeRange,
  warsawDayRange,
} from "@/lib/laundry-dates"

export function ReceptionistLaundryPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const days = useMemo(() => warsawDayRange(7), [])
  const rangeFrom = days[0]
  const rangeTo = days[days.length - 1]
  const [selectedDay, setSelectedDay] = useState(days[0])

  const [cancelSlot, setCancelSlot] = useState<LaundrySlot | null>(null)
  const [breakdownMachine, setBreakdownMachine] = useState<LaundryMachine | null>(
    null,
  )
  const [breakdownReason, setBreakdownReason] = useState("")
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  const scheduleQuery = useQuery({
    queryKey: ["receptionist", "laundry", "schedule", rangeFrom, rangeTo],
    queryFn: () => getReceptionistLaundrySchedule(rangeFrom, rangeTo),
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ["receptionist", "laundry", "schedule"],
      }),
      queryClient.invalidateQueries({ queryKey: ["receptionist", "desk"] }),
    ])
  }

  const cancelMutation = useMutation({
    mutationFn: (id: string) => cancelReceptionistLaundryBooking(id),
    onSuccess: async () => {
      setCancelSlot(null)
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

  const breakdownMutation = useMutation({
    mutationFn: () =>
      reportLaundryMachineBreakdown(breakdownMachine!.id, breakdownReason),
    onSuccess: async (result) => {
      setBreakdownMachine(null)
      setBreakdownReason("")
      setActionError(null)
      setActionInfo(
        `Pralka wyłączona. Anulowano ${result.cancelledCount} przyszłych rezerwacji.`,
      )
      await invalidate()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się zgłosić awarii pralki"),
      )
    },
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => restoreLaundryMachine(id),
    onSuccess: async () => {
      setActionError(null)
      setActionInfo("Pralka przywrócona do użytku")
      await invalidate()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się przywrócić pralki"),
      )
    },
  })

  const schedule = scheduleQuery.data
  const machines = schedule?.machines ?? []
  const daySlots =
    schedule?.days.find((d) => d.date === selectedDay)?.slots ?? []

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
            <Waves className="size-5 text-primary" />
            Pralnia — moderacja
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            {user.dormitoryName ?? "Twój DS"} — grafik, anulowanie slotów i awarie
            pralek
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
            "Nie udało się pobrać grafiku pralni",
          )}
        </p>
      )}

      {schedule && machines.length === 0 && (
        <p className="text-sm text-muted-foreground">Brak pralek w akademiku.</p>
      )}

      <div className="space-y-4">
        {machines.map((machine) => {
          const slots = daySlots
            .filter((s) => s.machineId === machine.id)
            .sort(
              (a, b) =>
                new Date(a.startTime).getTime() - new Date(b.startTime).getTime(),
            )
          const ooo = machine.status === "OUT_OF_ORDER"

          return (
            <Card key={machine.id} className="border-border/70 shadow-none">
              <CardHeader className="border-b border-border/60 pb-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <CardTitle className="text-base font-medium flex items-center gap-2">
                      {machine.identifier}
                      {ooo && (
                        <Badge variant="destructive">Wyłączona</Badge>
                      )}
                    </CardTitle>
                    <CardDescription>{machine.floorLocation}</CardDescription>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {ooo ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={restoreMutation.isPending}
                        onClick={() => {
                          setActionError(null)
                          setActionInfo(null)
                          restoreMutation.mutate(machine.id)
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
                          setBreakdownReason("")
                          setBreakdownMachine(machine)
                        }}
                      >
                        <AlertTriangle className="size-3.5 mr-1.5" />
                        Zgłoś awarię
                      </Button>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardContent className="pt-4">
                {ooo ? (
                  <p className="text-sm text-muted-foreground">
                    Pralka wyłączona — nowe rezerwacje zablokowane. Przywróć po
                    naprawie.
                  </p>
                ) : slots.length === 0 ? (
                  <p className="text-sm text-muted-foreground">Brak slotów w tym dniu.</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {slots.map((slot) => (
                      <StaffSlotButton
                        key={`${slot.machineId}-${slot.startTime}`}
                        slot={slot}
                        onCancel={() => {
                          setActionError(null)
                          setActionInfo(null)
                          setCancelSlot(slot)
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
        open={Boolean(cancelSlot)}
        onOpenChange={(open) => {
          if (!open) setCancelSlot(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Anulować rezerwację?</DialogTitle>
            <DialogDescription>
              {cancelSlot && (
                <>
                  Slot {formatWarsawTimeRange(cancelSlot.startTime, cancelSlot.endTime)}
                  {cancelSlot.residentLabel
                    ? ` — ${cancelSlot.residentLabel}`
                    : ""}
                  . Slot zostanie zwolniony dla innych mieszkańców.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setCancelSlot(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={
                cancelMutation.isPending ||
                !cancelSlot?.bookingId ||
                cancelSlot.bookingStatus !== "CONFIRMED"
              }
              onClick={() => {
                if (cancelSlot?.bookingId) {
                  cancelMutation.mutate(cancelSlot.bookingId)
                }
              }}
            >
              Anuluj rezerwację
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(breakdownMachine)}
        onOpenChange={(open) => {
          if (!open) {
            setBreakdownMachine(null)
            setBreakdownReason("")
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Zgłoś awarię pralki</DialogTitle>
            <DialogDescription>
              {breakdownMachine?.identifier} zostanie wyłączona. Przyszłe
              potwierdzone rezerwacje zostaną anulowane, a system utworzy
              zgłoszenie usterki.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="breakdown-reason">Powód awarii</FieldLabel>
              <Textarea
                id="breakdown-reason"
                value={breakdownReason}
                onChange={(e) => setBreakdownReason(e.target.value)}
                placeholder="Np. wyciek wody, nie wiruje…"
                rows={3}
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setBreakdownMachine(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={
                breakdownMutation.isPending || breakdownReason.trim().length === 0
              }
              onClick={() => breakdownMutation.mutate()}
            >
              Wyłącz pralkę
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function StaffSlotButton({
  slot,
  onCancel,
}: {
  slot: LaundrySlot
  onCancel: () => void
}) {
  const timeLabel = formatWarsawTimeRange(slot.startTime, slot.endTime)
  const canCancel =
    slot.state === "OCCUPIED" &&
    slot.bookingStatus === "CONFIRMED" &&
    Boolean(slot.bookingId)

  if (slot.state === "FREE") {
    return (
      <span
        className={cn(
          "inline-flex flex-col items-start rounded-lg border border-border/60 px-3 py-2 text-xs",
          "bg-muted/30 text-muted-foreground",
        )}
      >
        <span className="font-medium text-foreground">{timeLabel}</span>
        <span>Wolny</span>
      </span>
    )
  }

  if (slot.state === "UNAVAILABLE") {
    return (
      <span
        className={cn(
          "inline-flex flex-col items-start rounded-lg border border-transparent px-3 py-2 text-xs",
          "bg-muted/40 text-muted-foreground/70",
        )}
      >
        <span>{timeLabel}</span>
        <span>Niedostępny</span>
      </span>
    )
  }

  return (
    <button
      type="button"
      disabled={!canCancel}
      onClick={onCancel}
      title={
        canCancel
          ? "Kliknij, aby anulować"
          : slot.bookingStatus === "KEY_ISSUED"
            ? "Klucz wydany — obsłuż na pulpicie"
            : undefined
      }
      className={cn(
        "inline-flex flex-col items-start rounded-lg border px-3 py-2 text-xs text-left transition-colors",
        canCancel
          ? "border-primary/40 bg-primary/5 hover:bg-primary/10 cursor-pointer"
          : "border-border/60 bg-muted/40 cursor-default",
      )}
    >
      <span className="font-medium">{timeLabel}</span>
      <span className="text-muted-foreground max-w-[11rem] truncate">
        {slot.residentLabel ?? "Zajęty"}
      </span>
      <span className="font-medium mt-0.5">
        {slot.bookingStatus === "KEY_ISSUED"
          ? "Klucz wydany"
          : canCancel
            ? "Anuluj"
            : "Zajęty"}
      </span>
    </button>
  )
}
