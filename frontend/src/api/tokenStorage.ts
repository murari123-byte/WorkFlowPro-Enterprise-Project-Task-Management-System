// Where tokens live in the browser.
// - Access token: only in memory (a JS variable). Gone on page reload, never written to disk.
// - Refresh token: localStorage, so the user stays logged in after a reload.
//   Trade-off: any script running on the page (XSS) could read it. See docs/frontend.md.

const REFRESH_KEY = 'workflowpro.refreshToken';

let accessToken: string | null = null;

export const tokenStorage = {
  getAccessToken: () => accessToken,
  setAccessToken: (token: string | null) => {
    accessToken = token;
  },
  getRefreshToken: () => localStorage.getItem(REFRESH_KEY),
  setRefreshToken: (token: string | null) => {
    if (token) {
      localStorage.setItem(REFRESH_KEY, token);
    } else {
      localStorage.removeItem(REFRESH_KEY);
    }
  },
  clear: () => {
    accessToken = null;
    localStorage.removeItem(REFRESH_KEY);
  },
};
