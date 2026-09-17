export interface ActiveLaundry {
  id: string
  machineName: string
  slotTime: string
  date: string
  status: "CONFIRMED" | "KEY_ISSUED"
  pickupDeadline: string
}

export interface ActiveRoom {
  id: string
  roomName: string
  timeRange: string
  date: string
  participants: number
  status: "CONFIRMED" | "KEY_ISSUED"
}

export interface ActiveIssue {
  id: string
  title: string
  category: string
  location: string
  status: "NEW" | "ASSIGNED" | "IN_PROGRESS"
  statusLabel: string
  lastNote?: string
}

export interface ActiveAnnouncement {
  id: string
  title: string
  author: string
  date: string
  content: string
  isUrgent?: boolean
}

export type AgendaReservation =
  | { kind: "laundry"; item: ActiveLaundry }
  | { kind: "room"; item: ActiveRoom }

export interface DashboardAgenda {
  banner: ActiveAnnouncement | null
  todayHero: AgendaReservation | null
  todayRest: AgendaReservation[]
  tomorrow: AgendaReservation[]
  openIssues: ActiveIssue[]
}

function isToday(date: string): boolean {
  return date.toLowerCase() === "dzisiaj"
}

function isTomorrow(date: string): boolean {
  return date.toLowerCase() === "jutro"
}

function reservationUrgency(entry: AgendaReservation): number {
  if (entry.kind === "laundry" && entry.item.pickupDeadline) return 0
  if (entry.kind === "laundry") return 1
  return 2
}

/** Leading time label for list rows (e.g. "14:00" from "14:00 – 17:00"). */
export function leadingTime(range: string): string {
  const match = range.match(/^\s*(\d{1,2}:\d{2})/)
  return match?.[1] ?? range
}

export function groupDashboardAgenda(input: {
  laundry: ActiveLaundry | null
  room: ActiveRoom | null
  issue: ActiveIssue | null
  announcement: ActiveAnnouncement | null
}): DashboardAgenda {
  const today: AgendaReservation[] = []
  const tomorrow: AgendaReservation[] = []

  if (input.laundry) {
    const entry: AgendaReservation = { kind: "laundry", item: input.laundry }
    if (isToday(input.laundry.date)) today.push(entry)
    else if (isTomorrow(input.laundry.date)) tomorrow.push(entry)
    else tomorrow.push(entry)
  }

  if (input.room) {
    const entry: AgendaReservation = { kind: "room", item: input.room }
    if (isToday(input.room.date)) today.push(entry)
    else if (isTomorrow(input.room.date)) tomorrow.push(entry)
    else tomorrow.push(entry)
  }

  today.sort((a, b) => reservationUrgency(a) - reservationUrgency(b))

  const todayHero = today[0] ?? null
  const todayRest = today.slice(1)

  return {
    banner: input.announcement,
    todayHero,
    todayRest,
    tomorrow,
    openIssues: input.issue ? [input.issue] : [],
  }
}

export function hasAgendaItems(agenda: DashboardAgenda): boolean {
  return Boolean(
    agenda.todayHero ||
      agenda.todayRest.length ||
      agenda.tomorrow.length ||
      agenda.openIssues.length
  )
}
