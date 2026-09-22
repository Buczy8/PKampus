import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  UserProfile,
} from '@/api/types'

export interface VerifyEmailResponse {
  message: string
  status: string
}

export async function login(request: LoginRequest): Promise<AuthResponse> {
  const { data } = await apiClient.post<ApiResponse<AuthResponse>>('/auth/login', request)
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Login failed')
  }
  return data.data
}

export async function registerResident(
  request: RegisterRequest,
  photo: File,
): Promise<RegisterResponse> {
  const formData = new FormData()
  formData.append(
    'data',
    new Blob([JSON.stringify(request)], { type: 'application/json' }),
  )
  formData.append('photo', photo)

  const { data } = await apiClient.post<ApiResponse<RegisterResponse>>(
    '/auth/register',
    formData,
  )

  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Registration failed')
  }
  return data.data
}

export async function verifyEmail(token: string): Promise<VerifyEmailResponse> {
  const { data } = await apiClient.get<ApiResponse<VerifyEmailResponse>>(
    '/auth/verify-email',
    { params: { token } },
  )

  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Email verification failed')
  }
  return data.data
}

export async function getCurrentUser(): Promise<UserProfile> {
  const { data } = await apiClient.get<ApiResponse<UserProfile>>('/auth/me')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load current user')
  }
  return data.data
}

export async function changePassword(
  request: ChangePasswordRequest,
): Promise<UserProfile> {
  const { data } = await apiClient.post<ApiResponse<UserProfile>>(
    '/auth/change-password',
    request,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to change password')
  }
  return data.data
}

export async function logout(refreshToken?: string | null): Promise<void> {
  await apiClient.post('/auth/logout', refreshToken ? { refreshToken } : {})
}

export interface VerifyResetTokenResponse {
  valid: boolean
  maskedEmail?: string
}

export async function forgotPassword(email: string): Promise<string> {
  const { data } = await apiClient.post<ApiResponse<string>>('/auth/forgot-password', { email })
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to process request')
  }
  return data.data ?? data.message ?? 'Link do resetu hasła został wysłany.'
}

export async function verifyResetToken(token: string): Promise<VerifyResetTokenResponse> {
  const { data } = await apiClient.get<ApiResponse<VerifyResetTokenResponse>>(
    '/auth/verify-reset-token',
    { params: { token } },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to verify token')
  }
  return data.data
}

export async function resetPassword(token: string, newPassword: string): Promise<string> {
  const { data } = await apiClient.post<ApiResponse<string>>('/auth/reset-password', {
    token,
    newPassword,
  })
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to reset password')
  }
  return data.data ?? data.message ?? 'Hasło zostało pomyślnie zmienione.'
}

export { refreshAccessToken } from '@/api/client'

