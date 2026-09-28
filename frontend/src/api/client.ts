import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from './tokenStorage';
import type { AuthResponse } from '../types';

/** Every request goes to the API Gateway. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9080';

export const api = axios.create({ baseURL: API_BASE_URL, timeout: 15000 });

// 1. Attach the access token to every request.
api.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Called when the session cannot be refreshed (set by AuthContext). */
let onSessionExpired: () => void = () => {};
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

/**
 * Exchange the refresh token for a new pair. If several requests fail with 401 at the same
 * time, they all wait for ONE refresh call (refresh tokens are single-use).
 */
let refreshPromise: Promise<AuthResponse> | null = null;
export function refreshSession(): Promise<AuthResponse> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    return Promise.reject(new Error('No refresh token'));
  }
  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>(`${API_BASE_URL}/api/auth/refresh`, { refreshToken })
      .then((response) => {
        tokenStorage.setAccessToken(response.data.accessToken);
        tokenStorage.setRefreshToken(response.data.refreshToken);
        return response.data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

// 2. Access token expired (401)? Refresh once and repeat the original request.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith('/api/auth/');
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      try {
        await refreshSession();
        return api(original);
      } catch {
        tokenStorage.clear();
        onSessionExpired();
      }
    }
    return Promise.reject(error);
  },
);
