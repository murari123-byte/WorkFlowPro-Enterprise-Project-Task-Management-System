import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { Loading } from '../components/States';
import type { Role } from '../types';

/**
 * Protects routes: not logged in -> /login. With roles: missing role -> dashboard.
 * This only hides pages; the API still rejects anything the user may not do.
 */
export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { user, initializing, hasRole } = useAuth();
  const location = useLocation();

  if (initializing) {
    return <Loading text="Checking your session..." />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (roles && !hasRole(...roles)) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
