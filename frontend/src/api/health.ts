import { apiClient } from './client';
import type { ApiResponse, HealthData } from './types';

export const fetchHealth = async (): Promise<ApiResponse<HealthData>> => {
  const response = await apiClient.get<ApiResponse<HealthData>>('/health');
  return response.data;
};
