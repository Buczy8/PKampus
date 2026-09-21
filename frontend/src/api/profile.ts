import { apiClient } from '@/api/client'
import type { ApiResponse, ResidentCard } from '@/api/types'
import { isAxiosError } from 'axios'

export class CardAccountError extends Error {
  readonly code: 'ACCOUNT_BLOCKED' | 'ACCOUNT_CHECKED_OUT' | 'ACCOUNT_NOT_ACTIVE'

  constructor(code: CardAccountError['code']) {
    super(code)
    this.name = 'CardAccountError'
    this.code = code
  }
}

function parseAccountError(message: string | undefined): CardAccountError['code'] | null {
  if (!message) return null
  if (message.includes('ACCOUNT_BLOCKED')) return 'ACCOUNT_BLOCKED'
  if (message.includes('ACCOUNT_CHECKED_OUT')) return 'ACCOUNT_CHECKED_OUT'
  if (message.includes('ACCOUNT_NOT_ACTIVE')) return 'ACCOUNT_NOT_ACTIVE'
  return null
}

export async function getResidentCard(): Promise<ResidentCard> {
  try {
    const { data } = await apiClient.get<ApiResponse<ResidentCard>>('/profile/card')
    if (!data.success || !data.data) {
      throw new Error(data.message ?? 'Failed to load resident card')
    }
    return data.data
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 403) {
      const payload = error.response.data as ApiResponse<unknown> | undefined
      const code = parseAccountError(payload?.message)
      if (code) throw new CardAccountError(code)
    }
    throw error
  }
}
