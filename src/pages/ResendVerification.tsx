import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export const ResendVerification: React.FC = () => {
  const { resendVerification } = useAuth();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    await resendVerification(email);
    setLoading(false);
  };

  return (
    <div className="container" style={{ maxWidth: '520px', paddingTop: '40px' }}>
      <div className="card" style={cardStyle}>
        <h1 style={titleStyle}>Reenviar Verificação</h1>
        <p style={subtitleStyle}>Insira seu e-mail cadastrado para receber um novo código de verificação.</p>
        <form onSubmit={handleSubmit} style={formStyle}>
          <label className="form-label">E-mail</label>
          <input className="form-input" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="seu@email.com" required />
          <button className="btn btn-primary" type="submit" disabled={loading}>{loading ? 'Enviando...' : 'Reenviar Código'}</button>
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
