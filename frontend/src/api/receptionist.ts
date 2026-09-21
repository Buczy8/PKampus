import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  DeskLaundryBooking,
  DeskLaundryMachine,
  DeskRoomBooking,
  DeskThematicRoom,
  LaundrySchedule,
  MachineBreakdownResult,
  ReceptionistDesk,
  RoomMaintenanceResult,
  StaffIssue,
  StaffIssueFilters,
  StaffRoomSchedule,
  UpdateIssueStatusRequest,
} from '@/api/types'

export async function getReceptionistDesk(): Promise<ReceptionistDesk> {
  const { data } = await apiClient.get<ApiResponse<ReceptionistDesk>>(
    '/receptionist/desk',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load receptionist desk')
  }
  return data.data
}

export async function getReceptionistLaundrySchedule(
  from: string,
  to: string,
): Promise<LaundrySchedule> {
  const { data } = await apiClient.get<ApiResponse<LaundrySchedule>>(
    '/receptionist/laundry/schedule',
    { params: { from, to } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load laundry schedule')
  }
  return data.data
}

export async function cancelReceptionistLaundryBooking(
  id: string,
): Promise<DeskLaundryBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskLaundryBooking>>(
    `/receptionist/laundry/${id}/cancel`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to cancel laundry booking')
  }
  return data.data
}

export async function reportLaundryMachineBreakdown(
  id: string,
  reason: string,
): Promise<MachineBreakdownResult> {
  const { data } = await apiClient.post<ApiResponse<MachineBreakdownResult>>(
    `/receptionist/laundry/machines/${id}/breakdown`,
    { reason },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to report machine breakdown')
  }
  return data.data
}

export async function restoreLaundryMachine(
  id: string,
): Promise<DeskLaundryMachine> {
  const { data } = await apiClient.post<ApiResponse<DeskLaundryMachine>>(
    `/receptionist/laundry/machines/${id}/restore`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to restore laundry machine')
  }
  return data.data
}

export async function getReceptionistRoomSchedule(
  from: string,
  to: string,
): Promise<StaffRoomSchedule> {
  const { data } = await apiClient.get<ApiResponse<StaffRoomSchedule>>(
    '/receptionist/rooms/schedule',
    { params: { from, to } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load room schedule')
  }
  return data.data
}

export async function cancelReceptionistRoomBooking(
  id: string,
): Promise<DeskRoomBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskRoomBooking>>(
    `/receptionist/rooms/${id}/cancel`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to cancel room booking')
  }
  return data.data
}

export async function reportRoomMaintenance(
  id: string,
  reason: string,
): Promise<RoomMaintenanceResult> {
  const { data } = await apiClient.post<ApiResponse<RoomMaintenanceResult>>(
    `/receptionist/rooms/${id}/maintenance`,
    { reason },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to report room maintenance')
  }
  return data.data
}

export async function restoreThematicRoom(id: string): Promise<DeskThematicRoom> {
  const { data } = await apiClient.post<ApiResponse<DeskThematicRoom>>(
    `/receptionist/rooms/${id}/restore`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to restore thematic room')
  }
  return data.data
}

export async function listReceptionistIssues(
  filters: StaffIssueFilters = {},
): Promise<StaffIssue[]> {
  const { data } = await apiClient.get<ApiResponse<StaffIssue[]>>(
    '/receptionist/issues',
    {
      params: {
        status: filters.status,
        category: filters.category,
        urgency: filters.urgency,
        from: filters.from,
        to: filters.to,
        roomNumber: filters.roomNumber || undefined,
        floor: filters.floor,
      },
      paramsSerializer: {
        indexes: null,
      },
    },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load issues')
  }
  return data.data
}

export async function getReceptionistIssue(id: string): Promise<StaffIssue> {
  const { data } = await apiClient.get<ApiResponse<StaffIssue>>(
    `/receptionist/issues/${id}`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load issue')
  }
  return data.data
}

export async function updateReceptionistIssueStatus(
  id: string,
  body: UpdateIssueStatusRequest,
): Promise<StaffIssue> {
  const { data } = await apiClient.patch<ApiResponse<StaffIssue>>(
    `/receptionist/issues/${id}/status`,
    body,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update issue status')
  }
  return data.data
}

export async function issueLaundryKey(id: string): Promise<DeskLaundryBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskLaundryBooking>>(
    `/receptionist/laundry/${id}/issue-key`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to issue laundry key')
  }
  return data.data
}

export async function returnLaundryKey(id: string): Promise<DeskLaundryBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskLaundryBooking>>(
    `/receptionist/laundry/${id}/return-key`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to return laundry key')
  }
  return data.data
}

export async function issueRoomKey(id: string): Promise<DeskRoomBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskRoomBooking>>(
    `/receptionist/rooms/${id}/issue-key`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to issue room key')
  }
  return data.data
}

export async function returnRoomKey(id: string): Promise<DeskRoomBooking> {
  const { data } = await apiClient.post<ApiResponse<DeskRoomBooking>>(
    `/receptionist/rooms/${id}/return-key`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to return room key')
  }
  return data.data
}
