export interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
  timestamp: string
}

export interface HealthData {
  status: string
  system: string
  version: string
  serverTime: string
}

export interface UserProfile {
  id: string
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  avatarUrl?: string | null
  role: string
  status: string
  dormitoryId?: string | null
  dormitoryName?: string | null
  roomNumber?: string | null
  createdAt: string
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  refreshToken: string
  refreshExpiresInSeconds: number
  user: UserProfile
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phoneNumber: string
  dormitoryId: string
  declaredRoomNumber: string
}

export interface RegisterResponse {
  message: string
  email: string
}

export interface Dormitory {
  id: string
  name: string
  code: string
  address: string
  floorsCount: number
}

export interface PendingResident {
  id: string
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  declaredRoomNumber: string
  dormitoryId: string
  dormitoryName: string
  avatarUrl?: string | null
  createdAt: string
}

export interface ActivateResidentRequest {
  roomNumber?: string
}

export interface ActivateResidentResponse {
  message: string
  userId: string
  roomNumber: string
  status: string
  roomAssignmentId?: string
  academicYear?: string
}

export interface RejectResidentRequest {
  reason: string
}

export type LaundryMachineStatus = 'AVAILABLE' | 'OUT_OF_ORDER'

export type LaundrySlotState = 'FREE' | 'OCCUPIED' | 'MINE' | 'UNAVAILABLE'

export type LaundryBookingStatus =
  | 'CONFIRMED'
  | 'KEY_ISSUED'
  | 'COMPLETED'
  | 'CANCELLED_USER'
  | 'AUTO_CANCELLED_15MIN'
  | 'CANCELLED_MACHINE_OUT_OF_ORDER'

export interface LaundryMachine {
  id: string
  identifier: string
  floorLocation: string
  status: LaundryMachineStatus
}

export interface LaundrySlot {
  machineId: string
  startTime: string
  endTime: string
  state: LaundrySlotState
}

export interface LaundryScheduleDay {
  date: string
  slots: LaundrySlot[]
}

export interface LaundrySchedule {
  openingTime: string
  closingTime: string
  slotDurationMinutes: number
  machines: LaundryMachine[]
  days: LaundryScheduleDay[]
}

export interface LaundryBooking {
  id: string
  machineId: string
  machineIdentifier: string
  userId: string
  startTime: string
  endTime: string
  status: LaundryBookingStatus
  createdAt: string
}

export interface CreateLaundryBookingRequest {
  machineId: string
  startTime: string
  endTime: string
}

export const ADMIN_ROLES = ['DORM_ADMIN', 'SUPER_ADMIN'] as const

export function isAdminRole(role: string | undefined | null): boolean {
  return role === 'DORM_ADMIN' || role === 'SUPER_ADMIN'
}

export function isSuperAdminRole(role: string | undefined | null): boolean {
  return role === 'SUPER_ADMIN'
}

export function isDormAdminRole(role: string | undefined | null): boolean {
  return role === 'DORM_ADMIN'
}

export interface SuperAdminDormitory {
  id: string
  name: string
  code: string
  address: string
  floorsCount: number
  laundryOpeningTime: string
  laundryClosingTime: string
  laundrySlotDurationMinutes: number
  createdAt: string
}

export interface CreateDormitoryRequest {
  code: string
  name: string
  address: string
  floorsCount: number
}

export interface UpdateDormitoryRequest {
  code?: string
  name?: string
  address?: string
  floorsCount?: number
  laundryOpeningTime?: string
  laundryClosingTime?: string
  laundrySlotDurationMinutes?: number
}

export interface DormAdminAccount {
  id: string
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  status: string
  dormitoryId: string | null
  dormitoryName: string | null
  dormitoryCode: string | null
  createdAt: string
}

export interface CreateDormAdminRequest {
  firstName: string
  lastName: string
  email: string
  phoneNumber: string
  password: string
  dormitoryId: string
}

export interface UpdateDormAdminRequest {
  dormitoryId?: string
  status?: 'ACTIVE' | 'BLOCKED'
  firstName?: string
  lastName?: string
  phoneNumber?: string
}

export type DormEventCategory = 'BED_LINEN' | 'TECHNICAL_OUTAGE' | 'ADMIN_NOTICE' | 'STUDENT_EVENT'
export type DormEventPriority = 'INFO' | 'WARNING' | 'CRITICAL'

export interface DormEvent {
  id: string
  authorId: string | null
  authorName: string | null
  dormitoryId: string | null
  title: string
  description: string
  category: DormEventCategory
  priority: DormEventPriority
  pinned: boolean
  eventDate: string
  endDate: string | null
  createdAt: string
}

export interface CreateCampusEventRequest {
  title: string
  description: string
  category: Exclude<DormEventCategory, 'STUDENT_EVENT'>
  priority: DormEventPriority
  pinned?: boolean
  eventDate: string
  endDate?: string | null
}

export interface UpdateCampusEventRequest {
  title?: string
  description?: string
  category?: Exclude<DormEventCategory, 'STUDENT_EVENT'>
  priority?: DormEventPriority
  pinned?: boolean
  eventDate?: string
  endDate?: string | null
}

export type ThematicRoomStatus = 'AVAILABLE' | 'MAINTENANCE'

export interface ThematicRoom {
  id: string
  dormitoryId: string
  name: string
  maxCapacity: number
  openingTime: string
  closingTime: string
  spansMidnight: boolean
  maxDurationHours: number
  description: string | null
  status: ThematicRoomStatus
  createdAt: string
}

export interface CreateThematicRoomRequest {
  name: string
  maxCapacity: number
  openingTime: string
  closingTime: string
  spansMidnight?: boolean
  maxDurationHours: number
  description?: string
  status?: ThematicRoomStatus
}

export interface UpdateThematicRoomRequest {
  name?: string
  maxCapacity?: number
  openingTime?: string
  closingTime?: string
  spansMidnight?: boolean
  maxDurationHours?: number
  description?: string | null
  status?: ThematicRoomStatus
}
