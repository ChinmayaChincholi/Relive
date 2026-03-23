import { useNavigate } from 'react-router-dom';

export default function HowItWorks() {
  const navigate = useNavigate();

  const steps = [
    {
      num: 'Step 1',
      icon: '📁',
      title: 'Import your photos',
      desc: 'Select photos from your device — as many as you want. No folders to organise, no setup required. Just drop them in.',
      example: '"I added 47 photos from my Goa trip"',
    },
    {
      num: 'Step 2',
      icon: '⏳',
      title: 'Wait a moment',
      desc: 'Relive quietly understands your photos — who\'s in them, where they were taken, what\'s happening. Go make a cup of tea.',
      example: '"Relive found 6 people and 3 locations"',
    },
    {
      num: 'Step 3',
      icon: '🔍',
      title: 'Ask for any memory',
      desc: 'Type what you remember — a person, a place, an occasion, a feeling. Relive finds the right photos instantly.',
      example: '"Photos of Mom at the birthday party 2022"',
    },
  ];

  return (
    <div style={{ background: 'var(--bg)', minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>

      <div style={{
        position: 'absolute', width: '700px', height: '200px',
        background: 'radial-gradient(ellipse, rgba(245,158,11,0.09) 0%, transparent 70%)',
        top: 0, left: '50%', transform: 'translateX(-50%)',
        pointerEvents: 'none',
      }} />

      <nav style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '18px 48px',
        borderBottom: '1px solid var(--border)',
        position: 'relative', zIndex: 10,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '30px', height: '30px',
            background: 'linear-gradient(135deg, #f59e0b, #d97706)',
            borderRadius: '7px',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '14px',
          }}>📸</div>
          <span style={{ fontFamily: 'Syne, sans-serif', fontSize: '18px', fontWeight: '800', letterSpacing: '-0.5px' }}>
            Relive
          </span>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            onClick={() => navigate('/')}
            style={{
              padding: '7px 16px', border: '1px solid var(--border2)',
              borderRadius: '8px', color: '#94a3b8', fontSize: '12px', background: 'transparent',
            }}
          >← Back</button>
          <button
            onClick={() => navigate('/register')}
            style={{
              padding: '7px 16px', border: 'none',
              borderRadius: '8px', background: 'linear-gradient(135deg, #f59e0b, #d97706)',
              color: '#1c1004', fontSize: '12px', fontWeight: '700',
            }}
          >Sign up</button>
        </div>
      </nav>

      <div style={{ textAlign: 'center', padding: '48px 48px 36px', position: 'relative', zIndex: 10 }}>
        <h2 style={{ fontSize: '32px', marginBottom: '10px' }}>
          Three steps to{' '}
          <span style={{
            background: 'linear-gradient(135deg, #fbbf24, #f59e0b)',
            WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
          }}>relive</span>{' '}everything
        </h2>
        <p style={{ fontSize: '14px', color: 'var(--text3)', fontWeight: '300' }}>
          Import once. Search forever.
        </p>
      </div>

      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '14px', padding: '0 48px',
        position: 'relative', zIndex: 10,
      }}>
        {steps.map((s, i) => (
          <div key={i} className={`fade-up-${i + 2}`} style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '16px',
            padding: '24px',
          }}>
            <div style={{
              fontSize: '10px', fontWeight: '700', color: '#f59e0b',
              textTransform: 'uppercase', letterSpacing: '1.5px', marginBottom: '14px',
            }}>{s.num}</div>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>{s.icon}</div>
            <div style={{
              fontSize: '16px', fontWeight: '700', color: '#f1f5f9',
              marginBottom: '10px', fontFamily: 'Syne, sans-serif',
            }}>{s.title}</div>
            <div style={{ fontSize: '13px', color: 'var(--text3)', lineHeight: 1.8, marginBottom: '14px' }}>
              {s.desc}
            </div>
            <div style={{
              background: 'rgba(245,158,11,0.07)',
              border: '1px solid rgba(245,158,11,0.15)',
              borderRadius: '8px',
              padding: '9px 12px',
              fontSize: '12px', color: 'var(--text2)', fontStyle: 'italic',
            }}>{s.example}</div>
          </div>
        ))}
      </div>

      <div style={{ textAlign: 'center', padding: '36px 48px 48px', position: 'relative', zIndex: 10 }}>
        <button
          onClick={() => navigate('/register')}
          style={{
            padding: '13px 36px',
            background: 'linear-gradient(135deg, #f59e0b, #d97706)',
            border: 'none', borderRadius: '10px',
            fontSize: '15px', fontWeight: '700',
            color: '#1c1004', fontFamily: 'Syne, sans-serif',
          }}
        >Get started →</button>
      </div>
    </div>
  );
}