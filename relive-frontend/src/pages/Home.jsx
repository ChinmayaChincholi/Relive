import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppLayout from '../components/AppLayout';
import { getMyMedia, getImageUrl, getFaceCropUrl } from '../services/mediaService';
import { getPeople } from '../services/faceService';

export default function Home() {
  const navigate = useNavigate();
  const [media, setMedia] = useState([]);
  const [people, setPeople] = useState([]);
  const [progress, setProgress] = useState({ total: 0, completed: 0, processing: 0 });
  const [locationCount, setLocationCount] = useState(0);

  useEffect(() => {
    getMyMedia().then(data => {
      setMedia(data);
      const total = data.length;
      const completed = data.filter(m => m.status === 'COMPLETED').length;
      const processing = data.filter(m => m.status === 'PROCESSING').length;
      setProgress({ total, completed, processing });

      // Count unique locations
      const locs = new Set(
        data
          .filter(m => m.location && m.location.trim())
          .map(m => m.location.split('(')[0].trim())
      );
      setLocationCount(locs.size);
    }).catch(() => {});

    getPeople().then(setPeople).catch(() => {});
  }, []);

  const recent = media.slice(0, 6);

  const statCard = (label, value, sub, subColor = '#f59e0b', onClick = null) => (
    <div
      onClick={onClick}
      style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: '12px',
        padding: '14px 16px',
        cursor: onClick ? 'pointer' : 'default',
        transition: onClick ? 'border-color 0.2s' : 'none',
      }}
      onMouseEnter={e => { if (onClick) e.currentTarget.style.borderColor = 'rgba(245,158,11,0.3)'; }}
      onMouseLeave={e => { if (onClick) e.currentTarget.style.borderColor = 'var(--border)'; }}
    >
      <div style={{ fontSize: '10px', color: 'var(--text3)', marginBottom: '5px', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{label}</div>
      <div style={{ fontSize: '24px', fontWeight: '800', fontFamily: 'Syne, sans-serif', color: 'var(--text)' }}>{value}</div>
      {sub && <div style={{ fontSize: '11px', color: subColor, marginTop: '2px' }}>{sub}</div>}
    </div>
  );

  return (
    <AppLayout>
      <div style={{ padding: '24px 24px' }}>

        {/* Import Media Banner */}
        <div
          onClick={() => navigate('/import')}
          className="fade-up"
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '14px',
            padding: '18px 20px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            cursor: 'pointer',
            marginBottom: '22px',
            transition: 'border-color 0.2s, background 0.2s',
          }}
          onMouseEnter={e => {
            e.currentTarget.style.borderColor = 'rgba(245,158,11,0.3)';
            e.currentTarget.style.background = 'rgba(255,255,255,0.04)';
          }}
          onMouseLeave={e => {
            e.currentTarget.style.borderColor = 'var(--border)';
            e.currentTarget.style.background = 'var(--surface)';
          }}
        >
          <div style={{
            width: '44px', height: '44px',
            background: 'rgba(245,158,11,0.1)',
            border: '1px solid rgba(245,158,11,0.2)',
            borderRadius: '10px',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '20px', flexShrink: 0,
          }}>📁</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text)', marginBottom: '2px', fontFamily: 'Syne, sans-serif' }}>
              Import Media
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)' }}>
              Add photos to your Relive library
            </div>
          </div>
          <div style={{
            background: 'linear-gradient(135deg, #f59e0b, #d97706)',
            border: 'none', borderRadius: '8px',
            padding: '8px 16px',
            fontSize: '12px', fontWeight: '700', color: '#1c1004',
            flexShrink: 0,
          }}>Import →</div>
        </div>

        {/* Stats */}
        <div className="fade-up-2" style={{
          display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)',
          gap: '10px', marginBottom: '24px',
        }}>
          {statCard('Total photos', progress.total || '—', progress.total > 0 ? `${progress.processing} processing` : null)}
          {statCard('Processed', progress.completed || '—', progress.total > 0 ? `${Math.round((progress.completed / progress.total) * 100)}% done` : null)}
          {statCard('People found', people.length || '—', people.filter(p => !p.name).length > 0 ? `${people.filter(p => !p.name).length} unnamed` : 'All named', people.filter(p => !p.name).length > 0 ? '#f59e0b' : '#22c55e')}
          {statCard(
            'Locations',
            locationCount || '—',
            locationCount > 0 ? 'View on map →' : 'from metadata',
            '#f59e0b',
            locationCount > 0 ? () => navigate('/map') : null
          )}
        </div>

        {/* Recent Photos */}
        <div className="fade-up-3">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <div style={{ fontSize: '14px', fontWeight: '600', fontFamily: 'Syne, sans-serif' }}>Recent photos</div>
            <div
              onClick={() => navigate('/media')}
              style={{ fontSize: '12px', color: '#f59e0b', cursor: 'pointer' }}
            >View all →</div>
          </div>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)',
            gap: '6px', marginBottom: '24px',
          }}>
            {recent.length === 0
              ? Array(6).fill(null).map((_, i) => (
                <div key={i} style={{
                  aspectRatio: '1', borderRadius: '8px',
                  background: 'var(--surface)', border: '1px solid var(--border)',
                }} />
              ))
              : recent.map(item => (
                <div
                  key={item.id}
                  onClick={() => navigate('/media')}
                  style={{
                    aspectRatio: '1', borderRadius: '8px',
                    overflow: 'hidden', position: 'relative',
                    border: item.status === 'PROCESSING' ? '1px solid rgba(245,158,11,0.25)' : '1px solid var(--border)',
                    cursor: 'pointer',
                    background: 'var(--surface)',
                  }}
                >
                  {item.status === 'COMPLETED' ? (
                    <img
                      src={getImageUrl(item.id)}
                      alt=""
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <>
                      <div style={{
                        width: '100%', height: '100%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '18px', color: 'var(--text3)',
                        background: 'rgba(245,158,11,0.04)',
                      }}>⏳</div>
                      <div style={{
                        position: 'absolute', bottom: 0, left: 0,
                        height: '2px', width: '50%',
                        background: 'linear-gradient(90deg, #d97706, #fbbf24)',
                      }} />
                    </>
                  )}
                </div>
              ))
            }
          </div>
        </div>

        {/* Your People */}
        <div className="fade-up-4">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <div style={{ fontSize: '14px', fontWeight: '600', fontFamily: 'Syne, sans-serif' }}>Your people</div>
            <div
              onClick={() => navigate('/faces')}
              style={{ fontSize: '12px', color: '#f59e0b', cursor: 'pointer' }}
            >Manage →</div>
          </div>
          {people.length === 0 ? (
            <div style={{
              background: 'var(--surface)', border: '1px solid var(--border)',
              borderRadius: '10px', padding: '20px', textAlign: 'center',
              fontSize: '12px', color: 'var(--text3)',
            }}>
              No people detected yet. Import and process photos first.
            </div>
          ) : (
            <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
              {people.slice(0, 6).map(person => (
                <div
                  key={person.personId}
                  onClick={() => navigate('/faces')}
                  style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '5px', cursor: 'pointer' }}
                >
                  <div style={{
                    width: '44px', height: '44px', borderRadius: '50%',
                    background: 'var(--surface2)',
                    border: person.name ? '2px solid rgba(245,158,11,0.3)' : '2px solid rgba(245,158,11,0.5)',
                    overflow: 'hidden',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {person.representativeCrop ? (
                      <img
                        src={getFaceCropUrl(person.representativeCrop)}
                        alt=""
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <span style={{ fontSize: '18px' }}>?</span>
                    )}
                  </div>
                  <div style={{
                    fontSize: '10px',
                    color: person.name ? 'var(--text2)' : 'var(--text3)',
                  }}>{person.name || 'Unknown'}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}