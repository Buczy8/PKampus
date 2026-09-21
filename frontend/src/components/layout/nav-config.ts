import type { ComponentType } from "react"
import {
  Building2,
  DoorClosed,
  IdCard,
  LayoutDashboard,
  Megaphone,
  MessageSquare,
  Settings,
  Shirt,
  UserCog,
  Users,
  Waves,
  Wrench,
} from "lucide-react"

import {
  isDormAdminRole,
  isReceptionistRole,
  isSuperAdminRole,
} from "@/api/types"

export interface NavItem {
  title: string
  to: string
  icon: ComponentType<{ className?: string }>
}

export interface NavSection {
  label: string
  items: NavItem[]
}

export const residentNavSections: NavSection[] = [
  {
    label: "Główne",
    items: [
      { title: "Pulpit", to: "/dashboard", icon: LayoutDashboard },
      { title: "Karta Mieszkańca", to: "/card", icon: IdCard },
    ],
  },
  {
    label: "Usługi i rezerwacje",
    items: [
      { title: "Pralnia", to: "/laundry", icon: Waves },
      { title: "Salki tematyczne", to: "/rooms", icon: DoorClosed },
      { title: "Zgłoszenia usterek", to: "/issues", icon: Wrench },
    ],
  },
  {
    label: "Społeczność",
    items: [
      { title: "Komunikaty", to: "/events", icon: Megaphone },
      { title: "Tablica", to: "/board", icon: MessageSquare },
    ],
  },
  {
    label: "Konto",
    items: [{ title: "Ustawienia", to: "/settings", icon: Settings }],
  },
]

export const receptionistNavSections: NavSection[] = [
  {
    label: "Portiernia",
    items: [
      { title: "Pulpit", to: "/receptionist", icon: LayoutDashboard },
      { title: "Pralnia", to: "/receptionist/laundry", icon: Waves },
      { title: "Salki", to: "/receptionist/rooms", icon: DoorClosed },
      { title: "Usterki", to: "/receptionist/issues", icon: Wrench },
      { title: "Komunikaty", to: "/receptionist/events", icon: Megaphone },
      { title: "Tablica", to: "/receptionist/board", icon: MessageSquare },
    ],
  },
  {
    label: "Konto",
    items: [{ title: "Ustawienia", to: "/settings", icon: Settings }],
  },
]

export const dormAdminNavSections: NavSection[] = [
  {
    label: "Administracja DS",
    items: [
      { title: "Mieszkańcy", to: "/admin/residents", icon: Users },
      { title: "Meldunki", to: "/admin/checkins", icon: UserCog },
      { title: "Pokoje", to: "/admin/dorm-rooms", icon: Building2 },
      { title: "Salki", to: "/admin/rooms", icon: DoorClosed },
      { title: "Pralki", to: "/admin/laundry", icon: Shirt },
      { title: "Komunikaty", to: "/admin/events", icon: Megaphone },
      { title: "Portierzy", to: "/admin/porters", icon: IdCard },
    ],
  },
  {
    label: "Konto",
    items: [{ title: "Ustawienia", to: "/settings", icon: Settings }],
  },
]

export const superAdminNavSections: NavSection[] = [
  {
    label: "AOS",
    items: [
      { title: "Akademiki", to: "/superadmin/dormitories", icon: Building2 },
      { title: "Konta ADS", to: "/superadmin/admins", icon: UserCog },
      { title: "Komunikaty", to: "/superadmin/events", icon: Megaphone },
    ],
  },
  {
    label: "Konto",
    items: [{ title: "Ustawienia", to: "/settings", icon: Settings }],
  },
]

export const residentBottomNav: NavItem[] = [
  { title: "Pulpit", to: "/dashboard", icon: LayoutDashboard },
  { title: "Karta", to: "/card", icon: IdCard },
  { title: "Pralnia", to: "/laundry", icon: Waves },
  { title: "Salki", to: "/rooms", icon: DoorClosed },
  { title: "Usterki", to: "/issues", icon: Wrench },
  { title: "Komunikaty", to: "/events", icon: Megaphone },
  { title: "Tablica", to: "/board", icon: MessageSquare },
]

export const receptionistBottomNav: NavItem[] = [
  { title: "Pulpit", to: "/receptionist", icon: LayoutDashboard },
  { title: "Pralnia", to: "/receptionist/laundry", icon: Waves },
  { title: "Usterki", to: "/receptionist/issues", icon: Wrench },
  { title: "Komunikaty", to: "/receptionist/events", icon: Megaphone },
  { title: "Tablica", to: "/receptionist/board", icon: MessageSquare },
]

export const dormAdminBottomNav: NavItem[] = [
  { title: "Mieszkańcy", to: "/admin/residents", icon: Users },
  { title: "Meldunki", to: "/admin/checkins", icon: UserCog },
  { title: "Salki", to: "/admin/rooms", icon: DoorClosed },
  { title: "Komunikaty", to: "/admin/events", icon: Megaphone },
  { title: "Portierzy", to: "/admin/porters", icon: IdCard },
]

