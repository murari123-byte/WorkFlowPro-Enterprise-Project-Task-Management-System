import type { UserSummary } from '../types';

export function fullName(user: UserSummary | null | undefined): string {
  return user ? `${user.firstName} ${user.lastName}` : 'Unassigned';
}

/** "IN_PROGRESS" -> "In progress" */
export function label(value: string): string {
  const text = value.replaceAll('_', ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  // Plain dates ("2026-10-01") are shown as-is to avoid time-zone shifts
  const date = value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function todayIso(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}
