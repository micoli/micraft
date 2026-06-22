export function DisconnectOverlay({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div style={{
      position: 'fixed', inset: 0, display: 'flex', flexDirection: 'column',
      alignItems: 'center', justifyContent: 'center',
      background: 'rgba(0,0,0,0.72)', color: '#fff',
      font: 'bold 22px/2 monospace', zIndex: 1000, textAlign: 'center',
    }}>
      ⚠️ DISCONNECTED
      <span style={{ fontSize: 15, fontWeight: 'normal' }}>{message}</span>
    </div>
  );
}
