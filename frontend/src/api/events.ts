import { apiClient } from '@/api/client'
import type { ApiResponse, DormEvent } from '@/api/types'

export async function getActiveBanner(): Promise<DormEvent | null> {
  const { data } = await apiClient.get<ApiResponse<DormEvent | null>>('/events/banner')
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to load banner')
  }
  return data.data ?? null
}
