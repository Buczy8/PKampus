import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateDormEventRequest,
  DormEvent,
  UpdateDormEventRequest,
} from '@/api/types'

export async function listAdminEvents(): Promise<DormEvent[]> {
  const { data } = await apiClient.get<ApiResponse<DormEvent[]>>('/admin/events')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load dorm notices')
  }
  return data.data
}

export async function createAdminEvent(
  request: CreateDormEventRequest,
): Promise<DormEvent> {
  const { data } = await apiClient.post<ApiResponse<DormEvent>>(
    '/admin/events',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to publish dorm notice')
  }
  return data.data
}

export async function updateAdminEvent(
  id: string,
  request: UpdateDormEventRequest,
): Promise<DormEvent> {
  const { data } = await apiClient.patch<ApiResponse<DormEvent>>(
    `/admin/events/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update dorm notice')
  }
  return data.data
}

export async function deleteAdminEvent(id: string): Promise<void> {
  const { data } = await apiClient.delete<ApiResponse<null>>(`/admin/events/${id}`)
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to delete dorm notice')
  }
}
