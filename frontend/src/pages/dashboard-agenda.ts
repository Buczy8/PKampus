import type {
  Issue,
  IssueCategory,
  IssueStatus,
  LaundryBooking,
  RoomBooking,
} from "@/api/types"
import {
  formatWarsawTimeRange,
  warsawDateOf,
  warsawDateString,
} from "@/lib/laundry-dates"

export type DayBucket = "today" | "tomorrow" | "upcoming"

export interface ActiveLaundry {
  id: string
  machineName: string
  slotTime: string
  /** YYYY-MM-DD in Europe/Warsaw */
  dateKey: string
  dayBucket: DayBucket
  startMs: number
  status: "CONFIRMED" | "KEY_ISSUED"
  /** HH:mm when CONFIRMED (15-min key pickup rule); empty when KEY_ISSUED */
  pickupDeadline: string
}

export interface ActiveRoom {
  id: string
  roomName: string
  timeRange: string
  dateKey: string
  dayBucket: DayBucket
  startMs: number
  participants: number
  status: "CONFIRMED" | "KEY_ISSUED"
}

export interface ActiveIssue {
  id: string
  title: string
  category: string
  location: string
  status: IssueStatus
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
  upcoming: AgendaReservation[]
  openIssues: ActiveIssue[]
}

const OPEN_ISSUE_STATUSES: ReadonlySet<IssueStatus> = new Set([
  "NEW",
  "ASSIGNED_TO_MAINTENANCE",
  "IN_PROGRESS",
  "PARTS_REQUIRED",
])

const CATEGORY_LABELS: Record<IssueCategory, string> = {
  PLUMBING: "Hydraulika",
  ELECTRICAL: "Elektryka",
  FURNITURE: "Meble / wyposażenie",
  LOCKSMITH: "Ślusarstwo / zamki",
  OTHER: "Inne",
}

function addCalendarDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number)
  const utc = new Date(Date.UTC(y, m - 1, d + days))
  const yyyy = utc.getUTCFullYear()
  const mm = String(utc.getUTCMonth() + 1).padStart(2, "0")
  const dd = String(utc.getUTCDate()).padStart(2, "0")
  return `${yyyy}-${mm}-${dd}`
}

function dayBucketFor(dateKey: string, todayKey: string): DayBucket {
  if (dateKey === todayKey) return "today"
  if (dateKey === addCalendarDays(todayKey, 1)) return "tomorrow"
  return "upcoming"
}

