import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProtectedRoute } from '../components/ProtectedRoute';

const auth = vi.hoisted(() => ({
  currentUser: null as null | { id: string },
  isAdmin: false,
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => auth,
}));

describe('ProtectedRoute', () => {
  beforeEach(() => {
    auth.currentUser = null;
    auth.isAdmin = false;
  });

  it('redirects anonymous and non-admin users away from admin routes', () => {
    const firstRender = renderAuditRoute();
    expect(screen.getByText('Login')).toBeInTheDocument();
    firstRender.unmount();

    auth.currentUser = { id: 'u_student' };
    renderAuditRoute();
    expect(screen.getByText('Home')).toBeInTheDocument();
  });

  it('allows administrators to open the protected content', () => {
    auth.currentUser = { id: 'u_admin' };
    auth.isAdmin = true;

    renderAuditRoute();

    expect(screen.getByText('Auditoria protegida')).toBeInTheDocument();
  });
});

function renderAuditRoute() {
  return render(auditRouter());
}

function auditRouter() {
  return (
    <MemoryRouter initialEntries={['/audit-logs']}>
      <Routes>
        <Route path="/" element={<div>Home</div>} />
        <Route path="/login" element={<div>Login</div>} />
        <Route
          path="/audit-logs"
          element={(
            <ProtectedRoute adminOnly>
              <div>Auditoria protegida</div>
            </ProtectedRoute>
          )}
        />
      </Routes>
    </MemoryRouter>
  );
}
