import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { projectsApi, tasksApi } from '../api/endpoints';
import { errorMessage, fieldErrors } from '../api/errors';
import { Field } from '../components/Field';
import { ErrorMessage, Loading } from '../components/States';
import { TASK_PRIORITIES, type TaskPriority, type UserSummary } from '../types';
import { fullName, label, todayIso } from '../utils/format';

/** Create (/tasks/new?projectId=...) or edit (/tasks/:id/edit). */
export function TaskFormPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [projectId, setProjectId] = useState(params.get('projectId') ?? '');
  const [projectName, setProjectName] = useState('');
  const [members, setMembers] = useState<UserSummary[]>([]);
  const [form, setForm] = useState({ title: '', description: '', priority: 'MEDIUM' as TaskPriority, dueDate: '', assigneeId: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Load once. `current` ignores a late answer (e.g. React StrictMode runs effects twice in dev),
  // so it can never overwrite what the user has already typed.
  useEffect(() => {
    let current = true;
    const load = async () => {
      try {
        if (id) {
          const task = await tasksApi.get(id);
          if (!current) return;
          setProjectId(task.projectId);
          setProjectName(task.projectName);
          setForm({ title: task.title, description: task.description ?? '', priority: task.priority, dueDate: task.dueDate ?? '', assigneeId: '' });
        } else if (projectId) {
          const project = await projectsApi.get(projectId);
          if (!current) return;
          setProjectName(project.name);
          setMembers(project.members);
        } else {
          setServerError('Open a project and use "New task" to create a task.');
        }
      } catch (e) {
        if (current) setServerError(errorMessage(e));
      } finally {
        if (current) setLoading(false);
      }
    };
    load();
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const update = (key: keyof typeof form) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
      setForm({ ...form, [key]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.title.trim()) found.title = 'Title is required';
    if (form.title.length > 200) found.title = 'Title can be at most 200 characters';
    if (form.description.length > 5000) found.description = 'Description can be at most 5000 characters';
    if (form.dueDate && form.dueDate < todayIso()) found.dueDate = 'Due date cannot be in the past';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSaving(true);
    setServerError(null);
    const base = { title: form.title.trim(), description: form.description, priority: form.priority, dueDate: form.dueDate || null };
    try {
      const saved = id
        ? await tasksApi.update(id, base)
        : await tasksApi.create({ ...base, projectId, assigneeId: form.assigneeId || null });
      navigate(`/tasks/${saved.id}`);
    } catch (err) {
      setErrors(fieldErrors(err));
      setServerError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <Loading />;

  return (
    <>
      <div className="page-header">
        <div>
          {projectName && <p className="breadcrumb"><Link to={`/projects/${projectId}`}>{projectName}</Link> /</p>}
          <h1>{isEdit ? 'Edit task' : 'New task'}</h1>
        </div>
      </div>
      <form className="card form" onSubmit={handleSubmit} noValidate>
        <ErrorMessage message={serverError} />
        <Field label="Title" error={errors.title}>
          <input value={form.title} onChange={update('title')} maxLength={200} autoFocus />
        </Field>
        <Field label="Description" error={errors.description}>
          <textarea rows={5} value={form.description} onChange={update('description')} maxLength={5000} />
        </Field>
        <div className="form-row">
          <Field label="Priority" error={errors.priority}>
            <select value={form.priority} onChange={update('priority')}>
              {TASK_PRIORITIES.map((p) => <option key={p} value={p}>{label(p)}</option>)}
            </select>
          </Field>
          <Field label="Due date" error={errors.dueDate}>
            <input type="date" value={form.dueDate} onChange={update('dueDate')} min={todayIso()} />
          </Field>
        </div>
        {!isEdit && (
          <Field label="Assignee" error={errors.assigneeId} hint="Only project members can be assigned">
            <select value={form.assigneeId} onChange={update('assigneeId')}>
              <option value="">Unassigned</option>
              {members.map((m) => <option key={m.id} value={m.id}>{fullName(m)}</option>)}
            </select>
          </Field>
        )}
        <div className="form-actions">
          <Link to={id ? `/tasks/${id}` : projectId ? `/projects/${projectId}` : '/tasks'} className="btn">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={saving || (!isEdit && !projectId)}>
            {saving ? 'Saving...' : isEdit ? 'Save changes' : 'Create task'}
          </button>
        </div>
      </form>
    </>
  );
}
