import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import AppLayout from '../components/AppLayout';
import { getMyMedia, getImageUrl } from '../services/mediaService';
import { getPeople } from '../services/faceService';

export default function MediaDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [media, setMedia] = useState(null);
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    setLoading(true);
    setNotFound(false);
    Promise.all([getMyMedia(), getPeople()])
      .then(([mediaList, peopleList]) => {
        const found = mediaList.find(m => String(m.id) === String(id));
        if (!found) {
          setNotFound(true);
        } else {
          setMedia(found);
          setPeople(peopleList.filter(p => p.mediaIds.includes(found.id)));
        }
        setLoading(false);
      })
      .catch(() => {
        setNotFound(true);
        setLoading(false);
      });
  }, [id]);

  const backButton = (
    <button
      onClick={() => navigate('/media')}
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
  );

  if (loading) {
    return (
      <AppLayout>
        <div style={{ padding: '24px' }}>
          <div style={{ marginBottom: '22px' }}>{backButton}</div>
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>
            Loading photo details...
          </div>
        </div>
      </AppLayout>
    );
  }

  if (notFound || !media) {
    return (
      <AppLayout>
        <div style={{ padding: '24px' }}>
          <div style={{ marginBottom: '22px' }}>{backButton}</div>
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '60px 24px', textAlign: 'center',
            color: 'var(--text3)', fontSize: '13px',
          }}>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>🖼️</div>
            Photo not found.
          </div>
        </div>
      </AppLayout>
    );
  }

  const detailRow = (label, value) => (
    value ? (
      <div style={{ marginBottom: '14px' }}>
        <div style={{
          fontSize: '10px', color: 'var(--text3)', textTransform: 'uppercase',
          letterSpacing: '0.5px', fontWeight: '600', marginBottom: '4px',
        }}>{label}</div>
        <div style={{ fontSize: '13px', color: 'var(--text)', lineHeight: '1.5' }}>{value}</div>
      </div>
    ) : null
  );

  return (
    <AppLayout>
      <div style={{ padding: '24px', maxWidth: '1000px' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px', marginBottom: '22px' }}>
          {backButton}
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800' }}>
              Photo Details
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)' }}>{media.fileName}</div>
          </div>
        </div>

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(280px, 1.4fr) minmax(220px, 1fr)',
          gap: '24px',
        }}>
          {/* Full image */}
          <div style={{
            borderRadius: '14px', overflow: 'hidden',
            border: '1px solid var(--border)', background: 'var(--bg3)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            maxHeight: '640px',
          }}>
            <img
              src={getImageUrl(media.id)}
              alt={media.fileName}
              style={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }}
            />
          </div>

          {/* Details panel */}
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '20px',
          }}>
            {detailRow('Caption', media.sceneCaption)}
            {detailRow(
              'Date Taken',
              media.dateTaken
                ? new Date(media.dateTaken).toLocaleString(undefined, {
                    dateStyle: 'long', timeStyle: 'short',
                  })
                : null
            )}
            {detailRow('Location', media.location)}
            {detailRow('Event Type', media.eventType)}
            {detailRow(
              'Faces Found',
              media.faceCount != null ? `${media.faceCount} face${media.faceCount !== 1 ? 's' : ''}` : null
            )}

            {people.length > 0 && (
              <div style={{ marginBottom: '4px' }}>
                <div style={{
                  fontSize: '10px', color: 'var(--text3)', textTransform: 'uppercase',
                  letterSpacing: '0.5px', fontWeight: '600', marginBottom: '8px',
                }}>People Found</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                  {people.map(p => (
                    <button
                      key={p.personId}
                      onClick={() => navigate(`/faces/person/${p.personId}`, {
                        state: { personName: p.name || 'Unknown Person' }
                      })}
                      style={{
                        padding: '5px 10px', borderRadius: '999px',
                        background: 'rgba(245,158,11,0.1)',
                        border: '1px solid rgba(245,158,11,0.25)',
                        fontSize: '11px', color: '#fbbf24', cursor: 'pointer',
                      }}
                    >{p.name || 'Unknown'}</button>
                  ))}
                </div>
              </div>
            )}

            {!media.sceneCaption && !media.dateTaken && !media.location &&
             !media.eventType && (media.faceCount == null || media.faceCount === 0) &&
             people.length === 0 && (
              <div style={{ fontSize: '12px', color: 'var(--text3)' }}>
                No additional details available for this photo yet.
              </div>
            )}
          </div>
        </div>
      </div>
    </AppLayout>
  );
}