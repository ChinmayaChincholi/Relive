import { useNavigate } from 'react-router-dom';

export default function TopBar() {
  const navigate = useNavigate();

  return (
    <div style={{
      padding: '11px 20px',
      borderBottom: '1px solid var(--border)',
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
      background: 'rgba(255,255,255,0.01)',
      position: 'sticky',
      top: 0,
      zIndex: 10,
    }}>
      <div
        onClick={() => navigate('/ask')}
        style={{
          flex: 1,
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: '9px',
          padding: '8px 13px',
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          fontSize: '12px',
          color: 'var(--text3)',
          cursor: 'pointer',
          transition: 'border-color 0.2s',
        }}
        onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--border2)'}
        onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}
      >
        <span style={{ fontSize: '13px' }}>🔍</span>
        <span>Search your memories...</span>
      </div>
      <div style={{
        width: '32px', height: '32px',
        background: 'linear-gradient(135deg, #f59e0b, #d97706)',
        borderRadius: '50%',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: '12px', fontWeight: '700', color: '#1c1004',
        flexShrink: 0,
        fontFamily: 'Syne, sans-serif',
      }}>C</div>
    </div>
  );
}