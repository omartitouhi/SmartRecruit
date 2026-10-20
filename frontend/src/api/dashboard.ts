import type {
  Account,
  AccountStatus,
  AuditLogEntry,
  CompanyStats,
  GlobalStats,
  Job,
  PaginatedResponse,
  Role,
} from '../types';
import { apiClient, isMocked } from './client';
import { mockDashboardApi } from './mock/dashboard';

export interface UserFilters {
  role?: Role | '';
  status?: AccountStatus | '';
  page?: number;
  size?: number;
}

export interface AuditFilters {
  /** ISO 8601. */
  from?: string;
  to?: string;
  actorId?: string;
  action?: string;
  page?: number;
  size?: number;
}

export interface DashboardApi {
  /** ADMIN. */
  globalStats(): Promise<GlobalStats>;
  /** Recruteur membre de l'entreprise, ou ADMIN ; 403 sinon. */
  companyStats(companyId: string): Promise<CompanyStats>;
  users(filters?: UserFilters): Promise<PaginatedResponse<Account>>;
  /** Un admin ne peut pas se désactiver lui-même (400). */
  setUserStatus(accountId: string, status: Extract<AccountStatus, 'ACTIVE' | 'DISABLED'>): Promise<Account>;
  /** Retire l'offre (MODERATED) avec un motif de 500 caractères maximum. */
  moderateJob(jobId: string, reason: string): Promise<Job>;
  /** Plus récents d'abord. */
  auditLog(filters?: AuditFilters): Promise<PaginatedResponse<AuditLogEntry>>;
}

const clean = (params: object) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== undefined && v !== null));

// dashboard-service — API_CONTRACTS.md §5.
export const httpDashboardApi: DashboardApi = {
  async globalStats() {
    return (await apiClient.get<GlobalStats>('/dashboard/stats/global')).data;
  },
  async companyStats(companyId) {
    return (await apiClient.get<CompanyStats>(`/dashboard/stats/company/${companyId}`)).data;
  },
  async users(filters = {}) {
    return (await apiClient.get<PaginatedResponse<Account>>('/dashboard/admin/users', { params: clean(filters) })).data;
  },
  async setUserStatus(accountId, status) {
    return (await apiClient.patch<Account>(`/dashboard/admin/users/${accountId}/status`, { status })).data;
  },
  async moderateJob(jobId, reason) {
    return (await apiClient.patch<Job>(`/dashboard/admin/jobs/${jobId}/moderation`, { reason })).data;
  },
  async auditLog(filters = {}) {
    return (
      await apiClient.get<PaginatedResponse<AuditLogEntry>>('/dashboard/admin/audit-log', { params: clean(filters) })
    ).data;
  },
};

export const dashboardApi: DashboardApi = isMocked('dashboard') ? mockDashboardApi : httpDashboardApi;
