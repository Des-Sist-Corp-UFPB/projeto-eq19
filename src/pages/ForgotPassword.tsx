import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export const ForgotPassword: React.FC = () => {
  const { resetPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    await resetPassword(email, newPassword, confirmPassword);
    setLoading(false);
  };

  return (
    <div className="container" style={{ maxWidth: '520px', paddingTop: '40px' }}>
      <div className="card" style={cardStyle}>
        <h1 style={titleStyle}>Redefinir senha</h1>
        <p style={subtitleStyle}>Defina uma nova senha para sua conta.</p>
        <form onSubmit={handleSubmit} style={formStyle}>
          <label className="form-label">E-mail</label>
          <input className="form-input" type="email" value={email} onChange={e => setEmail(e.target.value)} required />
          <label className="form-label">Nova senha</label>
          <input className="form-input" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
          <label className="form-label">Confirmar senha</label>
          <input className="form-input" type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} required />
          <button className="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Salvando...' : 'Redefinir senha'}</button>
        </form>
        <p style={linksStyle}><Link to="/login">Voltar para login</Link></p>
      </div>
    </div>
  );
};

const cardStyle: React.CSSProperties = { padding: '28px' };
const titleStyle: React.CSSProperties = { fontSize: '1.8rem', fontFamily: 'var(--font-title)', marginBottom: '6px' };
const subtitleStyle: React.CSSProperties = { color: 'var(--color-text-muted)', marginBottom: '18px' };
const formStyle: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: '10px' };
const linksStyle: React.CSSProperties = { marginTop: '14px', fontSize: '0.92rem' };
