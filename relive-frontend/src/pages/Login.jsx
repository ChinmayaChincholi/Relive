import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { loginUser } from '../services/authService';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleLogin = async () => {
    if (!email || !password) { setError('Please enter email and password'); return; }
    try {
      setLoading(true);
      setError('');
      const data = await loginUser(email, password);
      if (!data.token) { setError('Invalid credentials'); return; }
      localStorage.setItem('token', data.token);
      navigate('/home');
    } catch {
      setError('Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = {
    width: '100%',
    background: 'rgba(255,255,255,0.04)',
    border: '1px solid var(--border2)',
    borderRadius: '9px',
    padding: '10px 14px',
    fontSize: '13px',
    color: 'var(--text)',
    outline: 'none',
    transition: 'border-color 0.2s',
  };

  return (
    <div style={{
      background: 'var(--bg)', minHeight: '100vh',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      position: 'relative', overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute', width: '500px', height: '400px',
        background: 'radial-gradient(circle, rgba(245,158,11,0.1) 0%, transparent 70%)',
        top: '-150px', left: '50%', transform: 'translateX(-50%)',
        pointerEvents: 'none',
      }} />

      <div className="fade-up" style={{
        background: 'rgba(255,255,255,0.025)',
        border: '1px solid var(--border)',
        borderRadius: '20px',
        padding: '36px 32px',
        width: '360px',
        position: 'relative', zIndex: 10,
      }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <div style={{
            width: '40px', height: '40px',
            background: 'linear-gradient(135deg, #f59e0b, #d97706)',
            borderRadius: '10px',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '18px', margin: '0 auto 12px',
          }}>📸</div>
          <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '22px', fontWeight: '800', marginBottom: '4px' }}>
            Welcome back
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text3)', fontWeight: '300' }}>
            Sign in to your memories
          </div>
        </div>

        <div style={{ marginBottom: '14px' }}>
          <div style={{ fontSize: '11px', color: 'var(--text2)', marginBottom: '5px' }}>Email</div>
          <input
            style={inputStyle}
            placeholder="you@email.com"
            value={email}
            onChange={e => setEmail(e.target.value)}
            onFocus={e => e.target.style.borderColor = 'rgba(245,158,11,0.5)'}
            onBlur={e => e.target.style.borderColor = 'var(--border2)'}
            onKeyDown={e => e.key === 'Enter' && handleLogin()}
          />
        </div>

        <div style={{ marginBottom: '20px' }}>
          <div style={{ fontSize: '11px', color: 'var(--text2)', marginBottom: '5px' }}>Password</div>
          <input
            type="password"
            style={inputStyle}
            placeholder="••••••••"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onFocus={e => e.target.style.borderColor = 'rgba(245,158,11,0.5)'}
            onBlur={e => e.target.style.borderColor = 'var(--border2)'}
            onKeyDown={e => e.key === 'Enter' && handleLogin()}
          />
        </div>

        {error && (
          <div style={{
            background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: '8px', padding: '8px 12px',
            fontSize: '12px', color: '#f87171', marginBottom: '14px',
          }}>{error}</div>
        )}

        <button
          onClick={handleLogin}
          disabled={loading}
          style={{
            width: '100%', padding: '12px',
            background: loading ? 'rgba(245,158,11,0.5)' : 'linear-gradient(135deg, #f59e0b, #d97706)',
            border: 'none', borderRadius: '10px',
            fontSize: '14px', fontWeight: '700', color: '#1c1004',
            fontFamily: 'Syne, sans-serif',
            cursor: loading ? 'not-allowed' : 'pointer',
          }}
        >{loading ? 'Signing in...' : 'Log in'}</button>

        <div style={{ textAlign: 'center', marginTop: '18px', fontSize: '12px', color: 'var(--text3)' }}>
          Don't have an account?{' '}
          <Link to="/register" style={{ color: '#fbbf24' }}>Sign up</Link>
        </div>
        <div style={{ textAlign: 'center', marginTop: '8px', fontSize: '12px' }}>
          <Link to="/" style={{ color: 'var(--text4)' }}>← Back to home</Link>
        </div>
      </div>
    </div>
  );
}