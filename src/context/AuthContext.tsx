import React, { createContext, useContext, useMemo, useState } from 'react';
import type { User } from '../types';
import { isStrongPassword } from '../auth/security';
import { AUTH_TOKEN_KEY, changePasswordBackend, loginBackend, registerUserBackend, resetPasswordBackend, resendVerificationBackend, verifyEmailCode as verifyEmailCodeBackend } from '../services/api';
import { useDatabase } from './DatabaseContext';
import { useToast } from './ToastContext';

interface AuthContextType {
  currentUser: User | null;
  isAdmin: boolean;
  login: (email: string, password: string, remember?: boolean) => Promise<{ ok: boolean; message: string }>;
  register: (name: string, email: string, password: string, confirmPassword: string) => Promise<{ ok: boolean; message: string }>;
  logout: () => void;
  resetPassword: (email: string, newPassword: string, confirmPassword: string) => Promise<{ ok: boolean; message: string }>;
  changePassword: (currentPassword: string, newPassword: string, confirmPassword: string) => Promise<{ ok: boolean; message: string }>;
  resendVerification: (email: string) => Promise<{ ok: boolean; message: string }>;
  verifyEmailCode: (email: string, code: string) => Promise<{ ok: boolean; message: string }>;
  availableUsers: User[];
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);
const SESSION_KEY = 'tabula_auth_session';