export const superAdminBottomNav: NavItem[] = [
  { title: "Akademiki", to: "/superadmin/dormitories", icon: Building2 },
  { title: "ADS", to: "/superadmin/admins", icon: UserCog },
  { title: "Komunikaty", to: "/superadmin/events", icon: Megaphone },
  { title: "Ustawienia", to: "/settings", icon: Settings },
]

export function navSectionsForRole(role: string | undefined | null): NavSection[] {
  if (isSuperAdminRole(role)) return superAdminNavSections
  if (isDormAdminRole(role)) return dormAdminNavSections
  if (isReceptionistRole(role)) return receptionistNavSections
  return residentNavSections
}

export function bottomNavForRole(role: string | undefined | null): NavItem[] {
  if (isSuperAdminRole(role)) return superAdminBottomNav
  if (isDormAdminRole(role)) return dormAdminBottomNav
  if (isReceptionistRole(role)) return receptionistBottomNav
  return residentBottomNav
}

export const routeTitles: Record<string, { title: string; subtitle: string }> = {
  "/dashboard": {
    title: "Kokpit Mieszkańca",
    subtitle: "Przegląd rezerwacji i spraw",
  },
  "/card": {
    title: "Wirtualna Karta Mieszkańca",
    subtitle: "Oficjalny identyfikator OS PK",
  },
  "/laundry": {
    title: "Pralnia",
    subtitle: "Harmonogram i rezerwacje pralek",
  },
  "/rooms": {
    title: "Salki Tematyczne",
    subtitle: "Rezerwacje sal do nauki i relaksu",
  },
  "/issues": {
    title: "Zgłoszenia Usterek",
    subtitle: "Zgłaszaj awarie do konserwatora",
  },
  "/events": {
    title: "Komunikaty",
    subtitle: "Oficjalne ogłoszenia ADS, portierni i AOS",
  },
  "/board": {
    title: "Tablica sąsiedzka",
    subtitle: "Ogłoszenia mieszkańców",
  },
  "/settings": {
    title: "Ustawienia Profilu",
    subtitle: "Dane kontaktowe i bezpieczeństwo",
  },
  "/receptionist": {
    title: "Pulpit portiera",
    subtitle: "Rezerwacje na dziś i usterki",
  },
  "/receptionist/laundry": {
    title: "Pralnia — moderacja",
    subtitle: "Grafik, anulowanie i awarie",
  },
  "/receptionist/rooms": {
    title: "Salki — moderacja",
    subtitle: "Grafik, anulowanie i awarie",
  },
  "/receptionist/issues": {
    title: "Usterki — rejestr",
    subtitle: "Statusy i notatki personelu",
  },
  "/receptionist/events": {
    title: "Komunikaty",
    subtitle: "Oficjalne ogłoszenia portierni",
  },
  "/receptionist/board": {
    title: "Tablica — moderacja",
    subtitle: "Posty mieszkańców DS",
  },
  "/admin/residents": {
    title: "Mieszkańcy",
    subtitle: "Blokady, wymeldowanie, ROOM_BAN",
  },
  "/admin/checkins": {
    title: "Meldunki",
    subtitle: "Wnioski PENDING_APPROVAL",
  },
  "/admin/dorm-rooms": {
    title: "Pokoje",
    subtitle: "Katalog pokoi akademika",
  },
  "/admin/rooms": {
    title: "Salki tematyczne",
    subtitle: "Konfiguracja zasobów DS",
  },
  "/admin/laundry": {
    title: "Pralki",
    subtitle: "Maszyny pralnicze DS",
  },
  "/admin/events": {
    title: "Komunikaty DS",
    subtitle: "Oficjalne ogłoszenia akademika",
  },
  "/admin/porters": {
    title: "Portierzy",
    subtitle: "Konta RECEPTIONIST",
  },
  "/superadmin/dormitories": {
    title: "Akademiki",
    subtitle: "Zarządzanie obiektami OS PK",
  },
  "/superadmin/admins": {
    title: "Konta ADS",
    subtitle: "Administratorzy Domów Studenckich",
  },
  "/superadmin/events": {
    title: "Komunikaty kampusowe",
    subtitle: "Ogłoszenia AOS",
  },
}

export function titleForPath(pathname: string): { title: string; subtitle: string } {
  return (
    routeTitles[pathname] ?? {
      title: "PKampus",
      subtitle: "Portal",
    }
  )
}

/** Paths that should use exact NavLink matching (home of a role). */
export function isNavEndPath(to: string): boolean {
  return (
    to === "/dashboard" ||
    to === "/receptionist" ||
    to === "/admin/residents" ||
    to === "/superadmin/dormitories"
  )
}
