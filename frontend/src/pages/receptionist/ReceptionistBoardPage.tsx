import { useState } from "react"
import { Link, useOutletContext } from "react-router-dom"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, ChevronDown, ChevronRight, MessageSquare } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  listReceptionistBoardComments,
  listReceptionistBoardPosts,
  removeReceptionistBoardComment,
  removeReceptionistBoardPost,
} from "@/api/receptionist"
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
import { Field, FieldLabel } from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import type {
  BoardComment,
  BoardPost,
  BoardPostCategory,
  BoardPostStatusFilter,
  UserProfile,
} from "@/api/types"

const CATEGORIES: { value: BoardPostCategory | "ALL"; label: string }[] = [
  { value: "ALL", label: "Wszystkie" },
  { value: "BORROW_HELP", label: "Pożyczę / Pomoc" },
  { value: "BUY_SELL", label: "Kupię / Sprzedam" },
  { value: "LOST_FOUND", label: "Zgubiono / Znaleziono" },
  { value: "GENERAL", label: "Ogólne" },
]

function categoryLabel(c: BoardPostCategory): string {
  return CATEGORIES.find((x) => x.value === c)?.label ?? c
}

function statusLabel(s: string): string {
  switch (s) {
    case "RESOLVED":
      return "Rozwiązane"
    case "REMOVED_MODERATOR":
      return "Usunięte"
    default:
      return "Aktywne"
  }
}

function formatWhen(iso: string): string {
  try {
    return new Date(iso).toLocaleString("pl-PL", {
      dateStyle: "short",
      timeStyle: "short",
    })
  } catch {
    return iso
  }
}

