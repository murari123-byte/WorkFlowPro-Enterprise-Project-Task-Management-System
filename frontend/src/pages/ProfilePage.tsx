import { useState, type FormEvent } from 'react';
import { usersApi } from '../api/endpoints';
import { errorMessage, fieldErrors } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../components/Field';
import { ErrorMessage } from '../components/States';
import { formatDate, label } from '../utils/format';

export function ProfilePage() {
  const { user, setUser, logout } = useAuth();
  const [name, setName] = useState({ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' });
  const [nameErrors, setNameErrors] = useState<Record<string, string>>({});
  const [nameMessage, setNameMessage] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);

  const [pw, setPw] = useState({ current: '', next: '', confirm: '' });
  const [pwErrors, setPwErrors] = useState<Record<string, string>>({});
  const [pwError, setPwError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  if (!user) return null;

  const saveName = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!name.firstName.trim()) found.firstName = 'First name is required';
    if (!name.lastName.trim()) found.lastName = 'Last name is required';
    setNameErrors(found);
    setNameMessage(null);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    setNameError(null);
    try {
      setUser(await usersApi.updateMe(name.firstName.trim(), name.lastName.trim()));
      setNameMessage('Profile saved.');
    } catch (err) {
      setNameErrors(fieldErrors(err));
      setNameError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!pw.current) found.current = 'Enter your current password';
    if (pw.next.length < 8 || pw.next.length > 72) found.next = 'New password must be 8–72 characters';
    if (pw.confirm !== pw.next) found.confirm = 'Passwords do not match';
    setPwErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    setPwError(null);
    try {
      await usersApi.changePassword(pw.current, pw.next);
      // The backend signs out every session after a password change
      alert('Password changed. Please sign in again with your new password.');
      await logout();
    } catch (err) {
      setPwError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="page-header"><h1>My profile</h1></div>
      <div className="grid-2">
        <form className="card form" onSubmit={saveName} noValidate>
          <h2 className="card-title">Details</h2>
          <dl className="details">
            <dt>Email</dt><dd>{user.email}</dd>
            <dt>Roles</dt><dd>{user.roles.map(label).join(', ')}</dd>
            <dt>Member since</dt><dd>{formatDate(user.createdAt)}</dd>
          </dl>
          <ErrorMessage message={nameError} />
          {nameMessage && <div className="alert alert-success">{nameMessage}</div>}
          <Field label="First name" error={nameErrors.firstName}>
            <input value={name.firstName} onChange={(e) => setName({ ...name, firstName: e.target.value })} maxLength={100} />
          </Field>
          <Field label="Last name" error={nameErrors.lastName}>
            <input value={name.lastName} onChange={(e) => setName({ ...name, lastName: e.target.value })} maxLength={100} />
          </Field>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={saving}>Save</button>
          </div>
        </form>

        <form className="card form" onSubmit={changePassword} noValidate>
          <h2 className="card-title">Change password</h2>
          <p className="muted small">You will be signed out on all devices.</p>
          <ErrorMessage message={pwError} />
          <Field label="Current password" error={pwErrors.current}>
            <input type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
          </Field>
          <Field label="New password" error={pwErrors.next} hint="8–72 characters">
            <input type="password" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />
          </Field>
          <Field label="Confirm new password" error={pwErrors.confirm}>
            <input type="password" autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />
          </Field>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={saving}>Change password</button>
          </div>
        </form>
      </div>
    </>
  );
}
