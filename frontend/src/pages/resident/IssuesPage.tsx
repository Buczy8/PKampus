import { useState } from "react"
import { useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, Camera, CheckCircle2, Plus, Wrench } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import { createIssue, listMyIssues } from "@/api/issues"
import type {
  Issue,
  IssueCategory,
  IssueLocationType,
  IssueStatus,
  IssueUrgency,
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
import { formatWarsawDateTime } from "@/lib/laundry-dates"

const COMMON_AREAS = [
  "kuchnia piętrowa",
  "węzeł sanitarny",
  "korytarz",
  "pralnia",
  "winda",
  "inne",
] as const

const CATEGORIES: { value: IssueCategory; label: string }[] = [
  { value: "PLUMBING", label: "Hydraulika" },
  { value: "ELECTRICAL", label: "Elektryka" },
  { value: "FURNITURE", label: "Meble / wyposażenie" },
  { value: "LOCKSMITH", label: "Ślusarstwo / zamki" },
  { value: "OTHER", label: "Inne" },
]

function categoryLabel(category: IssueCategory): string {
  return CATEGORIES.find((c) => c.value === category)?.label ?? category
}

function statusLabel(status: IssueStatus): string {
  switch (status) {
    case "NEW":
      return "Nowe"
    case "ASSIGNED_TO_MAINTENANCE":
      return "Przekazane konserwatorowi"
    case "IN_PROGRESS":
      return "W trakcie naprawy"
    case "RESOLVED":
      return "Naprawione"
    case "REJECTED":
      return "Odrzucone"
    case "PARTS_REQUIRED":
      return "Wymaga części"
    default:
      return status
  }
}

function urgencyLabel(urgency: IssueUrgency): string {
  return urgency === "URGENT" ? "Pilne" : "Normalne"
}

export function IssuesPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [locationType, setLocationType] = useState<IssueLocationType>("MY_ROOM")
  const [commonArea, setCommonArea] = useState<string>(COMMON_AREAS[0])
  const [category, setCategory] = useState<IssueCategory>("PLUMBING")
  const [urgency, setUrgency] = useState<IssueUrgency>("NORMAL")
  const [description, setDescription] = useState("")
  const [photo, setPhoto] = useState<File | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const issuesQuery = useQuery({
    queryKey: ["issues", "me"],
    queryFn: listMyIssues,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createIssue(
        {
          locationType,
          commonAreaName: locationType === "COMMON_AREA" ? commonArea : undefined,
          category,
          urgency,
          description: description.trim(),
        },
        photo,
      ),
    onSuccess: async () => {
      setDialogOpen(false)
      resetForm()
      await queryClient.invalidateQueries({ queryKey: ["issues", "me"] })
    },
    onError: (error) => {
      setFormError(getApiErrorMessage(error, "Nie udało się zgłosić usterki"))
    },
  })

  const issues = issuesQuery.data ?? []

  function resetForm() {
    setLocationType("MY_ROOM")
    setCommonArea(COMMON_AREAS[0])
    setCategory("PLUMBING")
    setUrgency("NORMAL")
    setDescription("")
    setPhoto(null)
    setFormError(null)
  }

  function openDialog() {
    resetForm()
    setDialogOpen(true)
  }

  function submit() {
    setFormError(null)
    if (!description.trim()) {
      setFormError("Podaj opis usterki")
      return
    }
    if (photo && photo.size > 5 * 1024 * 1024) {
      setFormError("Zdjęcie może mieć maksymalnie 5 MB")
      return
    }
    createMutation.mutate()
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Wrench className="size-5 text-muted-foreground" />
            Zgłoszenia usterek
          </h2>
          <p className="text-sm text-muted-foreground">
            Zgłaszaj awarie w pokoju {user.roomNumber ?? "—"} lub w częściach wspólnych
            akademika.
          </p>
        </div>

        <Button className="gap-2" type="button" onClick={openDialog}>
          <Plus className="size-4" />
          <span>Nowe zgłoszenie</span>
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Moje zgłoszenia</CardTitle>
            <CardDescription>
              Status i ostatnia notatka portiera (FR-ISSUE-05)
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {issuesQuery.isLoading && (
              <p className="text-sm text-muted-foreground">Ładowanie…</p>
            )}
            {issuesQuery.isError && (
              <p className="text-sm text-destructive">
                {getApiErrorMessage(issuesQuery.error, "Nie udało się pobrać zgłoszeń")}
              </p>
            )}
            {!issuesQuery.isLoading && issues.length === 0 && (
              <div className="flex flex-col items-center justify-center py-10 text-center text-muted-foreground">
                <CheckCircle2 className="size-12 stroke-1 mb-3 opacity-60" />
                <p className="font-medium text-foreground">Brak zgłoszeń</p>
                <p className="text-xs max-w-sm mt-1">
                  Gdy zgłosisz usterkę, zobaczysz tu jej status i notatki portiera.
                </p>
              </div>
            )}
            {issues.map((issue) => (
              <IssueRow key={issue.id} issue={issue} />
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Zasady zgłaszania</CardTitle>
            <CardDescription>Wskazówki dla mieszkańców</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs text-muted-foreground">
            <div className="flex items-start gap-2">
              <Camera className="size-4 text-muted-foreground shrink-0 mt-0.5" />
              <span>
                <strong>Zdjęcie:</strong> opcjonalnie JPEG, PNG lub WebP do 5 MB.
              </span>
            </div>
            <div className="flex items-start gap-2">
              <AlertCircle className="size-4 text-muted-foreground shrink-0 mt-0.5" />
              <span>
                <strong>Pilne:</strong> zalania, spięcia elektryczne, uszkodzone zamki.
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open)
          if (!open) resetForm()
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Nowe zgłoszenie usterki</DialogTitle>
            <DialogDescription>
              Wybierz lokalizację, kategorię i opisz problem. Zdjęcie jest opcjonalne.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel>Lokalizacja</FieldLabel>
              <Select
                value={locationType}
                onValueChange={(v) => setLocationType(v as IssueLocationType)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MY_ROOM">
                    Mój pokój{user.roomNumber ? ` (${user.roomNumber})` : ""}
                  </SelectItem>
                  <SelectItem value="COMMON_AREA">Część wspólna</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            {locationType === "COMMON_AREA" ? (
              <Field>
                <FieldLabel>Część wspólna</FieldLabel>
                <Select value={commonArea} onValueChange={setCommonArea}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {COMMON_AREAS.map((area) => (
                      <SelectItem key={area} value={area}>
                        {area.charAt(0).toUpperCase() + area.slice(1)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
            <Field>
              <FieldLabel>Kategoria</FieldLabel>
              <Select
                value={category}
                onValueChange={(v) => setCategory(v as IssueCategory)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
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
                onValueChange={(v) => setUrgency(v as IssueUrgency)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NORMAL">Normalne</SelectItem>
                  <SelectItem value="URGENT">Pilne</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="issue-desc">Opis</FieldLabel>
              <Textarea
                id="issue-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={4}
                maxLength={4000}
                placeholder="Opisz usterkę możliwie precyzyjnie…"
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="issue-photo">Zdjęcie (opcjonalnie)</FieldLabel>
              <Input
                id="issue-photo"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
              />
              {photo ? (
                <p className="text-xs text-muted-foreground mt-1">
                  {photo.name} ({Math.round(photo.size / 1024)} KB)
                </p>
              ) : null}
            </Field>
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
              onClick={() => setDialogOpen(false)}
              disabled={createMutation.isPending}
            >
              Anuluj
            </Button>
            <Button
              type="button"
              disabled={createMutation.isPending}
              onClick={submit}
            >
              {createMutation.isPending ? "Wysyłam…" : "Zgłoś usterkę"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function IssueRow({ issue }: { issue: Issue }) {
  return (
    <div className="rounded-lg border border-border p-3 space-y-2">
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
        <div>
          <p className="font-medium text-sm">{issue.locationLabel}</p>
          <p className="text-xs text-muted-foreground">
            {categoryLabel(issue.category)} · {urgencyLabel(issue.urgency)} ·{" "}
            {formatWarsawDateTime(issue.createdAt)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {issue.urgency === "URGENT" ? (
            <Badge variant="destructive">Pilne</Badge>
          ) : null}
          <Badge variant="secondary">{statusLabel(issue.status)}</Badge>
        </div>
      </div>
      <p className="text-sm whitespace-pre-wrap">{issue.description}</p>
      {issue.staffNotes ? (
        <p className="text-xs rounded-md bg-muted/50 border border-border px-2 py-1.5">
          <span className="font-medium text-foreground">Notatka portiera: </span>
          {issue.staffNotes}
        </p>
      ) : null}
      {issue.hasPhoto && issue.photoUrl ? (
        <a
          href={issue.photoUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-block"
        >
          <img
            src={issue.photoUrl}
            alt="Zdjęcie usterki"
            className="max-h-40 rounded-md border border-border object-cover"
          />
        </a>
      ) : null}
    </div>
  )
}
