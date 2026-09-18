import { useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  activateResident,
  listPendingResidents,
  rejectResident,
} from '@/api/admin'
import {
  createThematicRoom,
  listAdminThematicRooms,
  updateThematicRoom,
} from '@/api/admin-rooms'
import {
  createAdminEvent,
  deleteAdminEvent,
  listAdminEvents,
  updateAdminEvent,
} from '@/api/admin-events'
import { logout } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import type {
  CreateDormEventRequest,
  CreateThematicRoomRequest,
  DormEvent,
  PendingResident,
  ThematicRoom,
  UserProfile,
} from '@/api/types'
import { clearAuthTokens, getRefreshToken } from '@/lib/auth-storage'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'

type Section = 'residents' | 'rooms' | 'events'

function initials(firstName: string, lastName: string) {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

function formatTime(value: string): string {
  return value.length >= 5 ? value.slice(0, 5) : value
}

/** HTML time inputs give HH:mm; API expects HH:mm:ss. */
function toApiTime(value: string): string {
  const trimmed = value.trim()
  if (/^\d{2}:\d{2}:\d{2}$/.test(trimmed)) return trimmed
  if (/^\d{2}:\d{2}$/.test(trimmed)) return `${trimmed}:00`
  return trimmed
}

function emptyEventForm(): CreateDormEventRequest {
  return {
    title: '',
    description: '',
    priority: 'INFO',
    eventDate: new Date().toISOString().slice(0, 16),
    endDate: '',
  }
}

function toIsoFromLocalInput(value: string): string {
  if (!value) return new Date().toISOString()
  return new Date(value).toISOString()
}

function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('pl-PL', {
      dateStyle: 'short',
      timeStyle: 'short',
    })
  } catch {
    return iso
  }
}

function toLocalInputValue(iso: string | null | undefined): string {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return ''
  }
}

const emptyRoomForm: CreateThematicRoomRequest = {
  name: '',
  maxCapacity: 10,
  openingTime: '06:00',
  closingTime: '23:30',
  maxDurationHours: 4,
  description: '',
}

