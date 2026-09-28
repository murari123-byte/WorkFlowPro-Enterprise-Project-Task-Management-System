import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { refreshSession, setSessionExpiredHandler } from '../api/client';
import { authApi } from '../api/endpoints';
import { tokenStorage } from '../api/tokenStorage';
import type { AuthResponse, Role, User } from '../types';

interface AuthState {
  user: User | null;
  /** true while we check a saved session on page load */
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (data: { email: string; password: string; firstName: string; lastName: string }) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Holds the logged-in user. The user object comes from the backend (login/refresh response);
 * the roles here are only used to show or hide UI - the backend checks every request itself.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<User | null>(null);
  // Only need to check a session on load if a refresh token was saved
  const [initializing, setInitializing] = useState(() => tokenStorage.getRefreshToken() !== null);

  const applySession = useCallback((session: AuthResponse) => {
    tokenStorage.setAccessToken(session.accessToken);
    tokenStorage.setRefreshToken(session.refreshToken);
    setUserState(session.user);
  }, []);

  // On page load: if a refresh token was saved, turn it into a fresh session.
  useEffect(() => {
    setSessionExpiredHandler(() => setUserState(null));
    if (!tokenStorage.getRefreshToken()) {
      return;
    }
    refreshSession()
      .then((session) => setUserState(session.user))
      .catch(() => tokenStorage.clear())
      .finally(() => setInitializing(false));
  }, []);

  const value = useMemo<AuthState>(() => ({
    user,
    initializing,
    login: async (email, password) => applySession(await authApi.login(email, password)),
    register: async (data) => applySession(await authApi.register(data)),
    logout: async () => {
      const refreshToken = tokenStorage.getRefreshToken();
      tokenStorage.clear();
      setUserState(null);
      if (refreshToken) {
        await authApi.logout(refreshToken).catch(() => undefined);
      }
    },
    setUser: setUserState,
    hasRole: (...roles) => !!user && roles.some((role) => user.roles.includes(role)),
  }), [user, initializing, applySession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
