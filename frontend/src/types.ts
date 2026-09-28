// Shapes of the JSON the backend returns (see docs/api.md).

export type Role = 'ADMIN' | 'PROJECT_MANAGER' | 'TEAM_LEAD' | 'EMPLOYEE';
export const ALL_ROLES: Role[] = ['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'EMPLOYEE'];

export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export const PROJECT_STATUSES: ProjectStatus[] = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'COMPLETED' | 'CANCELLED';
export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED', 'CANCELLED'];

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: Role[];
  enabled: boolean;
  createdAt: string;
}

export interface UserSummary {
  id: string;
  firstName: string;
  lastName: string;
  email: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProjectSummary {
  id: string;
  name: string;
  status: ProjectStatus;
  startDate: string | null;
  endDate: string | null;
  manager: UserSummary;
  updatedAt: string;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  allowedStatuses: ProjectStatus[];
  startDate: string | null;
  endDate: string | null;
  manager: UserSummary;
  members: UserSummary[];
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectStats {
  total: number;
  active: number;
  completed: number;
  byStatus: Record<ProjectStatus, number>;
}

export interface TaskSummary {
  id: string;
  projectId: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  overdue: boolean;
  assignee: UserSummary | null;
  updatedAt: string;
}

export interface Task {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  overdue: boolean;
  assignee: UserSummary | null;
  createdBy: UserSummary;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  allowedStatuses: TaskStatus[];
  permissions: { canEdit: boolean; canAssign: boolean; canDelete: boolean };
}

export interface TaskHistoryEntry {
  id: number;
  action: 'CREATED' | 'UPDATED' | 'ASSIGNED' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED' | 'DUE_DATE_CHANGED';
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  actor: UserSummary;
  createdAt: string;
}

export interface TaskStats {
  total: number;
  pending: number;
  completed: number;
  overdue: number;
  myOpenTasks: number;
  byStatus: Record<TaskStatus, number>;
  byPriority: Record<TaskPriority, number>;
}

/** The backend's error format (ErrorResponse). */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}
