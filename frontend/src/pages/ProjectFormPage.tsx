import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { projectsApi } from '../api/endpoints';
import { errorMessage, fieldErrors } from '../api/errors';
import { Field } from '../components/Field';
import { ErrorMessage, Loading } from '../components/States';

/** Create (/projects/new) or edit (/projects/:id/edit). */
export function ProjectFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', description: '', startDate: '', endDate: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!id) return;
    projectsApi.get(id)
      .then((p) => setForm({ name: p.name, description: p.description ?? '', startDate: p.startDate ?? '', endDate: p.endDate ?? '' }))
      .catch((e) => setServerError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [id]);

  const update = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm({ ...form, [key]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!form.name.trim()) found.name = 'Name is required';
    if (form.name.length > 150) found.name = 'Name can be at most 150 characters';
    if (form.description.length > 2000) found.description = 'Description can be at most 2000 characters';
    if (form.startDate && form.endDate && form.endDate < form.startDate) found.endDate = 'End date must not be before start date';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSaving(true);
    setServerError(null);
    const payload = {
      name: form.name.trim(),
      description: form.description,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
    };
    try {
      const saved = id ? await projectsApi.update(id, payload) : await projectsApi.create(payload);
      navigate(`/projects/${saved.id}`);
    } catch (err) {
      const fields = fieldErrors(err);
      if (fields.dateRangeValid) fields.endDate = fields.dateRangeValid;
      setErrors(fields);
      setServerError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <Loading />;

  return (
    <>
      <div className="page-header">
        <h1>{isEdit ? 'Edit project' : 'New project'}</h1>
      </div>
      <form className="card form" onSubmit={handleSubmit} noValidate>
        <ErrorMessage message={serverError} />
        <Field label="Name" error={errors.name}>
          <input value={form.name} onChange={update('name')} maxLength={150} autoFocus />
        </Field>
        <Field label="Description" error={errors.description}>
          <textarea rows={4} value={form.description} onChange={update('description')} maxLength={2000} />
        </Field>
        <div className="form-row">
          <Field label="Start date" error={errors.startDate}>
            <input type="date" value={form.startDate} onChange={update('startDate')} />
          </Field>
          <Field label="End date" error={errors.endDate}>
            <input type="date" value={form.endDate} onChange={update('endDate')} min={form.startDate || undefined} />
          </Field>
        </div>
        {!isEdit && <p className="muted small">You will be the project manager. New projects start in Planning.</p>}
        <div className="form-actions">
          <Link to={id ? `/projects/${id}` : '/projects'} className="btn">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving...' : isEdit ? 'Save changes' : 'Create project'}
          </button>
        </div>
      </form>
    </>
  );
}
