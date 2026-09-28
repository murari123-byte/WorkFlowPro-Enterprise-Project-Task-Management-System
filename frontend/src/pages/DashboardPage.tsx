import { Link } from 'react-router-dom';
import { projectsApi, tasksApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { PriorityBadge, StatusBadge, OverdueBadge } from '../components/Badges';
import { EmptyState, ErrorMessage, Loading } from '../components/States';
import { PROJECT_STATUSES, TASK_PRIORITIES, TASK_STATUSES } from '../types';
import { formatDate, label } from '../utils/format';

function StatCard({ title, value, tone }: { title: string; value: number; tone?: string }) {
  return (
    <div className={`stat-card${tone ? ` stat-${tone}` : ''}`}>
      <span className="stat-value">{value}</span>
      <span className="stat-title">{title}</span>
    </div>
  );
}

/** Simple horizontal bar chart with plain CSS (no chart library needed). */
function BarList({ title, rows }: { title: string; rows: { key: string; value: number }[] }) {
  const max = Math.max(1, ...rows.map((row) => row.value));
  return (
    <div className="card">
      <h2 className="card-title">{title}</h2>
      <ul className="bars">
        {rows.map((row) => (
          <li key={row.key}>
            <span className="bar-label">{label(row.key)}</span>
            <span className="bar-track">
              <span className={`bar-fill fill-${row.key.toLowerCase()}`} style={{ width: `${(row.value / max) * 100}%` }} />
            </span>
            <span className="bar-value">{row.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  // The three requests are independent, so they run in parallel
  const stats = useApi(() => Promise.all([projectsApi.stats(), tasksApi.stats()]), []);
  const myTasks = useApi(
    () => tasksApi.search({ assigneeId: user?.id, open: true, sort: 'dueDate,asc', size: 5 }),
    [user?.id],
  );

  if (stats.loading) return <Loading text="Loading dashboard..." />;
  if (stats.error || !stats.data) return <ErrorMessage message={stats.error ?? 'No data'} onRetry={stats.reload} />;

  const [projects, tasks] = stats.data;
  const openMyTasks = myTasks.data?.content ?? [];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Welcome, {user?.firstName}</h1>
          <p className="muted">Numbers below include only the projects you are a member of{user?.roles.includes('ADMIN') ? ' (you are an admin: all projects)' : ''}.</p>
        </div>
      </div>

      <section className="stats-grid" aria-label="Project statistics">
        <StatCard title="Total projects" value={projects.total} />
        <StatCard title="Active projects" value={projects.active} tone="blue" />
        <StatCard title="Completed projects" value={projects.completed} tone="green" />
        <StatCard title="Total tasks" value={tasks.total} />
        <StatCard title="Pending tasks" value={tasks.pending} tone="amber" />
        <StatCard title="Completed tasks" value={tasks.completed} tone="green" />
        <StatCard title="Overdue tasks" value={tasks.overdue} tone="red" />
        <StatCard title="My open tasks" value={tasks.myOpenTasks} tone="blue" />
      </section>

      <section className="grid-3">
        <BarList title="Tasks by status" rows={TASK_STATUSES.map((s) => ({ key: s, value: tasks.byStatus[s] ?? 0 }))} />
        <BarList title="Tasks by priority" rows={TASK_PRIORITIES.map((p) => ({ key: p, value: tasks.byPriority[p] ?? 0 }))} />
        <BarList title="Projects by status" rows={PROJECT_STATUSES.map((s) => ({ key: s, value: projects.byStatus[s] ?? 0 }))} />
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">My tasks</h2>
          <Link to="/tasks?mine=1">View all my tasks</Link>
        </div>
        {myTasks.loading ? (
          <Loading />
        ) : myTasks.error ? (
          <ErrorMessage message={myTasks.error} onRetry={myTasks.reload} />
        ) : openMyTasks.length === 0 ? (
          <EmptyState title="Nothing assigned to you right now." />
        ) : (
          <ul className="task-mini-list">
            {openMyTasks.map((task) => (
              <li key={task.id}>
                <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                <span className="row-gap">
                  <StatusBadge status={task.status} />
                  <PriorityBadge priority={task.priority} />
                  {task.overdue && <OverdueBadge />}
                  <span className="muted small">Due {formatDate(task.dueDate)}</span>
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
