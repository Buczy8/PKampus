import { useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  activateResident,
  listPendingResidents,
  rejectResident,
} from '@/api/admin'
import { logout } from '@/api/auth'
import { getApiErrorMessage } from '@/api/errors'
import type { PendingResident, UserProfile } from '@/api/types'
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'

function initials(firstName: string, lastName: string) {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

export function AdminPage() {
  const user = useOutletContext<UserProfile>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [activateTarget, setActivateTarget] = useState<PendingResident | null>(null)
  const [rejectTarget, setRejectTarget] = useState<PendingResident | null>(null)
  const [roomNumber, setRoomNumber] = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const pendingQuery = useQuery({
    queryKey: ['admin', 'pending-residents'],
    queryFn: listPendingResidents,
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

  const pending = pendingQuery.data ?? []

  return (
    <main className="flex min-h-svh w-full items-start justify-center p-6 md:p-10">
      <div className="flex w-full max-w-4xl flex-col gap-6">
        <div className="flex items-center justify-between gap-4">
          <p className="text-2xl font-medium tracking-tight">
            <span className="uppercase">Pk</span>ampus
          </p>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{user.role}</Badge>
            <Button variant="outline" type="button" onClick={handleLogout}>
              Logout
            </Button>
          </div>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Pending residents</CardTitle>
            <CardDescription>
              Review residency applications for{' '}
              {user.dormitoryName ?? 'your dormitory'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {pendingQuery.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading applications…</p>
            ) : pendingQuery.isError ? (
              <p className="text-sm text-destructive">
                {getApiErrorMessage(pendingQuery.error)}
              </p>
            ) : pending.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No pending applications for{' '}
                {user.dormitoryName ?? 'your dormitories'} right now.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Resident</TableHead>
                    <TableHead>Room</TableHead>
                    <TableHead>Phone</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
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
                            <p className="truncate text-muted-foreground">
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
                            Approve
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
                            Reject
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}

            <p className="mt-6 text-center text-sm text-muted-foreground">
              <Link to="/dashboard" className="underline-offset-4 hover:underline">
                Back to dashboard
              </Link>
            </p>
          </CardContent>
        </Card>
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
            <DialogTitle>Approve residency</DialogTitle>
            <DialogDescription>
              {activateTarget
                ? `Activate ${activateTarget.firstName} ${activateTarget.lastName}. You can keep or override the declared room.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="roomNumber">Room number</FieldLabel>
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
              Cancel
            </Button>
            <Button
              type="button"
              onClick={() => activateMutation.mutate()}
              disabled={activateMutation.isPending}
            >
              {activateMutation.isPending ? 'Approving…' : 'Approve'}
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
            <DialogTitle>Reject application</DialogTitle>
            <DialogDescription>
              {rejectTarget
                ? `Reject ${rejectTarget.firstName} ${rejectTarget.lastName}. A reason is required and will be emailed.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="rejectReason">Reason</FieldLabel>
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
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => rejectMutation.mutate()}
              disabled={rejectMutation.isPending || rejectReason.trim().length === 0}
            >
              {rejectMutation.isPending ? 'Rejecting…' : 'Reject'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}
