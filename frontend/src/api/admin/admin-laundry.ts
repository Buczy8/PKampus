import { apiClient } from '@/api/client'
import type {
  AdminLaundryMachine,
  ApiResponse,
  CreateLaundryMachineRequest,
  UpdateLaundryMachineRequest,
} from '@/api/types'

export async function listAdminLaundryMachines(): Promise<AdminLaundryMachine[]> {
  const { data } = await apiClient.get<ApiResponse<AdminLaundryMachine[]>>(
    '/admin/laundry-machines',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load laundry machines')
  }
  return data.data
}

export async function createLaundryMachine(
  request: CreateLaundryMachineRequest,
): Promise<AdminLaundryMachine> {
  const { data } = await apiClient.post<ApiResponse<AdminLaundryMachine>>(
    '/admin/laundry-machines',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create laundry machine')
  }
  return data.data
}

export async function updateLaundryMachine(
  id: string,
  request: UpdateLaundryMachineRequest,
): Promise<AdminLaundryMachine> {
  const { data } = await apiClient.patch<ApiResponse<AdminLaundryMachine>>(
    `/admin/laundry-machines/${id}`,
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to update laundry machine')
  }
  return data.data
}
