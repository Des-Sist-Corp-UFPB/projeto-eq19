import React from 'react';
import { HashRouter as Router, Routes, Route } from 'react-router-dom';
import { DatabaseProvider } from './context/DatabaseContext';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';

// Pages
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

// Components
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { ProtectedRoute } from './components/ProtectedRoute';

function App() {
  return (
    <ToastProvider>
      <DatabaseProvider>
        <AuthProvider>
          <Router>
            <div style={appWrapperStyle}>
              {/* Navbar houses the login simulator bar and search modal */}
              <Navbar />

              {/* Main Content Area */}
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
                  <Route path="/events" element={<ProtectedRoute><Events /></ProtectedRoute>} />
                  <Route path="/players" element={<ProtectedRoute><Players /></ProtectedRoute>} />
                  <Route path="/players/:id" element={<ProtectedRoute><PlayerProfile /></ProtectedRoute>} />
                  <Route path="/stats" element={<ProtectedRoute><Stats /></ProtectedRoute>} />
                </Routes>
              </main>

              {/* Community Footer */}
              <Footer />
            </div>
          </Router>
        </AuthProvider>
      </DatabaseProvider>
    </ToastProvider>
  );
}

// Global App Styles
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
