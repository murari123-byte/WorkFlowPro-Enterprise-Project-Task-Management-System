import { useState } from 'react';
import { Link } from 'react-router-dom';
import { projectsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { useDebounce } from '../hooks/useDebounce';
import { StatusBadge } from '../components/Badges';
import { Pagination } from '../components/Pagination';
import { EmptyState, ErrorMessage, Loading } from '../components/States';
import { PROJECT_STATUSES, type ProjectStatus } from '../types';
import { formatDate, fullName, label } from '../utils/format';

const SORTS = [
  { value: 'updatedAt,desc', text: 'Recently updated' },
  { value: 'name,asc', text: 'Name (A–Z)' },
  { value: 'endDate,asc', text: 'End date (soonest)' },
  { value: 'createdAt,desc', text: 'Newest' },
];

export function ProjectsPage() {
  const { hasRole } = useAuth();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ProjectStatus | ''>('');
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounce(search);

  const { data, loading, error, reload } = useApi(
    () => projectsApi.list({ search: debouncedSearch, status, sort, page, size: 10 }),
    [debouncedSearch, status, sort, page],
  );

  return (
    <>
      <div className="page-header">
        <h1>Projects</h1>
        {hasRole('ADMIN', 'PROJECT_MANAGER') && (
          <Link to="/projects/new" className="btn btn-primary">New project</Link>
        )}
      </div>

      <div className="card">
        <div className="filters">
          <input type="search" placeholder="Search projects" value={search} aria-label="Search projects"
                 onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={status} aria-label="Status" onChange={(e) => { setStatus(e.target.value as ProjectStatus | ''); setPage(0); }}>
            <option value="">All statuses</option>
            {PROJECT_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
          <select value={sort} aria-label="Sort" onChange={(e) => { setSort(e.target.value); setPage(0); }}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.text}</option>)}
          </select>
        </div>

        {error ? (
          <ErrorMessage message={error} onRetry={reload} />
        ) : loading && !data ? (
          <Loading text="Loading projects..." />
        ) : !data || data.content.length === 0 ? (
          <EmptyState title="No projects found.">
            <p className="muted">You only see projects you are a member of.</p>
          </EmptyState>
        ) : (
          <>
            <div className={`table-wrap${loading ? ' is-loading' : ''}`}>
              <table className="table">
                <thead>
                  <tr><th>Name</th><th>Status</th><th>Manager</th><th>Start</th><th>End</th></tr>
                </thead>
                <tbody>
                  {data.content.map((project) => (
                    <tr key={project.id}>
                      <td data-label="Name"><Link to={`/projects/${project.id}`}>{project.name}</Link></td>
                      <td data-label="Status"><StatusBadge status={project.status} /></td>
                      <td data-label="Manager">{fullName(project.manager)}</td>
                      <td data-label="Start">{formatDate(project.startDate)}</td>
                      <td data-label="End">{formatDate(project.endDate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data} onChange={setPage} />
          </>
        )}
      </div>
    </>
  );
}
