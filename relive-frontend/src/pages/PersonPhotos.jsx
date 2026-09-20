import { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import AppLayout from '../components/AppLayout';
import ConfirmModal from '../components/ConfirmModal';
import { getPhotosForPerson, getCropsForPerson, splitFaces, getPeople } from '../services/faceService';
import { getImageUrl, getFaceCropUrl } from '../services/mediaService';

export default function PersonPhotos() {
    const { id } = useParams();
    const location = useLocation();
    const navigate = useNavigate();
    const personName = location.state?.personName || 'Unknown Person';

    const [photos, setPhotos] = useState([]);
    const [loading, setLoading] = useState(true);

    const [crops, setCrops] = useState([]);
    const [cropsLoading, setCropsLoading] = useState(true);
    const [selectedCropIds, setSelectedCropIds] = useState(new Set());

    const [splitModalOpen, setSplitModalOpen] = useState(false);
    const [splitting, setSplitting] = useState(false);
    const [otherPeople, setOtherPeople] = useState([]);
    const [otherPeopleLoading, setOtherPeopleLoading] = useState(false);

    const fetchPhotos = () => {
        getPhotosForPerson(Number(id))
            .then(data => { setPhotos(data); setLoading(false); })
            .catch(() => setLoading(false));
    };

    const fetchCrops = () => {
        setCropsLoading(true);
        getCropsForPerson(Number(id))
            .then(data => { setCrops(data); setCropsLoading(false); })
            .catch(() => setCropsLoading(false));
    };

    useEffect(() => { fetchPhotos(); fetchCrops(); }, [id]);

    const toggleCropSelected = (embeddingId) => {
        setSelectedCropIds(prev => {
            const next = new Set(prev);
            if (next.has(embeddingId)) next.delete(embeddingId);
            else next.add(embeddingId);
            return next;
        });
    };

    const openSplitModal = async () => {
        setSplitModalOpen(true);
        setOtherPeopleLoading(true);
        try {
            const people = await getPeople();
            setOtherPeople(people.filter(p => p.personId !== Number(id)));
        } catch {
            setOtherPeople([]);
        } finally {
            setOtherPeopleLoading(false);
        }
    };

    const cancelSplit = () => {
        setSplitModalOpen(false);
    };

    const finalizeSplit = async (targetPersonId) => {
        setSplitting(true);
        try {
            await splitFaces(Array.from(selectedCropIds), targetPersonId || null);
            setSelectedCropIds(new Set());
            setSplitModalOpen(false);
            await Promise.all([fetchPhotos(), fetchCrops()]);
        } catch (e) {
            alert('Split failed: ' + e.message);
        } finally {
            setSplitting(false);
        }
    };

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

                {/* Face crop strip */}
                <div style={{ marginBottom: '28px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
                        <div style={{ fontSize: '12px', color: 'var(--text3)' }}>
                            Faces in this group{cropsLoading ? '' : ` (${crops.length})`} — select any that don't belong
                        </div>
                        {selectedCropIds.size > 0 && (
                            <button
                                onClick={openSplitModal}
                                style={{
                                    padding: '6px 14px',
                                    background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                                    border: 'none', borderRadius: '8px',
                                    fontSize: '11px', fontWeight: '700', color: '#1c1004',
                                    cursor: 'pointer',
                                }}
                            >
                                Not the same person ({selectedCropIds.size})
                            </button>
                        )}
                    </div>

                    {cropsLoading ? (
                        <div style={{ fontSize: '12px', color: 'var(--text3)' }}>Loading faces...</div>
                    ) : (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                            {crops.map(crop => {
                                const selected = selectedCropIds.has(crop.embeddingId);
                                return (
                                    <div
                                        key={crop.embeddingId}
                                        onClick={() => toggleCropSelected(crop.embeddingId)}
                                        style={{
                                            width: '64px', height: '64px',
                                            borderRadius: '10px', overflow: 'hidden',
                                            border: selected ? '2px solid #f59e0b' : '1px solid var(--border)',
                                            cursor: 'pointer',
                                            position: 'relative',
                                            opacity: selected ? 1 : 0.9,
                                            transition: 'border-color 0.15s, opacity 0.15s',
                                        }}
                                    >
                                        <img
                                            src={getFaceCropUrl(crop.cropPath)}
                                            alt="face"
                                            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                                        />
                                        {selected && (
                                            <div style={{
                                                position: 'absolute', top: '3px', right: '3px',
                                                width: '16px', height: '16px', borderRadius: '50%',
                                                background: '#f59e0b', color: '#1c1004',
                                                fontSize: '11px', fontWeight: '800',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            }}>✓</div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Photo grid */}
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

            {/* Split target picker */}
            {splitModalOpen && (
                <div
                    style={{
                        position: 'fixed', inset: 0, zIndex: 1200,
                        background: 'rgba(0,0,0,0.6)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}
                    onClick={cancelSplit}
                >
                    <div
                        onClick={e => e.stopPropagation()}
                        style={{
                            background: '#0f1520', border: '1px solid var(--border2)',
                            borderRadius: '16px', padding: '28px 32px',
                            maxWidth: '380px', width: '90%',
                        }}
                    >
                        <div style={{
                            fontFamily: 'Syne, sans-serif', fontSize: '15px', fontWeight: '700',
                            marginBottom: '8px', color: 'var(--text)',
                        }}>
                            Move {selectedCropIds.size} face{selectedCropIds.size !== 1 ? 's' : ''} to...
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text3)', marginBottom: '16px', lineHeight: '1.5' }}>
                            Pick an existing person, or leave unselected to create a new one.
                        </div>

                        <div style={{ maxHeight: '220px', overflowY: 'auto', marginBottom: '16px' }}>
                            {otherPeopleLoading ? (
                                <div style={{ fontSize: '12px', color: 'var(--text3)' }}>Loading people...</div>
                            ) : otherPeople.length === 0 ? (
                                <div style={{ fontSize: '12px', color: 'var(--text3)' }}>No other people yet.</div>
                            ) : (
                                otherPeople.map(p => (
                                    <div
                                        key={p.personId}
                                        onClick={() => finalizeSplit(p.personId)}
                                        style={{
                                            display: 'flex', alignItems: 'center', gap: '10px',
                                            padding: '8px', borderRadius: '8px', cursor: 'pointer',
                                            marginBottom: '4px',
                                        }}
                                        onMouseEnter={e => e.currentTarget.style.background = 'var(--surface)'}
                                        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                                    >
                                        <div style={{
                                            width: '32px', height: '32px', borderRadius: '50%', overflow: 'hidden',
                                            background: 'var(--bg3)', flexShrink: 0,
                                        }}>
                                            {p.representativeCrop && (
                                                <img
                                                    src={getFaceCropUrl(p.representativeCrop)}
                                                    alt=""
                                                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                />
                                            )}
                                        </div>
                                        <div style={{ fontSize: '12px', color: 'var(--text2)' }}>
                                            {p.name || 'Unnamed'}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                                onClick={cancelSplit}
                                disabled={splitting}
                                style={{
                                    flex: 1, padding: '9px',
                                    background: 'var(--surface)',
                                    border: '1px solid var(--border)', borderRadius: '8px',
                                    fontSize: '12px', color: 'var(--text3)', cursor: 'pointer',
                                }}
                            >Cancel</button>
                            <button
                                onClick={() => finalizeSplit(null)}
                                disabled={splitting}
                                style={{
                                    flex: 1, padding: '9px',
                                    background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                                    border: 'none', borderRadius: '8px',
                                    fontSize: '12px', fontWeight: '700', color: '#1c1004', cursor: 'pointer',
                                }}
                            >{splitting ? '...' : 'New person'}</button>
                        </div>
                    </div>
                </div>
            )}
        </AppLayout>
    );
}