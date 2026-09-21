import { apiClient } from '@/api/client'
import type { ApiResponse, CreateIssueRequest, Issue } from '@/api/types'

export async function listMyIssues(): Promise<Issue[]> {
  const { data } = await apiClient.get<ApiResponse<Issue[]>>('/issues/me')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load issues')
  }
  return data.data
}

export async function createIssue(
  request: CreateIssueRequest,
  photo?: File | null,
): Promise<Issue> {
  const formData = new FormData()
  formData.append(
    'data',
    new Blob([JSON.stringify(request)], { type: 'application/json' }),
  )
  if (photo) {
    formData.append('photo', photo)
  }

  const { data } = await apiClient.post<ApiResponse<Issue>>('/issues', formData)
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create issue')
  }
  return data.data
}
