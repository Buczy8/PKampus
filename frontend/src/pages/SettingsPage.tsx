import { useOutletContext } from "react-router-dom"
import { Check, Lock, Monitor, Moon, Palette, Phone, Sun, User } from "lucide-react"
import { useTheme } from "next-themes"

import type { UserProfile } from "@/api/types"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "cn"

export function SettingsPage() {
  const user = useOutletContext<UserProfile>()
  const { theme, setTheme } = useTheme()
  const initials = `${user.firstName?.[0] ?? ""}${user.lastName?.[0] ?? ""}`.toUpperCase() || "M"

  const themes = [
    {
      id: "light",
      name: "Jasny",
      desc: "Klasyczny, jasny motyw",
      icon: Sun,
      color: "text-amber-500",
    },
    {
      id: "dark",
      name: "Ciemny",
      desc: "Mniejsze zmęczenie oczu w nocy",
      icon: Moon,
      color: "text-blue-400",
    },
    {
      id: "system",
      name: "Systemowy",
      desc: "Zgodny z ustawieniami urządzenia",
      icon: Monitor,
      color: "text-muted-foreground",
    },
  ]

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h2 className="text-xl font-bold tracking-tight text-foreground">
          Ustawienia Profilu i Konta
        </h2>
        <p className="text-sm text-muted-foreground">
          Zarządzaj swoimi danymi kontaktowymi, preferencjami wyglądu oraz zabezpieczeniami konta w PKampus.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Profile Card */}
        <Card className="md:col-span-1 flex flex-col items-center p-6 text-center h-fit">
          <Avatar size="lg" className="size-20 ring-4 ring-border mb-3">
            {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt={user.firstName} />}
            <AvatarFallback className="text-xl font-bold bg-primary/10 text-primary">
              {initials}
            </AvatarFallback>
          </Avatar>
          <h3 className="font-bold text-base">
            {user.firstName} {user.lastName}
          </h3>
          <p className="text-xs text-muted-foreground">{user.email}</p>

          <div className="mt-4 flex flex-col gap-1.5 w-full text-xs">
            <div className="flex justify-between py-1 border-b border-border/60">
              <span className="text-muted-foreground">Akademik</span>
              <span className="font-semibold">{user.dormitoryName ?? "—"}</span>
            </div>
            <div className="flex justify-between py-1 border-b border-border/60">
              <span className="text-muted-foreground">Numer pokoju</span>
              <span className="font-semibold">{user.roomNumber ?? "—"}</span>
            </div>
            <div className="flex justify-between py-1">
              <span className="text-muted-foreground">Status konta</span>
              <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-600 border-emerald-500/30">
                {user.status}
              </Badge>
            </div>
          </div>
        </Card>

        {/* Form and Appearance sections */}
        <div className="md:col-span-2 space-y-6">
          {/* Appearance / Theme */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Palette className="size-4 text-primary" />
                Motyw i Wygląd
              </CardTitle>
              <CardDescription>
                Wybierz preferowany schemat kolorystyczny aplikacji
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {themes.map((t) => {
                  const Icon = t.icon
                  const isActive = theme === t.id
                  return (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => setTheme(t.id)}
                      className={cn(
                        "relative flex flex-col items-start p-3.5 rounded-xl border text-left transition-all cursor-pointer",
                        isActive
                          ? "border-primary bg-primary/5 ring-2 ring-primary/20 shadow-xs"
                          : "border-border hover:border-muted-foreground/40 hover:bg-muted/30"
                      )}
                    >
                      <div className="flex items-center justify-between w-full mb-2">
                        <div className={cn("size-8 rounded-lg bg-muted flex items-center justify-center", t.color)}>
                          <Icon className="size-4.5" />
                        </div>
                        {isActive && (
                          <span className="flex size-5 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-xs">
                            <Check className="size-3" />
                          </span>
                        )}
                      </div>
                      <span className="font-semibold text-sm text-foreground">
                        {t.name}
                      </span>
                      <span className="text-[11px] text-muted-foreground mt-0.5 leading-tight">
                        {t.desc}
                      </span>
                    </button>
                  )
                })}
              </div>
            </CardContent>
          </Card>

          {/* Contact Information */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <User className="size-4 text-primary" />
                Dane kontaktowe
              </CardTitle>
              <CardDescription>
                Numer telefonu widoczny dla administracji oraz przy rezerwacji salek
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="phone">Numer telefonu</Label>
                <div className="relative">
                  <Phone className="size-4 absolute left-3 top-3 text-muted-foreground" />
                  <Input
                    id="phone"
                    defaultValue={user.phoneNumber}
                    className="pl-9"
                    placeholder="+48 123 456 789"
                  />
                </div>
              </div>
            </CardContent>
            <CardFooter className="pt-2">
              <Button size="sm">Zapisz zmiany</Button>
            </CardFooter>
          </Card>

          {/* Security */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base flex items-center gap-2">
                <Lock className="size-4 text-primary" />
                Bezpieczeństwo konta
              </CardTitle>
              <CardDescription>
                Zmiana hasła dostępowego do PKampus
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="old-pass">Aktualne hasło</Label>
                <Input id="old-pass" type="password" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="new-pass">Nowe hasło</Label>
                <Input id="new-pass" type="password" />
              </div>
            </CardContent>
            <CardFooter className="pt-2">
              <Button variant="outline" size="sm">
                Zaktualizuj hasło
              </Button>
            </CardFooter>
          </Card>
        </div>
      </div>
    </div>
  )
}