export function AdminPage() {
  const user = useOutletContext<UserProfile>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [section, setSection] = useState<Section>('residents')
  const [activateTarget, setActivateTarget] = useState<PendingResident | null>(null)
  const [rejectTarget, setRejectTarget] = useState<PendingResident | null>(null)
  const [roomNumber, setRoomNumber] = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const [roomDialogOpen, setRoomDialogOpen] = useState(false)
  const [editingRoom, setEditingRoom] = useState<ThematicRoom | null>(null)
  const [roomForm, setRoomForm] = useState<CreateThematicRoomRequest>(emptyRoomForm)

  const [eventDialogOpen, setEventDialogOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState<DormEvent | null>(null)
  const [eventForm, setEventForm] = useState<CreateDormEventRequest>(emptyEventForm)

  const pendingQuery = useQuery({
    queryKey: ['admin', 'pending-residents'],
    queryFn: listPendingResidents,
  })

  const roomsQuery = useQuery({
    queryKey: ['admin', 'thematic-rooms'],
    queryFn: listAdminThematicRooms,
    enabled: section === 'rooms',
  })

  const eventsQuery = useQuery({
    queryKey: ['admin', 'events'],
    queryFn: listAdminEvents,
    enabled: section === 'events',
  })

  const activateMutation = useMutation({
    mutationFn: () =>
      activateResident(activateTarget!.id, {
        roomNumber: roomNumber.trim() || undefined,
      }),
    onSuccess: async () => {
      setActivateTarget(null)
      setRoomNumber('')
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'pending-residents'] })
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, 'Activation failed'))
    },
  })

  const rejectMutation = useMutation({
    mutationFn: () =>
      rejectResident(rejectTarget!.id, { reason: rejectReason.trim() }),
    onSuccess: async () => {
      setRejectTarget(null)
      setRejectReason('')
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'pending-residents'] })
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, 'Rejection failed'))
    },
  })

  const saveRoomMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateThematicRoomRequest = {
        name: roomForm.name.trim(),
        maxCapacity: roomForm.maxCapacity,
        openingTime: toApiTime(roomForm.openingTime),
        closingTime: toApiTime(roomForm.closingTime),
        maxDurationHours: roomForm.maxDurationHours,
        description: roomForm.description || undefined,
        status: roomForm.status,
      }
      if (editingRoom) {
        return updateThematicRoom(editingRoom.id, {
          ...payload,
          description: roomForm.description || null,
        })
      }
      return createThematicRoom(payload)
    },
    onSuccess: async () => {
      setRoomDialogOpen(false)
      setEditingRoom(null)
      setRoomForm(emptyRoomForm)
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'thematic-rooms'] })
      await queryClient.invalidateQueries({ queryKey: ['rooms', 'catalog'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Save failed')),
  })

  const toggleMaintenanceMutation = useMutation({
    mutationFn: (room: ThematicRoom) =>
      updateThematicRoom(room.id, {
        status: room.status === 'AVAILABLE' ? 'MAINTENANCE' : 'AVAILABLE',
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'thematic-rooms'] })
      await queryClient.invalidateQueries({ queryKey: ['rooms', 'catalog'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Update failed')),
  })

  const saveEventMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateDormEventRequest = {
        title: eventForm.title.trim(),
        description: eventForm.description.trim(),
        priority: eventForm.priority,
        eventDate: toIsoFromLocalInput(eventForm.eventDate),
        endDate: eventForm.endDate ? toIsoFromLocalInput(eventForm.endDate) : null,
      }
      if (editingEvent) {
        return updateAdminEvent(editingEvent.id, payload)
      }
      return createAdminEvent(payload)
    },
    onSuccess: async () => {
      setEventDialogOpen(false)
      setEditingEvent(null)
      setEventForm(emptyEventForm())
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      await queryClient.invalidateQueries({ queryKey: ['events'] })
      await queryClient.invalidateQueries({ queryKey: ['events', 'banner'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Save failed')),
  })

  const deleteEventMutation = useMutation({
    mutationFn: (id: string) => deleteAdminEvent(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      await queryClient.invalidateQueries({ queryKey: ['events'] })
      await queryClient.invalidateQueries({ queryKey: ['events', 'banner'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Delete failed')),
  })

  async function handleLogout() {
    try {
      await logout(getRefreshToken())
    } catch {
      // ignore logout API errors — clear local session anyway
    }
    clearAuthTokens()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  function openCreateRoom() {
    setEditingRoom(null)
    setRoomForm(emptyRoomForm)
    setActionError(null)
    setRoomDialogOpen(true)
  }

  function openEditRoom(room: ThematicRoom) {
    setEditingRoom(room)
    setRoomForm({
      name: room.name,
      maxCapacity: room.maxCapacity,
      openingTime: formatTime(room.openingTime),
      closingTime: formatTime(room.closingTime),
      maxDurationHours: room.maxDurationHours,
      description: room.description ?? '',
      status: room.status,
    })
    setActionError(null)
    setRoomDialogOpen(true)
  }

  function openCreateEvent() {
    setEditingEvent(null)
    setEventForm(emptyEventForm())
    setActionError(null)
    setEventDialogOpen(true)
  }

  function openEditEvent(event: DormEvent) {
    setEditingEvent(event)
    setEventForm({
      title: event.title,
      description: event.description,
      priority: event.priority,
      eventDate: toLocalInputValue(event.eventDate),
      endDate: toLocalInputValue(event.endDate),
    })
    setActionError(null)
    setEventDialogOpen(true)
  }

  const pending = pendingQuery.data ?? []
  const rooms = roomsQuery.data ?? []
  const events = eventsQuery.data ?? []

  return (
    <main className="flex min-h-svh w-full items-start justify-center p-6 md:p-10">
      <div className="flex w-full max-w-4xl flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-2xl font-medium tracking-tight">
              <span className="uppercase">Pk</span>ampus
            </p>
            <p className="text-sm text-muted-foreground">
              Panel ADS — {user.dormitoryName ?? 'Twój akademik'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{user.role}</Badge>
            <Button variant="outline" type="button" onClick={() => void handleLogout()}>
              Wyloguj
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant={section === 'residents' ? 'default' : 'outline'}
            onClick={() => setSection('residents')}
          >
            Meldunki
          </Button>
          <Button
            type="button"
            size="sm"
            variant={section === 'rooms' ? 'default' : 'outline'}
            onClick={() => setSection('rooms')}
          >
            Salki
          </Button>
          <Button
            type="button"
            size="sm"
            variant={section === 'events' ? 'default' : 'outline'}
            onClick={() => setSection('events')}
          >
            Komunikaty
          </Button>
        </div>

        {section === 'residents' && (
          <Card>
            <CardHeader>
              <CardTitle>Oczekujące meldunki</CardTitle>
              <CardDescription>
                Wnioski PENDING_APPROVAL dla {user.dormitoryName ?? 'Twojego DS'}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {pendingQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Ładowanie…</p>
              ) : pendingQuery.isError ? (
                <p className="text-sm text-destructive">
                  {getApiErrorMessage(pendingQuery.error)}
                </p>
              ) : pending.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Brak oczekujących wniosków.
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Mieszkaniec</TableHead>
                      <TableHead>Pokój</TableHead>
                      <TableHead>Telefon</TableHead>
                      <TableHead className="text-right">Akcje</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pending.map((resident) => (
                      <TableRow key={resident.id}>
                        <TableCell>
                          <div className="flex items-center gap-3">
                            <Avatar>
                              {resident.avatarUrl ? (
                                <AvatarImage
                                  src={resident.avatarUrl}
                                  alt={`${resident.firstName} ${resident.lastName}`}
                                />
                              ) : null}
                              <AvatarFallback>
                                {initials(resident.firstName, resident.lastName)}
                              </AvatarFallback>
                            </Avatar>
                            <div className="min-w-0">
                              <p className="truncate font-medium">
                                {resident.firstName} {resident.lastName}
                              </p>
                              <p className="truncate text-muted-foreground text-sm">
                                {resident.email}
                              </p>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>{resident.declaredRoomNumber}</TableCell>
                        <TableCell>{resident.phoneNumber}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              type="button"
                              size="sm"
                              onClick={() => {
                                setActionError(null)
                                setRoomNumber(resident.declaredRoomNumber)
                                setActivateTarget(resident)
                              }}
                            >
                              Akceptuj
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setActionError(null)
                                setRejectReason('')
                                setRejectTarget(resident)
                              }}
                            >
                              Odrzuć
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        )}

        {section === 'rooms' && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>Salki tematyczne</CardTitle>
                <CardDescription>
                  Konfiguracja salek w {user.dormitoryName ?? 'Twoim DS'}
                </CardDescription>
              </div>
              <Button type="button" onClick={openCreateRoom}>
                Dodaj salkę
              </Button>
            </CardHeader>
            <CardContent>
              {roomsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Ładowanie…</p>
              ) : roomsQuery.isError ? (
                <p className="text-sm text-destructive">
                  {getApiErrorMessage(roomsQuery.error)}
                </p>
              ) : rooms.length === 0 ? (
                <p className="text-sm text-muted-foreground">Brak salek — dodaj pierwszą.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nazwa</TableHead>
                      <TableHead>Pojemność</TableHead>
                      <TableHead>Godziny</TableHead>
                      <TableHead>Max h</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Akcje</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rooms.map((room) => (
                      <TableRow key={room.id}>
                        <TableCell className="font-medium">{room.name}</TableCell>
                        <TableCell>{room.maxCapacity}</TableCell>
                        <TableCell>
                          {formatTime(room.openingTime)}–{formatTime(room.closingTime)}
                          {room.spansMidnight ? ' (+1)' : ''}
                        </TableCell>
                        <TableCell>{room.maxDurationHours} h</TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              room.status === 'AVAILABLE' ? 'secondary' : 'destructive'
                            }
                          >
                            {room.status === 'AVAILABLE' ? 'Dostępna' : 'Remont'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right space-x-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => openEditRoom(room)}
                          >
                            Edytuj
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            disabled={toggleMaintenanceMutation.isPending}
                            onClick={() => toggleMaintenanceMutation.mutate(room)}
                          >
                            {room.status === 'AVAILABLE' ? 'Remont' : 'Przywróć'}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>              )}
            </CardContent>
          </Card>
        )}

        {section === 'events' && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>Komunikaty DS</CardTitle>
                <CardDescription>
                  Oficjalne ogłoszenia dla {user.dormitoryName ?? 'Twojego DS'}
                </CardDescription>
              </div>
              <Button type="button" onClick={openCreateEvent}>
                Nowy komunikat
              </Button>
            </CardHeader>
            <CardContent>
              {eventsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Ładowanie…</p>
              ) : eventsQuery.isError ? (
                <p className="text-sm text-destructive">
                  {getApiErrorMessage(eventsQuery.error)}
                </p>
              ) : events.length === 0 ? (
                <p className="text-sm text-muted-foreground">Brak komunikatów.</p>
              ) : (
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
                          <div className="min-w-0">
                            <p className="font-medium">{event.title}</p>
                            <p className="truncate text-sm text-muted-foreground">
                              {event.description}
                            </p>
                          </div>
                        </TableCell>
                        <TableCell>{event.priority}</TableCell>
                        <TableCell>{formatWhen(event.eventDate)}</TableCell>
                        <TableCell className="text-right space-x-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => openEditEvent(event)}
                          >
                            Edytuj
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            disabled={deleteEventMutation.isPending}
                            onClick={() => deleteEventMutation.mutate(event.id)}
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
        )}
      </div>

      <Dialog
        open={Boolean(activateTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setActivateTarget(null)
            setActionError(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Akceptuj meldunek</DialogTitle>
            <DialogDescription>
              {activateTarget
                ? `Aktywuj ${activateTarget.firstName} ${activateTarget.lastName}. Możesz zmienić numer pokoju.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="roomNumber">Numer pokoju</FieldLabel>
              <Input
                id="roomNumber"
                value={roomNumber}
                onChange={(e) => setRoomNumber(e.target.value)}
                maxLength={10}
              />
            </Field>
            {actionError ? (
              <Field data-invalid>
                <FieldError>{actionError}</FieldError>
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setActivateTarget(null)}
              disabled={activateMutation.isPending}
            >
              Anuluj
            </Button>
            <Button
              type="button"
              onClick={() => activateMutation.mutate()}
              disabled={activateMutation.isPending}
            >
              {activateMutation.isPending ? 'Akceptowanie…' : 'Akceptuj'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(rejectTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setRejectTarget(null)
            setActionError(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Odrzuć wniosek</DialogTitle>
            <DialogDescription>
              {rejectTarget
                ? `Odrzuć ${rejectTarget.firstName} ${rejectTarget.lastName}. Powód zostanie wysłany e-mailem.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="rejectReason">Powód</FieldLabel>
              <Textarea
                id="rejectReason"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                maxLength={1000}
                rows={4}
              />
            </Field>
            {actionError ? (
              <Field data-invalid>
                <FieldError>{actionError}</FieldError>
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setRejectTarget(null)}
              disabled={rejectMutation.isPending}
            >
              Anuluj
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => rejectMutation.mutate()}
              disabled={rejectMutation.isPending || rejectReason.trim().length === 0}
            >
              {rejectMutation.isPending ? 'Odrzucanie…' : 'Odrzuć'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={roomDialogOpen} onOpenChange={setRoomDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingRoom ? 'Edytuj salkę' : 'Nowa salka'}</DialogTitle>
            <DialogDescription>
              Ustaw pojemność, godziny otwarcia oraz maksymalny czas rezerwacji. Gdy zamknięcie
              jest wcześniejsze niż otwarcie, system uzna przejście przez północ.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="tr-name">Nazwa</FieldLabel>
              <Input
                id="tr-name"
                value={roomForm.name}
                onChange={(e) => setRoomForm((f) => ({ ...f, name: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="tr-cap">Max osób</FieldLabel>
              <Input
                id="tr-cap"
                type="number"
                min={1}
                value={roomForm.maxCapacity}
                onChange={(e) =>
                  setRoomForm((f) => ({
                    ...f,
                    maxCapacity: Number(e.target.value) || 1,
                  }))
                }
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="tr-open">Otwarcie</FieldLabel>
                <Input
                  id="tr-open"
                  type="time"
                  value={formatTime(roomForm.openingTime)}
                  onChange={(e) =>
                    setRoomForm((f) => ({ ...f, openingTime: e.target.value }))
                  }
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="tr-close">Zamknięcie</FieldLabel>
                <Input
                  id="tr-close"
                  type="time"
                  value={formatTime(roomForm.closingTime)}
                  onChange={(e) =>
                    setRoomForm((f) => ({ ...f, closingTime: e.target.value }))
                  }
                />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="tr-max-h">Max czas rezerwacji (h)</FieldLabel>
              <Input
                id="tr-max-h"
                type="number"
                min={1}
                max={24}
                value={roomForm.maxDurationHours}
                onChange={(e) =>
                  setRoomForm((f) => ({
                    ...f,
                    maxDurationHours: Number(e.target.value) || 1,
                  }))
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="tr-desc">Opis</FieldLabel>
              <Textarea
                id="tr-desc"
                value={roomForm.description ?? ''}
                onChange={(e) =>
                  setRoomForm((f) => ({ ...f, description: e.target.value }))
                }
                rows={3}
              />
            </Field>
            {actionError ? <FieldError>{actionError}</FieldError> : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              disabled={saveRoomMutation.isPending || !roomForm.name.trim()}
              onClick={() => saveRoomMutation.mutate()}
            >
              Zapisz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={eventDialogOpen} onOpenChange={setEventDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingEvent ? 'Edytuj komunikat' : 'Nowy komunikat DS'}
            </DialogTitle>
            <DialogDescription>
              Komunikaty ADS są zawsze przypięte. Priorytet CRITICAL pokazuje się jako baner u
              mieszkańców.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="evt-title">Tytuł</FieldLabel>
              <Input
                id="evt-title"
                value={eventForm.title}
                onChange={(e) => setEventForm((f) => ({ ...f, title: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="evt-desc">Treść</FieldLabel>
              <Textarea
                id="evt-desc"
                value={eventForm.description}
                onChange={(e) =>
                  setEventForm((f) => ({ ...f, description: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel>Priorytet</FieldLabel>
              <Select
                value={eventForm.priority}
                onValueChange={(value) =>
                  setEventForm((f) => ({
                    ...f,
                    priority: value as CreateDormEventRequest['priority'],
                  }))
                }
              >
                <SelectTrigger>
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
              <FieldLabel htmlFor="evt-start">Data od</FieldLabel>
              <Input
                id="evt-start"
                type="datetime-local"
                value={eventForm.eventDate}
                onChange={(e) =>
                  setEventForm((f) => ({ ...f, eventDate: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="evt-end">Data do (opcjonalnie)</FieldLabel>
              <Input
                id="evt-end"
                type="datetime-local"
                value={eventForm.endDate ?? ''}
                onChange={(e) =>
                  setEventForm((f) => ({ ...f, endDate: e.target.value }))
                }
              />
            </Field>
            {actionError ? <FieldError>{actionError}</FieldError> : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              disabled={
                saveEventMutation.isPending ||
                !eventForm.title.trim() ||
                !eventForm.description.trim()
              }
              onClick={() => saveEventMutation.mutate()}
            >
              Zapisz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}
