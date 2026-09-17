import { apiClient } from '@/api/client'
import type {
  ActivateResidentRequest,
  ActivateResidentResponse,
  ApiResponse,
  PendingResident,
  RejectResidentRequest,
} from '@/api/types'

export async function listPendingResidents(): Promise<PendingResident[]> {
  const { data } = await apiClient.get<ApiResponse<PendingResident[]>>(
    '/admin/residents/pending',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load pending residents')
  }
  return data.data
}

export async function activateResident(
  id: string,
  request: ActivateResidentRequest = {},
): Promise<ActivateResidentResponse> {
  const { data } = await apiClient.post<ApiResponse<ActivateResidentResponse>>(
    `/admin/residents/${id}/activate`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to activate resident')
  }
  return data.data
}

export async function rejectResident(
  id: string,
  request: RejectResidentRequest,
): Promise<void> {
  const { data } = await apiClient.post<ApiResponse<null>>(
    `/admin/residents/${id}/reject`,
    request,
  )
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to reject resident')
  }
}
