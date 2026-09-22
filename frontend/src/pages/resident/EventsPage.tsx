import { useOutletContext } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Megaphone } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import { listEvents } from "@/api/events"
import type { DormEvent, DormEventPriority, UserProfile } from "@/api/types"
import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

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

function priorityLabel(priority: DormEventPriority): string {
  switch (priority) {
    case "CRITICAL":
      return "Krytyczny"
    case "WARNING":
      return "Ostrzeżenie"
    default:
      return "Informacja"
  }
}

function eventScopeBadge(event: DormEvent): {
  label: string
  variant: "default" | "secondary"
} {
  if (event.dormitoryId == null) {
    return { label: "AOS · kampus", variant: "secondary" }
  }
  return { label: "ADS / Portiernia · Twój DS", variant: "default" }
}

export function EventsPage() {
  const user = useOutletContext<UserProfile>()

  const eventsQuery = useQuery({
    queryKey: ["events", "feed"],
    queryFn: listEvents,
  })

  const events = eventsQuery.data ?? []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight flex items-center gap-2">
          <Megaphone className="size-5 text-primary" />
          Komunikaty
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          Oficjalne ogłoszenia ADS, portierni oraz AOS
          {user.dormitoryName ? ` — ${user.dormitoryName}` : ""}.
        </p>
      </div>

      {eventsQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Ładowanie…</p>
      )}
      {eventsQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(eventsQuery.error)}
        </p>
      )}
      {events.length === 0 && !eventsQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Brak oficjalnych komunikatów.</p>
      )}

      {events.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {events.map((event) => {
            const scopeBadge = eventScopeBadge(event)
            return (
              <Card key={event.id} className="border-border/70 shadow-none">
                <CardHeader className="pb-2">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge variant={scopeBadge.variant} className="text-[10px]">
                        {scopeBadge.label}
                      </Badge>
                      <Badge
                        variant={
                          event.priority === "CRITICAL" ? "destructive" : "outline"
                        }
                        className="text-[10px]"
                      >
                        {priorityLabel(event.priority)}
                      </Badge>
                    </div>
                    <span className="text-[11px] text-muted-foreground">
                      {formatWhen(event.eventDate)}
                    </span>
                  </div>
                  <CardTitle className="text-base mt-2">{event.title}</CardTitle>
                  {event.authorName ? (
                    <CardDescription className="text-xs">
                      Dodał(a): {event.authorName}
                    </CardDescription>
                  ) : null}
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-foreground/90 leading-relaxed whitespace-pre-wrap">
                    {event.description}
                  </p>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
