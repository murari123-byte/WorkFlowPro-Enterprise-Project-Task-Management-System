import type { ReactNode } from 'react';

export function Loading({ text = 'Loading...' }: { text?: string }) {
  return (
    <div className="state" role="status">
      <span className="spinner" aria-hidden="true" /> {text}
    </div>
  );
}

export function ErrorMessage({ message, onRetry }: { message: string | null; onRetry?: () => void }) {
  if (!message) {
    return null;
  }
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-small" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty-title">{title}</p>
      {children}
    </div>
  );
}