function formatWarsawClock(iso: string): string {
  return new Intl.DateTimeFormat("pl-PL", {
    timeZone: "Europe/Warsaw",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(iso))
}

function issueStatusLabel(status: IssueStatus): string {
  switch (status) {
    case "NEW":
      return "Nowe"
    case "ASSIGNED_TO_MAINTENANCE":
      return "Przekazane konserwatorowi"
    case "IN_PROGRESS":
      return "W trakcie naprawy"
    case "PARTS_REQUIRED":
      return "Wymaga części"
    case "RESOLVED":
      return "Naprawione"
    case "REJECTED":
      return "Odrzucone"
    default:
      return status
  }
}

function issueTitle(description: string): string {
  const trimmed = description.trim()
  if (trimmed.length <= 80) return trimmed
  return `${trimmed.slice(0, 77)}…`
}

export function mapLaundryBooking(
  booking: LaundryBooking,
  todayKey: string = warsawDateString(),
): ActiveLaundry | null {
  if (booking.status !== "CONFIRMED" && booking.status !== "KEY_ISSUED") {
    return null
  }
  const dateKey = warsawDateOf(booking.startTime)
  const pickupDeadline =
    booking.status === "CONFIRMED"
      ? formatWarsawClock(
          new Date(new Date(booking.startTime).getTime() + 15 * 60_000).toISOString(),
        )
      : ""

  return {
    id: booking.id,
    machineName: booking.machineIdentifier,
    slotTime: formatWarsawTimeRange(booking.startTime, booking.endTime),
    dateKey,
    dayBucket: dayBucketFor(dateKey, todayKey),
    startMs: new Date(booking.startTime).getTime(),
    status: booking.status,
    pickupDeadline,
  }
}

export function mapRoomBooking(
  booking: RoomBooking,
  todayKey: string = warsawDateString(),
): ActiveRoom | null {
  if (booking.status !== "CONFIRMED" && booking.status !== "KEY_ISSUED") {
    return null
  }
  const dateKey = warsawDateOf(booking.startTime)
  return {
    id: booking.id,
    roomName: booking.roomName,
    timeRange: formatWarsawTimeRange(booking.startTime, booking.endTime),
    dateKey,
    dayBucket: dayBucketFor(dateKey, todayKey),
    startMs: new Date(booking.startTime).getTime(),
    participants: booking.participantsCount,
    status: booking.status,
  }
}

export function mapOpenIssue(issue: Issue): ActiveIssue | null {
  if (!OPEN_ISSUE_STATUSES.has(issue.status)) return null
  return {
    id: issue.id,
    title: issueTitle(issue.description),
    category: CATEGORY_LABELS[issue.category] ?? issue.category,
    location: issue.locationLabel,
    status: issue.status,
    statusLabel: issueStatusLabel(issue.status),
    lastNote: issue.staffNotes ?? undefined,
  }
}

function reservationUrgency(entry: AgendaReservation): number {
  if (entry.kind === "laundry" && entry.item.pickupDeadline) return 0
  if (entry.kind === "laundry") return 1
  return 2
}

function sortReservations(a: AgendaReservation, b: AgendaReservation): number {
  const byTime = a.item.startMs - b.item.startMs
  if (byTime !== 0) return byTime
  return reservationUrgency(a) - reservationUrgency(b)
}

/** Leading time label for list rows (e.g. "14:00" from "14:00 – 17:00"). */
export function leadingTime(range: string): string {
  const match = range.match(/^\s*(\d{1,2}:\d{2})/)
  return match?.[1] ?? range
}

export function groupDashboardAgenda(input: {
  laundry: ActiveLaundry[]
  rooms: ActiveRoom[]
  issues: ActiveIssue[]
  announcement: ActiveAnnouncement | null
}): DashboardAgenda {
  const today: AgendaReservation[] = []
  const tomorrow: AgendaReservation[] = []
  const upcoming: AgendaReservation[] = []

  for (const item of input.laundry) {
    const entry: AgendaReservation = { kind: "laundry", item }
    if (item.dayBucket === "today") today.push(entry)
    else if (item.dayBucket === "tomorrow") tomorrow.push(entry)
    else upcoming.push(entry)
  }

  for (const item of input.rooms) {
    const entry: AgendaReservation = { kind: "room", item }
    if (item.dayBucket === "today") today.push(entry)
    else if (item.dayBucket === "tomorrow") tomorrow.push(entry)
    else upcoming.push(entry)
  }

  today.sort(sortReservations)
  tomorrow.sort(sortReservations)
  upcoming.sort(sortReservations)

  // Hero: prefer laundry with pickup deadline among today's items (same urgency order).
  const todaySortedByUrgency = [...today].sort(
    (a, b) => reservationUrgency(a) - reservationUrgency(b) || a.item.startMs - b.item.startMs,
  )
  const todayHero = todaySortedByUrgency[0] ?? null
  const todayRest = today.filter((e) => e !== todayHero)

  return {
    banner: input.announcement,
    todayHero,
    todayRest,
    tomorrow,
    upcoming,
    openIssues: input.issues,
  }
}

export function hasAgendaItems(agenda: DashboardAgenda): boolean {
  return Boolean(
    agenda.todayHero ||
      agenda.todayRest.length ||
      agenda.tomorrow.length ||
      agenda.upcoming.length ||
      agenda.openIssues.length,
  )
}