const authErrorMessage = (error: unknown, fallback: string) => {
  if (!(error instanceof Error)) return fallback;
  const match = error.message.match(/API request failed \((\d+)\): (.*)/);
  if (!match) return fallback;

  const body = match[2];
  try {
    const parsed = JSON.parse(body) as { error?: string };
    return parsed.error || fallback;
  } catch {
    return body.trim() || fallback;
  }
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { state, addUser } = useDatabase();
  const { showToast } = useToast();
  const [sessionUserId, setSessionUserId] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    const token = localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY);
    if (!token) return null;
    return localStorage.getItem(SESSION_KEY) || sessionStorage.getItem(SESSION_KEY);
  });

  const currentUser = useMemo(() => {
    return sessionUserId ? state.users.find(user => user.id === sessionUserId) || null : null;
  }, [sessionUserId, state.users]);

  const isAdmin = currentUser?.role === 'admin';

  const persistSession = (userId: string, token: string, remember: boolean) => {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);
    localStorage.removeItem(AUTH_TOKEN_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_KEY);
    if (remember) {
      localStorage.setItem(SESSION_KEY, userId);
      localStorage.setItem(AUTH_TOKEN_KEY, token);
    } else {
      sessionStorage.setItem(SESSION_KEY, userId);
      sessionStorage.setItem(AUTH_TOKEN_KEY, token);
    }
    setSessionUserId(userId);
  };

  const selectOrAddUser = (serverUser: User) => {
    const existing = state.users.find(user => user.id === serverUser.id || user.email.toLowerCase() === serverUser.email.toLowerCase());
    if (existing) {
      return existing.id;
    }

    const created = addUser(serverUser);
    return created?.id || serverUser.id;
  };

  const login = async (email: string, password: string, remember = true) => {
    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail || !password) {
      const message = 'Informe e-mail e senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      const response = await loginBackend(normalizedEmail, password);
      const userId = selectOrAddUser(response.user);
      persistSession(userId, response.token, remember);
      showToast(`Bem-vindo, ${response.user.name.split(' ')[0]}!`, 'success');
      return { ok: true, message: response.message || 'Login realizado com sucesso.' };
    } catch (err) {
      const message = authErrorMessage(err, 'Credenciais inválidas ou servidor indisponível.');
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const register = async (name: string, email: string, password: string, confirmPassword: string) => {
    if (!name.trim() || !email.trim() || !password || !confirmPassword) {
      const message = 'Preencha todos os campos para criar sua conta.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      const message = 'Informe um e-mail válido.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    if (password !== confirmPassword) {
      const message = 'As senhas não coincidem.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    if (!isStrongPassword(password)) {
      const message = 'A senha deve ter no mínimo 8 caracteres, incluir letra maiúscula, número e símbolo.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    const normalizedEmail = email.trim().toLowerCase();
    try {
      const response = await registerUserBackend(name.trim(), normalizedEmail, password);
      if (response.token && response.user) {
        const userId = selectOrAddUser(response.user);
        persistSession(userId, response.token, true);
        showToast('Conta criada com sucesso no servidor.', 'success');
      } else {
        showToast(response.message || 'Conta criada. Verifique seu e-mail para ativar sua conta.', 'info');
      }
      return { ok: true, message: response.message || 'Conta criada com sucesso.' };
    } catch (err) {
      const message = authErrorMessage(
        err,
        'Não foi possível criar a conta. Verifique se o e-mail já está cadastrado.',
      );
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const resendVerification = async (email: string) => {
    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail) {
      const message = 'Informe o e-mail.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      const response = await resendVerificationBackend(normalizedEmail);
      showToast(response.message || 'E-mail de verificação reenviado.', 'success');
      return { ok: true, message: response.message || 'E-mail reenviado com sucesso.' };
    } catch (err) {
      const message = authErrorMessage(err, 'Não foi possível reenviar o e-mail de verificação.');
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const logout = () => {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);
    localStorage.removeItem(AUTH_TOKEN_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_KEY);
    setSessionUserId(null);
    showToast('Você saiu da sua conta.', 'info');
  };

  const resetPassword = async (email: string, newPassword: string, confirmPassword: string) => {
    if (!email.trim() || !newPassword || !confirmPassword) {
      const message = 'Preencha os campos para redefinir a senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (newPassword !== confirmPassword) {
      const message = 'As senhas não coincidem.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (!isStrongPassword(newPassword)) {
      const message = 'A senha deve ter no mínimo 8 caracteres, incluir letra maiúscula, número e símbolo.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      await resetPasswordBackend(email.trim().toLowerCase(), newPassword);
      showToast('Senha redefinida com sucesso.', 'success');
      return { ok: true, message: 'Senha redefinida com sucesso.' };
    } catch {
      const message = 'Não foi possível redefinir a senha no servidor.';
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const changePassword = async (currentPassword: string, newPassword: string, confirmPassword: string) => {
    if (!currentUser) {
      const message = 'Você precisa estar autenticado para trocar a senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (!currentPassword || !newPassword || !confirmPassword) {
      const message = 'Preencha todos os campos para trocar a senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (newPassword !== confirmPassword) {
      const message = 'As senhas novas não coincidem.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (!isStrongPassword(newPassword)) {
      const message = 'A senha deve ter no mínimo 8 caracteres, incluir letra maiúscula, número e símbolo.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      await changePasswordBackend(currentUser.email, currentPassword, newPassword);
      showToast('Senha alterada com sucesso.', 'success');
      return { ok: true, message: 'Senha alterada com sucesso.' };
    } catch {
      const message = 'Senha atual incorreta ou servidor indisponível.';
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const verifyEmailCode = async (email: string, code: string) => {
    const normalizedEmail = email.trim().toLowerCase();
    const cleanCode = code.trim();
    if (!normalizedEmail || !cleanCode) {
      const message = 'Informe o e-mail e o código de verificação.';
      showToast(message, 'error');
      return { ok: false, message };
    }
    if (!/^\d{6}$/.test(cleanCode)) {
      const message = 'O código de verificação deve conter exatamente 6 dígitos.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      const response = await verifyEmailCodeBackend(normalizedEmail, cleanCode);
      showToast(response.message || 'E-mail verificado com sucesso.', 'success');
      return { ok: true, message: response.message || 'E-mail verificado com sucesso.' };
    } catch (err) {
      const message = authErrorMessage(err, 'Não foi possível verificar o e-mail.');
      showToast(message, 'error');
      return { ok: false, message };
    }
  };

  const contextValue: AuthContextType = {
    currentUser,
    isAdmin,
    login,
    register,
    logout,
    resetPassword,
    changePassword,
    resendVerification,
    verifyEmailCode,
    availableUsers: state.users
  };

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
