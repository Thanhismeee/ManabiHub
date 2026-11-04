export interface ApiResponse<T> {
  success: boolean;
  messageCode: string;
  message: string;
  data: T;
  errors?: unknown;
  timestamp?: string;
  path?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
