import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { errorMessage, fieldErrors } from '../api/errors';
import { Field } from '../components/Field';
import { ErrorMessage } from '../components/States';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const update = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value });

  // Same rules as the backend (RegisterRequest), checked early for a better experience
  const validate = () => {
    const found: Record<string, string> = {};
    if (!form.firstName.trim()) found.firstName = 'First name is required';
    if (!form.lastName.trim()) found.lastName = 'Last name is required';
    if (!EMAIL_PATTERN.test(form.email.trim())) found.email = 'Enter a valid email address';
    if (form.password.length < 8 || form.password.length > 72) found.password = 'Password must be 8–72 characters';
    if (form.confirm !== form.password) found.confirm = 'Passwords do not match';
    return found;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    setServerError(null);
    try {
      await register({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(fieldErrors(err));
      setServerError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit} noValidate>
        <h1 className="brand brand-large">WorkFlow<span>Pro</span></h1>
        <p className="muted">Create your account. New accounts start as Employee.</p>
        <ErrorMessage message={serverError} />
        <div className="form-row">
          <Field label="First name" error={errors.firstName}>
            <input value={form.firstName} onChange={update('firstName')} autoComplete="given-name" autoFocus />
          </Field>
          <Field label="Last name" error={errors.lastName}>
            <input value={form.lastName} onChange={update('lastName')} autoComplete="family-name" />
          </Field>
        </div>
        <Field label="Email" error={errors.email}>
          <input type="email" value={form.email} onChange={update('email')} autoComplete="email" />
        </Field>
        <Field label="Password" error={errors.password} hint="8–72 characters">
          <input type="password" value={form.password} onChange={update('password')} autoComplete="new-password" />
        </Field>
        <Field label="Confirm password" error={errors.confirm}>
          <input type="password" value={form.confirm} onChange={update('confirm')} autoComplete="new-password" />
        </Field>
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
        <p className="muted center">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
