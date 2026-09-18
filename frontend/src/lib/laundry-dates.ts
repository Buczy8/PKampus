import type { LaundryBooking } from '@/api/types'

const WARSAW = 'Europe/Warsaw'

/** Calendar date YYYY-MM-DD in Europe/Warsaw. */
export function warsawDateString(date: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: WARSAW,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function addCalendarDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const utc = new Date(Date.UTC(y, m - 1, d + days))
  const yyyy = utc.getUTCFullYear()
  const mm = String(utc.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(utc.getUTCDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** Next `count` calendar days starting today (Warsaw), as YYYY-MM-DD. */
export function warsawDayRange(count: number): string[] {
  const result: string[] = [warsawDateString()]
  while (result.length < count) {
    result.push(addCalendarDays(result[result.length - 1], 1))
  }
  return result
}

export function formatWarsawTimeRange(startIso: string, endIso: string): string {
  const timeFmt = new Intl.DateTimeFormat('pl-PL', {
    timeZone: WARSAW,
    hour: '2-digit',
    minute: '2-digit',
  })
  return `${timeFmt.format(new Date(startIso))} – ${timeFmt.format(new Date(endIso))}`
}

export function formatWarsawDateTime(iso: string): string {
  return new Intl.DateTimeFormat('pl-PL', {
    timeZone: WARSAW,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function formatDayChipLabel(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const utcNoon = new Date(Date.UTC(y, m - 1, d, 12))
  return new Intl.DateTimeFormat('pl-PL', {
    timeZone: 'UTC',
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(utcNoon)
}

function warsawMidnightInstant(isoDate: string): Date {
  const [y, m, d] = isoDate.split('-').map(Number)
  const asIfUtcMidnight = Date.UTC(y, m - 1, d, 0, 0, 0)
  const shown = new Intl.DateTimeFormat('en-US', {
    timeZone: WARSAW,
    hour: 'numeric',
    minute: 'numeric',
    hourCycle: 'h23',
  }).formatToParts(new Date(asIfUtcMidnight))
  const sh = Number(shown.find((p) => p.type === 'hour')?.value)
  const sm = Number(shown.find((p) => p.type === 'minute')?.value)
  return new Date(asIfUtcMidnight - (sh * 60 + sm) * 60 * 1000)
}

export function countActiveBookingsInRollingWindow(
  bookings: LaundryBooking[],
  fromInstant: Date = new Date(),
): number {
  const center = warsawDateString(fromInstant)
  const windowStart = warsawMidnightInstant(addCalendarDays(center, -6))
  const windowEnd = warsawMidnightInstant(addCalendarDays(center, 7))

  return bookings.filter((b) => {
    if (b.status !== 'CONFIRMED' && b.status !== 'KEY_ISSUED') return false
    const start = new Date(b.startTime)
    return start >= windowStart && start < windowEnd
  }).length
}

export function isBookingCancellable(
  booking: LaundryBooking,
  now = new Date(),
): boolean {
  return booking.status === 'CONFIRMED' && new Date(booking.startTime) > now
}
