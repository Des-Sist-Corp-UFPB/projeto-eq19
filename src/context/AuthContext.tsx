import React, { createContext, useContext, useMemo, useState } from 'react';
import type { User } from '../types';
import { hashPassword, verifyPassword, isStrongPassword } from '../auth/security';
import { registerUserBackend } from '../services/api';
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
  availableUsers: User[];
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);
const SESSION_KEY = 'tabula_auth_session';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { state, editUser } = useDatabase();
  const { showToast } = useToast();
  const [sessionUserId, setSessionUserId] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(SESSION_KEY) || sessionStorage.getItem(SESSION_KEY);
  });

  const currentUser = useMemo(() => {
    return sessionUserId ? state.users.find(user => user.id === sessionUserId) || null : null;
  }, [sessionUserId, state.users]);

  const isAdmin = currentUser?.role === 'admin';

  const login = async (email: string, password: string, remember = true) => {
    const normalizedEmail = email.trim().toLowerCase();
    const user = state.users.find(item => item.email.toLowerCase() === normalizedEmail);

    if (!user || !user.passwordHash) {
      const message = 'Credenciais inválidas. Verifique e-mail e senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    const passwordOk = await verifyPassword(password, user.passwordHash);
    if (!passwordOk) {
      const message = 'Credenciais inválidas. Verifique e-mail e senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    if (remember) localStorage.setItem(SESSION_KEY, user.id); else sessionStorage.setItem(SESSION_KEY, user.id);
    setSessionUserId(user.id);
    showToast(`Bem-vindo, ${user.name.split(' ')[0]}!`, 'success');
    return { ok: true, message: 'Login realizado com sucesso.' };
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
    if (state.users.some(user => user.email.toLowerCase() === normalizedEmail)) {
      const message = 'Este e-mail já está cadastrado.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    try {
      await registerUserBackend(name.trim(), normalizedEmail, password);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Não foi possível criar a conta no momento.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    showToast('Conta criada com sucesso no servidor.', 'success');
    return { ok: true, message: 'Conta criada com sucesso.' };
  };

  const logout = () => {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);
    setSessionUserId(null);
    showToast('Você saiu da sua conta.', 'info');
  };

  const resetPassword = async (email: string, newPassword: string, confirmPassword: string) => {
    if (!email.trim() || !newPassword || !confirmPassword) {
      const message = 'Preencha os campos para redefinir a senha.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    const normalizedEmail = email.trim().toLowerCase();
    const user = state.users.find(item => item.email.toLowerCase() === normalizedEmail);
    if (!user) {
      const message = 'Nenhuma conta encontrada com este e-mail.';
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

    const passwordHash = await hashPassword(newPassword);
    editUser(user.id, { passwordHash });
    showToast('Senha redefinida com sucesso.', 'success');
    return { ok: true, message: 'Senha redefinida com sucesso.' };
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

    const passwordOk = await verifyPassword(currentPassword, currentUser.passwordHash);
    if (!passwordOk) {
      const message = 'Senha atual incorreta.';
      showToast(message, 'error');
      return { ok: false, message };
    }

    const passwordHash = await hashPassword(newPassword);
    editUser(currentUser.id, { passwordHash });
    showToast('Senha alterada com sucesso.', 'success');
    return { ok: true, message: 'Senha alterada com sucesso.' };
  };

  const contextValue: AuthContextType = {
    currentUser,
    isAdmin,
    login,
    register,
    logout,
    resetPassword,
    changePassword,
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
