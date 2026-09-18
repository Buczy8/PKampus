import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateCampusEventRequest,
  CreateDormAdminRequest,
  CreateDormitoryRequest,
  DormAdminAccount,
  DormEvent,
  SuperAdminDormitory,
  UpdateCampusEventRequest,
  UpdateDormAdminRequest,
  UpdateDormitoryRequest,
} from '@/api/types'

export async function listSuperAdminDormitories(): Promise<SuperAdminDormitory[]> {
  const { data } = await apiClient.get<ApiResponse<SuperAdminDormitory[]>>(
    '/superadmin/dormitories',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load dormitories')
  }
  return data.data
}

export async function createDormitory(
  request: CreateDormitoryRequest,
): Promise<SuperAdminDormitory> {
  const { data } = await apiClient.post<ApiResponse<SuperAdminDormitory>>(
    '/superadmin/dormitories',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create dormitory')
  }
  return data.data
}

export async function updateDormitory(
  id: string,
  request: UpdateDormitoryRequest,
): Promise<SuperAdminDormitory> {
  const { data } = await apiClient.patch<ApiResponse<SuperAdminDormitory>>(
    `/superadmin/dormitories/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update dormitory')
  }
  return data.data
}

export async function listDormAdmins(): Promise<DormAdminAccount[]> {
  const { data } = await apiClient.get<ApiResponse<DormAdminAccount[]>>(
    '/superadmin/dorm-admins',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load dormitory admins')
  }
  return data.data
}

export async function createDormAdmin(
  request: CreateDormAdminRequest,
): Promise<DormAdminAccount> {
  const { data } = await apiClient.post<ApiResponse<DormAdminAccount>>(
    '/superadmin/dorm-admins',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create dormitory admin')
  }
  return data.data
}

export async function updateDormAdmin(
  id: string,
  request: UpdateDormAdminRequest,
): Promise<DormAdminAccount> {
  const { data } = await apiClient.patch<ApiResponse<DormAdminAccount>>(
    `/superadmin/dorm-admins/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update dormitory admin')
  }
  return data.data
}

export async function listCampusEvents(): Promise<DormEvent[]> {
  const { data } = await apiClient.get<ApiResponse<DormEvent[]>>('/superadmin/events')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load campus events')
  }
  return data.data
}

export async function createCampusEvent(
  request: CreateCampusEventRequest,
): Promise<DormEvent> {
  const { data } = await apiClient.post<ApiResponse<DormEvent>>(
    '/superadmin/events',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to publish campus notice')
  }
  return data.data
}

export async function updateCampusEvent(
  id: string,
  request: UpdateCampusEventRequest,
): Promise<DormEvent> {
  const { data } = await apiClient.patch<ApiResponse<DormEvent>>(
    `/superadmin/events/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update campus notice')
  }
  return data.data
}

export async function deleteCampusEvent(id: string): Promise<void> {
  const { data } = await apiClient.delete<ApiResponse<null>>(`/superadmin/events/${id}`)
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to delete campus notice')
  }
}
