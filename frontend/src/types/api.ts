// Enveloppes génériques — DATA_MODEL.md §1 (pagination, erreurs).

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  /** Présent uniquement sur les erreurs de validation (400) : champ → message. */
  fieldErrors?: Record<string, string>;
}
