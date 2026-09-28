import { useState } from 'react';
import { usersApi } from '../api/endpoints';
import { useApi } from '../hooks/useApi';
import { useDebounce } from '../hooks/useDebounce';
import type { Role, User } from '../types';

/**
 * Search box + list of matching users (from GET /api/users). Used to add project members
 * and to pick a new project manager. Only ADMIN / PROJECT_MANAGER / TEAM_LEAD can search users.
 */
export function UserSelect({ onSelect, role, excludeIds = [], buttonText }: {
  onSelect: (user: User) => void | Promise<void>;
  role?: Role;
  excludeIds?: string[];
  buttonText: string;
}) {
  const [search, setSearch] = useState('');
  const debounced = useDebounce(search);
  const { data, loading, error } = useApi(() => usersApi.search({ search: debounced, role, size: 8 }), [debounced, role]);
  const users = (data?.content ?? []).filter((user) => user.enabled && !excludeIds.includes(user.id));

  return (
    <div className="user-select">
      <input
        type="search"
        placeholder="Search by name or email"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        aria-label="Search users"
      />
      {error && <p className="field-error">{error}</p>}
      {loading ? (
        <p className="muted small">Searching...</p>
      ) : users.length === 0 ? (
        <p className="muted small">No matching users.</p>
      ) : (
        <ul className="user-select-list">
          {users.map((user) => (
            <li key={user.id}>
              <span>
                {user.firstName} {user.lastName} <span className="muted small">{user.email}</span>
              </span>
              <button type="button" className="btn btn-small" onClick={() => onSelect(user)}>
                {buttonText}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
