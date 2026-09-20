import { useEffect, useState } from 'react';
import AppLayout from '../components/AppLayout';
import { getMyMedia, getImageUrl } from '../services/mediaService';

// Same normalisation Home.jsx uses when counting places, so the "Places"
// number on the Home page always equals the number of sections shown here.
const placeNameOf = (location) => location.split('(')[0].trim();

const dateValue = (m) => (m.dateTaken ? new Date(m.dateTaken).getTime() : 0);

export default function Places() {
  const [places, setPlaces] = useState([]); // [{ name, photos: [...] }]
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getMyMedia()
      .then(data => {
        const byPlace = new Map();
        data.forEach(m => {
          if (!m.location || !m.location.trim()) return;
          const name = placeNameOf(m.location);
          if (!name) return;
          if (!byPlace.has(name)) byPlace.set(name, []);
          byPlace.get(name).push(m);
        });

        const grouped = Array.from(byPlace, ([name, photos]) => ({
          name,
          photos: photos.sort((a, b) => dateValue(b) - dateValue(a)), // newest first
        }));
        // Places with the most photos first; ties broken alphabetically.
        grouped.sort((a, b) => b.photos.length - a.photos.length || a.name.localeCompare(b.name));
        setPlaces(grouped);
      })
      .catch(() => setError('Could not load your places. Make sure the backend is running.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>
        {/* Header */}
        <div style={{ marginBottom: '24px' }}>
          <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '2px' }}>
            Your Places
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text3)', fontWeight: '300' }}>
            {loading ? 'Loading...' : `${places.length} place${places.length !== 1 ? 's' : ''} · sorted by most photos`}
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>Loading your places...</div>
        ) : error ? (
          <div style={{
            background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: '14px', padding: '40px 24px', textAlign: 'center',
            color: '#f87171', fontSize: '13px',
          }}>
            <div style={{ fontSize: '32px', marginBottom: '12px' }}>⚠️</div>
            {error}
          </div>
        ) : places.length === 0 ? (
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '60px 24px', textAlign: 'center',
            color: 'var(--text3)', fontSize: '13px',
          }}>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>📍</div>
            No places found yet. Places come from the location stored in your photos' metadata.
          </div>
        ) : (
          places.map(place => (
            <div key={place.name} style={{ marginBottom: '32px' }}>
              {/* Place title */}
              <div style={{
                fontFamily: 'Syne, sans-serif', fontSize: '18px', fontWeight: '700',
                color: 'var(--text)', marginBottom: '12px',
              }}>
                {place.name}
              </div>

              {/* Photos taken at this place */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                gap: '8px',
              }}>
                {place.photos.map(photo => (
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
                    <div style={{ height: '160px', overflow: 'hidden', background: 'var(--bg3)' }}>
                      <img
                        src={getImageUrl(photo.id)}
                        alt={photo.fileName}
                        loading="lazy"
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
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>
    </AppLayout>
  );
}