import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export const Login: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const result = await login(email, password, remember);
    setLoading(false);
    if (result.ok) navigate('/');
  };

  return (
    <div className="container" style={{ maxWidth: '480px', paddingTop: '40px' }}>
      <div className="card" style={cardStyle}>
        <h1 style={titleStyle}>Entrar</h1>
        <p style={subtitleStyle}>Acesse sua conta com e-mail e senha.</p>
        <form onSubmit={handleSubmit} style={formStyle}>
          <label className="form-label">E-mail</label>
          <input className="form-input" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="seu@email.com" required />
          <label className="form-label">Senha</label>
          <input className="form-input" type="password" value={password} onChange={e => setPassword(e.target.value)} required />
          <label style={checkboxStyle}><input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)} /> Manter sessão conectada</label>
          <button className="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Entrando...' : 'Entrar'}</button>
        </form>
        <div style={linksStyle}>
          <Link to="/forgot-password">Esqueci a senha</Link>
          <Link to="/register">Criar conta</Link>
        </div>
      </div>
    </div>
  );
};

const cardStyle: React.CSSProperties = { padding: '28px' };
const titleStyle: React.CSSProperties = { fontSize: '1.8rem', fontFamily: 'var(--font-title)', marginBottom: '6px' };
const subtitleStyle: React.CSSProperties = { color: 'var(--color-text-muted)', marginBottom: '18px' };
const formStyle: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: '10px' };
const checkboxStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem', color: 'var(--color-text-muted)' };
const linksStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', marginTop: '14px', fontSize: '0.9rem' };
