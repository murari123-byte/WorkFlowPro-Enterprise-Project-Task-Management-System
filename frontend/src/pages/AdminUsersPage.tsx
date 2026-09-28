import { useState } from 'react';
import { usersApi } from '../api/endpoints';
import { errorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';
import { useApi } from '../hooks/useApi';
import { useDebounce } from '../hooks/useDebounce';
import { Pagination } from '../components/Pagination';
import { EmptyState, ErrorMessage, Loading } from '../components/States';
import { ALL_ROLES, type Role, type User } from '../types';
import { formatDate, label } from '../utils/format';

/** ADMIN only: find users, change their roles, enable or disable accounts. */
export function AdminUsersPage() {
  const { user: me } = useAuth();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<{ id: string; roles: Role[] } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const debounced = useDebounce(search);

  const { data, setData, loading, error, reload } = useApi(
    () => usersApi.search({ search: debounced, role, page, size: 10, sort: 'createdAt,desc' }),
    [debounced, role, page],
  );

  /** Put the updated user from the API response into the table. */
  const replace = (updated: User) =>
    data && setData({ ...data, content: data.content.map((u) => (u.id === updated.id ? updated : u)) });

  const run = async (action: () => Promise<User>) => {
    setBusy(true);
    setActionError(null);
    try {
      replace(await action());
      setEditing(null);
    } catch (err) {
      setActionError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const toggleRole = (r: Role) => editing && setEditing({
    ...editing,
    roles: editing.roles.includes(r) ? editing.roles.filter((x) => x !== r) : [...editing.roles, r],
  });

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Users</h1>
          <p className="muted">Role changes take effect the next time the user signs in or their session refreshes.</p>
        </div>
      </div>
      <div className="card">
        <div className="filters">
          <input type="search" placeholder="Search by name or email" value={search} aria-label="Search users"
                 onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={role} aria-label="Role" onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0); }}>
            <option value="">All roles</option>
            {ALL_ROLES.map((r) => <option key={r} value={r}>{label(r)}</option>)}
          </select>
        </div>
        <ErrorMessage message={actionError} />

        {error ? (
          <ErrorMessage message={error} onRetry={reload} />
        ) : loading && !data ? (
          <Loading text="Loading users..." />
        ) : !data || data.content.length === 0 ? (
          <EmptyState title="No users found." />
        ) : (
          <>
            <div className={`table-wrap${loading ? ' is-loading' : ''}`}>
              <table className="table">
                <thead>
                  <tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th>Joined</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  {data.content.map((u) => {
                    const isMe = u.id === me?.id;
                    const isEditing = editing?.id === u.id;
                    return (
                      <tr key={u.id}>
                        <td data-label="Name">{u.firstName} {u.lastName}{isMe && <span className="muted small"> (you)</span>}</td>
                        <td data-label="Email">{u.email}</td>
                        <td data-label="Roles">
                          {isEditing ? (
                            <div className="role-checks">
                              {ALL_ROLES.map((r) => (
                                <label key={r} className="checkbox">
                                  <input type="checkbox" checked={editing.roles.includes(r)} onChange={() => toggleRole(r)}
                                         disabled={isMe && r === 'ADMIN'} />
                                  {label(r)}
                                </label>
                              ))}
                            </div>
                          ) : (
                            u.roles.map(label).join(', ')
                          )}
                        </td>
                        <td data-label="Status">
                          <span className={`badge ${u.enabled ? 'status-active' : 'status-cancelled'}`}>
                            {u.enabled ? 'Active' : 'Disabled'}
                          </span>
                        </td>
                        <td data-label="Joined">{formatDate(u.createdAt)}</td>
                        <td data-label="Actions">
                          <div className="row-gap">
                            {isEditing ? (
                              <>
                                <button type="button" className="btn btn-small btn-primary" disabled={busy || editing.roles.length === 0}
                                        onClick={() => run(() => usersApi.updateRoles(u.id, editing.roles))}>Save</button>
                                <button type="button" className="btn btn-small" onClick={() => setEditing(null)}>Cancel</button>
                              </>
                            ) : (
                              <button type="button" className="btn btn-small" onClick={() => setEditing({ id: u.id, roles: u.roles })}>
                                Edit roles
                              </button>
                            )}
                            {!isMe && (
                              <button type="button" className={`btn btn-small ${u.enabled ? 'btn-danger' : ''}`} disabled={busy}
                                      onClick={() => run(() => usersApi.updateStatus(u.id, !u.enabled))}>
                                {u.enabled ? 'Disable' : 'Enable'}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
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
