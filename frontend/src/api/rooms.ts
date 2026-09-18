import { apiClient } from '@/api/client'
import type { ApiResponse, ThematicRoom } from '@/api/types'

export async function listThematicRooms(): Promise<ThematicRoom[]> {
  const { data } = await apiClient.get<ApiResponse<ThematicRoom[]>>('/rooms')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load thematic rooms')
  }
  return data.data
}
