import { useEffect, useState } from 'react';
import AppLayout from '../components/AppLayout';
import { getMyMedia, getImageUrl, deleteMedia } from '../services/mediaService';

export default function Media() {
  const [mediaList, setMediaList] = useState([]);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(true);
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState(new Set());
  const [deleting, setDeleting] = useState(false);

  const loadMedia = () => {
    getMyMedia().then(data => {
      setMediaList(data);
      setLoading(false);
    }).catch(() => setLoading(false));
  };

  useEffect(() => {
    loadMedia();
    const interval = setInterval(loadMedia, 8000);
    return () => clearInterval(interval);
  }, []);

  const filtered = mediaList.filter(m => {
    if (filter === 'processing') return m.status === 'PROCESSING';
    if (filter === 'ready') return m.status === 'COMPLETED';
    return true;
  });

  // Sort: photos WITH dateTaken first (descending), then photos WITHOUT dateTaken
  const withDate = filtered
    .filter(m => m.dateTaken)
    .sort((a, b) => new Date(b.dateTaken) - new Date(a.dateTaken));
  const withoutDate = filtered
    .filter(m => !m.dateTaken)
    .sort((a, b) => new Date(b.uploadedAt || 0) - new Date(a.uploadedAt || 0));

  // Group "with date" by month
  const groupedWithDate = withDate.reduce((acc, item) => {
    const date = new Date(item.dateTaken);
    const key = date.toLocaleString('default', { month: 'long', year: 'numeric' });
    if (!acc[key]) acc[key] = [];
    acc[key].push(item);
    return acc;
  }, {});

  const processing = mediaList.filter(m => m.status === 'PROCESSING').length;
  const completed = mediaList.filter(m => m.status === 'COMPLETED').length;

  const toggleSelect = (id) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleDelete = async () => {
    if (selected.size === 0) return;
    if (!window.confirm(`Delete ${selected.size} photo${selected.size > 1 ? 's' : ''}? This cannot be undone.`)) return;
    setDeleting(true);
    for (const id of selected) {
      try { await deleteMedia(id); } catch (e) { console.error('Delete failed for', id, e); }
    }
    setSelected(new Set());
    setSelectMode(false);
    setDeleting(false);
    loadMedia();
  };

  const renderPhotoCard = (item) => (
    <div
      key={item.id}
      onClick={() => selectMode && toggleSelect(item.id)}
      style={{
        borderRadius: '10px', overflow: 'hidden',
        background: 'var(--surface)',
        border: selected.has(item.id)
          ? '2px solid #f59e0b'
          : item.status === 'PROCESSING'
            ? '1px solid rgba(245,158,11,0.2)'
            : '1px solid var(--border)',
        position: 'relative',
        cursor: selectMode ? 'pointer' : 'default',
        transition: 'border-color 0.15s',
      }}
    >
      {/* Selection checkbox */}
      {selectMode && (
        <div style={{
          position: 'absolute', top: '6px', left: '6px', zIndex: 10,
          width: '20px', height: '20px',
          borderRadius: '50%',
          background: selected.has(item.id) ? '#f59e0b' : 'rgba(0,0,0,0.5)',
          border: `2px solid ${selected.has(item.id) ? '#f59e0b' : 'rgba(255,255,255,0.5)'}`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '10px', color: '#000',
          transition: 'all 0.15s',
        }}>
          {selected.has(item.id) && '✓'}
        </div>
      )}

      <div style={{ height: '160px', background: 'var(--bg3)', overflow: 'hidden' }}>
        {item.status === 'COMPLETED' ? (
          <img
            src={getImageUrl(item.id)}
            alt={item.fileName}
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        ) : (
          <div style={{
            width: '100%', height: '100%',
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
            gap: '8px', background: 'rgba(245,158,11,0.04)',
          }}>
            <div style={{ fontSize: '24px' }}>⏳</div>
            <div style={{ fontSize: '10px', color: '#fbbf24' }}>Analysing...</div>
          </div>
        )}
      </div>

      {item.status === 'PROCESSING' && (
        <div style={{
          position: 'absolute', bottom: 0, left: 0, right: 0,
          height: '3px', background: 'rgba(255,255,255,0.06)',
        }}>
          <div style={{
            height: '3px', width: '50%',
            background: 'linear-gradient(90deg, #d97706, #fbbf24)',
            animation: 'shimmer 1.5s infinite',
            position: 'relative', overflow: 'hidden',
          }} />
        </div>
      )}

      <div style={{ padding: '9px 10px' }}>
        <div style={{
          fontSize: '11px', color: 'var(--text2)',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          marginBottom: '4px',
        }}>{item.fileName}</div>
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {item.faceCount > 0 && (
            <span style={{ fontSize: '9px', color: 'var(--text3)' }}>👤 {item.faceCount}</span>
          )}
          {item.eventType && (
            <span style={{ fontSize: '9px', color: 'var(--text3)' }}>· {item.eventType}</span>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '18px' }}>
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '2px' }}>
              All Photos
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)' }}>
              {mediaList.length} photos · {processing} processing · {completed} ready
            </div>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            {selectMode && selected.size > 0 && (
              <button
                onClick={handleDelete}
                disabled={deleting}
                style={{
                  padding: '7px 14px', background: 'rgba(239,68,68,0.1)',
                  border: '1px solid rgba(239,68,68,0.3)', borderRadius: '8px',
                  fontSize: '12px', color: '#f87171', cursor: 'pointer',
                }}
              >{deleting ? 'Deleting...' : `Delete (${selected.size})`}</button>
            )}
            <button
              onClick={() => { setSelectMode(s => !s); setSelected(new Set()); }}
              style={{
                padding: '7px 14px', background: selectMode ? 'rgba(245,158,11,0.1)' : 'var(--surface)',
                border: `1px solid ${selectMode ? 'rgba(245,158,11,0.4)' : 'var(--border)'}`,
                borderRadius: '8px', fontSize: '12px',
                color: selectMode ? '#f59e0b' : 'var(--text2)', cursor: 'pointer',
              }}
            >{selectMode ? 'Cancel' : 'Select'}</button>
          </div>
        </div>

        {/* Filters */}
        <div style={{ display: 'flex', gap: '6px', marginBottom: '20px' }}>
          {[['all', 'All'], ['processing', 'Processing'], ['ready', 'Ready']].map(([val, label]) => (
            <button
              key={val}
              onClick={() => setFilter(val)}
              style={{
                padding: '6px 14px', borderRadius: '7px', fontSize: '12px',
                border: filter === val ? '1px solid rgba(245,158,11,0.4)' : '1px solid var(--border)',
                background: filter === val ? 'rgba(245,158,11,0.1)' : 'transparent',
                color: filter === val ? '#fbbf24' : 'var(--text3)',
                cursor: 'pointer', transition: 'all 0.15s',
              }}
            >{label}</button>
          ))}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>Loading your photos...</div>
        ) : filtered.length === 0 ? (
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '60px 24px', textAlign: 'center',
            color: 'var(--text3)', fontSize: '13px',
          }}>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>🖼️</div>
            No photos yet. Import some to get started.
          </div>
        ) : (
          <>
            {/* Photos with dateTaken grouped by month */}
            {Object.entries(groupedWithDate).map(([month, items]) => (
              <div key={month} style={{ marginBottom: '24px' }}>
                <div style={{
                  fontSize: '12px', fontWeight: '600', color: 'var(--text3)',
                  marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px',
                }}>{month}</div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                  gap: '6px',
                }}>
                  {items.map(renderPhotoCard)}
                </div>
              </div>
            ))}

            {/* Photos without dateTaken */}
            {withoutDate.length > 0 && (
              <div style={{ marginBottom: '24px' }}>
                <div style={{
                  fontSize: '12px', fontWeight: '600', color: 'var(--text3)',
                  marginBottom: '10px', textTransform: 'uppercase', letterSpacing: '0.5px',
                }}>No date info</div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                  gap: '6px',
                }}>
                  {withoutDate.map(renderPhotoCard)}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </AppLayout>
  );
}