export function ReceptionistBoardPage() {
  const user = useOutletContext<UserProfile>()
  const queryClient = useQueryClient()

  const [category, setCategory] = useState<BoardPostCategory | "ALL">("ALL")
  const [status, setStatus] = useState<BoardPostStatusFilter>("ALL")
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [removePostTarget, setRemovePostTarget] = useState<BoardPost | null>(null)
  const [removeCommentTarget, setRemoveCommentTarget] =
    useState<BoardComment | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  const postsQuery = useQuery({
    queryKey: ["receptionist", "posts", category, status],
    queryFn: () =>
      listReceptionistBoardPosts({
        category: category === "ALL" ? undefined : category,
        status,
      }),
  })

  const commentsQuery = useQuery({
    queryKey: ["receptionist", "posts", expandedId, "comments"],
    queryFn: () => listReceptionistBoardComments(expandedId!),
    enabled: expandedId != null,
  })

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ["receptionist", "posts"] })
  }

  const removePostMutation = useMutation({
    mutationFn: (id: string) => removeReceptionistBoardPost(id),
    onSuccess: async () => {
      setRemovePostTarget(null)
      setActionError(null)
      setActionInfo("Post usunięty (moderacja)")
      setExpandedId(null)
      await invalidate()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się usunąć posta"))
    },
  })

  const removeCommentMutation = useMutation({
    mutationFn: (id: string) => removeReceptionistBoardComment(id),
    onSuccess: async () => {
      setRemoveCommentTarget(null)
      setActionError(null)
      setActionInfo("Komentarz usunięty")
      await queryClient.invalidateQueries({
        queryKey: ["receptionist", "posts", expandedId, "comments"],
      })
      await invalidate()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się usunąć komentarza"))
    },
  })

  const toggleExpand = (post: BoardPost) => {
    setExpandedId((cur) => (cur === post.id ? null : post.id))
  }

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
          <MessageSquare className="size-5 text-primary" />
          Tablica — moderacja
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          {user.dormitoryName ?? "Twój DS"} — posty mieszkańców (tylko zasięg DS)
        </p>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}
      {actionInfo && !actionError && (
        <p className="text-sm text-primary">{actionInfo}</p>
      )}

      <Card className="border-border/70 shadow-none">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Filtry</CardTitle>
          <CardDescription>Posty kampusowe są poza zakresem portiera</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          <Field>
            <FieldLabel>Kategoria</FieldLabel>
            <Select
              value={category}
              onValueChange={(v) => setCategory(v as BoardPostCategory | "ALL")}
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
            <FieldLabel>Status</FieldLabel>
            <Select
              value={status}
              onValueChange={(v) => setStatus(v as BoardPostStatusFilter)}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Aktywne + rozwiązane</SelectItem>
                <SelectItem value="ACTIVE">Aktywne</SelectItem>
                <SelectItem value="RESOLVED">Rozwiązane</SelectItem>
              </SelectContent>
            </Select>
          </Field>
        </CardContent>
      </Card>

      {postsQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Ładowanie…</p>
      )}
      {postsQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(postsQuery.error)}
        </p>
      )}
      {postsQuery.data?.length === 0 && (
        <p className="text-sm text-muted-foreground">Brak postów do moderacji.</p>
      )}

      <div className="space-y-3">
        {postsQuery.data?.map((post) => {
          const open = expandedId === post.id
          return (
            <Card key={post.id} className="border-border/70 shadow-none">
              <CardContent className="p-4 sm:p-5 space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold">{post.title}</p>
                      <Badge variant="secondary">{statusLabel(post.status)}</Badge>
                      <Badge variant="outline">{categoryLabel(post.category)}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {post.authorDisplayName}
                      {post.authorRoomNumber ? ` · pok. ${post.authorRoomNumber}` : ""}{" "}
                      · {formatWhen(post.createdAt)} · {post.commentCount} koment.
                    </p>
                    <p className="text-sm whitespace-pre-wrap">{post.content}</p>
                  </div>
                  <div className="flex flex-wrap gap-2 shrink-0">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => toggleExpand(post)}
                    >
                      {open ? (
                        <ChevronDown className="size-4 mr-1" />
                      ) : (
                        <ChevronRight className="size-4 mr-1" />
                      )}
                      Komentarze
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="destructive"
                      disabled={removePostMutation.isPending}
                      onClick={() => setRemovePostTarget(post)}
                    >
                      Usuń post
                    </Button>
                  </div>
                </div>

                {open && (
                  <div className="border-t border-border/60 pt-3 space-y-2">
                    {commentsQuery.isLoading && (
                      <p className="text-sm text-muted-foreground">Ładowanie komentarzy…</p>
                    )}
                    {commentsQuery.isError && (
                      <p className="text-sm text-destructive">
                        {getApiErrorMessage(commentsQuery.error)}
                      </p>
                    )}
                    {commentsQuery.data?.length === 0 && (
                      <p className="text-sm text-muted-foreground">Brak komentarzy.</p>
                    )}
                    {commentsQuery.data?.map((c) => (
                      <div
                        key={c.id}
                        className="flex flex-wrap items-start justify-between gap-2 rounded-lg bg-muted/40 px-3 py-2"
                      >
                        <div className="min-w-0 text-sm">
                          <p className="font-medium">
                            {c.authorDisplayName}
                            {c.authorRoomNumber ? ` · pok. ${c.authorRoomNumber}` : ""}
                          </p>
                          <p className="text-muted-foreground text-xs">
                            {formatWhen(c.createdAt)}
                          </p>
                          <p className="mt-1 whitespace-pre-wrap">{c.content}</p>
                        </div>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="text-destructive"
                          disabled={removeCommentMutation.isPending}
                          onClick={() => setRemoveCommentTarget(c)}
                        >
                          Usuń
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}
      </div>

      <Dialog
        open={removePostTarget != null}
        onOpenChange={(open) => {
          if (!open) setRemovePostTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Usunąć post z tablicy?</DialogTitle>
            <DialogDescription>
              {removePostTarget
                ? `„${removePostTarget.title}” zostanie oznaczony jako usunięty przez moderację i zniknie z widoku mieszkańców.`
                : null}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setRemovePostTarget(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={removePostMutation.isPending || !removePostTarget}
              onClick={() => {
                if (removePostTarget) removePostMutation.mutate(removePostTarget.id)
              }}
            >
              Usuń post
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={removeCommentTarget != null}
        onOpenChange={(open) => {
          if (!open) setRemoveCommentTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Usunąć komentarz?</DialogTitle>
            <DialogDescription>
              Komentarz zniknie z wątku na tablicy.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setRemoveCommentTarget(null)}
            >
              Wróć
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={removeCommentMutation.isPending || !removeCommentTarget}
              onClick={() => {
                if (removeCommentTarget) {
                  removeCommentMutation.mutate(removeCommentTarget.id)
                }
              }}
            >
              Usuń komentarz
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
