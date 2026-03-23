import { useEffect, useState } from 'react';
import AppLayout from '../components/AppLayout';
import { getPeople, namePerson, getPhotosForPerson } from '../services/faceService';
import { getImageUrl, getFaceCropUrl } from '../services/mediaService';

export default function Faces() {
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [nameInputs, setNameInputs] = useState({});
  const [savingId, setSavingId] = useState(null);
  const [selectedPerson, setSelectedPerson] = useState(null);
  const [personPhotos, setPersonPhotos] = useState([]);

  const fetchPeople = async () => {
    try {
      setLoading(true);
      const data = await getPeople();
      setPeople(data);
    } catch {}
    finally { setLoading(false); }
  };

  useEffect(() => { fetchPeople(); }, []);

  const handleNameSubmit = async (personId) => {
    const name = nameInputs[personId]?.trim();
    if (!name) return;
    try {
      setSavingId(personId);
      await namePerson(personId, name);
      setPeople(prev => prev.map(p => p.personId === personId ? { ...p, name } : p));
      setNameInputs(prev => ({ ...prev, [personId]: '' }));
      if (selectedPerson?.personId === personId) setSelectedPerson(prev => ({ ...prev, name }));
    } catch { alert('Failed to save name'); }
    finally { setSavingId(null); }
  };

  const handlePersonClick = async (person) => {
    if (selectedPerson?.personId === person.personId) {
      setSelectedPerson(null); setPersonPhotos([]); return;
    }
    setSelectedPerson(person);
    try {
      const photos = await getPhotosForPerson(person.personId);
      setPersonPhotos(photos);
    } catch {}
  };

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '2px' }}>
              Your People
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)', fontWeight: '300' }}>
              {people.length} people found across your photos. Name them to search by name.
            </div>
          </div>
          <button
            onClick={fetchPeople}
            style={{
              padding: '7px 14px', background: 'var(--surface)',
              border: '1px solid var(--border)', borderRadius: '8px',
              fontSize: '12px', color: 'var(--text2)', cursor: 'pointer',
            }}
          >Refresh</button>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>
            Grouping faces across your photos...
          </div>
        ) : people.length === 0 ? (
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '14px', padding: '60px 24px', textAlign: 'center',
            color: 'var(--text3)', fontSize: '13px',
          }}>
            <div style={{ fontSize: '36px', marginBottom: '14px' }}>👥</div>
            No faces detected yet. Make sure your photos have finished processing.
          </div>
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
            gap: '12px',
          }}>
            {people.map(person => (
              <div key={person.personId} style={{
                background: 'var(--surface)',
                border: `1px solid ${selectedPerson?.personId === person.personId
                  ? 'rgba(245,158,11,0.5)'
                  : person.name ? 'var(--border)' : 'rgba(245,158,11,0.18)'}`,
                borderRadius: '14px',
                padding: '16px 14px',
                textAlign: 'center',
                transition: 'border-color 0.2s',
              }}>
                {/* Face avatar */}
                <div
                  onClick={() => handlePersonClick(person)}
                  style={{ cursor: 'pointer', marginBottom: '10px' }}
                >
                  <div style={{
                    width: '72px', height: '72px', borderRadius: '50%',
                    overflow: 'hidden',
                    border: `2px solid ${person.name ? 'rgba(245,158,11,0.35)' : 'rgba(245,158,11,0.55)'}`,
                    margin: '0 auto 8px',
                    background: 'var(--surface2)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {person.representativeCrop ? (
                      <img
                        src={getFaceCropUrl(person.representativeCrop)}
                        alt="face"
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <span style={{ fontSize: '28px' }}>?</span>
                    )}
                  </div>
                  <div style={{
                    fontSize: '13px', fontWeight: '600',
                    fontFamily: 'Syne, sans-serif',
                    color: person.name ? 'var(--text)' : 'var(--text3)',
                    marginBottom: '2px',
                  }}>{person.name || 'Unknown'}</div>
                  <div style={{ fontSize: '10px', color: 'var(--text3)' }}>
                    {person.mediaIds.length} photo{person.mediaIds.length !== 1 ? 's' : ''}
                  </div>
                </div>

                {/* Name input */}
                <div onClick={e => e.stopPropagation()}>
                  <input
                    type="text"
                    placeholder={person.name ? `Rename "${person.name}"` : 'Add name...'}
                    value={nameInputs[person.personId] || ''}
                    onChange={e => setNameInputs(prev => ({ ...prev, [person.personId]: e.target.value }))}
                    onKeyDown={e => e.key === 'Enter' && handleNameSubmit(person.personId)}
                    style={{
                      width: '100%', background: 'rgba(255,255,255,0.04)',
                      border: '1px solid var(--border)',
                      borderRadius: '7px', padding: '6px 8px',
                      fontSize: '11px', color: 'var(--text2)', outline: 'none',
                      marginBottom: '6px',
                      transition: 'border-color 0.2s',
                    }}
                    onFocus={e => e.target.style.borderColor = 'rgba(245,158,11,0.4)'}
                    onBlur={e => e.target.style.borderColor = 'var(--border)'}
                  />
                  <button
                    onClick={() => handleNameSubmit(person.personId)}
                    disabled={savingId === person.personId}
                    style={{
                      width: '100%', padding: '6px',
                      background: savingId === person.personId ? 'rgba(245,158,11,0.3)' : 'rgba(245,158,11,0.1)',
                      border: '1px solid rgba(245,158,11,0.2)',
                      borderRadius: '7px', fontSize: '11px',
                      color: '#f59e0b', cursor: 'pointer',
                      transition: 'all 0.15s',
                    }}
                  >{savingId === person.personId ? 'Saving...' : 'Save Name'}</button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Selected person photos */}
        {selectedPerson && (
          <div style={{ marginTop: '32px' }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '14px',
            }}>
              <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '16px', fontWeight: '700' }}>
                Photos of {selectedPerson.name || 'Unknown Person'}
              </div>
              <button
                onClick={() => { setSelectedPerson(null); setPersonPhotos([]); }}
                style={{
                  padding: '4px 10px', background: 'var(--surface)',
                  border: '1px solid var(--border)', borderRadius: '6px',
                  fontSize: '11px', color: 'var(--text3)', cursor: 'pointer',
                }}
              >Close ×</button>
            </div>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
              gap: '8px',
            }}>
              {personPhotos.map(photo => (
                <div key={photo.id} style={{
                  height: '160px', borderRadius: '10px', overflow: 'hidden',
                  border: '1px solid var(--border)',
                }}>
                  <img
                    src={getImageUrl(photo.id)}
                    alt={photo.fileName}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </AppLayout>
  );
}