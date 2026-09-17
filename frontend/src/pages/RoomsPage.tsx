import { useOutletContext } from "react-router-dom"
import { BookOpen, Calendar, DoorClosed, Gamepad2, Info, Users } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"

export function RoomsPage() {
  const user = useOutletContext<UserProfile>()

  const sampleRooms = [
    {
      id: "1",
      name: "Salka Cichej Nauki „Kujon”",
      type: "QUIET_STUDY_KUJON",
      capacity: 16,
      maxHours: 4,
      icon: BookOpen,
      desc: "Wyciszona przestrzeń ze stanowiskami do nauki indywidualnej i grupowej. Obowiązuje cisza.",
    },
    {
      id: "2",
      name: "Strefa Relaksu „Chillout”",
      type: "CHILLOUT",
      capacity: 30,
      maxHours: "14:00 - 02:00",
      icon: Users,
      desc: "Spotkania integracyjne mieszkańców. Rezerwacja nocna z regulaminowym zwrotem klucza do 10:00 rano.",
    },
    {
      id: "3",
      name: "Salka Audio-Video / Bilard",
      type: "STANDARD",
      capacity: 14,
      maxHours: 4,
      icon: Gamepad2,
      desc: "Projektor multimedialny, stół bilardowy oraz konsola do gier.",
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <DoorClosed className="size-5 text-purple-500" />
            Salki Tematyczne — {user.dormitoryName ?? "Twój akademik"}
          </h2>
          <p className="text-sm text-muted-foreground">
            Zgodnie z Regulaminem OS PK jako Organizator odpowiadasz za porządek i stan wyposażenia.
          </p>
        </div>
      </div>

      <div className="flex items-start gap-3 p-4 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-900 dark:text-purple-200 text-sm">
        <Info className="size-5 text-purple-500 shrink-0 mt-0.5" />
        <div>
          <strong>Odpowiedzialność Organizatora (FR-ROOM-03):</strong> Rezerwując salkę deklarujesz liczbę uczestników, cel rezerwacji oraz akceptujesz regulamin i cennik szkód. Odbiór klucza na portierni w ciągu 15 min.
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {sampleRooms.map((room) => {
          const Icon = room.icon
          return (
            <Card key={room.id} className="flex flex-col justify-between">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="size-9 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center">
                    <Icon className="size-5" />
                  </div>
                  <Badge variant="outline" className="text-xs">
                    Do {room.capacity} osób
                  </Badge>
                </div>
                <CardTitle className="text-base mt-2">{room.name}</CardTitle>
                <CardDescription className="line-clamp-2">{room.desc}</CardDescription>
              </CardHeader>
              <CardFooter className="pt-2">
                <Button variant="outline" size="sm" className="w-full gap-2">
                  <Calendar className="size-3.5" />
                  <span>Sprawdź grafik</span>
                </Button>
              </CardFooter>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
