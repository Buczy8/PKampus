import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateRoomBanRequest,
  ManagedResident,
  Sanction,
} from '@/api/types'

export async function listManagedResidents(): Promise<ManagedResident[]> {
  const { data } = await apiClient.get<ApiResponse<ManagedResident[]>>(
    '/admin/residents',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load residents')
  }
  return data.data
}

export async function blockResident(id: string): Promise<ManagedResident> {
  const { data } = await apiClient.post<ApiResponse<ManagedResident>>(
    `/admin/residents/${id}/block`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to block resident')
  }
  return data.data
}

export async function unblockResident(id: string): Promise<ManagedResident> {
  const { data } = await apiClient.post<ApiResponse<ManagedResident>>(
    `/admin/residents/${id}/unblock`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to unblock resident')
  }
  return data.data
}

export async function checkoutResident(id: string): Promise<ManagedResident> {
  const { data } = await apiClient.post<ApiResponse<ManagedResident>>(
    `/admin/residents/${id}/checkout`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to check out resident')
  }
  return data.data
}

export async function issueRoomBan(
  id: string,
  request: CreateRoomBanRequest,
): Promise<Sanction> {
  const { data } = await apiClient.post<ApiResponse<Sanction>>(
    `/admin/residents/${id}/room-ban`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to issue ROOM_BAN')
  }
  return data.data
}

export async function revokeRoomBan(
  residentId: string,
  sanctionId: string,
): Promise<Sanction> {
  const { data } = await apiClient.post<ApiResponse<Sanction>>(
    `/admin/residents/${residentId}/room-ban/${sanctionId}/revoke`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to revoke ROOM_BAN')
  }
  return data.data
}
