import type { ReactNode } from 'react';

/**
 * Label + input + validation message, used by every form.
 * Hint and error sit OUTSIDE the <label>, so screen readers announce the field as just its label.
 */
export function Field({ label, error, children, hint }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div className={`field${error ? ' field-invalid' : ''}`}>
      <label className="field-control">
        <span className="field-label">{label}</span>
        {children}
      </label>
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error" role="alert">{error}</span>}
    </div>
  );
}
