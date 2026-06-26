import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export const VerifyEmail: React.FC = () => {
  const { verifyEmailCode } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email || '');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const result = await verifyEmailCode(email, code);
    setLoading(false);
    if (result.ok) {
      navigate('/login');
    }
  };

  return (
    <div className="container" style={{ maxWidth: '520px', paddingTop: '40px' }}>
      <div className="card" style={cardStyle}>
        <h1 style={titleStyle}>Verificar E-mail</h1>
        <p style={subtitleStyle}>Insira seu e-mail e o código de verificação de 6 dígitos enviado para você.</p>
        <form onSubmit={handleSubmit} style={formStyle}>
          <label className="form-label">E-mail</label>
          <input
            className="form-input"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="seu@email.com"
            required
          />
          <label className="form-label">Código de verificação</label>
          <input
            className="form-input"
            type="text"
            maxLength={6}
            value={code}
            onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
            placeholder="123456"
            required
          />
          <button className="btn btn-primary" type="submit" disabled={loading}>
            {loading ? 'Verificando...' : 'Verificar E-mail'}
          </button>
        </form>
        <div style={linksStyle}>
          <Link to="/login">Voltar para login</Link>
          <Link to="/resend-verification">Reenviar código</Link>
        </div>
      </div>
    </div>
  );
};

const cardStyle: React.CSSProperties = { padding: '28px' };
const titleStyle: React.CSSProperties = { fontSize: '1.8rem', fontFamily: 'var(--font-title)', marginBottom: '6px' };
const subtitleStyle: React.CSSProperties = { color: 'var(--color-text-muted)', marginBottom: '18px' };
const formStyle: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: '10px' };
const linksStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', marginTop: '14px', fontSize: '0.92rem' };
