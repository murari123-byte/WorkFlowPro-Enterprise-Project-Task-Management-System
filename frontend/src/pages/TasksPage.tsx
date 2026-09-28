import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { projectsApi } from '../api/endpoints';
import { useApi } from '../hooks/useApi';
import { TaskTable } from '../components/TaskTable';

/** All tasks from every project I can see. ?mine=1 starts with "Assigned to me" ticked. */
export function TasksPage() {
  const [params] = useSearchParams();
  // Project names for the "Project" column: one request for up to 100 projects
  const { data } = useApi(() => projectsApi.list({ size: 100, sort: 'name,asc' }), []);
  const projectNames = useMemo(
    () => Object.fromEntries((data?.content ?? []).map((p) => [p.id, p.name])),
    [data],
  );

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Tasks</h1>
          <p className="muted">Tasks from all projects you are a member of. Create tasks from a project page.</p>
        </div>
      </div>
      <div className="card">
        <TaskTable showProject projectNames={projectNames} initialMine={params.get('mine') === '1'} />
      </div>
    </>
  );
}
