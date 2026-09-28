import { useState } from 'react';
import { Link } from 'react-router-dom';
import { tasksApi, type TaskFilters } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { useDebounce } from '../hooks/useDebounce';
import { TASK_PRIORITIES, TASK_STATUSES, type TaskPriority, type TaskStatus } from '../types';
import { formatDate, fullName, label } from '../utils/format';
import { OverdueBadge, PriorityBadge, StatusBadge } from './Badges';
import { Pagination } from './Pagination';
import { EmptyState, ErrorMessage, Loading } from './States';

const SORTS = [
  { value: 'updatedAt,desc', text: 'Recently updated' },
  { value: 'dueDate,asc', text: 'Due date (soonest)' },
  { value: 'priority,desc', text: 'Priority (highest)' },
  { value: 'title,asc', text: 'Title (A–Z)' },
  { value: 'createdAt,desc', text: 'Newest' },
];

/** Search, filter, sort and page through tasks - either of one project or of all my projects. */
export function TaskTable({ projectId, initialMine = false, showProject = false, projectNames = {} }: {
  projectId?: string;
  initialMine?: boolean;
  showProject?: boolean;
  projectNames?: Record<string, string>;
}) {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [priority, setPriority] = useState<TaskPriority | ''>('');
  const [mine, setMine] = useState(initialMine);
  const [overdue, setOverdue] = useState(false);
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounce(search);

  const filters: TaskFilters = {
    projectId, search: debouncedSearch, status, priority, overdue, sort, page, size: 10,
    assigneeId: mine ? user?.id : undefined,
  };
  const { data, loading, error, reload } = useApi(() => tasksApi.search(filters), [JSON.stringify(filters)]);

  // Any filter change starts again at page 1
  const onFilter = <T,>(setter: (value: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  return (
    <div>
      <div className="filters">
        <input type="search" placeholder="Search tasks" value={search} aria-label="Search tasks"
               onChange={(e) => onFilter(setSearch)(e.target.value)} />
        <select value={status} aria-label="Status" onChange={(e) => onFilter(setStatus)(e.target.value as TaskStatus | '')}>
          <option value="">All statuses</option>
          {TASK_STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
        </select>
        <select value={priority} aria-label="Priority" onChange={(e) => onFilter(setPriority)(e.target.value as TaskPriority | '')}>
          <option value="">All priorities</option>
          {TASK_PRIORITIES.map((p) => <option key={p} value={p}>{label(p)}</option>)}
        </select>
        <select value={sort} aria-label="Sort" onChange={(e) => onFilter(setSort)(e.target.value)}>
          {SORTS.map((s) => <option key={s.value} value={s.value}>{s.text}</option>)}
        </select>
        <label className="checkbox">
          <input type="checkbox" checked={mine} onChange={(e) => onFilter(setMine)(e.target.checked)} /> Assigned to me
        </label>
        <label className="checkbox">
          <input type="checkbox" checked={overdue} onChange={(e) => onFilter(setOverdue)(e.target.checked)} /> Overdue only
        </label>
      </div>

      {error ? (
        <ErrorMessage message={error} onRetry={reload} />
      ) : loading && !data ? (
        <Loading text="Loading tasks..." />
      ) : !data || data.content.length === 0 ? (
        <EmptyState title="No tasks match these filters." />
      ) : (
        <>
          <div className={`table-wrap${loading ? ' is-loading' : ''}`}>
            <table className="table">
              <thead>
                <tr>
                  <th>Title</th>
                  {showProject && <th>Project</th>}
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Assignee</th>
                  <th>Due</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((task) => (
                  <tr key={task.id}>
                    <td data-label="Title"><Link to={`/tasks/${task.id}`}>{task.title}</Link></td>
                    {showProject && (
                      <td data-label="Project">
                        <Link to={`/projects/${task.projectId}`}>{projectNames[task.projectId] ?? 'Open project'}</Link>
                      </td>
                    )}
                    <td data-label="Status"><StatusBadge status={task.status} /></td>
                    <td data-label="Priority"><PriorityBadge priority={task.priority} /></td>
                    <td data-label="Assignee">{fullName(task.assignee)}</td>
                    <td data-label="Due">
                      {formatDate(task.dueDate)} {task.overdue && <OverdueBadge />}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data} onChange={setPage} />
        </>
      )}
    </div>
  );
}
