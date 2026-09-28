import type { Page } from '../types';

export function Pagination<T>({ page, onChange }: { page: Page<T>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return null;
  }
  const from = page.page * page.size + 1;
  const to = Math.min(from + page.content.length - 1, page.totalElements);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">
        {from}–{to} of {page.totalElements}
      </span>
      <div className="pagination-buttons">
        <button type="button" className="btn btn-small" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
          Previous
        </button>
        <span>
          Page {page.page + 1} of {page.totalPages}
        </span>
        <button
          type="button"
          className="btn btn-small"
          disabled={page.page + 1 >= page.totalPages}
          onClick={() => onChange(page.page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  );
}
