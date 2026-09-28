import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { label } from '../utils/format';

export function Layout() {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const close = () => setMenuOpen(false);

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/" className="brand" onClick={close}>
            WorkFlow<span>Pro</span>
          </NavLink>
          <button
            type="button"
            className="menu-toggle"
            aria-label="Toggle navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            ☰
          </button>
          <nav className={`nav${menuOpen ? ' nav-open' : ''}`}>
            <NavLink to="/" end onClick={close}>Dashboard</NavLink>
            <NavLink to="/projects" onClick={close}>Projects</NavLink>
            <NavLink to="/tasks" onClick={close}>Tasks</NavLink>
            {hasRole('ADMIN') && <NavLink to="/admin/users" onClick={close}>Users</NavLink>}
            <NavLink to="/profile" className="nav-user" onClick={close}>
              {user?.firstName} <small>{user && label(user.roles[0] ?? '')}</small>
            </NavLink>
            <button type="button" className="btn btn-small btn-ghost" onClick={handleLogout}>
              Log out
            </button>
          </nav>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  );
}
