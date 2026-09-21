import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  CreateRoomBookingRequest,
  RoomAvailability,
  RoomBooking,
  ThematicRoom,
} from '@/api/types'

export async function listThematicRooms(): Promise<ThematicRoom[]> {
  const { data } = await apiClient.get<ApiResponse<ThematicRoom[]>>('/rooms')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load thematic rooms')
  }
  return data.data
}

export async function getRoomAvailability(
  roomId: string,
  from: string,
  to: string,
): Promise<RoomAvailability> {
  const { data } = await apiClient.get<ApiResponse<RoomAvailability>>(
    `/rooms/${roomId}/availability`,
    { params: { from, to } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load room availability')
  }
  return data.data
}

export async function listMyRoomBookings(): Promise<RoomBooking[]> {
  const { data } = await apiClient.get<ApiResponse<RoomBooking[]>>(
    '/rooms/bookings/me',
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load room bookings')
  }
  return data.data
}

export async function createRoomBooking(
  request: CreateRoomBookingRequest,
): Promise<RoomBooking> {
  const { data } = await apiClient.post<ApiResponse<RoomBooking>>(
    '/rooms/bookings',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create room booking')
  }
  return data.data
}

export async function cancelRoomBooking(id: string): Promise<RoomBooking> {
  const { data } = await apiClient.delete<ApiResponse<RoomBooking>>(
    `/rooms/bookings/${id}`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to cancel room booking')
  }
  return data.data
}
