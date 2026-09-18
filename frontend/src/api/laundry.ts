import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateLaundryBookingRequest,
  LaundryBooking,
  LaundrySchedule,
} from '@/api/types'

export async function getLaundrySchedule(
  from: string,
  to: string,
): Promise<LaundrySchedule> {
  const { data } = await apiClient.get<ApiResponse<LaundrySchedule>>(
    '/laundry/schedule',
    { params: { from, to } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load laundry schedule')
  }
  return data.data
}

export async function listMyLaundryBookings(): Promise<LaundryBooking[]> {
  const { data } = await apiClient.get<ApiResponse<LaundryBooking[]>>(
    '/laundry/bookings/me',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load laundry bookings')
  }
  return data.data
}

export async function createLaundryBooking(
  request: CreateLaundryBookingRequest,
): Promise<LaundryBooking> {
  const { data } = await apiClient.post<ApiResponse<LaundryBooking>>(
    '/laundry/bookings',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create laundry booking')
  }
  return data.data
}

export async function cancelLaundryBooking(id: string): Promise<LaundryBooking> {
  const { data } = await apiClient.delete<ApiResponse<LaundryBooking>>(
    `/laundry/bookings/${id}`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to cancel laundry booking')
  }
  return data.data
}
