import { apiClient } from '@/api/client'
import type { ApiResponse, Dormitory } from '@/api/types'

export async function getDormitories(): Promise<Dormitory[]> {
  const { data } = await apiClient.get<ApiResponse<Dormitory[]>>('/dormitories')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load dormitories')
  }
  return data.data
}
