import { useState } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Megaphone } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  createReceptionistEvent,
  deleteReceptionistEvent,
  listReceptionistEvents,
  updateReceptionistEvent,
} from "@/api/receptionist"
import type {
  CreateDormEventRequest,
  DormEvent,
  UserProfile,
} from "@/api/types"
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

function emptyEventForm(): CreateDormEventRequest {
  return {
    title: "",
    description: "",
    priority: "INFO",
    eventDate: new Date().toISOString().slice(0, 16),
    endDate: "",
  }
}

function toIsoFromLocalInput(value: string): string {
  if (!value) return new Date().toISOString()
  return new Date(value).toISOString()
}

function toLocalInputValue(iso: string | null | undefined): string {
  if (!iso) return ""
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, "0")
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return ""
  }
}

function formatWhen(iso: string | null | undefined): string {
  if (!iso) return "—"
  try {
    return new Date(iso).toLocaleString("pl-PL", {
      dateStyle: "short",
      timeStyle: "short",
    })
  } catch {
    return iso
  }
}

function priorityLabel(p: string): string {
  switch (p) {
    case "WARNING":
      return "Ostrzeżenie"
    case "CRITICAL":
      return "Krytyczny"
    default:
      return "Informacja"
  }
}

export function ReceptionistEventsPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<DormEvent | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DormEvent | null>(null)
  const [form, setForm] = useState<CreateDormEventRequest>(emptyEventForm())
  const [actionError, setActionError] = useState<string | null>(null)

  const eventsQuery = useQuery({
    queryKey: ["receptionist", "events"],
    queryFn: listReceptionistEvents,
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["receptionist", "events"] }),
      queryClient.invalidateQueries({ queryKey: ["events"] }),
      queryClient.invalidateQueries({ queryKey: ["events", "banner"] }),
    ])
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload: CreateDormEventRequest = {
        title: form.title.trim(),
        description: form.description.trim(),
        priority: form.priority,
        eventDate: toIsoFromLocalInput(form.eventDate),
        endDate: form.endDate ? toIsoFromLocalInput(form.endDate) : null,
      }
      if (editing) {
        return updateReceptionistEvent(editing.id, payload)
      }
      return createReceptionistEvent(payload)
    },
    onSuccess: async () => {
      setDialogOpen(false)
      setEditing(null)
      setForm(emptyEventForm())
      setActionError(null)
      await invalidate()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się zapisać komunikatu"))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteReceptionistEvent(id),
    onSuccess: async () => {
      setDeleteTarget(null)
      setActionError(null)
      await invalidate()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się usunąć komunikatu"))
    },
  })

  const openCreate = () => {
    setEditing(null)
    setForm(emptyEventForm())
    setDialogOpen(true)
  }

  const openEdit = (event: DormEvent) => {
    setEditing(event)
    setForm({
      title: event.title,
      description: event.description,
      priority: event.priority,
      eventDate: toLocalInputValue(event.eventDate),
      endDate: toLocalInputValue(event.endDate),
    })
    setDialogOpen(true)
  }

  const events = eventsQuery.data ?? []

  return (
    <div className="space-y-6">
      <div>
        <Button type="button" variant="ghost" size="sm" asChild className="-ml-2 mb-1">
          <Link to="/receptionist">
            <ArrowLeft className="size-3.5 mr-1" />
            Pulpit
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold tracking-tight flex items-center gap-2">
          <Megaphone className="size-5 text-primary" />
          Komunikaty
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          {user.dormitoryName ?? "Twój DS"} — oficjalne ogłoszenia portierni
        </p>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}

      <Card className="border-border/70 shadow-none">
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle className="text-base">Lista komunikatów</CardTitle>
            <CardDescription>
              CRITICAL + przypięcie = baner u mieszkańców
            </CardDescription>
          </div>
          <Button type="button" onClick={openCreate}>
            Nowy komunikat
          </Button>
        </CardHeader>
        <CardContent>
          {eventsQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Ładowanie…</p>
          )}
          {eventsQuery.isError && (
            <p className="text-sm text-destructive">
              {getApiErrorMessage(eventsQuery.error)}
            </p>
          )}
          {events.length === 0 && !eventsQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Brak komunikatów.</p>
          )}
          {events.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tytuł</TableHead>
                  <TableHead>Priorytet</TableHead>
                  <TableHead>Termin</TableHead>
                  <TableHead className="text-right">Akcje</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell>
                      <p className="font-medium">{event.title}</p>
                      <p className="truncate text-sm text-muted-foreground max-w-xs">
                        {event.description}
                      </p>
                    </TableCell>
                    <TableCell>{priorityLabel(event.priority)}</TableCell>
                    <TableCell>{formatWhen(event.eventDate)}</TableCell>
                    <TableCell className="text-right space-x-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => openEdit(event)}
                      >
                        Edytuj
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="destructive"
                        disabled={deleteMutation.isPending}
                        onClick={() => setDeleteTarget(event)}
                      >
                        Usuń
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editing ? "Edytuj komunikat" : "Nowy komunikat DS"}
            </DialogTitle>
            <DialogDescription>
              Komunikaty są przypięte. Priorytet CRITICAL pokazuje się jako baner.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>Tytuł</FieldLabel>
              <Input
                value={form.title}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel>Treść</FieldLabel>
              <Textarea
                value={form.description}
                onChange={(e) =>
                  setForm((f) => ({ ...f, description: e.target.value }))
                }
                rows={4}
              />
            </Field>
            <Field>
              <FieldLabel>Priorytet</FieldLabel>
              <Select
                value={form.priority}
                onValueChange={(value) =>
                  setForm((f) => ({
                    ...f,
                    priority: value as CreateDormEventRequest["priority"],
                  }))
                }
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="INFO">Informacja</SelectItem>
                  <SelectItem value="WARNING">Ostrzeżenie</SelectItem>
                  <SelectItem value="CRITICAL">Krytyczny</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>Data od</FieldLabel>
              <Input
                type="datetime-local"
                value={form.eventDate}
                onChange={(e) =>
                  setForm((f) => ({ ...f, eventDate: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel>Data do (wygaśnięcie)</FieldLabel>
              <Input
                type="datetime-local"
                value={form.endDate ?? ""}
                onChange={(e) =>
                  setForm((f) => ({ ...f, endDate: e.target.value }))
                }
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
              Anuluj
            </Button>
            <Button
              type="button"
              disabled={
                saveMutation.isPending ||
                !form.title.trim() ||
                !form.description.trim()
              }
              onClick={() => saveMutation.mutate()}
            >
              Zapisz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteTarget != null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Usunąć komunikat?</DialogTitle>
            <DialogDescription>
              {deleteTarget
                ? `„${deleteTarget.title}” zniknie z widoku mieszkańców.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeleteTarget(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={deleteMutation.isPending || !deleteTarget}
              onClick={() => {
                if (deleteTarget) deleteMutation.mutate(deleteTarget.id)
              }}
            >
              Usuń komunikat
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
