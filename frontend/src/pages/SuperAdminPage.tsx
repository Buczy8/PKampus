import { useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { logout } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import {
  createCampusEvent,
  createDormAdmin,
  createDormitory,
  deleteCampusEvent,
  listCampusEvents,
  listDormAdmins,
  listSuperAdminDormitories,
  updateCampusEvent,
  updateDormAdmin,
  updateDormitory,
} from '@/api/superadmin'
import type {
  CreateCampusEventRequest,
  CreateDormAdminRequest,
  CreateDormitoryRequest,
  DormAdminAccount,
  DormEvent,
  SuperAdminDormitory,
  UserProfile,
} from '@/api/types'
import { clearAuthTokens, getRefreshToken } from '@/lib/auth-storage'
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

type Section = 'dormitories' | 'admins' | 'events'

const emptyDormForm: CreateDormitoryRequest = {
  code: '',
  name: '',
  address: '',
  floorsCount: 5,
}

const emptyAdminForm: CreateDormAdminRequest = {
  firstName: '',
  lastName: '',
  email: '',
  phoneNumber: '',
  password: '',
  dormitoryId: '',
}

function emptyEventForm(): CreateCampusEventRequest {
  return {
    title: '',
    description: '',
    category: 'ADMIN_NOTICE',
    priority: 'INFO',
    pinned: true,
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

export function SuperAdminPage() {
  const user = useOutletContext<UserProfile>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [section, setSection] = useState<Section>('dormitories')
  const [actionError, setActionError] = useState<string | null>(null)

  const [dormDialogOpen, setDormDialogOpen] = useState(false)
  const [editingDorm, setEditingDorm] = useState<SuperAdminDormitory | null>(null)
  const [dormForm, setDormForm] = useState<CreateDormitoryRequest>(emptyDormForm)

  const [adminDialogOpen, setAdminDialogOpen] = useState(false)
  const [adminForm, setAdminForm] = useState<CreateDormAdminRequest>(emptyAdminForm)

  const [eventDialogOpen, setEventDialogOpen] = useState(false)
  const [editingEvent, setEditingEvent] = useState<DormEvent | null>(null)
  const [eventForm, setEventForm] = useState<CreateCampusEventRequest>(emptyEventForm)

  const dormsQuery = useQuery({
    queryKey: ['superadmin', 'dormitories'],
    queryFn: listSuperAdminDormitories,
  })
  const adminsQuery = useQuery({
    queryKey: ['superadmin', 'dorm-admins'],
    queryFn: listDormAdmins,
  })
  const eventsQuery = useQuery({
    queryKey: ['superadmin', 'events'],
    queryFn: listCampusEvents,
  })

  const dormOptions = (dormsQuery.data ?? []).map((d) => ({
    id: d.id,
    label: d.name,
  }))
  const dorms = dormsQuery.data ?? []
  const admins = adminsQuery.data ?? []
  const events = eventsQuery.data ?? []

  const saveDormMutation = useMutation({
    mutationFn: async () => {
      if (editingDorm) {
        return updateDormitory(editingDorm.id, {
          code: dormForm.code,
          name: dormForm.name,
          address: dormForm.address,
          floorsCount: dormForm.floorsCount,
        })
      }
      return createDormitory(dormForm)
    },
    onSuccess: async () => {
      setDormDialogOpen(false)
      setEditingDorm(null)
      setDormForm(emptyDormForm)
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['superadmin', 'dormitories'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Save failed')),
  })

  const saveAdminMutation = useMutation({
    mutationFn: () => createDormAdmin(adminForm),
    onSuccess: async () => {
      setAdminDialogOpen(false)
      setAdminForm(emptyAdminForm)
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['superadmin', 'dorm-admins'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Create failed')),
  })

  const toggleAdminMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'BLOCKED' }) =>
      updateDormAdmin(id, { status }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['superadmin', 'dorm-admins'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Update failed')),
  })

  const saveEventMutation = useMutation({
    mutationFn: async () => {
      const payload: CreateCampusEventRequest = {
        ...eventForm,
        category: 'ADMIN_NOTICE',
        pinned: true,
        eventDate: toIsoFromLocalInput(eventForm.eventDate),
        endDate: eventForm.endDate ? toIsoFromLocalInput(eventForm.endDate) : null,
      }
      if (editingEvent) {
        return updateCampusEvent(editingEvent.id, payload)
      }
      return createCampusEvent(payload)
    },
    onSuccess: async () => {
      setEventDialogOpen(false)
      setEditingEvent(null)
      setEventForm(emptyEventForm())
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: ['superadmin', 'events'] })
      await queryClient.invalidateQueries({ queryKey: ['events', 'banner'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Save failed')),
  })

  const deleteEventMutation = useMutation({
    mutationFn: (id: string) => deleteCampusEvent(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['superadmin', 'events'] })
      await queryClient.invalidateQueries({ queryKey: ['events', 'banner'] })
    },
    onError: (error) => setActionError(getApiErrorMessage(error, 'Delete failed')),
  })

  async function handleLogout() {
    try {
      await logout(getRefreshToken())
    } catch {
      // ignore
    }
    clearAuthTokens()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  function openCreateDorm() {
    setEditingDorm(null)
    setDormForm(emptyDormForm)
    setActionError(null)
    setDormDialogOpen(true)
  }

  function openEditDorm(dorm: SuperAdminDormitory) {
    setEditingDorm(dorm)
    setDormForm({
      code: dorm.code,
      name: dorm.name,
      address: dorm.address,
      floorsCount: dorm.floorsCount,
    })
    setActionError(null)
    setDormDialogOpen(true)
  }

  function openCreateAdmin() {
    setAdminForm({
      ...emptyAdminForm,
      dormitoryId: dorms[0]?.id ?? '',
    })
    setActionError(null)
    setAdminDialogOpen(true)
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
      category: 'ADMIN_NOTICE',
      priority: event.priority,
      pinned: true,
      eventDate: new Date(event.eventDate).toISOString().slice(0, 16),
      endDate: event.endDate ? new Date(event.endDate).toISOString().slice(0, 16) : '',
    })
    setActionError(null)
    setEventDialogOpen(true)
  }

  return (
    <main className="flex min-h-svh w-full items-start justify-center p-6 md:p-10">
      <div className="flex w-full max-w-5xl flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-2xl font-medium tracking-tight">
              <span className="uppercase">Pk</span>ampus
            </p>
            <p className="text-sm text-muted-foreground">Panel Super Admina (AOS)</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{user.role}</Badge>
            <Button variant="outline" type="button" onClick={() => void handleLogout()}>
              Wyloguj
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {(
            [
              ['dormitories', 'Akademiki'],
              ['admins', 'Kierownicy ADS'],
              ['events', 'Komunikaty'],
            ] as const
          ).map(([id, label]) => (
            <Button
              key={id}
              type="button"
              size="sm"
              variant={section === id ? 'default' : 'outline'}
              onClick={() => setSection(id)}
            >
              {label}
            </Button>
          ))}
        </div>

        {actionError ? (
          <p className="text-sm text-destructive">{actionError}</p>
        ) : null}

        {section === 'dormitories' && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>Akademiki</CardTitle>
                <CardDescription>Dodawanie i edycja obiektów DS</CardDescription>
              </div>
              <Button type="button" onClick={openCreateDorm}>
                Dodaj akademik
              </Button>
            </CardHeader>
            <CardContent>
              {dormsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Ładowanie…</p>
              ) : dormsQuery.isError ? (
                <p className="text-sm text-destructive">
                  {getApiErrorMessage(dormsQuery.error)}
                </p>
              ) : dorms.length === 0 ? (
                <p className="text-sm text-muted-foreground">Brak akademików.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Kod</TableHead>
                      <TableHead>Nazwa</TableHead>
                      <TableHead>Adres</TableHead>
                      <TableHead>Piętra</TableHead>
                      <TableHead>Slot pralni</TableHead>
                      <TableHead className="text-right">Akcje</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {dorms.map((dorm) => (
                      <TableRow key={dorm.id}>
                        <TableCell className="font-medium">{dorm.code}</TableCell>
                        <TableCell>{dorm.name}</TableCell>
                        <TableCell className="max-w-[200px] truncate">
                          {dorm.address}
                        </TableCell>
                        <TableCell>{dorm.floorsCount}</TableCell>
                        <TableCell>{dorm.laundrySlotDurationMinutes} min</TableCell>
                        <TableCell className="text-right">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => openEditDorm(dorm)}
                          >
                            Edytuj
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

        {section === 'admins' && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>Kierownicy ADS</CardTitle>
                <CardDescription>Konta DORM_ADMIN przypisane do DS</CardDescription>
              </div>
              <Button type="button" onClick={openCreateAdmin} disabled={dorms.length === 0}>
                Dodaj kierownika
              </Button>
            </CardHeader>
            <CardContent>
              {adminsQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">Ładowanie…</p>
              ) : adminsQuery.isError ? (
                <p className="text-sm text-destructive">
                  {getApiErrorMessage(adminsQuery.error)}
                </p>
              ) : admins.length === 0 ? (
                <p className="text-sm text-muted-foreground">Brak kont ADS.</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Osoba</TableHead>
                      <TableHead>DS</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead className="text-right">Akcje</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {admins.map((admin: DormAdminAccount) => (
                      <TableRow key={admin.id}>
                        <TableCell>
                          <div className="min-w-0">
                            <p className="font-medium">
                              {admin.firstName} {admin.lastName}
                            </p>
                            <p className="truncate text-muted-foreground text-sm">
                              {admin.email}
                            </p>
                          </div>
                        </TableCell>
                        <TableCell>
                          {admin.dormitoryName ?? '—'}
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant={admin.status === 'ACTIVE' ? 'secondary' : 'destructive'}
                          >
                            {admin.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            disabled={toggleAdminMutation.isPending}
                            onClick={() =>
                              toggleAdminMutation.mutate({
                                id: admin.id,
                                status: admin.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE',
                              })
                            }
                          >
                            {admin.status === 'ACTIVE' ? 'Zablokuj' : 'Odblokuj'}
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

        {section === 'events' && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle>Komunikaty kampusowe</CardTitle>
                <CardDescription>
                  Oficjalne ogłoszenia AOS (zasięg całego osiedla)
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

      <Dialog open={dormDialogOpen} onOpenChange={setDormDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingDorm ? 'Edytuj akademik' : 'Nowy akademik'}
            </DialogTitle>
            <DialogDescription>
              Parametry pralni przy tworzeniu: 07:00–23:00, slot 180 min.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="dorm-code">Kod</FieldLabel>
              <Input
                id="dorm-code"
                value={dormForm.code}
                onChange={(e) => setDormForm((f) => ({ ...f, code: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="dorm-name">Nazwa</FieldLabel>
              <Input
                id="dorm-name"
                value={dormForm.name}
                onChange={(e) => setDormForm((f) => ({ ...f, name: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="dorm-address">Adres</FieldLabel>
              <Input
                id="dorm-address"
                value={dormForm.address}
                onChange={(e) => setDormForm((f) => ({ ...f, address: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="dorm-floors">Liczba pięter</FieldLabel>
              <Input
                id="dorm-floors"
                type="number"
                min={1}
                value={dormForm.floorsCount}
                onChange={(e) =>
                  setDormForm((f) => ({
                    ...f,
                    floorsCount: Number(e.target.value) || 1,
                  }))
                }
              />
            </Field>
            {actionError ? <FieldError>{actionError}</FieldError> : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              disabled={saveDormMutation.isPending}
              onClick={() => saveDormMutation.mutate()}
            >
              Zapisz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={adminDialogOpen} onOpenChange={setAdminDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Nowe konto ADS</DialogTitle>
            <DialogDescription>
              Kierownik DS otrzyma rolę DORM_ADMIN i dostęp do panelu meldunków.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="ads-first">Imię</FieldLabel>
              <Input
                id="ads-first"
                value={adminForm.firstName}
                onChange={(e) =>
                  setAdminForm((f) => ({ ...f, firstName: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="ads-last">Nazwisko</FieldLabel>
              <Input
                id="ads-last"
                value={adminForm.lastName}
                onChange={(e) =>
                  setAdminForm((f) => ({ ...f, lastName: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="ads-email">E-mail</FieldLabel>
              <Input
                id="ads-email"
                type="email"
                value={adminForm.email}
                onChange={(e) => setAdminForm((f) => ({ ...f, email: e.target.value }))}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="ads-phone">Telefon</FieldLabel>
              <Input
                id="ads-phone"
                value={adminForm.phoneNumber}
                onChange={(e) =>
                  setAdminForm((f) => ({ ...f, phoneNumber: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="ads-password">Hasło</FieldLabel>
              <Input
                id="ads-password"
                type="password"
                value={adminForm.password}
                onChange={(e) =>
                  setAdminForm((f) => ({ ...f, password: e.target.value }))
                }
              />
            </Field>
            <Field>
              <FieldLabel>Akademik</FieldLabel>
              <Select
                value={adminForm.dormitoryId}
                onValueChange={(value) =>
                  setAdminForm((f) => ({ ...f, dormitoryId: value }))
                }
              >
                <SelectTrigger>
                  <SelectValue placeholder="Wybierz DS" />
                </SelectTrigger>
                <SelectContent>
                  {dormOptions.map((opt) => (
                    <SelectItem key={opt.id} value={opt.id}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            {actionError ? <FieldError>{actionError}</FieldError> : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              disabled={saveAdminMutation.isPending || !adminForm.dormitoryId}
              onClick={() => saveAdminMutation.mutate()}
            >
              Utwórz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={eventDialogOpen} onOpenChange={setEventDialogOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingEvent ? 'Edytuj komunikat' : 'Nowy komunikat kampusowy'}
            </DialogTitle>
            <DialogDescription>
              Komunikaty AOS są zawsze przypięte. Priorytet CRITICAL pokazuje się jako baner u mieszkańców.
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
                    priority: value as CreateCampusEventRequest['priority'],
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
              disabled={saveEventMutation.isPending}
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
