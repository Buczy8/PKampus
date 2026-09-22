import { useEffect, useMemo, useState } from "react"
import { Link, useOutletContext, useSearchParams } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, ImageIcon, Wrench } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  getReceptionistIssue,
  listReceptionistIssues,
  updateReceptionistIssueStatus,
} from "@/api/receptionist"
import type {
  IssueCategory,
  IssueStatus,
  IssueUrgency,
  StaffIssue,
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
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "cn"
import { formatWarsawDateTime } from "@/lib/laundry-dates"

const OPEN_STATUSES: IssueStatus[] = [
  "NEW",
  "ASSIGNED_TO_MAINTENANCE",
  "IN_PROGRESS",
  "PARTS_REQUIRED",
]

const ALL_STATUSES: IssueStatus[] = [
  "NEW",
  "ASSIGNED_TO_MAINTENANCE",
  "IN_PROGRESS",
  "PARTS_REQUIRED",
  "RESOLVED",
  "REJECTED",
]

const ALLOWED_NEXT: Record<IssueStatus, IssueStatus[]> = {
  NEW: ["ASSIGNED_TO_MAINTENANCE", "REJECTED"],
  ASSIGNED_TO_MAINTENANCE: ["IN_PROGRESS", "REJECTED"],
  IN_PROGRESS: ["PARTS_REQUIRED", "RESOLVED"],
  PARTS_REQUIRED: ["IN_PROGRESS", "RESOLVED"],
  RESOLVED: [],
  REJECTED: [],
}

const CATEGORIES: { value: IssueCategory; label: string }[] = [
  { value: "PLUMBING", label: "Hydraulika" },
  { value: "ELECTRICAL", label: "Elektryka" },
  { value: "FURNITURE", label: "Meble" },
  { value: "LOCKSMITH", label: "Ślusarstwo" },
  { value: "OTHER", label: "Inne" },
]

function statusLabel(status: IssueStatus): string {
  switch (status) {
    case "NEW":
      return "Nowe"
    case "ASSIGNED_TO_MAINTENANCE":
      return "Przekazane"
    case "IN_PROGRESS":
      return "W trakcie"
    case "RESOLVED":
      return "Naprawione"
    case "REJECTED":
      return "Odrzucone"
    case "PARTS_REQUIRED":
      return "Części"
    default:
      return status
  }
}

function categoryLabel(category: IssueCategory): string {
  return CATEGORIES.find((c) => c.value === category)?.label ?? category
}

function urgencyLabel(urgency: IssueUrgency): string {
  return urgency === "URGENT" ? "Pilne" : "Normalne"
}

export function ReceptionistIssuesPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const [statusFilter, setStatusFilter] = useState<"open" | "all" | IssueStatus>(
    "open",
  )
  const [category, setCategory] = useState<IssueCategory | "ALL">("ALL")
  const [urgency, setUrgency] = useState<IssueUrgency | "ALL">("ALL")
  const [roomNumber, setRoomNumber] = useState("")
  const [floor, setFloor] = useState("")
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [nextStatus, setNextStatus] = useState<IssueStatus | "">("")
  const [staffNotes, setStaffNotes] = useState("")
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  useEffect(() => {
    const id = searchParams.get("id")
    if (id) {
      setSelectedId(id)
      setActionError(null)
      setNextStatus("")
    }
  }, [searchParams])

  const clearIssueParam = () => {
    if (!searchParams.has("id")) return
    const next = new URLSearchParams(searchParams)
    next.delete("id")
    setSearchParams(next, { replace: true })
  }

  const filters = useMemo(() => {
    const status: IssueStatus[] | undefined =
      statusFilter === "open"
        ? OPEN_STATUSES
        : statusFilter === "all"
          ? undefined
          : [statusFilter]

    const floorNum = floor.trim() === "" ? undefined : Number(floor)
    return {
      status,
      category: category === "ALL" ? undefined : category,
      urgency: urgency === "ALL" ? undefined : urgency,
      from: from || undefined,
      to: to || undefined,
      roomNumber: roomNumber.trim() || undefined,
      floor:
        floorNum !== undefined && !Number.isNaN(floorNum) ? floorNum : undefined,
    }
  }, [statusFilter, category, urgency, from, to, roomNumber, floor])

  const listQuery = useQuery({
    queryKey: ["receptionist", "issues", filters],
    queryFn: () => listReceptionistIssues(filters),
  })

  const detailQuery = useQuery({
    queryKey: ["receptionist", "issues", "detail", selectedId],
    queryFn: () => getReceptionistIssue(selectedId!),
    enabled: selectedId != null,
  })

  useEffect(() => {
    if (detailQuery.data && detailQuery.data.id === selectedId) {
      setStaffNotes(detailQuery.data.staffNotes ?? "")
    }
  }, [detailQuery.data, selectedId])

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["receptionist", "issues"] }),
      queryClient.invalidateQueries({ queryKey: ["receptionist", "desk"] }),
    ])
  }

  const updateMutation = useMutation({
    mutationFn: () =>
      updateReceptionistIssueStatus(selectedId!, {
        status: nextStatus as IssueStatus,
        staffNotes,
      }),
    onSuccess: async () => {
      setActionError(null)
      setActionInfo("Status zgłoszenia zaktualizowany")
      closeDetail()
      setStaffNotes("")
      await invalidate()
    },
    onError: (error) => {
      setActionError(
        getApiErrorMessage(error, "Nie udało się zaktualizować statusu"),
      )
    },
  })

  const openDetail = (issue: StaffIssue) => {
    setSelectedId(issue.id)
    setActionError(null)
    setNextStatus("")
    setStaffNotes(issue.staffNotes ?? "")
    setSearchParams({ id: issue.id }, { replace: true })
  }

  const closeDetail = () => {
    setSelectedId(null)
    setNextStatus("")
    clearIssueParam()
  }

  const detail = detailQuery.data
  const nextOptions = detail ? ALLOWED_NEXT[detail.status] : []

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
            <Wrench className="size-5 text-primary" />
            Usterki — rejestr
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            {user.dormitoryName ?? "Twój DS"} — filtrowanie, podgląd i zmiana statusu
          </p>
        </div>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}
      {actionInfo && !actionError && (
        <p className="text-sm text-primary">{actionInfo}</p>
      )}

      <Card className="border-border/70 shadow-none">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Filtry</CardTitle>
          <CardDescription>Domyślnie otwarte zgłoszenia</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field>
            <FieldLabel>Status</FieldLabel>
            <Select
              value={statusFilter}
              onValueChange={(v) =>
                setStatusFilter(v as "open" | "all" | IssueStatus)
              }
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="open">Otwarte</SelectItem>
                <SelectItem value="all">Wszystkie</SelectItem>
                {ALL_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {statusLabel(s)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Kategoria</FieldLabel>
            <Select
              value={category}
              onValueChange={(v) => setCategory(v as IssueCategory | "ALL")}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Wszystkie</SelectItem>
                {CATEGORIES.map((c) => (
                  <SelectItem key={c.value} value={c.value}>
                    {c.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Pilność</FieldLabel>
            <Select
              value={urgency}
              onValueChange={(v) => setUrgency(v as IssueUrgency | "ALL")}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Wszystkie</SelectItem>
                <SelectItem value="NORMAL">Normalne</SelectItem>
                <SelectItem value="URGENT">Pilne</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Nr pokoju</FieldLabel>
            <Input
              value={roomNumber}
              onChange={(e) => setRoomNumber(e.target.value)}
              placeholder="np. 312"
            />
          </Field>
          <Field>
            <FieldLabel>Piętro</FieldLabel>
            <Input
              type="number"
              value={floor}
              onChange={(e) => setFloor(e.target.value)}
              placeholder="np. 3"
            />
          </Field>
          <Field>
            <FieldLabel>Od daty</FieldLabel>
            <Input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel>Do daty</FieldLabel>
            <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
        </CardContent>
      </Card>

      {listQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Ładowanie rejestru…</p>
      )}
      {listQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(listQuery.error, "Nie udało się pobrać usterek")}
        </p>
      )}

      {listQuery.data && listQuery.data.length === 0 && (
        <p className="text-sm text-muted-foreground">Brak zgłoszeń dla filtrów.</p>
      )}

      <div className="space-y-3">
        {listQuery.data?.map((issue) => (
          <button
            key={issue.id}
            type="button"
            onClick={() => openDetail(issue)}
            className={cn(
              "w-full text-left rounded-2xl border border-border/70 bg-card p-4 sm:p-5",
              "hover:border-primary/40 transition-colors",
            )}
          >
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-semibold truncate">{issue.locationLabel}</p>
                  <Badge variant="secondary">{statusLabel(issue.status)}</Badge>
                  {issue.urgency === "URGENT" && (
                    <Badge variant="destructive">Pilne</Badge>
                  )}
                  {issue.hasPhoto && (
                    <Badge variant="outline" className="gap-1">
                      <ImageIcon className="size-3" />
                      Zdjęcie
                    </Badge>
                  )}
                </div>
                <p className="text-sm text-muted-foreground">
                  {categoryLabel(issue.category)} ·{" "}
                  {issue.reporterFirstName} {issue.reporterLastName}
                </p>
                <p className="text-sm line-clamp-2">{issue.description}</p>
              </div>
              <p className="text-xs text-muted-foreground tabular-nums shrink-0">
                {formatWarsawDateTime(issue.createdAt)}
              </p>
            </div>
          </button>
        ))}
      </div>

      <Dialog
        open={selectedId != null}
        onOpenChange={(open) => {
          if (!open) closeDetail()
        }}
      >
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Szczegóły usterki</DialogTitle>
            <DialogDescription>
              Zmiana statusu i notatka dla mieszkańca
            </DialogDescription>
          </DialogHeader>

          {detailQuery.isLoading && (
            <p className="text-sm text-muted-foreground">Ładowanie…</p>
          )}
          {detailQuery.isError && (
            <p className="text-sm text-destructive">
              {getApiErrorMessage(
                detailQuery.error,
                "Nie udało się wczytać szczegółów",
              )}
            </p>
          )}

          {detail && (
            <div className="space-y-4">
              <div className="space-y-1 text-sm">
                <p>
                  <span className="text-muted-foreground">Lokalizacja:</span>{" "}
                  {detail.locationLabel}
                  {detail.floor != null ? ` (p. ${detail.floor})` : ""}
                </p>
                <p>
                  <span className="text-muted-foreground">Zgłaszający:</span>{" "}
                  {detail.reporterFirstName} {detail.reporterLastName}
                </p>
                <p>
                  <span className="text-muted-foreground">Kategoria:</span>{" "}
                  {categoryLabel(detail.category)} · {urgencyLabel(detail.urgency)}
                </p>
                <p>
                  <span className="text-muted-foreground">Status:</span>{" "}
                  {statusLabel(detail.status)}
                </p>
                <p>
                  <span className="text-muted-foreground">Utworzono:</span>{" "}
                  {formatWarsawDateTime(detail.createdAt)}
                </p>
                <p className="pt-1 whitespace-pre-wrap">{detail.description}</p>
              </div>

              {detail.photoUrl && (
                <a
                  href={detail.photoUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="block overflow-hidden rounded-lg border border-border/70"
                >
                  <img
                    src={detail.photoUrl}
                    alt="Zdjęcie usterki"
                    className="w-full max-h-64 object-contain bg-muted/40"
                  />
                </a>
              )}

              {nextOptions.length > 0 ? (
                <FieldGroup>
                  <Field>
                    <FieldLabel>Nowy status</FieldLabel>
                    <Select
                      value={nextStatus || undefined}
                      onValueChange={(v) => setNextStatus(v as IssueStatus)}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Wybierz status" />
                      </SelectTrigger>
                      <SelectContent>
                        {nextOptions.map((s) => (
                          <SelectItem key={s} value={s}>
                            {statusLabel(s)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel>
                      Notatka
                      {nextStatus === "REJECTED" ? " (wymagana)" : ""}
                    </FieldLabel>
                    <Textarea
                      value={staffNotes}
                      onChange={(e) => setStaffNotes(e.target.value)}
                      rows={3}
                      placeholder="Widoczna dla zgłaszającego"
                    />
                  </Field>
                </FieldGroup>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Zgłoszenie zamknięte — brak dalszych przejść statusu.
                  {detail.staffNotes ? (
                    <>
                      {" "}
                      Ostatnia notatka: <em>{detail.staffNotes}</em>
                    </>
                  ) : null}
                </p>
              )}
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setSelectedId(null)}
            >
              Zamknij
            </Button>
            {detail && nextOptions.length > 0 && (
              <Button
                type="button"
                disabled={
                  !nextStatus ||
                  updateMutation.isPending ||
                  (nextStatus === "REJECTED" && !staffNotes.trim())
                }
                onClick={() => updateMutation.mutate()}
              >
                Zapisz status
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
