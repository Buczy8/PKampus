import { isAxiosError } from 'axios'
import type { ApiResponse } from '@/api/types'

export function getApiErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (!isAxiosError(error)) {
    return error instanceof Error ? error.message : fallback
  }

  const payload = error.response?.data as ApiResponse<unknown> | undefined
  if (payload?.message) {
    return payload.message
  }

  if (typeof error.message === 'string' && error.message) {
    return error.message
  }

  return fallback
}
