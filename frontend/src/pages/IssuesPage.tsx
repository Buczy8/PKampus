import { useOutletContext } from "react-router-dom"
import { AlertCircle, Camera, CheckCircle2, Plus, Wrench } from "lucide-react"

import type { UserProfile } from "@/api/types"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export function IssuesPage() {
  const user = useOutletContext<UserProfile>()

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Wrench className="size-5 text-amber-500" />
            Zgłoszenia Usterek (Rejestr Konserwatora)
          </h2>
          <p className="text-sm text-muted-foreground">
            Zgłaszaj awarie hydrauliczne, elektryczne, stolarskie i ślusarskie z załączonym zdjęciem do MinIO S3.
          </p>
        </div>

        <Button className="gap-2 bg-amber-600 hover:bg-amber-700 text-white">
          <Plus className="size-4" />
          <span>Nowe zgłoszenie awarii</span>
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Moje zgłoszenia</CardTitle>
            <CardDescription>
              Historia spraw dla pokoju {user.roomNumber ?? "—"} oraz zgłoszeń w częściach wspólnych
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
            <CheckCircle2 className="size-12 stroke-1 mb-3 text-emerald-500/60" />
            <p className="font-medium text-foreground">Brak zarejestrowanych awarii</p>
            <p className="text-xs max-w-sm mt-1">
              Gdy zgłosisz usterkę, będziesz mógł śledzić jej status w czasie rzeczywistym oraz notatki konserwatora (FR-ISSUE-05).
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Zasady zgłaszania</CardTitle>
            <CardDescription>
              Wskazówki dla mieszkańców
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs text-muted-foreground">
            <div className="flex items-start gap-2">
              <Camera className="size-4 text-primary shrink-0 mt-0.5" />
              <span>
                <strong>Zdjęcia awarii:</strong> Dołącz wyraźne zdjęcie uszkodzenia (formaty JPEG, PNG, WebP do 5 MB).
              </span>
            </div>
            <div className="flex items-start gap-2">
              <AlertCircle className="size-4 text-amber-500 shrink-0 mt-0.5" />
              <span>
                <strong>Pilność URGENT:</strong> Oznacz jako pilne zalania, spięcia instalacji elektrycznej oraz uszkodzenia zamków w drzwiach.
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
