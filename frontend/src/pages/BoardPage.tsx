import { useOutletContext } from "react-router-dom"
import { Megaphone, Plus } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export function BoardPage() {
  const user = useOutletContext<UserProfile>()

  const announcements = [
    {
      id: "1",
      title: "Przegląd instalacji ppoż i wentylacji",
      author: "Administracja DS",
      date: "Dzisiaj, 09:30",
      category: "ADMIN",
      content: "W najbliższy czwartek w godzinach 10:00–14:00 odbędzie się okresowy przegląd czujników dymu w pokojach. Prosimy o udostępnienie pomieszczeń.",
    },
    {
      id: "2",
      title: "Poszukuję żelazka / deski do prasowania",
      author: "Katarzyna (pok. 312)",
      date: "Wczoraj",
      category: "HELP",
      content: "Czy ktoś na 3. piętrze mógłby pożyczyć żelazko na 30 minut przed obroną projektu? Z góry dziękuję!",
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Megaphone className="size-5 text-rose-500" />
            Tablica Ogłoszeń i Pomoc Sąsiedzka
          </h2>
          <p className="text-sm text-muted-foreground">
            Oficjalne komunikaty Kierownictwa {user.dormitoryName ?? "DS"} oraz ogłoszenia mieszkańców.
          </p>
        </div>

        <Button className="gap-2">
          <Plus className="size-4" />
          <span>Dodaj wpis</span>
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {announcements.map((item) => (
          <Card key={item.id} className="shadow-xs">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between gap-2">
                <Badge
                  variant={item.category === "ADMIN" ? "default" : "secondary"}
                  className="text-[10px]"
                >
                  {item.category === "ADMIN" ? "Oficjalny komunikat" : "Pomoc sąsiedzka"}
                </Badge>
                <span className="text-[11px] text-muted-foreground">{item.date}</span>
              </div>
              <CardTitle className="text-base mt-2">{item.title}</CardTitle>
              <CardDescription className="text-xs">Dodał(a): {item.author}</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-foreground/90 leading-relaxed">{item.content}</p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
