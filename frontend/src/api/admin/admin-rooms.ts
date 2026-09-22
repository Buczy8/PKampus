import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateThematicRoomRequest,
  ThematicRoom,
  UpdateThematicRoomRequest,
} from '@/api/types'

export async function listAdminThematicRooms(): Promise<ThematicRoom[]> {
  const { data } = await apiClient.get<ApiResponse<ThematicRoom[]>>(
    '/admin/thematic-rooms',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load thematic rooms')
  }
  return data.data
}

export async function createThematicRoom(
  request: CreateThematicRoomRequest,
): Promise<ThematicRoom> {
  const { data } = await apiClient.post<ApiResponse<ThematicRoom>>(
    '/admin/thematic-rooms',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create thematic room')
  }
  return data.data
}

export async function updateThematicRoom(
  id: string,
  request: UpdateThematicRoomRequest,
): Promise<ThematicRoom> {
  const { data } = await apiClient.patch<ApiResponse<ThematicRoom>>(
    `/admin/thematic-rooms/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update thematic room')
  }
  return data.data
}
