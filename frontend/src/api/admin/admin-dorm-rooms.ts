import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateDormRoomRequest,
  DormRoom,
  UpdateDormRoomRequest,
} from '@/api/types'

export async function listAdminDormRooms(): Promise<DormRoom[]> {
  const { data } = await apiClient.get<ApiResponse<DormRoom[]>>('/admin/dorm-rooms')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load dorm rooms')
  }
  return data.data
}

export async function createDormRoom(
  request: CreateDormRoomRequest,
): Promise<DormRoom> {
  const { data } = await apiClient.post<ApiResponse<DormRoom>>(
    '/admin/dorm-rooms',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create dorm room')
  }
  return data.data
}

export async function updateDormRoom(
  id: string,
  request: UpdateDormRoomRequest,
): Promise<DormRoom> {
  const { data } = await apiClient.patch<ApiResponse<DormRoom>>(
    `/admin/dorm-rooms/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update dorm room')
  }
  return data.data
}
