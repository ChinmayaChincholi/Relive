import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import AppLayout from '../components/AppLayout';
import { getPeople, namePerson, mergePeople, deletePerson } from '../services/faceService';
import { getFaceCropUrl } from '../services/mediaService';

export default function Faces() {
  const navigate = useNavigate();
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [nameInputs, setNameInputs] = useState({});
  const [savingId, setSavingId] = useState(null);

  // Select mode (for deletion)
  const [selectMode, setSelectMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [deleting, setDeleting] = useState(false);

  // Merge mode (select two groups to combine)
  const [mergeMode, setMergeMode] = useState(false);
  const [mergeSelectedIds, setMergeSelectedIds] = useState([]);
  const [merging, setMerging] = useState(false);
  const [mergeNamePrompt, setMergeNamePrompt] = useState(false);
  const [mergeName, setMergeName] = useState('');

  // Drag-and-drop merge
  const dragPersonId = useRef(null);
  const [dragOverId, setDragOverId] = useState(null);

  const fetchPeople = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getPeople();
      // Sort by number of photos descending
      data.sort((a, b) => b.mediaIds.length - a.mediaIds.length);
      setPeople(data);
    } catch (e) {
      setError('Could not load faces. Make sure all three services are running and photos have finished processing.');
    } finally {
      setLoading(false);
    }
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
    } catch {
      alert('Failed to save name');
    } finally {
      setSavingId(null);
    }
  };

  // ── Merge logic ───────────────────────────────────────────────────────────

  const handleMergeSelect = (personId) => {
    setMergeSelectedIds(prev => {
      if (prev.includes(personId)) return prev.filter(id => id !== personId);
      if (prev.length >= 2) return prev; // max 2 at a time
      return [...prev, personId];
    });
  };

  const confirmMerge = async (overrideName) => {
    if (mergeSelectedIds.length < 2) return;
    const [id1, id2] = mergeSelectedIds;
    const p1 = people.find(p => p.personId === id1);
    const p2 = people.find(p => p.personId === id2);

    // Determine name: prefer existing name from p1, else p2, else user input
    let finalName = overrideName?.trim() || p1?.name || p2?.name || null;

    setMerging(true);
    try {
      await mergePeople(id1, id2, finalName);
      setMergeMode(false);
      setMergeSelectedIds([]);
      setMergeNamePrompt(false);
      setMergeName('');
      await fetchPeople();
    } catch (e) {
      alert('Merge failed: ' + e.message);
    } finally {
      setMerging(false);
    }
  };

  const initiateMerge = () => {
    if (mergeSelectedIds.length < 2) return;
    const p1 = people.find(p => p.personId === mergeSelectedIds[0]);
    const p2 = people.find(p => p.personId === mergeSelectedIds[1]);
    // If both unnamed → ask for name
    if (!p1?.name && !p2?.name) {
      setMergeNamePrompt(true);
    } else {
      confirmMerge(null);
    }
  };

  // ── Drag-and-drop merge ───────────────────────────────────────────────────

  const handleDragStart = (personId) => {
    dragPersonId.current = personId;
  };

  const handleDrop = async (targetPersonId) => {
    const sourceId = dragPersonId.current;
    dragPersonId.current = null;
    setDragOverId(null);
    if (!sourceId || sourceId === targetPersonId) return;

    const src = people.find(p => p.personId === sourceId);
    const tgt = people.find(p => p.personId === targetPersonId);
    const name = src?.name || tgt?.name || null;

    if (!name && !src?.name && !tgt?.name) {
      // Ask for name via a quick prompt
      const entered = window.prompt('Enter a name for the combined group (or leave blank):');
      setMerging(true);
      try {
        await mergePeople(sourceId, targetPersonId, entered?.trim() || null);
        await fetchPeople();
      } catch (e) { alert('Merge failed'); } finally { setMerging(false); }
    } else {
      setMerging(true);
      try {
        await mergePeople(sourceId, targetPersonId, name);
        await fetchPeople();
      } catch (e) { alert('Merge failed'); } finally { setMerging(false); }
    }
  };

  // ── Delete logic ──────────────────────────────────────────────────────────

  const toggleSelectForDelete = (personId) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(personId)) next.delete(personId);
      else next.add(personId);
      return next;
    });
  };

  const handleDelete = async () => {
    if (selectedIds.size === 0) return;
    if (!window.confirm(`Delete ${selectedIds.size} group${selectedIds.size > 1 ? 's' : ''}? This cannot be undone.`)) return;
    setDeleting(true);
    for (const id of selectedIds) {
      try { await deletePerson(id); } catch (e) { console.error('Delete failed for', id, e); }
    }
    setSelectedIds(new Set());
    setSelectMode(false);
    setDeleting(false);
    await fetchPeople();
  };

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>

        {/* Merge name prompt modal */}
        {mergeNamePrompt && (
          <div style={{
            position: 'fixed', inset: 0, zIndex: 1000,
            background: 'rgba(0,0,0,0.6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{
              background: '#0f1520', border: '1px solid var(--border2)',
              borderRadius: '16px', padding: '28px 32px',
              maxWidth: '320px', width: '90%',
            }}>
              <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '15px', fontWeight: '700', marginBottom: '8px' }}>
                Name this combined group
              </div>
              <div style={{ fontSize: '12px', color: 'var(--text3)', marginBottom: '16px' }}>
                Both groups are unnamed. Enter a name for the merged group (optional).
              </div>
              <input
                autoFocus
                value={mergeName}
                onChange={e => setMergeName(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && confirmMerge(mergeName)}
                placeholder="Enter name..."
                style={{
                  width: '100%', background: 'rgba(255,255,255,0.04)',
                  border: '1px solid var(--border2)', borderRadius: '8px',
                  padding: '8px 12px', fontSize: '13px', color: 'var(--text)',
                  outline: 'none', marginBottom: '14px',
                }}
              />
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  onClick={() => { setMergeNamePrompt(false); setMergeName(''); }}
                  style={{
                    flex: 1, padding: '8px', background: 'var(--surface)',
                    border: '1px solid var(--border)', borderRadius: '8px',
                    fontSize: '12px', color: 'var(--text3)', cursor: 'pointer',
                  }}
                >Cancel</button>
                <button
                  onClick={() => confirmMerge(mergeName)}
                  disabled={merging}
                  style={{
                    flex: 1, padding: '8px',
                    background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                    border: 'none', borderRadius: '8px',
                    fontSize: '12px', fontWeight: '700', color: '#1c1004',
                    cursor: 'pointer',
                  }}
                >{merging ? 'Merging...' : 'Merge'}</button>
              </div>
            </div>
          </div>
        )}

        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px', flexWrap: 'wrap', gap: '10px' }}>
          <div>
            <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '2px' }}>
              Your People
            </div>
            <div style={{ fontSize: '12px', color: 'var(--text3)', fontWeight: '300' }}>
              {people.length} people found · sorted by most photos
            </div>
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {/* Delete selected button */}
            {selectMode && selectedIds.size > 0 && (
              <button
                onClick={handleDelete}
                disabled={deleting}
                style={{
                  padding: '7px 14px', background: 'rgba(239,68,68,0.1)',
                  border: '1px solid rgba(239,68,68,0.3)', borderRadius: '8px',
                  fontSize: '12px', color: '#f87171', cursor: 'pointer',
                }}
              >{deleting ? 'Deleting...' : `Delete (${selectedIds.size})`}</button>
            )}

            {/* Merge button (shown in merge mode when 2 selected) */}
            {mergeMode && mergeSelectedIds.length === 2 && (
              <button
                onClick={initiateMerge}
                disabled={merging}
                style={{
                  padding: '7px 14px',
                  background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                  border: 'none', borderRadius: '8px',
                  fontSize: '12px', fontWeight: '700', color: '#1c1004', cursor: 'pointer',
                }}
              >{merging ? 'Merging...' : `Merge ${mergeSelectedIds.length} groups`}</button>
            )}

            <button
              onClick={() => {
                setMergeMode(s => !s);
                setMergeSelectedIds([]);
                setSelectMode(false);
                setSelectedIds(new Set());
              }}
              style={{
                padding: '7px 14px',
                background: mergeMode ? 'rgba(245,158,11,0.1)' : 'var(--surface)',
                border: `1px solid ${mergeMode ? 'rgba(245,158,11,0.4)' : 'var(--border)'}`,
                borderRadius: '8px', fontSize: '12px',
                color: mergeMode ? '#f59e0b' : 'var(--text2)', cursor: 'pointer',
              }}
            >{mergeMode ? 'Cancel Merge' : '🔗 Merge Faces'}</button>

            <button
              onClick={() => {
                setSelectMode(s => !s);
                setSelectedIds(new Set());
                setMergeMode(false);
                setMergeSelectedIds([]);
              }}
              style={{
                padding: '7px 14px',
                background: selectMode ? 'rgba(245,158,11,0.1)' : 'var(--surface)',
                border: `1px solid ${selectMode ? 'rgba(245,158,11,0.4)' : 'var(--border)'}`,
                borderRadius: '8px', fontSize: '12px',
                color: selectMode ? '#f59e0b' : 'var(--text2)', cursor: 'pointer',
              }}
            >{selectMode ? 'Cancel' : 'Select'}</button>

            <button
              onClick={fetchPeople}
              style={{
                padding: '7px 14px', background: 'var(--surface)',
                border: '1px solid var(--border)', borderRadius: '8px',
                fontSize: '12px', color: 'var(--text2)', cursor: 'pointer',
              }}
            >Refresh</button>
          </div>
        </div>

        {/* Mode hints */}
        {mergeMode && !selectMode && (
          <div style={{
            background: 'rgba(245,158,11,0.07)', border: '1px solid rgba(245,158,11,0.2)',
            borderRadius: '10px', padding: '10px 14px', marginBottom: '16px',
            fontSize: '12px', color: '#fbbf24',
          }}>
            💡 Select 2 face groups to merge them, or drag one face card and drop it onto another.
          </div>
        )}

        {loading ? (
          <div style={{ textAlign: 'center', padding: '60px', color: 'var(--text3)' }}>Loading faces...</div>
        ) : error ? (
          <div style={{
            background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.2)',
            borderRadius: '14px', padding: '40px 24px', textAlign: 'center',
            color: '#f87171', fontSize: '13px',
          }}>
            <div style={{ fontSize: '32px', marginBottom: '12px' }}>⚠️</div>
            {error}
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
            {people.map(person => {
              const isMergeSelected = mergeSelectedIds.includes(person.personId);
              const isDeleteSelected = selectedIds.has(person.personId);
              const isDragTarget = dragOverId === person.personId;

              return (
                <div
                  key={person.personId}
                  draggable={mergeMode || (!selectMode && !mergeMode)}
                  onDragStart={() => handleDragStart(person.personId)}
                  onDragOver={e => { e.preventDefault(); setDragOverId(person.personId); }}
                  onDragLeave={() => setDragOverId(null)}
                  onDrop={() => handleDrop(person.personId)}
                  style={{
                    background: 'var(--surface)',
                    border: `${isMergeSelected || isDeleteSelected ? '2px' : '1px'} solid ${
                      isMergeSelected ? '#f59e0b'
                      : isDeleteSelected ? '#f87171'
                      : isDragTarget ? 'rgba(245,158,11,0.6)'
                      : person.name ? 'var(--border)' : 'rgba(245,158,11,0.18)'
                    }`,
                    borderRadius: '14px',
                    padding: '16px 14px',
                    textAlign: 'center',
                    transition: 'border-color 0.15s, transform 0.1s',
                    transform: isDragTarget ? 'scale(1.03)' : 'scale(1)',
                    cursor: mergeMode || selectMode ? 'pointer' : 'grab',
                  }}
                  onClick={() => {
                    if (mergeMode) { handleMergeSelect(person.personId); return; }
                    if (selectMode) { toggleSelectForDelete(person.personId); return; }
                    // Navigate to person's photo page
                    navigate(`/faces/person/${person.personId}`, {
                      state: { personName: person.name || 'Unknown Person' }
                    });
                  }}
                >
                  {/* Selection indicator */}
                  {(mergeMode || selectMode) && (
                    <div style={{
                      width: '20px', height: '20px', borderRadius: '50%',
                      background: isMergeSelected || isDeleteSelected ? '#f59e0b' : 'rgba(255,255,255,0.06)',
                      border: `2px solid ${isMergeSelected || isDeleteSelected ? '#f59e0b' : 'rgba(255,255,255,0.2)'}`,
                      margin: '0 auto 8px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '10px', color: '#000',
                    }}>
                      {(isMergeSelected || isDeleteSelected) && '✓'}
                    </div>
                  )}

                  {/* Face avatar */}
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
                        onError={e => { e.target.style.display = 'none'; }}
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
                  <div style={{ fontSize: '10px', color: 'var(--text3)', marginBottom: '10px' }}>
                    {person.mediaIds.length} photo{person.mediaIds.length !== 1 ? 's' : ''}
                  </div>

                  {/* Name input (hidden in merge/select mode) */}
                  {!mergeMode && !selectMode && (
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
                          marginBottom: '6px', transition: 'border-color 0.2s',
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
                          color: '#f59e0b', cursor: 'pointer', transition: 'all 0.15s',
                        }}
                      >{savingId === person.personId ? 'Saving...' : 'Save Name'}</button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </AppLayout>
  );
}