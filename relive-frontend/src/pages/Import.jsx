import { useRef, useState, useEffect } from 'react';
import { uploadFolder } from '../services/mediaService';
import { getMyMedia } from '../services/mediaService';
import AppLayout from '../components/AppLayout';

export default function Import() {
  const fileInputRef = useRef();
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ current: 0, total: 0 });
  const [message, setMessage] = useState('');
  const [queue, setQueue] = useState([]);
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    const fetchQueue = () => {
      getMyMedia().then(data => {
        const processing = data.filter(m => m.status === 'PROCESSING');
        const completed = data.filter(m => m.status === 'COMPLETED');
        setQueue([
          ...processing.map(m => ({ ...m, display: 'processing' })),
          ...completed.slice(0, 3).map(m => ({ ...m, display: 'done' })),
        ]);
      }).catch(() => {});
    };
    fetchQueue();
    const interval = setInterval(fetchQueue, 5000);
    return () => clearInterval(interval);
  }, []);

  const handleFiles = async (files) => {
    const arr = Array.from(files);
    if (!arr.length) return;

    setUploading(true);
    setProgress({ current: 0, total: arr.length });
    let skipped = 0, failed = 0;

    for (let i = 0; i < arr.length; i++) {
      setProgress({ current: i + 1, total: arr.length });
      setMessage(`Uploading ${i + 1} of ${arr.length}: ${arr[i].name}`);
      try {
        const fd = new FormData();
        fd.append('files', arr[i]);
        const res = await uploadFolder(fd);
        if (res && res.includes('already exists')) skipped++;
      } catch { failed++; }
    }

    const uploaded = arr.length - skipped - failed;
    let summary = `Done! ${uploaded} uploaded`;
    if (skipped > 0) summary += `, ${skipped} skipped`;
    if (failed > 0) summary += `, ${failed} failed`;
    setMessage(summary + '. Processing in background...');
    setUploading(false);
    setProgress({ current: 0, total: 0 });
    fileInputRef.current.value = '';

    // Refresh queue
    setTimeout(() => {
      getMyMedia().then(data => {
        const processing = data.filter(m => m.status === 'PROCESSING');
        const completed = data.filter(m => m.status === 'COMPLETED');
        setQueue([
          ...processing.map(m => ({ ...m, display: 'processing' })),
          ...completed.slice(0, 3).map(m => ({ ...m, display: 'done' })),
        ]);
      }).catch(() => {});
    }, 1000);
  };

  const processingItems = queue.filter(q => q.display === 'processing');
  const doneItems = queue.filter(q => q.display === 'done');
  const totalProcessed = queue.filter(q => q.display === 'done').length;
  const totalProcessing = processingItems.length;
  const progPct = queue.length > 0 ? Math.round((totalProcessed / queue.length) * 100) : 0;

  return (
    <AppLayout>
      <div style={{ padding: '24px' }}>
        <div style={{ fontFamily: 'Syne, sans-serif', fontSize: '20px', fontWeight: '800', marginBottom: '20px' }}>
          Import Media
        </div>

        {/* Drop zone */}
        <div
          className="fade-up"
          onDragOver={e => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={e => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
          onClick={() => !uploading && fileInputRef.current.click()}
          style={{
            border: `2px dashed ${dragOver ? 'rgba(245,158,11,0.6)' : 'rgba(245,158,11,0.22)'}`,
            borderRadius: '16px',
            padding: '40px 24px',
            textAlign: 'center',
            background: dragOver ? 'rgba(245,158,11,0.07)' : 'rgba(245,158,11,0.025)',
            cursor: uploading ? 'not-allowed' : 'pointer',
            marginBottom: '18px',
            transition: 'all 0.2s',
          }}
        >
          <div style={{ fontSize: '40px', marginBottom: '12px' }}>📁</div>
          <div style={{ fontSize: '15px', fontWeight: '600', fontFamily: 'Syne, sans-serif', color: 'var(--text)', marginBottom: '5px' }}>
            {dragOver ? 'Drop to import' : 'Drop your photos here'}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text3)', marginBottom: '16px', fontWeight: '300' }}>
            or click to browse — supports JPG, PNG, HEIC
          </div>
          <button
            disabled={uploading}
            style={{
              padding: '9px 24px',
              background: uploading ? 'rgba(245,158,11,0.4)' : 'linear-gradient(135deg, #f59e0b, #d97706)',
              border: 'none', borderRadius: '8px',
              fontSize: '13px', fontWeight: '700', color: '#1c1004',
              cursor: uploading ? 'not-allowed' : 'pointer',
              fontFamily: 'Syne, sans-serif',
            }}
          >{uploading ? `Uploading ${progress.current}/${progress.total}...` : 'Choose Photos'}</button>
        </div>

        <input
          type="file" multiple accept="image/*"
          style={{ display: 'none' }}
          ref={fileInputRef}
          onChange={e => handleFiles(e.target.files)}
        />

        {/* Upload progress */}
        {uploading && (
          <div style={{
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '12px', padding: '14px 16px', marginBottom: '14px',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
              <span style={{ fontSize: '12px', fontWeight: '600', color: 'var(--text2)' }}>Uploading files</span>
              <span style={{ fontSize: '12px', color: '#fbbf24', fontWeight: '600' }}>
                {progress.current} / {progress.total}
              </span>
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text3)', marginBottom: '8px' }}>{message}</div>
            <div style={{ height: '4px', background: 'rgba(255,255,255,0.06)', borderRadius: '2px', overflow: 'hidden' }}>
              <div style={{
                height: '4px',
                width: `${Math.round((progress.current / progress.total) * 100)}%`,
                background: 'linear-gradient(90deg, #d97706, #fbbf24)',
                borderRadius: '2px',
                transition: 'width 0.3s ease',
                position: 'relative', overflow: 'hidden',
              }}>
                <div style={{
                  position: 'absolute', inset: 0,
                  background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent)',
                  animation: 'shimmer 1.2s infinite',
                }} />
              </div>
            </div>
          </div>
        )}

        {message && !uploading && (
          <div style={{
            background: 'rgba(34,197,94,0.07)', border: '1px solid rgba(34,197,94,0.2)',
            borderRadius: '10px', padding: '10px 14px',
            fontSize: '12px', color: '#4ade80', marginBottom: '14px',
          }}>{message}</div>
        )}

        {/* Processing queue */}
        {queue.length > 0 && (
          <div className="fade-up-2">
            {/* Overall progress */}
            <div style={{
              background: 'var(--surface)', border: '1px solid var(--border)',
              borderRadius: '12px', padding: '16px 18px', marginBottom: '12px',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text2)', fontFamily: 'Syne, sans-serif' }}>
                  Processing queue
                </span>
                <span style={{ fontSize: '13px', color: '#fbbf24', fontWeight: '600' }}>
                  {totalProcessed} of {queue.length} done
                </span>
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text3)', marginBottom: '10px' }}>
                {totalProcessing > 0 ? `${totalProcessing} photo${totalProcessing > 1 ? 's' : ''} being analysed...` : 'All photos ready to search'}
              </div>
              <div style={{ height: '6px', background: 'rgba(255,255,255,0.06)', borderRadius: '3px', overflow: 'hidden' }}>
                <div style={{
                  height: '6px',
                  width: `${progPct}%`,
                  background: 'linear-gradient(90deg, #d97706, #f59e0b, #fbbf24)',
                  borderRadius: '3px',
                  transition: 'width 0.5s ease',
                  position: 'relative', overflow: 'hidden',
                }}>
                  <div style={{
                    position: 'absolute', inset: 0,
                    background: 'linear-gradient(90deg, transparent, rgba(255,255,255,0.25), transparent)',
                    animation: 'shimmer 1.5s infinite',
                  }} />
                </div>
              </div>
            </div>

            {/* In progress */}
            {processingItems.length > 0 && (
              <>
                <div style={{ fontSize: '10px', color: 'var(--text3)', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '7px' }}>
                  In progress
                </div>
                {processingItems.map(item => (
                  <div key={item.id} style={{
                    background: 'var(--surface)',
                    border: '1px solid var(--border)',
                    borderRadius: '9px', padding: '10px 13px', marginBottom: '5px',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                      <div style={{
                        width: '24px', height: '24px', borderRadius: '5px',
                        background: 'var(--surface2)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '12px', flexShrink: 0,
                      }}>📷</div>
                      <div style={{
                        fontSize: '11px', color: 'var(--text2)', flex: 1,
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                      }}>{item.fileName}</div>
                      <div style={{ fontSize: '10px', color: '#fbbf24', flexShrink: 0 }}>Analysing...</div>
                    </div>
                    <div style={{ height: '2px', background: 'rgba(255,255,255,0.06)', borderRadius: '1px' }}>
                      <div style={{
                        height: '2px', width: '50%',
                        background: 'linear-gradient(90deg, #d97706, #fbbf24)',
                        borderRadius: '1px',
                        animation: 'shimmer 1.5s infinite',
                      }} />
                    </div>
                  </div>
                ))}
              </>
            )}

            {/* Completed */}
            {doneItems.length > 0 && (
              <>
                <div style={{ fontSize: '10px', color: 'var(--text3)', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '7px', marginTop: '12px' }}>
                  Ready to search
                </div>
                {doneItems.map(item => (
                  <div key={item.id} style={{
                    background: 'var(--surface)',
                    border: '1px solid rgba(34,197,94,0.15)',
                    borderRadius: '9px', padding: '10px 13px', marginBottom: '5px',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <div style={{
                        width: '24px', height: '24px', borderRadius: '5px',
                        background: 'var(--surface2)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '12px', flexShrink: 0,
                      }}>📷</div>
                      <div style={{
                        fontSize: '11px', color: 'var(--text2)', flex: 1,
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                      }}>{item.fileName}</div>
                      <div style={{ fontSize: '10px', color: '#22c55e', flexShrink: 0 }}>✓ Ready</div>
                    </div>
                  </div>
                ))}
              </>
            )}
          </div>
        )}
      </div>
    </AppLayout>
  );
}