import { apiClient } from '@/api/client'
import type {
  ApiResponse,
  BoardComment,
  BoardPost,
  CreateBoardPostRequest,
  ListBoardPostsParams,
  PagedResponse,
} from '@/api/types'

export async function listBoardPosts(
  params: ListBoardPostsParams = {},
): Promise<PagedResponse<BoardPost>> {
  const query: Record<string, string> = {}
  if (params.category) query.category = params.category
  if (params.scope) query.scope = params.scope
  if (params.status) query.status = params.status
  if (params.page !== undefined) query.page = String(params.page)
  if (params.size !== undefined) query.size = String(params.size)

  const { data } = await apiClient.get<ApiResponse<PagedResponse<BoardPost>>>(
    '/posts',
    { params: query },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load posts')
  }
  return data.data
}

export async function createBoardPost(
  request: CreateBoardPostRequest,
): Promise<BoardPost> {
  const { data } = await apiClient.post<ApiResponse<BoardPost>>('/posts', request)
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to create post')
  }
  return data.data
}

export async function resolveBoardPost(id: string): Promise<BoardPost> {
  const { data } = await apiClient.patch<ApiResponse<BoardPost>>(
    `/posts/${id}/resolve`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to resolve post')
  }
  return data.data
}

export async function deleteBoardPost(id: string): Promise<void> {
  const { data } = await apiClient.delete<ApiResponse<null>>(`/posts/${id}`)
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to delete post')
  }
}

export async function listBoardComments(postId: string): Promise<BoardComment[]> {
  const { data } = await apiClient.get<ApiResponse<BoardComment[]>>(
    `/posts/${postId}/comments`,
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to load comments')
  }
  return data.data
}

export async function createBoardComment(
  postId: string,
  content: string,
): Promise<BoardComment> {
  const { data } = await apiClient.post<ApiResponse<BoardComment>>(
    `/posts/${postId}/comments`,
    { content },
  )
  if (!data.success || !data.data) {
    throw new Error(data.message ?? 'Failed to add comment')
  }
  return data.data
}

export async function deleteBoardComment(commentId: string): Promise<void> {
  const { data } = await apiClient.delete<ApiResponse<null>>(
    `/posts/comments/${commentId}`,
  )
  if (!data.success) {
    throw new Error(data.message ?? 'Failed to delete comment')
  }
}
