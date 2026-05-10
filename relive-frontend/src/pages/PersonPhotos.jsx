import { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import AppLayout from '../components/AppLayout';
import { getPhotosForPerson } from '../services/faceService';
import { getImageUrl } from '../services/mediaService';

export default function PersonPhotos() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const personName = location.state?.personName || 'Unknown Person';

  const [photos, setPhotos] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getPhotosForPerson(Number(id))
      .then(data => { setPhotos(data); setLoading(false); })
      .catch(() => setLoading(false));
  }, [id]);

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>
        {/* Header with back arrow */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px', marginBottom: '22px' }}>
          <button
            onClick={() => navigate('/faces')}
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: '8px',
              padding: '7px 12px',
              fontSize: '16px',
              color: 'var(--text2)',
              cursor: 'pointer',
              display: 'flex', alignItems: 'center',
              transition: 'border-color 0.15s',
            }}
            onMouseEnter={e => e.currentTarget.style.borderColor = 'rgba(245,158,11,0.4)'}
            onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}
          >←</button>
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800' }}>
              {personName}
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)' }}>
              {loading ? 'Loading...' : `${photos.length} photo${photos.length !== 1 ? 's' : ''}`}
            </div>
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>
            Loading photos...
          </div>
        ) : photos.length === 0 ? (
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '60px 24px', textAlign: 'center',
            color: 'var(--text3)', fontSize: '13px',
          }}>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>🖼️</div>
            No photos found for this person.
          </div>
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: '8px',
          }}>
            {photos.map(photo => (
              <div
                key={photo.id}
                style={{
                  borderRadius: '10px', overflow: 'hidden',
                  border: '1px solid var(--border)',
                  background: 'var(--surface)',
                  transition: 'border-color 0.15s',
                }}
                onMouseEnter={e => e.currentTarget.style.borderColor = 'rgba(245,158,11,0.3)'}
                onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}
              >
                <div style={{ height: '180px', overflow: 'hidden', background: 'var(--bg3)' }}>
                  <img
                    src={getImageUrl(photo.id)}
                    alt={photo.fileName}
                    style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                  />
                </div>
                <div style={{ padding: '8px 10px' }}>
                  <div style={{
                    fontSize: '10px', color: 'var(--text3)',
                    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  }}>{photo.fileName}</div>
                  {photo.dateTaken && (
                    <div style={{ fontSize: '9px', color: '#f59e0b', marginTop: '3px' }}>
                      {new Date(photo.dateTaken).toLocaleDateString()}
                    </div>
                  )}
                  {photo.location && (
                    <div style={{ fontSize: '9px', color: 'var(--text3)', marginTop: '2px' }}>
                      📍 {photo.location.split('(')[0].trim()}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </AppLayout>
  );
}