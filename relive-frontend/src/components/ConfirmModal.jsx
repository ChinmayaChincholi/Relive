export default function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}) {
  if (!open) return null;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1200,
        background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
      onClick={onCancel}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: '#0f1520', border: '1px solid var(--border2)',
          borderRadius: '16px', padding: '28px 32px',
          maxWidth: '340px', width: '90%',
        }}
      >
        <div style={{
          fontFamily: 'Syne, sans-serif', fontSize: '15px', fontWeight: '700',
          marginBottom: '8px', color: 'var(--text)',
        }}>
          {title}
        </div>
        {message && (
          <div style={{ fontSize: '12px', color: 'var(--text3)', marginBottom: '20px', lineHeight: '1.5' }}>
            {message}
          </div>
        )}
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={onCancel}
            disabled={busy}
            style={{
              flex: 1, padding: '9px',
              background: 'var(--surface)',
              border: '1px solid var(--border)', borderRadius: '8px',
              fontSize: '12px', color: 'var(--text3)', cursor: 'pointer',
            }}
          >{cancelLabel}</button>
          <button
            onClick={onConfirm}
            disabled={busy}
            style={{
              flex: 1, padding: '9px',
              background: danger
                ? 'rgba(239,68,68,0.15)'
                : 'linear-gradient(135deg, #f59e0b, #d97706)',
              border: danger ? '1px solid rgba(239,68,68,0.4)' : 'none',
              borderRadius: '8px',
              fontSize: '12px', fontWeight: '700',
              color: danger ? '#f87171' : '#1c1004',
              cursor: 'pointer',
            }}
          >{busy ? '...' : confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}