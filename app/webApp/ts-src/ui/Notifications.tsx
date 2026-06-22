interface Props {
  notif: { msg: string; key: number } | null;
}

export function Notifications({ notif }: Props) {
  if (!notif) return null;
  return (
    <div style={{
      position: 'fixed', bottom: 100, left: '50%', transform: 'translateX(-50%)',
      background: 'rgba(0,0,0,0.72)', color: '#fff', font: '14px monospace',
      padding: '6px 14px', borderRadius: 4, zIndex: 1001, pointerEvents: 'none',
    }}>
      {notif.msg}
    </div>
  );
}
