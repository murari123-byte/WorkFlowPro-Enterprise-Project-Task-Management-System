# Frontend (React)

The web app for WorkFlowPro. It talks **only to the API Gateway** (`http://localhost:9080`).

## Technology

| Package | Version | Why |
|---|---|---|
| React | 19 | UI components |
| TypeScript | 6 | Types for every API response, errors found at build time |
| Vite | 8 | Dev server (`npm run dev`) and production build (`npm run build`) |
| React Router (`react-router-dom`) | 7 | Pages, URLs, protected routes |
| Axios | 1.20 | HTTP calls, interceptors for the token and automatic refresh |
| oxlint | 1.x (dev only) | Linting (`npm run lint`), comes with the Vite template |

Styling is **plain CSS** in one file (`src/index.css`). No UI or chart library.
The project was created with the official template: `npm create vite@latest frontend -- --template react-ts`.

## Run it

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173  (the gateway must be running on 9080)
npm run build        # type-check + production build into frontend/dist
npm run lint         # oxlint
```

Optional settings — copy `frontend/.env.example` to `frontend/.env.local`:

| Variable | Default | Meaning |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:9080` | API Gateway URL. Only `VITE_*` variables reach the browser, so never put secrets here |

The gateway must allow the page's origin: `CORS_ALLOWED_ORIGINS=http://localhost:5173` (the default).

## Folder structure

```
frontend/src/
├── main.tsx              entry point (renders <App/>, loads index.css)
├── App.tsx               all routes
├── index.css             all styles (variables, layout, components, responsive rules)
├── types.ts              TypeScript types matching the backend JSON
├── api/
│   ├── client.ts         Axios instance: adds the token, refreshes it once on 401
│   ├── tokenStorage.ts   where tokens are kept (see "Tokens" below)
│   ├── endpoints.ts      one function per backend endpoint (authApi, usersApi, projectsApi, tasksApi)
│   └── errors.ts         turns backend ErrorResponse into readable messages / field errors
├── auth/
│   ├── AuthContext.tsx   logged-in user, login/register/logout, restores the session on reload
│   └── RequireAuth.tsx   protects routes (optionally by role)
├── hooks/
│   ├── useApi.ts         load data with loading / error / reload, ignores late answers
│   └── useDebounce.ts    waits until the user stops typing before searching
├── components/           Layout (nav), TaskTable, UserSelect, Pagination, Field, Badges, States
├── pages/                one file per page (see below)
└── utils/format.ts       names, labels ("IN_PROGRESS" → "In progress"), dates
```

## Pages and routes

| URL | Page | Who |
|---|---|---|
| `/login` | Sign in | everyone |
| `/register` | Create account (always EMPLOYEE) | everyone |
| `/` | Dashboard: 8 stat cards, tasks by status/priority, projects by status, my tasks | logged in |
| `/projects` | Project list: search, status filter, sort, pagination | logged in |
| `/projects/new` | Create project | ADMIN, PROJECT_MANAGER |
| `/projects/:id` | Details, status buttons, members (add/remove), change manager (ADMIN), project tasks | members |
| `/projects/:id/edit` | Edit project | manager or ADMIN |
| `/tasks` | All my projects' tasks: search, status, priority, "assigned to me", overdue, sort, pagination | logged in |
| `/tasks/new?projectId=…` | Create task (with assignee) | project manager, TEAM_LEAD member, ADMIN |
| `/tasks/:id` | Details, status buttons, assign, activity timeline | project members |
| `/tasks/:id/edit` | Edit title, description, priority, due date | project manager, TEAM_LEAD member, ADMIN |
| `/profile` | Change name, change password | logged in |
| `/admin/users` | Search users, edit roles, enable/disable | ADMIN |
| anything else | Not found | logged in |

**The frontend only hides things. The backend decides.** Buttons are shown using what the API returns
(`canManage`, `allowedStatuses`, `permissions.canEdit/canAssign/canDelete`) and the user's roles, but every
action is checked again by the service. A user who types a URL for a page they may not use is sent to the
dashboard, and the API would answer 403/404 anyway.

## How the frontend talks to the backend

