import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Plus, Users } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import {
  createBoardComment,
  createBoardPost,
  deleteBoardPost,
  listBoardComments,
  listBoardPosts,
  resolveBoardPost,
} from "@/api/posts"
import type {
  BoardComment,
  BoardPost,
  BoardPostCategory,
  BoardPostScope,
  BoardPostStatusFilter,
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
import { cn } from "cn"

const CATEGORIES: { value: BoardPostCategory; label: string }[] = [
  { value: "BORROW_HELP", label: "Pożyczę / Pomoc" },
  { value: "BUY_SELL", label: "Kupię / Sprzedam / Oddam" },
  { value: "LOST_FOUND", label: "Zgubiono / Znaleziono" },
  { value: "GENERAL", label: "Pytanie ogólne" },
]

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

function categoryLabel(category: BoardPostCategory): string {
  return CATEGORIES.find((c) => c.value === category)?.label ?? category
}

function authorLine(post: BoardPost): string {
  const parts = [post.authorDisplayName]
  if (post.scope === "DORMITORY" && post.authorRoomNumber) {
    parts.push(`pok. ${post.authorRoomNumber}`)
  }
  if (post.authorDormitoryName) {
    parts.push(post.authorDormitoryName)
  }
  return parts.join(" · ")
}

function commentAuthorLine(comment: BoardComment, postScope: BoardPostScope): string {
  const parts = [comment.authorDisplayName]
  if (postScope === "DORMITORY" && comment.authorRoomNumber) {
    parts.push(`pok. ${comment.authorRoomNumber}`)
  }
  if (comment.authorDormitoryName) {
    parts.push(comment.authorDormitoryName)
  }
  return parts.join(" · ")
}

export function BoardPage() {
  const queryClient = useQueryClient()

  const [categoryFilter, setCategoryFilter] = useState<BoardPostCategory | "all">(
    "all",
  )
  const [scopeFilter, setScopeFilter] = useState<BoardPostScope | "all">("all")
  const [statusFilter, setStatusFilter] = useState<BoardPostStatusFilter>("ACTIVE")

  const [dialogOpen, setDialogOpen] = useState(false)
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [category, setCategory] = useState<BoardPostCategory>("GENERAL")
  const [scope, setScope] = useState<BoardPostScope>("DORMITORY")
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const postsQuery = useQuery({
    queryKey: ["posts", "feed", categoryFilter, scopeFilter, statusFilter],
    queryFn: () =>
      listBoardPosts({
        category: categoryFilter === "all" ? "" : categoryFilter,
        scope: scopeFilter === "all" ? "" : scopeFilter,
        status: statusFilter,
      }),
  })

  const invalidatePosts = () =>
    queryClient.invalidateQueries({ queryKey: ["posts", "feed"] })

  const createMutation = useMutation({
    mutationFn: () =>
      createBoardPost({
        title: title.trim(),
        content: content.trim(),
        category,
        scope,
      }),
    onSuccess: async () => {
      setDialogOpen(false)
      resetForm()
      await invalidatePosts()
    },
    onError: (error) => {
      setFormError(getApiErrorMessage(error, "Nie udało się opublikować posta"))
    },
  })

  const resolveMutation = useMutation({
    mutationFn: (id: string) => resolveBoardPost(id),
    onSuccess: async () => {
      setActionError(null)
      await invalidatePosts()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się oznaczyć jako rozwiązane"))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteBoardPost(id),
    onSuccess: async () => {
      setActionError(null)
      await invalidatePosts()
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Nie udało się usunąć posta"))
    },
  })

  const posts = postsQuery.data ?? []

  function resetForm() {
    setTitle("")
    setContent("")
    setCategory("GENERAL")
    setScope("DORMITORY")
    setFormError(null)
  }

  function openCreate() {
    resetForm()
    setDialogOpen(true)
  }

  function submitCreate() {
    setFormError(null)
    if (!title.trim() || !content.trim()) {
      setFormError("Podaj tytuł i treść")
      return
    }
    createMutation.mutate()
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight flex items-center gap-2">
            <Users className="size-5 text-primary" />
            Tablica sąsiedzka
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            Ogłoszenia mieszkańców (pomoc, kupno/sprzedaż, zguby). Bez anonimowości.
          </p>
        </div>
        <Button type="button" className="gap-2" onClick={openCreate}>
          <Plus className="size-4" />
          Nowy post
        </Button>
      </div>

      {actionError && <p className="text-sm text-destructive">{actionError}</p>}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Field>
            <FieldLabel>Kategoria</FieldLabel>
            <Select
              value={categoryFilter}
              onValueChange={(v) => setCategoryFilter(v as BoardPostCategory | "all")}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Wszystkie</SelectItem>
                {CATEGORIES.map((c) => (
                  <SelectItem key={c.value} value={c.value}>
                    {c.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Zasięg</FieldLabel>
            <Select
              value={scopeFilter}
              onValueChange={(v) => setScopeFilter(v as BoardPostScope | "all")}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Wszystkie</SelectItem>
                <SelectItem value="DORMITORY">Mój DS</SelectItem>
                <SelectItem value="CAMPUS">Kampus</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Status</FieldLabel>
            <Select
              value={statusFilter}
              onValueChange={(v) => setStatusFilter(v as BoardPostStatusFilter)}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACTIVE">Aktywne</SelectItem>
                <SelectItem value="RESOLVED">Rozwiązane</SelectItem>
                <SelectItem value="ALL">Wszystkie</SelectItem>
              </SelectContent>
            </Select>
          </Field>
        </div>

        {postsQuery.isLoading ? (
          <p className="text-sm text-muted-foreground">Ładowanie ogłoszeń…</p>
        ) : postsQuery.isError ? (
          <p className="text-sm text-destructive">
            {getApiErrorMessage(postsQuery.error, "Nie udało się pobrać postów")}
          </p>
        ) : posts.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Brak ogłoszeń dla wybranych filtrów.
          </p>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                resolving={resolveMutation.isPending}
                deleting={deleteMutation.isPending}
                onResolve={() => resolveMutation.mutate(post.id)}
                onDelete={() => {
                  if (window.confirm("Usunąć to ogłoszenie?")) {
                    deleteMutation.mutate(post.id)
                  }
                }}
                onCommentsChanged={() => void invalidatePosts()}
              />
            ))}
          </div>
        )}

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open)
          if (!open) resetForm()
        }}
      >
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Nowy post</DialogTitle>
            <DialogDescription>
              Publikacja bezpośrednia — widoczna od razu dla mieszkańców.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="post-title">Tytuł</FieldLabel>
              <Input
                id="post-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={150}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="post-content">Treść</FieldLabel>
              <Textarea
                id="post-content"
                value={content}
                onChange={(e) => setContent(e.target.value)}
                rows={4}
                maxLength={4000}
              />
            </Field>
            <Field>
              <FieldLabel>Kategoria</FieldLabel>
              <Select
                value={category}
                onValueChange={(v) => setCategory(v as BoardPostCategory)}
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
              <FieldLabel>Zasięg</FieldLabel>
              <Select
                value={scope}
                onValueChange={(v) => setScope(v as BoardPostScope)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DORMITORY">Mój akademik</SelectItem>
                  <SelectItem value="CAMPUS">Cały kampus (bez numeru pokoju)</SelectItem>
                </SelectContent>
              </Select>
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
              onClick={submitCreate}
            >
              {createMutation.isPending ? "Publikuję…" : "Opublikuj"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PostCard({
  post,
  resolving,
  deleting,
  onResolve,
  onDelete,
  onCommentsChanged,
}: {
  post: BoardPost
  resolving: boolean
  deleting: boolean
  onResolve: () => void
  onDelete: () => void
  onCommentsChanged: () => void
}) {
  const queryClient = useQueryClient()
  const resolved = post.status === "RESOLVED"
  const [threadOpen, setThreadOpen] = useState(false)
  const [draft, setDraft] = useState("")
  const [commentError, setCommentError] = useState<string | null>(null)

  const commentsQuery = useQuery({
    queryKey: ["posts", post.id, "comments"],
    queryFn: () => listBoardComments(post.id),
    enabled: threadOpen,
  })

  const commentMutation = useMutation({
    mutationFn: () => createBoardComment(post.id, draft.trim()),
    onSuccess: async () => {
      setDraft("")
      setCommentError(null)
      await queryClient.invalidateQueries({ queryKey: ["posts", post.id, "comments"] })
      onCommentsChanged()
    },
    onError: (error) => {
      setCommentError(getApiErrorMessage(error, "Nie udało się dodać komentarza"))
    },
  })

  const comments = commentsQuery.data ?? []

  return (
    <Card className={cn(resolved && "opacity-70")}>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant="outline" className="text-[10px]">
              {categoryLabel(post.category)}
            </Badge>
            <Badge variant="secondary" className="text-[10px]">
              {post.scope === "CAMPUS" ? "Kampus" : "Mój DS"}
            </Badge>
            {resolved ? (
              <Badge variant="outline" className="text-[10px]">
                Rozwiązane
              </Badge>
            ) : null}
          </div>
          <span className="text-[11px] text-muted-foreground">
            {formatWhen(post.createdAt)}
          </span>
        </div>
        <CardTitle className="text-base mt-2">{post.title}</CardTitle>
        <CardDescription className="text-xs">{authorLine(post)}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm text-foreground/90 leading-relaxed whitespace-pre-wrap">
          {post.content}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setThreadOpen((o) => !o)}
          >
            {threadOpen
              ? "Ukryj komentarze"
              : `Komentarze (${post.commentCount})`}
          </Button>
          {post.mine ? (
            <>
              {!resolved ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={resolving || deleting}
                  onClick={onResolve}
                >
                  Oznacz rozwiązane
                </Button>
              ) : null}
              <Button
                type="button"
                size="sm"
                variant="destructive"
                disabled={resolving || deleting}
                onClick={onDelete}
              >
                Usuń
              </Button>
            </>
          ) : null}
        </div>

        {threadOpen ? (
          <div className="rounded-lg border border-border bg-muted/20 p-3 space-y-3">
            {commentsQuery.isLoading ? (
              <p className="text-xs text-muted-foreground">Ładowanie komentarzy…</p>
            ) : commentsQuery.isError ? (
              <p className="text-xs text-destructive">
                {getApiErrorMessage(commentsQuery.error, "Nie udało się pobrać komentarzy")}
              </p>
            ) : comments.length === 0 ? (
              <p className="text-xs text-muted-foreground">Brak komentarzy — napisz pierwszy.</p>
            ) : (
              <ul className="space-y-2">
                {comments.map((c) => (
                  <li
                    key={c.id}
                    className="rounded-md border border-border bg-background px-2.5 py-2"
                  >
                    <p className="text-[11px] text-muted-foreground">
                      {commentAuthorLine(c, post.scope)} · {formatWhen(c.createdAt)}
                    </p>
                    <p className="text-sm whitespace-pre-wrap mt-0.5">{c.content}</p>
                  </li>
                ))}
              </ul>
            )}
            <div className="space-y-2">
              <Textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={2}
                maxLength={2000}
                placeholder="Napisz komentarz…"
              />
              {commentError ? (
                <p className="text-xs text-destructive">{commentError}</p>
              ) : null}
              <Button
                type="button"
                size="sm"
                disabled={commentMutation.isPending || !draft.trim()}
                onClick={() => commentMutation.mutate()}
              >
                {commentMutation.isPending ? "Wysyłam…" : "Wyślij komentarz"}
              </Button>
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
