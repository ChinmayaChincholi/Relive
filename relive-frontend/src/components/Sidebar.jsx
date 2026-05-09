import { useNavigate, useLocation } from 'react-router-dom';

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;

  const navItem = (icon, label, route) => {
    const active = path === route;
    return (
      <div
        onClick={() => navigate(route)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '9px 14px',
          fontSize: '13px',
          color: active ? '#f59e0b' : '#64748b',
          background: active ? 'rgba(245,158,11,0.07)' : 'transparent',
          borderRight: active ? '2px solid #f59e0b' : '2px solid transparent',
          cursor: 'pointer',
          transition: 'all 0.15s',
          fontWeight: active ? '500' : '400',
        }}
        onMouseEnter={e => { if (!active) e.currentTarget.style.color = '#94a3b8'; }}
        onMouseLeave={e => { if (!active) e.currentTarget.style.color = '#64748b'; }}
      >
        <span style={{ fontSize: '15px', width: '20px', textAlign: 'center' }}>{icon}</span>
        {label}
      </div>
    );
  };

  return (
    <div style={{
      width: 'var(--sidebar-w)',
      background: 'rgba(255,255,255,0.015)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      flexShrink: 0,
      height: '100vh',
      position: 'sticky',
      top: 0,
    }}>

      {/* Logo */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        padding: '18px 14px',
        borderBottom: '1px solid var(--border)',
      }}>
        <div style={{
          width: '32px', height: '32px',
          background: 'linear-gradient(135deg, #f59e0b, #d97706)',
          borderRadius: '8px',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '15px', flexShrink: 0,
        }}>📸</div>
        <span style={{ fontFamily: 'Syne, sans-serif', fontSize: '18px', fontWeight: '800', color: '#f1f5f9', letterSpacing: '-0.5px' }}>
          Relive
        </span>
      </div>

      {/* Import CTA */}
      <div style={{ padding: '12px 10px 4px' }}>
        <div
          onClick={() => navigate('/import')}
          style={{
            background: path === '/import'
              ? 'linear-gradient(135deg, rgba(245,158,11,0.22), rgba(217,119,6,0.14))'
              : 'linear-gradient(135deg, rgba(245,158,11,0.12), rgba(217,119,6,0.06))',
            border: `1px solid ${path === '/import' ? 'rgba(245,158,11,0.45)' : 'rgba(245,158,11,0.22)'}`,
            borderRadius: '10px',
            padding: '10px 12px',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
          onMouseEnter={e => {
            e.currentTarget.style.background = 'linear-gradient(135deg, rgba(245,158,11,0.22), rgba(217,119,6,0.12))';
            e.currentTarget.style.borderColor = 'rgba(245,158,11,0.4)';
          }}
          onMouseLeave={e => {
            if (path !== '/import') {
              e.currentTarget.style.background = 'linear-gradient(135deg, rgba(245,158,11,0.12), rgba(217,119,6,0.06))';
              e.currentTarget.style.borderColor = 'rgba(245,158,11,0.22)';
            }
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '15px' }}>📁</span>
            <span style={{ fontSize: '12px', fontWeight: '600', color: '#f59e0b' }}>Import Media</span>
          </div>
          <div style={{ fontSize: '10px', color: 'rgba(245,158,11,0.5)', marginTop: '2px', paddingLeft: '23px' }}>
            Add photos to Relive
          </div>
        </div>
      </div>

      {/* Nav */}
      <div style={{ flex: 1, paddingTop: '8px' }}>
        {navItem('🏠', 'Home', '/home')}
        {navItem('🔍', 'Search', '/ask')}
        {navItem('🖼️', 'All Photos', '/media')}
        {navItem('👥', 'Your People', '/faces')}
      </div>

    </div>
  );
}
