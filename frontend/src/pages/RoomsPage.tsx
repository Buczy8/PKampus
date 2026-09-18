import { useOutletContext } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { Calendar, DoorClosed, Info } from "lucide-react"

import { getApiErrorMessage } from "@/api/errors"
import { listThematicRooms } from "@/api/rooms"
import type { ThematicRoom, UserProfile } from "@/api/types"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"

function formatHours(room: ThematicRoom): string {
  const open = room.openingTime.slice(0, 5)
  const close = room.closingTime.slice(0, 5)
  if (room.spansMidnight) {
    return `${open}–${close} (+1)`
  }
  return `max ${room.maxDurationHours} h · ${open}–${close}`
}

export function RoomsPage() {
  const user = useOutletContext<UserProfile>()

  const roomsQuery = useQuery({
    queryKey: ["rooms", "catalog"],
    queryFn: listThematicRooms,
  })

  const rooms = roomsQuery.data ?? []

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <DoorClosed className="size-5 text-muted-foreground" />
            Salki Tematyczne — {user.dormitoryName ?? "Twój akademik"}
          </h2>
          <p className="text-sm text-muted-foreground">
            Zgodnie z Regulaminem OS PK jako Organizator odpowiadasz za porządek i stan wyposażenia.
          </p>
        </div>
      </div>

      <div className="flex items-start gap-3 p-4 rounded-xl border border-border bg-muted/40 text-sm">
        <Info className="size-5 text-muted-foreground shrink-0 mt-0.5" />
        <div>
          <strong>Odpowiedzialność Organizatora:</strong> Rezerwując salkę deklarujesz liczbę
          uczestników, cel rezerwacji oraz akceptujesz regulamin. Odbiór klucza na portierni w ciągu
          15 min. Rezerwacja online — wkrótce.
        </div>
      </div>

      {roomsQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">Ładowanie katalogu…</p>
      ) : roomsQuery.isError ? (
        <p className="text-sm text-destructive">{getApiErrorMessage(roomsQuery.error)}</p>
      ) : rooms.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Brak dostępnych salek w Twoim akademiku.
        </p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {rooms.map((room) => (
            <Card key={room.id} className="flex flex-col justify-between">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="size-9 rounded-lg bg-muted text-muted-foreground flex items-center justify-center">
                    <DoorClosed className="size-5" />
                  </div>
                  <Badge variant="outline" className="text-xs">
                    Do {room.maxCapacity} osób
                  </Badge>
                </div>
                <CardTitle className="text-base mt-2">{room.name}</CardTitle>
                <CardDescription className="line-clamp-2">
                  {room.description || formatHours(room)}
                </CardDescription>
                <p className="text-xs text-muted-foreground pt-1">{formatHours(room)}</p>
              </CardHeader>
              <CardFooter className="pt-2">
                <Button variant="outline" size="sm" className="w-full gap-2" disabled>
                  <Calendar className="size-3.5" />
                  <span>Rezerwacja wkrótce</span>
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
