export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface HealthData {
  status: string;
  system: string;
  version: string;
  serverTime: string;
}
