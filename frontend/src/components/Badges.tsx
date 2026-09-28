import type { ProjectStatus, TaskPriority, TaskStatus } from '../types';
import { label } from '../utils/format';

export function StatusBadge({ status }: { status: ProjectStatus | TaskStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{label(status)}</span>;
}

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <span className={`badge priority-${priority.toLowerCase()}`}>{label(priority)}</span>;
}

export function OverdueBadge() {
  return <span className="badge overdue">Overdue</span>;
}
