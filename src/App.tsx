import React from 'react';
import { HashRouter as Router, Routes, Route } from 'react-router-dom';
import { DatabaseProvider } from './context/DatabaseContext';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';

// Páginas
import { Home } from './pages/Home';
import { Games } from './pages/Games';
import { GameDetails } from './pages/GameDetails';
import { Sessions } from './pages/Sessions';
import { SessionDetails } from './pages/SessionDetails';
import { Events } from './pages/Events';
import { Players } from './pages/Players';
import { PlayerProfile } from './pages/PlayerProfile';
import { Stats } from './pages/Stats';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { ForgotPassword } from './pages/ForgotPassword';
import { ResendVerification } from './pages/ResendVerification';
import { VerifyEmail } from './pages/VerifyEmail';
import { AuditLogs } from './pages/AuditLogs';

// Componentes
import { Navbar } from './components/Navbar';
import { ProtectedRoute } from './components/ProtectedRoute';

function App() {
  return (
    <ToastProvider>
      <DatabaseProvider>
        <AuthProvider>
          <Router>
            <div style={appWrapperStyle}>
              {/* Navbar abriga a barra do simulador de login e o modal de busca */}
              <Navbar />

              {/* Área de Conteúdo Principal */}
              <main style={mainContentStyle}>
                <Routes>
                  <Route path="/" element={<Home />} />
                  <Route path="/games" element={<Games />} />
                  <Route path="/games/:id" element={<GameDetails />} />
                  <Route path="/sessions" element={<Sessions />} />
                  <Route path="/sessions/:id" element={<SessionDetails />} />
                  <Route path="/login" element={<Login />} />
                  <Route path="/register" element={<Register />} />
                  <Route path="/forgot-password" element={<ForgotPassword />} />
                  <Route path="/resend-verification" element={<ResendVerification />} />
                  <Route path="/verify-email" element={<VerifyEmail />} />
                  <Route path="/events" element={<ProtectedRoute><Events /></ProtectedRoute>} />
                  <Route path="/players" element={<ProtectedRoute><Players /></ProtectedRoute>} />
                  <Route path="/players/:id" element={<ProtectedRoute><PlayerProfile /></ProtectedRoute>} />
                  <Route path="/stats" element={<ProtectedRoute><Stats /></ProtectedRoute>} />
                  <Route path="/audit-logs" element={<ProtectedRoute adminOnly><AuditLogs /></ProtectedRoute>} />
                </Routes>
              </main>
            </div>
          </Router>
        </AuthProvider>
      </DatabaseProvider>
    </ToastProvider>
  );
}

// Estilos Globais da Aplicação
const appWrapperStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  minHeight: '100vh',
};

const mainContentStyle: React.CSSProperties = {
  flexGrow: 1,
  paddingBottom: '48px',
};

export default App;
