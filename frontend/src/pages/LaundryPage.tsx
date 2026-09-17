import { useOutletContext } from "react-router-dom"
import { Calendar, Clock, Info, Waves } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export function LaundryPage() {
  const user = useOutletContext<UserProfile>()

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Waves className="size-5 text-sky-500" />
            Pralnia — {user.dormitoryName ?? "Twój akademik"}
          </h2>
          <p className="text-sm text-muted-foreground">
            Grafik dostępności maszyn piorących i rezerwacja slotów (max 2 aktywne w tygodniu wg BR-01).
          </p>
        </div>

        <Button className="gap-2">
          <Clock className="size-4" />
          <span>Zarezerwuj pralkę</span>
        </Button>
      </div>

      {/* 15 min rule warning */}
      <div className="flex items-start gap-3 p-4 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-900 dark:text-sky-200 text-sm">
        <Info className="size-5 text-sky-500 shrink-0 mt-0.5" />
        <div>
          <strong>Reguła 15 minut (BR-02):</strong> Pamiętaj, aby odebrać klucz z portierni w ciągu 15 minut od wyznaczonej godziny rozpoczęcia. Po tym czasie slot zostanie automatycznie zwolniony dla innych mieszkańców.
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Grafik slotów pralniczych</CardTitle>
            <CardDescription>
              Widok 7-dniowy dla pralek w Twoim DS
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
            <Calendar className="size-12 stroke-1 mb-3 text-sky-500/60" />
            <p className="font-medium text-foreground">Trwa ładowanie harmonogramu...</p>
            <p className="text-xs max-w-sm mt-1">
              Moduł grafiku pralek (FR-LAUND-02) z ochroną anty-race PostgreSQL EXCLUDE USING gist (ADR-02).
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Moje rezerwacje</CardTitle>
            <CardDescription>
              Limit: 0 / 2 w tym tygodniu
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col items-center justify-center py-8 text-center text-muted-foreground">
            <Clock className="size-8 stroke-1 mb-2 text-muted-foreground/60" />
            <p className="text-xs">Brak zaplanowanych prań</p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