```
Page ──calls──► endpoints.ts (e.g. tasksApi.search(filters))
                   │
                   ▼
              client.ts (Axios)
                   │  + header  Authorization: Bearer <access token>
                   ▼
          API Gateway :9080  ──►  auth / project / task service
```

- **Login / register** return `accessToken`, `refreshToken` and `user`. `AuthContext` stores them.
- **Every request** gets `Authorization: Bearer <accessToken>` from the request interceptor.
- **Access token expired (401)** → the response interceptor calls `/api/auth/refresh` once, stores the
  new pair and repeats the original request. If several requests fail at the same time, they share
  **one** refresh call (refresh tokens are single-use on the backend).
- **Refresh fails** → tokens are cleared and the user is shown the login page.
- **Page reload** → if a refresh token is saved, `AuthContext` calls `/api/auth/refresh` to get a new
  access token and the current user before showing any protected page.
- **Logout** → `/api/auth/logout` revokes the refresh token, and the tokens are removed in the browser.

### Tokens

| Token | Stored in | Why |
|---|---|---|
| Access token (15 min) | memory only (a JS variable) | never written to disk; lost on reload, then refreshed |
| Refresh token (7 days) | `localStorage` (`workflowpro.refreshToken`) | keeps the user logged in across reloads |

Trade-off: a script injected into the page (XSS) could read `localStorage`. React escapes all text it
renders, and the app never uses `dangerouslySetInnerHTML`, which keeps that risk low. A stricter design
would put the refresh token in an `HttpOnly` cookie — listed under future improvements.

## Loading, errors, empty states, validation

- **Loading:** every page shows a spinner while its first request runs. Lists stay visible but faded
  while a new page / filter loads (no jumping).
- **Errors:** `errorMessage()` shows the backend's `message` (e.g. "Only the project manager or an
  ADMIN can change this project"). Network problems show "Cannot reach the server...". Most errors have
  a "Try again" button.
- **Empty states:** "No projects found.", "No tasks match these filters.", "Nothing assigned to you right now.", ...
- **Form validation:** every form checks the same rules as the backend before sending (required fields,
  lengths, email format, password 8–72, end date ≥ start date, due date not in the past). If the backend
  still rejects the input, its `fieldErrors` are shown under the matching fields.
- **Search boxes** are debounced (350 ms) so the API is not called on every key press.
- **Late answers are ignored** (`useApi` and the edit forms): if an older request finishes after a newer
  one, its result is thrown away, so it can never overwrite newer data or what the user typed.

## Performance choices

- The dashboard loads project stats and task stats **in parallel** (`Promise.all`).
- Lists request one page at a time (`size` 10) — never "all tasks".
- The task list's project names come from **one** request (`/api/projects?size=100`), not one per row.
- Project members are fetched on the task page **only** when the user may assign the task.
- After an action (status change, assign, add member) the page uses the object the API returns instead
  of reloading everything.

## Responsive layout

- ≥ 960 px: 4 stat cards per row, 3 charts side by side.
- < 960 px: 2 stat cards per row, charts stacked.
- < 760 px: the navigation becomes a ☰ menu, two-column areas stack, and **tables turn into cards**
  (each cell shows its column name through `data-label`).
- < 480 px: one stat card per row.

## How it was verified (2026-09-28)

`npm run build` (TypeScript + Vite) and `npm run lint` pass with no warnings.

An end-to-end browser run (Playwright, Chromium, real backend and database) passed all 21 steps:
protected route redirect, login validation, wrong password, register with validation, employee sees no
"New project"/"Users", employee blocked from `/admin/users`, session survives reload, admin creates a project
(with date validation), project PLANNING → ACTIVE, add member, create task with assignee, edit priority,
employee starts and submits the task but cannot complete it, admin completes it, task filters, dashboard,
admin changes a user's roles, profile update, not-found page, mobile menu and stacked tables with no
horizontal scrolling. No errors in the browser console.
The browser script is not part of the repository (Playwright is not a project dependency).

Bugs found and fixed during that run:
- Form hints and errors were inside the `<label>`, so screen readers read the error as part of the field
  name. They are now outside the label.
- The edit forms (task, project) could be reset to old values by a late second load (React StrictMode
  runs effects twice in development; a slow network can do the same). Late answers are now ignored.
