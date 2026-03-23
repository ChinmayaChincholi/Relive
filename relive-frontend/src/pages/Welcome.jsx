import { useNavigate } from 'react-router-dom';

export default function Welcome() {
  const navigate = useNavigate();

  return (
    <div style={{ background: 'var(--bg)', minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>

      {/* Ambient glows */}
      <div style={{
        position: 'absolute', width: '600px', height: '500px',
        background: 'radial-gradient(circle, rgba(245,158,11,0.13) 0%, transparent 70%)',
        top: '-200px', left: '50%', transform: 'translateX(-50%)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', width: '300px', height: '300px',
        background: 'radial-gradient(circle, rgba(139,92,246,0.08) 0%, transparent 70%)',
        bottom: 0, right: 0, pointerEvents: 'none',
      }} />

      {/* Nav */}
      <nav style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '20px 48px',
        borderBottom: '1px solid var(--border)',
        position: 'relative', zIndex: 10,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '34px', height: '34px',
            background: 'linear-gradient(135deg, #f59e0b, #d97706)',
            borderRadius: '9px',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '16px',
          }}>📸</div>
          <span style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', letterSpacing: '-0.5px' }}>
            Relive
          </span>
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <button
            onClick={() => navigate('/login')}
            style={{
              padding: '8px 20px',
              border: '1px solid var(--border2)',
              borderRadius: '8px',
              color: '#cbd5e1',
              fontSize: '13px',
              background: 'transparent',
              transition: 'border-color 0.2s',
            }}
            onMouseEnter={e => e.currentTarget.style.borderColor = 'rgba(255,255,255,0.25)'}
            onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border2)'}
          >Log in</button>
          <button
            onClick={() => navigate('/register')}
            style={{
              padding: '8px 20px',
              border: 'none',
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #f59e0b, #d97706)',
              color: '#1c1004',
              fontSize: '13px',
              fontWeight: '700',
            }}
          >Sign up</button>
        </div>
      </nav>

      {/* Hero */}
      <div style={{ textAlign: 'center', padding: '64px 48px 36px', position: 'relative', zIndex: 10 }}>

        <div className="fade-up" style={{
          display: 'inline-flex', alignItems: 'center', gap: '7px',
          background: 'rgba(245,158,11,0.1)',
          border: '1px solid rgba(245,158,11,0.25)',
          borderRadius: '20px',
          padding: '5px 14px',
          fontSize: '12px', color: '#fbbf24',
          marginBottom: '24px',
        }}>
          <span style={{
            width: '6px', height: '6px', background: '#fbbf24',
            borderRadius: '50%', animation: 'pulse 2s infinite',
          }} />
          AI-powered memory search
        </div>

        <h1 className="fade-up-2" style={{
          fontSize: 'clamp(36px, 6vw, 58px)',
          lineHeight: 1.05,
          marginBottom: '18px',
          color: '#f1f5f9',
        }}>
          Your memories,<br />
          <span style={{
            background: 'linear-gradient(135deg, #fbbf24, #f59e0b)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}>rediscovered</span>
        </h1>

        <p className="fade-up-3" style={{
          fontSize: '16px',
          color: 'var(--text2)',
          maxWidth: '480px',
          margin: '0 auto 36px',
          lineHeight: 1.8,
          fontWeight: '300',
        }}>
          Describe any moment in plain words and Relive finds the photo — no folders, no scrolling, no guessing.
        </p>

        <div className="fade-up-4" style={{ display: 'flex', gap: '12px', justifyContent: 'center' }}>
          <button
            onClick={() => navigate('/register')}
            style={{
              padding: '13px 32px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #f59e0b, #d97706)',
              border: 'none',
              color: '#1c1004',
              fontSize: '15px',
              fontWeight: '700',
              fontFamily: 'Syne, sans-serif',
              transition: 'transform 0.15s, box-shadow 0.15s',
            }}
            onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-1px)'; }}
            onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; }}
          >Get started</button>
          <button
            onClick={() => navigate('/how-it-works')}
            style={{
              padding: '13px 32px',
              borderRadius: '10px',
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid var(--border2)',
              color: '#e2e8f0',
              fontSize: '15px',
              fontWeight: '500',
            }}
          >See how it works →</button>
        </div>
      </div>

      {/* Query examples */}
      <div style={{
        display: 'flex', gap: '8px', justifyContent: 'center',
        flexWrap: 'wrap', padding: '0 48px 24px',
        position: 'relative', zIndex: 10,
      }}>
        {[
          '"Rahul eating pizza in Bangalore"',
          '"Family photos October 2022"',
          '"Night out at a restaurant"',
          '"Beach trip with friends"',
        ].map(q => (
          <div key={q} style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '20px',
            padding: '5px 14px',
            fontSize: '12px',
            color: 'var(--text3)',
            fontStyle: 'italic',
          }}>{q}</div>
        ))}
      </div>

      {/* Features */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '12px', padding: '0 48px 48px',
        position: 'relative', zIndex: 10,
      }}>
        {[
          { icon: '🔍', title: 'Search naturally', desc: 'Describe any photo in plain English and find it instantly.' },
          { icon: '👥', title: 'Find people', desc: 'Name the faces in your photos. Search by name anytime after.' },
          { icon: '📍', title: 'Places & dates', desc: 'Filter by location, year, or time of day without lifting a finger.' },
        ].map(f => (
          <div key={f.title} style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '14px',
            padding: '20px',
          }}>
            <div style={{ fontSize: '22px', marginBottom: '10px' }}>{f.icon}</div>
            <div style={{ fontSize: '13px', fontWeight: '600', color: '#e2e8f0', marginBottom: '5px', fontFamily: 'Syne, sans-serif' }}>{f.title}</div>
            <div style={{ fontSize: '12px', color: 'var(--text3)', lineHeight: 1.7 }}>{f.desc}</div>
          </div>
        ))}
      </div>
    </div>
  );
}