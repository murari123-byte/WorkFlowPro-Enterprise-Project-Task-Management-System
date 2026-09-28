// One small function per backend endpoint. Pages never build URLs themselves.
import { api } from './client';
import type {
  AuthResponse, Page, Project, ProjectStats, ProjectStatus, ProjectSummary, Role, Task, TaskHistoryEntry,
  TaskPriority, TaskStats, TaskStatus, TaskSummary, User,
} from '../types';

type Params = Record<string, string | number | boolean | undefined | null>;

/** Drops empty filters so the URL only contains what the user chose. */
function clean(params: Params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}

// ---------- auth ----------
export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/api/auth/login', { email, password }).then((r) => r.data),
  register: (data: { email: string; password: string; firstName: string; lastName: string }) =>
    api.post<AuthResponse>('/api/auth/register', data).then((r) => r.data),
  logout: (refreshToken: string) => api.post('/api/auth/logout', { refreshToken }),
};

// ---------- users ----------
export const usersApi = {
  me: () => api.get<User>('/api/users/me').then((r) => r.data),
  updateMe: (firstName: string, lastName: string) =>
    api.put<User>('/api/users/me', { firstName, lastName }).then((r) => r.data),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put('/api/users/me/password', { currentPassword, newPassword }),
  search: (params: { search?: string; role?: Role | ''; page?: number; size?: number; sort?: string }) =>
    api.get<Page<User>>('/api/users', { params: clean(params) }).then((r) => r.data),
  updateRoles: (id: string, roles: Role[]) => api.put<User>(`/api/users/${id}/roles`, { roles }).then((r) => r.data),
  updateStatus: (id: string, enabled: boolean) =>
    api.put<User>(`/api/users/${id}/status`, { enabled }).then((r) => r.data),
};

// ---------- projects ----------
export interface ProjectInput {
  name: string;
  description: string;
  startDate: string | null;
  endDate: string | null;
  managerId?: string | null;
}

export const projectsApi = {
  list: (params: { search?: string; status?: ProjectStatus | ''; page?: number; size?: number; sort?: string }) =>
    api.get<Page<ProjectSummary>>('/api/projects', { params: clean(params) }).then((r) => r.data),
  get: (id: string) => api.get<Project>(`/api/projects/${id}`).then((r) => r.data),
  create: (data: ProjectInput) => api.post<Project>('/api/projects', data).then((r) => r.data),
  update: (id: string, data: ProjectInput) => api.put<Project>(`/api/projects/${id}`, data).then((r) => r.data),
  changeStatus: (id: string, status: ProjectStatus) =>
    api.patch<Project>(`/api/projects/${id}/status`, { status }).then((r) => r.data),
  changeManager: (id: string, userId: string) =>
    api.put<Project>(`/api/projects/${id}/manager`, { userId }).then((r) => r.data),
  addMember: (id: string, userId: string) =>
    api.post<Project>(`/api/projects/${id}/members`, { userId }).then((r) => r.data),
  removeMember: (id: string, userId: string) => api.delete(`/api/projects/${id}/members/${userId}`),
  remove: (id: string) => api.delete(`/api/projects/${id}`),
  stats: () => api.get<ProjectStats>('/api/projects/stats').then((r) => r.data),
};

// ---------- tasks ----------
export interface TaskFilters {
  projectId?: string;
  search?: string;
  status?: TaskStatus | '';
  priority?: TaskPriority | '';
  assigneeId?: string;
  /** only TODO / IN_PROGRESS / IN_REVIEW */
  open?: boolean;
  overdue?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TaskInput {
  title: string;
  description: string;
  priority: TaskPriority;
  dueDate: string | null;
}

export const tasksApi = {
  search: (filters: TaskFilters) =>
    api.get<Page<TaskSummary>>('/api/tasks', { params: clean({ ...filters, open: filters.open || undefined, overdue: filters.overdue || undefined }) })
      .then((r) => r.data),
  get: (id: string) => api.get<Task>(`/api/tasks/${id}`).then((r) => r.data),
  create: (data: TaskInput & { projectId: string; assigneeId: string | null }) =>
    api.post<Task>('/api/tasks', data).then((r) => r.data),
  update: (id: string, data: TaskInput) => api.put<Task>(`/api/tasks/${id}`, data).then((r) => r.data),
  changeStatus: (id: string, status: TaskStatus) =>
    api.patch<Task>(`/api/tasks/${id}/status`, { status }).then((r) => r.data),
  assign: (id: string, assigneeId: string | null) =>
    api.put<Task>(`/api/tasks/${id}/assignee`, { assigneeId }).then((r) => r.data),
  remove: (id: string) => api.delete(`/api/tasks/${id}`),
  history: (id: string, page: number) =>
    api.get<Page<TaskHistoryEntry>>(`/api/tasks/${id}/history`, { params: { page, size: 20 } }).then((r) => r.data),
  stats: (projectId?: string) =>
    api.get<TaskStats>('/api/tasks/stats', { params: clean({ projectId }) }).then((r) => r.data),
};
