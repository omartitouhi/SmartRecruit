import type { ApplicationStatus, AuditLogEntry, PaginatedResponse, Role } from '../types';
import { apiClient, USE_MOCKS } from './client';
import { mockDashboardApi } from './mock/handlers';

/**
 * Statistiques globales de la plateforme (BE-ADMIN). Pas encore décrit dans DATA_MODEL.md :
 * forme proposée par le frontend, à valider avec dashboard-service avant de l'y ajouter.
 */
export interface DashboardStats {
  accountsByRole: Record<Role, number>;
  publishedJobs: number;
  applicationsByStatus: Record<ApplicationStatus, number>;
  averageMatchScore: number | null;
  /** Candidatures reçues par semaine, de la plus ancienne à la plus récente. */
  applicationsPerWeek: { weekStart: string; count: number }[];
}

export interface DashboardApi {
  stats(): Promise<DashboardStats>;
  auditLog(page?: number, size?: number): Promise<PaginatedResponse<AuditLogEntry>>;
}

// dashboard-service — contrats à confirmer (DTO AuditLogEntryResponse présent).
const httpDashboardApi: DashboardApi = {
  async stats() {
    return (await apiClient.get<DashboardStats>('/dashboard/stats')).data;
  },
  async auditLog(page = 0, size = 20) {
    return (await apiClient.get<PaginatedResponse<AuditLogEntry>>('/dashboard/audit-logs', { params: { page, size } }))
      .data;
  },
};

export const dashboardApi: DashboardApi = USE_MOCKS ? mockDashboardApi : httpDashboardApi;
