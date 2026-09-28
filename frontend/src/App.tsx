import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProfilePage } from './pages/ProfilePage';
import { ProjectDetailsPage } from './pages/ProjectDetailsPage';
import { ProjectFormPage } from './pages/ProjectFormPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { RegisterPage } from './pages/RegisterPage';
import { TaskDetailsPage } from './pages/TaskDetailsPage';
import { TaskFormPage } from './pages/TaskFormPage';
import { TasksPage } from './pages/TasksPage';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* logged in */}
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route index element={<DashboardPage />} />
              <Route path="projects" element={<ProjectsPage />} />
              <Route path="projects/:id" element={<ProjectDetailsPage />} />
              <Route path="projects/:id/edit" element={<ProjectFormPage />} />
              <Route path="tasks" element={<TasksPage />} />
              <Route path="tasks/new" element={<TaskFormPage />} />
              <Route path="tasks/:id" element={<TaskDetailsPage />} />
              <Route path="tasks/:id/edit" element={<TaskFormPage />} />
              <Route path="profile" element={<ProfilePage />} />

              {/* role-based pages: the API enforces the same rules */}
              <Route element={<RequireAuth roles={['ADMIN', 'PROJECT_MANAGER']} />}>
                <Route path="projects/new" element={<ProjectFormPage />} />
              </Route>
              <Route element={<RequireAuth roles={['ADMIN']} />}>
                <Route path="admin/users" element={<AdminUsersPage />} />
              </Route>
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
