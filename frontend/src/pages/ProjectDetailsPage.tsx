import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { projectsApi } from '../api/endpoints';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { StatusBadge } from '../components/Badges';
import { ErrorMessage, Loading } from '../components/States';
import { TaskTable } from '../components/TaskTable';
import { UserSelect } from '../components/UserSelect';
import type { ProjectStatus } from '../types';
import { formatDate, fullName, label } from '../utils/format';

export function ProjectDetailsPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { user, hasRole } = useAuth();
  const { data: project, setData, loading, error, reload } = useApi(() => projectsApi.get(id), [id]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showAddMember, setShowAddMember] = useState(false);
  const [showManager, setShowManager] = useState(false);

  if (loading) return <Loading text="Loading project..." />;
  if (error || !project) return <ErrorMessage message={error ?? 'Project not found'} onRetry={reload} />;

  const isOpen = project.status !== 'COMPLETED' && project.status !== 'CANCELLED';
  const isMember = project.members.some((m) => m.id === user?.id);
  // Same rule as the backend: manager, ADMIN, or TEAM_LEAD member may create tasks
  const canCreateTasks = (project.canManage || (hasRole('TEAM_LEAD') && isMember))
    && (project.status === 'PLANNING' || project.status === 'ACTIVE');

  /** Runs an action, shows its error, and stores the updated project the API returns. */
  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setActionError(null);
    try {
      const result = await action();
      if (result && typeof result === 'object' && 'id' in result) {
        setData(result as typeof project);
      } else {
        reload();
      }
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Delete project "${project.name}"? This cannot be undone.`)) return;
    setBusy(true);
    setActionError(null);
    try {
      await projectsApi.remove(project.id);
      navigate('/projects');
    } catch (err) {
      setActionError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <p className="breadcrumb"><Link to="/projects">Projects</Link> /</p>
          <h1>{project.name} <StatusBadge status={project.status} /></h1>
        </div>
        {project.canManage && (
          <div className="row-gap">
            {isOpen && <Link to={`/projects/${project.id}/edit`} className="btn">Edit</Link>}
            <button type="button" className="btn btn-danger" onClick={handleDelete} disabled={busy}>Delete</button>
          </div>
        )}
      </div>
      <ErrorMessage message={actionError} />

      <div className="grid-2">
        <section className="card">
          <h2 className="card-title">Details</h2>
          <p className="prewrap">{project.description || <span className="muted">No description.</span>}</p>
          <dl className="details">
            <dt>Manager</dt><dd>{fullName(project.manager)}</dd>
            <dt>Start</dt><dd>{formatDate(project.startDate)}</dd>
            <dt>End</dt><dd>{formatDate(project.endDate)}</dd>
            <dt>Last updated</dt><dd>{formatDate(project.updatedAt)}</dd>
          </dl>
          {project.allowedStatuses.length > 0 && (
            <div className="status-actions">
              <span className="muted small">Move to:</span>
              {project.allowedStatuses.map((status: ProjectStatus) => (
                <button key={status} type="button" className="btn btn-small" disabled={busy}
                        onClick={() => run(() => projectsApi.changeStatus(project.id, status))}>
                  {label(status)}
                </button>
              ))}
            </div>
          )}
          {hasRole('ADMIN') && isOpen && (
            <div className="subsection">
              <button type="button" className="btn btn-small btn-ghost" onClick={() => setShowManager((v) => !v)}>
                {showManager ? 'Close' : 'Change manager'}
              </button>
              {showManager && (
                <UserSelect role="PROJECT_MANAGER" excludeIds={[project.manager.id]} buttonText="Make manager"
                            onSelect={(u) => run(() => projectsApi.changeManager(project.id, u.id)).then(() => setShowManager(false))} />
              )}
            </div>
          )}
        </section>

        <section className="card">
          <div className="card-header">
            <h2 className="card-title">Members ({project.members.length})</h2>
            {project.canManage && isOpen && (
              <button type="button" className="btn btn-small" onClick={() => setShowAddMember((v) => !v)}>
                {showAddMember ? 'Close' : 'Add member'}
              </button>
            )}
          </div>
          {showAddMember && (
            <UserSelect excludeIds={project.members.map((m) => m.id)} buttonText="Add"
                        onSelect={(u) => run(() => projectsApi.addMember(project.id, u.id))} />
          )}
          <ul className="member-list">
            {project.members.map((member) => (
              <li key={member.id}>
                <span>
                  {fullName(member)}
                  {member.id === project.manager.id && <span className="badge status-active">Manager</span>}
                  <span className="muted small"> {member.email}</span>
                </span>
                {project.canManage && isOpen && member.id !== project.manager.id && (
                  <button type="button" className="btn btn-small btn-ghost" disabled={busy}
                          onClick={() => window.confirm(`Remove ${fullName(member)} from the project?`)
                            && run(() => projectsApi.removeMember(project.id, member.id))}>
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">Tasks</h2>
          {canCreateTasks && (
            <Link to={`/tasks/new?projectId=${project.id}`} className="btn btn-primary btn-small">New task</Link>
          )}
        </div>
        <TaskTable projectId={project.id} />
      </section>
    </>
  );
}
