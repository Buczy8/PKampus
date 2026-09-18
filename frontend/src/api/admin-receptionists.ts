import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateReceptionistRequest,
  ReceptionistAccount,
  UpdateReceptionistRequest,
} from '@/api/types'

export async function listReceptionists(): Promise<ReceptionistAccount[]> {
  const { data } = await apiClient.get<ApiResponse<ReceptionistAccount[]>>(
    '/admin/receptionists',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load receptionists')
  }
  return data.data
}

export async function createReceptionist(
  request: CreateReceptionistRequest,
): Promise<ReceptionistAccount> {
  const { data } = await apiClient.post<ApiResponse<ReceptionistAccount>>(
    '/admin/receptionists',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create receptionist')
  }
  return data.data
}

export async function updateReceptionist(
  id: string,
  request: UpdateReceptionistRequest,
): Promise<ReceptionistAccount> {
  const { data } = await apiClient.patch<ApiResponse<ReceptionistAccount>>(
    `/admin/receptionists/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update receptionist')
  }
  return data.data
}
