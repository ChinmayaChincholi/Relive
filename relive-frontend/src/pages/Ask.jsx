import { useState } from 'react';
import AppLayout from '../components/AppLayout';
import { searchNatural, getImageUrl } from '../services/mediaService';

export default function Ask() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) return;
    try {
      setLoading(true);
      setSearched(true);
      const data = await searchNatural(query);
      setResults(data);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const suggestions = [
    'Photos of people eating pizza',
    'Family photos October 2022',
    'Night out at a restaurant',
    'Group photos in Bangalore',
  ];

  return (
    <AppLayout noTopBar>
      <div style={{ padding: '24px' }}>
        <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '6px' }}>
          Search
        </div>
        <div style={{ fontSize: '13px', color: 'var(--text3)', marginBottom: '20px', fontWeight: '300' }}>
          Describe any memory in plain words
        </div>

        {/* Search input */}
        <div style={{
          background: 'var(--surface)',
          border: '1px solid var(--border2)',
          borderRadius: '13px',
          padding: '14px 18px',
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          marginBottom: '16px',
          transition: 'border-color 0.2s',
        }}
          onFocus={() => {}}
        >
          <span style={{ fontSize: '16px', flexShrink: 0 }}>🔍</span>
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder='Try "Rahul eating pizza in Bangalore" or "beach 2022"'
            style={{
              flex: 1, background: 'transparent',
              border: 'none', outline: 'none',
              fontSize: '14px', color: 'var(--text)',
              fontFamily: 'DM Sans, sans-serif',
            }}
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            style={{
              background: loading ? 'rgba(245,158,11,0.5)' : 'linear-gradient(135deg, #f59e0b, #d97706)',
              border: 'none', borderRadius: '8px',
              padding: '8px 18px',
              fontSize: '13px', fontWeight: '700', color: '#1c1004',
              cursor: loading ? 'not-allowed' : 'pointer',
              flexShrink: 0,
              fontFamily: 'Syne, sans-serif',
            }}
          >{loading ? '...' : 'Search'}</button>
        </div>

        {/* Suggestions (shown before search) */}
        {!searched && (
          <div style={{ marginBottom: '20px' }}>
            <div style={{ fontSize: '11px', color: 'var(--text3)', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '10px' }}>
              Try these
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {suggestions.map(s => (
                <div
                  key={s}
                  onClick={() => { setQuery(s); }}
                  style={{
                    background: 'var(--surface)',
                    border: '1px solid var(--border)',
                    borderRadius: '20px',
                    padding: '6px 14px',
                    fontSize: '12px',
                    color: 'var(--text3)',
                    cursor: 'pointer',
                    fontStyle: 'italic',
                    transition: 'border-color 0.2s, color 0.2s',
                  }}
                  onMouseEnter={e => {
                    e.currentTarget.style.borderColor = 'rgba(245,158,11,0.3)';
                    e.currentTarget.style.color = 'var(--text2)';
                  }}
                  onMouseLeave={e => {
                    e.currentTarget.style.borderColor = 'var(--border)';
                    e.currentTarget.style.color = 'var(--text3)';
                  }}
                >"{s}"</div>
              ))}
            </div>
          </div>
        )}

        {/* Results */}
        {searched && (
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text3)', marginBottom: '14px' }}>
              {loading ? 'Searching...' : `${results.length} result${results.length !== 1 ? 's' : ''} for `}
              {!loading && <span style={{ color: '#fbbf24', fontStyle: 'italic' }}>"{query}"</span>}
            </div>

            {!loading && results.length === 0 && (
              <div style={{
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: '12px', padding: '40px 24px', textAlign: 'center',
                color: 'var(--text3)', fontSize: '13px',
              }}>
                <div style={{ fontSize: '32px', marginBottom: '12px' }}>🔍</div>
                No photos matched this query. Try different words or check if photos are processed.
              </div>
            )}

            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: '10px',
            }}>
              {results.map(item => (
                <div key={item.id} style={{
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: '12px',
                  overflow: 'hidden',
                  transition: 'border-color 0.2s',
                }}
                  onMouseEnter={e => e.currentTarget.style.borderColor = 'rgba(245,158,11,0.3)'}
                  onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border)'}
                >
                  <div style={{ height: '160px', overflow: 'hidden', background: 'var(--bg3)' }}>
                    <img
                      src={getImageUrl(item.id)}
                      alt={item.fileName}
                      style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                    />
                  </div>
                  <div style={{ padding: '10px 12px' }}>
                    <div style={{
                      fontSize: '11px', color: 'var(--text3)', lineHeight: 1.5,
                      marginBottom: '6px', display: '-webkit-box',
                      WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
                    }}>{item.sceneCaption || 'No caption'}</div>
                    <div style={{ display: 'flex', gap: '5px', flexWrap: 'wrap' }}>
                      {item.dateTaken && (
                        <span style={{
                          fontSize: '9px', color: '#f59e0b',
                          background: 'rgba(245,158,11,0.08)',
                          borderRadius: '4px', padding: '2px 6px',
                        }}>
                          {new Date(item.dateTaken).toLocaleDateString()}
                        </span>
                      )}
                      {item.location && (
                        <span style={{
                          fontSize: '9px', color: '#f59e0b',
                          background: 'rgba(245,158,11,0.08)',
                          borderRadius: '4px', padding: '2px 6px',
                        }}>
                          📍 {item.location.split('(')[0].trim()}
                        </span>
                      )}
                      {item.faceCount > 0 && (
                        <span style={{
                          fontSize: '9px', color: '#f59e0b',
                          background: 'rgba(245,158,11,0.08)',
                          borderRadius: '4px', padding: '2px 6px',
                        }}>
                          👤 {item.faceCount}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </AppLayout>
  );
}