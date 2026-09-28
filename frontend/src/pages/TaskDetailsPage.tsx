import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { projectsApi, tasksApi } from '../api/endpoints';
import { errorMessage } from '../api/errors';
import { useApi } from '../hooks/useApi';
import { OverdueBadge, PriorityBadge, StatusBadge } from '../components/Badges';
import { EmptyState, ErrorMessage, Loading } from '../components/States';
import type { Task, TaskHistoryEntry, UserSummary } from '../types';
import { formatDate, formatDateTime, fullName, label } from '../utils/format';

/** One line of the activity timeline, in plain words. */
function describe(entry: TaskHistoryEntry): string {
  const from = entry.oldValue ?? 'none';
  const to = entry.newValue ?? 'none';
  switch (entry.action) {
    case 'CREATED':
      return 'created the task';
    case 'ASSIGNED':
      return entry.newValue ? `assigned it to ${entry.newValue}` : `removed the assignee (${from})`;
    case 'STATUS_CHANGED':
      return `changed status from ${label(from)} to ${label(to)}`;
    case 'PRIORITY_CHANGED':
      return `changed priority from ${label(from)} to ${label(to)}`;
    case 'DUE_DATE_CHANGED':
      return `changed the due date from ${formatDate(entry.oldValue)} to ${formatDate(entry.newValue)}`;
    case 'UPDATED':
      return entry.field === 'title' ? `renamed it from "${from}" to "${to}"` : `updated the ${entry.field ?? 'task'}`;
  }
}

function History({ taskId, version }: { taskId: string; version: number }) {
  const [entries, setEntries] = useState<TaskHistoryEntry[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Reload from the start whenever the task changes (version goes up)
  useEffect(() => {
    let current = true;
    tasksApi.history(taskId, 0)
      .then((result) => {
        if (!current) return;
        setEntries(result.content);
        setPage(0);
        setHasMore(result.totalPages > 1);
        setError(null);
      })
      .catch((e) => current && setError(errorMessage(e)))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
  }, [taskId, version]);

  const loadMore = async () => {
    setLoading(true);
    try {
      const result = await tasksApi.history(taskId, page + 1);
      setEntries((old) => [...old, ...result.content]);
      setPage(page + 1);
      setHasMore(page + 2 < result.totalPages);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="card">
      <h2 className="card-title">Activity</h2>
      <ErrorMessage message={error} />
      {entries.length === 0 && !loading ? (
        <EmptyState title="No activity yet." />
      ) : (
        <ol className="timeline">
          {entries.map((entry) => (
            <li key={entry.id}>
              <span className="timeline-text"><strong>{fullName(entry.actor)}</strong> {describe(entry)}</span>
              <span className="muted small">{formatDateTime(entry.createdAt)}</span>
            </li>
          ))}
        </ol>
      )}
      {loading && <Loading />}
      {hasMore && !loading && (
        <button type="button" className="btn btn-small" onClick={loadMore}>Show older activity</button>
      )}
    </section>
  );
}

export function TaskDetailsPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { data: task, setData, loading, error, reload } = useApi(() => tasksApi.get(id), [id]);
  const [members, setMembers] = useState<UserSummary[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [historyVersion, setHistoryVersion] = useState(0);

  // Project members are only needed for the assignee dropdown
  const canAssign = task?.permissions.canAssign ?? false;
  const projectId = task?.projectId;
  useEffect(() => {
    if (!canAssign || !projectId) return;
    projectsApi.get(projectId).then((p) => setMembers(p.members)).catch(() => setMembers([]));
  }, [canAssign, projectId]);

  if (loading) return <Loading text="Loading task..." />;
  if (error || !task) return <ErrorMessage message={error ?? 'Task not found'} onRetry={reload} />;

  const run = async (action: () => Promise<Task>) => {
    setBusy(true);
    setActionError(null);
    try {
      setData(await action());
      setHistoryVersion((v) => v + 1);
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Delete task "${task.title}"? This cannot be undone.`)) return;
    setBusy(true);
    try {
      await tasksApi.remove(task.id);
      navigate(`/projects/${task.projectId}`);
    } catch (err) {
      setActionError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="breadcrumb">
            <Link to="/projects">Projects</Link> / <Link to={`/projects/${task.projectId}`}>{task.projectName}</Link> /
          </p>
          <h1>{task.title}</h1>
          <div className="row-gap">
            <StatusBadge status={task.status} />
            <PriorityBadge priority={task.priority} />
            {task.overdue && <OverdueBadge />}
          </div>
        </div>
        <div className="row-gap">
          {task.permissions.canEdit && <Link to={`/tasks/${task.id}/edit`} className="btn">Edit</Link>}
          {task.permissions.canDelete && (
            <button type="button" className="btn btn-danger" onClick={handleDelete} disabled={busy}>Delete</button>
          )}
        </div>
      </div>
      <ErrorMessage message={actionError} />

      <div className="grid-2">
        <section className="card">
          <h2 className="card-title">Details</h2>
          <p className="prewrap">{task.description || <span className="muted">No description.</span>}</p>
          <dl className="details">
            <dt>Assignee</dt><dd>{fullName(task.assignee)}</dd>
            <dt>Due date</dt><dd>{formatDate(task.dueDate)}</dd>
            <dt>Created by</dt><dd>{fullName(task.createdBy)}, {formatDate(task.createdAt)}</dd>
            {task.completedAt && (<><dt>Completed</dt><dd>{formatDateTime(task.completedAt)}</dd></>)}
          </dl>

          {task.allowedStatuses.length > 0 && (
            <div className="status-actions">
              <span className="muted small">Move to:</span>
              {task.allowedStatuses.map((status) => (
                <button key={status} type="button" className="btn btn-small" disabled={busy}
                        onClick={() => run(() => tasksApi.changeStatus(task.id, status))}>
                  {label(status)}
                </button>
              ))}
            </div>
          )}

          {canAssign && (
            <label className="field field-control subsection">
              <span className="field-label">Assign to</span>
              <select value={task.assignee?.id ?? ''} disabled={busy}
                      onChange={(e) => run(() => tasksApi.assign(task.id, e.target.value || null))}>
                <option value="">Unassigned</option>
                {members.map((member) => <option key={member.id} value={member.id}>{fullName(member)}</option>)}
              </select>
            </label>
          )}
        </section>

        <History taskId={task.id} version={historyVersion} />
      </div>
    </>
  );
}
