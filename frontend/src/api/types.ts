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

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export type UserAccountStatus =
  | 'PENDING_EMAIL'
  | 'PENDING_APPROVAL'
  | 'MUST_CHANGE_PASSWORD'
  | 'ACTIVE'
  | 'BLOCKED'
  | 'CHECKED_OUT'

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

export type ManagedResidentStatus = 'ACTIVE' | 'BLOCKED'

export interface ActiveRoomBan {
  id: string
  startDate: string
  endDate: string
  reason: string
}

export interface ManagedResident {
  id: string
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  roomNumber: string | null
  status: ManagedResidentStatus
  avatarUrl?: string | null
  createdAt: string
  activeRoomBan?: ActiveRoomBan | null
}

export interface CreateRoomBanRequest {
  durationMonths: 1 | 2 | 3
  reason: string
}

export interface Sanction {
  id: string
  userId: string
  sanctionType: 'ROOM_BAN'
  reason: string
  startDate: string
  endDate: string
  active: boolean
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

export interface AdminLaundryMachine {
  id: string
  dormitoryId: string
  machineIdentifier: string
  floorLocation: string
  status: LaundryMachineStatus
  createdAt: string
}

export interface CreateLaundryMachineRequest {
  machineIdentifier: string
  floorLocation: string
}

export interface UpdateLaundryMachineRequest {
  machineIdentifier?: string
  floorLocation?: string
  status?: LaundryMachineStatus
}

export interface LaundrySlot {
  machineId: string
  startTime: string
  endTime: string
  state: LaundrySlotState
  bookingId?: string | null
  residentLabel?: string | null
  bookingStatus?: LaundryBookingStatus | null
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

export function isReceptionistRole(role: string | undefined | null): boolean {
  return role === 'RECEPTIONIST'
}

/** Default landing path after login / for unknown routes. */
export function homePathForRole(role: string | undefined | null): string {
  if (isSuperAdminRole(role)) return '/superadmin'
  if (isAdminRole(role)) return '/admin'
  if (isReceptionistRole(role)) return '/receptionist'
  return '/dashboard'
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

export interface ReceptionistAccount {
  id: string
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  status: string
  dormitoryId: string | null
  dormitoryName: string | null
  createdAt: string
}

export interface CreateReceptionistRequest {
  firstName: string
  lastName: string
  email: string
  phoneNumber: string
  password: string
}

export interface UpdateReceptionistRequest {
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

/** ADS dorm notice — category/pin set server-side (ADMIN_NOTICE, pinned). */
export interface CreateDormEventRequest {
  title: string
  description: string
  priority: DormEventPriority
  eventDate: string
  endDate?: string | null
}

export interface UpdateDormEventRequest {
  title?: string
  description?: string
  priority?: DormEventPriority
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

export type RoomBookingStatus =
  | 'CONFIRMED'
  | 'KEY_ISSUED'
  | 'COMPLETED'
  | 'CANCELLED_USER'
  | 'AUTO_CANCELLED_15MIN'
  | 'CANCELLED_ROOM_MAINTENANCE'

export interface RoomBooking {
  id: string
  roomId: string
  roomName: string
  userId: string
  startTime: string
  endTime: string
  participantsCount: number
  purpose: string
  status: RoomBookingStatus
  createdAt: string
}

export interface CreateRoomBookingRequest {
  roomId: string
  startTime: string
  endTime: string
  participantsCount: number
  purpose: string
  termsAccepted: boolean
}

export interface RoomBusyInterval {
  startTime: string
  endTime: string
}

export interface RoomAvailability {
  roomId: string
  busy: RoomBusyInterval[]
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

export interface DormRoom {
  id: string
  dormitoryId: string
  roomNumber: string
  floor: number
  capacity: number
  createdAt: string
}

export interface CreateDormRoomRequest {
  roomNumber: string
  floor: number
  capacity: number
}

export interface UpdateDormRoomRequest {
  roomNumber?: string
  floor?: number
  capacity?: number
}

export type IssueLocationType = 'MY_ROOM' | 'COMMON_AREA'

export type IssueCategory =
  | 'PLUMBING'
  | 'ELECTRICAL'
  | 'FURNITURE'
  | 'LOCKSMITH'
  | 'OTHER'

export type IssueUrgency = 'NORMAL' | 'URGENT'

export type IssueStatus =
  | 'NEW'
  | 'ASSIGNED_TO_MAINTENANCE'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'REJECTED'
  | 'PARTS_REQUIRED'

export interface Issue {
  id: string
  locationLabel: string
  roomId: string | null
  commonAreaName: string | null
  category: IssueCategory
  urgency: IssueUrgency
  description: string
  status: IssueStatus
  staffNotes: string | null
  hasPhoto: boolean
  photoUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateIssueRequest {
  locationType: IssueLocationType
  commonAreaName?: string
  category: IssueCategory
  urgency: IssueUrgency
  description: string
}

export type BoardPostCategory =
  | 'BORROW_HELP'
  | 'BUY_SELL'
  | 'LOST_FOUND'
  | 'GENERAL'

export type BoardPostScope = 'DORMITORY' | 'CAMPUS'

export type BoardPostStatus = 'ACTIVE' | 'RESOLVED' | 'REMOVED_MODERATOR'

export type BoardPostStatusFilter = 'ACTIVE' | 'RESOLVED' | 'ALL'

export interface BoardPost {
  id: string
  title: string
  content: string
  category: BoardPostCategory
  scope: BoardPostScope
  status: BoardPostStatus
  authorDisplayName: string
  authorRoomNumber: string | null
  authorDormitoryName: string | null
  mine: boolean
  commentCount: number
  createdAt: string
}

export interface BoardComment {
  id: string
  postId: string
  content: string
  authorDisplayName: string
  authorRoomNumber: string | null
  authorDormitoryName: string | null
  mine: boolean
  createdAt: string
}

export interface CreateBoardPostRequest {
  title: string
  content: string
  category: BoardPostCategory
  scope: BoardPostScope
}

export interface ListBoardPostsParams {
  category?: BoardPostCategory | ''
  scope?: BoardPostScope | ''
  status?: BoardPostStatusFilter
}

export interface DeskLaundryBooking {
  id: string
  machineId: string
  machineIdentifier: string
  residentId: string
  residentFirstName: string
  residentLastName: string
  residentRoomNumber: string | null
  residentPhoneNumber: string
  startTime: string
  endTime: string
  status: LaundryBookingStatus
  keyIssuedAt: string | null
}

export interface DeskRoomBooking {
  id: string
  roomId: string
  roomName: string
  residentId: string
  residentFirstName: string
  residentLastName: string
  residentRoomNumber: string | null
  residentPhoneNumber: string
  participantsCount: number
  startTime: string
  endTime: string
  status: RoomBookingStatus
  keyIssuedAt: string | null
}

export interface DeskOpenIssue {
  id: string
  locationLabel: string
  category: IssueCategory
  urgency: IssueUrgency
  description: string
  status: IssueStatus
  createdAt: string
}

export interface ReceptionistDesk {
  laundry: DeskLaundryBooking[]
  rooms: DeskRoomBooking[]
  openIssuesCount: number
  openIssues: DeskOpenIssue[]
}

export interface MachineBreakdownResult {
  machineId: string
  cancelledCount: number
  issueId: string
}

export interface DeskLaundryMachine {
  id: string
  machineIdentifier: string
  floorLocation: string
  status: LaundryMachineStatus
  notes: string | null
}

export interface StaffThematicRoom {
  id: string
  name: string
  status: ThematicRoomStatus
  openingTime: string
  closingTime: string
  maxCapacity: number
  spansMidnight: boolean
}

export interface StaffRoomBookingSlot {
  id: string
  roomId: string
  startTime: string
  endTime: string
  status: RoomBookingStatus
  residentLabel: string
  participantsCount: number
}

export interface StaffRoomScheduleDay {
  date: string
  bookings: StaffRoomBookingSlot[]
}

export interface StaffRoomSchedule {
  rooms: StaffThematicRoom[]
  days: StaffRoomScheduleDay[]
}

export interface RoomMaintenanceResult {
  roomId: string
  cancelledCount: number
  issueId: string
}

export interface DeskThematicRoom {
  id: string
  name: string
  status: ThematicRoomStatus
}

export interface StaffIssue {
  id: string
  locationLabel: string
  roomNumber: string | null
  floor: number | null
  commonAreaName: string | null
  category: IssueCategory
  urgency: IssueUrgency
  description: string
  status: IssueStatus
  staffNotes: string | null
  hasPhoto: boolean
  photoUrl: string | null
  reporterFirstName: string
  reporterLastName: string
  createdAt: string
  updatedAt: string
}

export interface StaffIssueFilters {
  status?: IssueStatus[]
  category?: IssueCategory
  urgency?: IssueUrgency
  from?: string
  to?: string
  roomNumber?: string
  floor?: number
}

export interface UpdateIssueStatusRequest {
  status: IssueStatus
  staffNotes?: string | null
}


