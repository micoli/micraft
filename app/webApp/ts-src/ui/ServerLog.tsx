import { LogEntry } from './types';

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

export function ServerLog({ logs }: { logs: LogEntry[] }) {
  if (logs.length === 0) return null;
  return (
    <div style={{
      position: 'fixed', bottom: 94, left: '50%', transform: 'translateX(-50%)',
      width: '60%', background: 'rgba(0,0,0,0.55)', borderRadius: '4px 4px 0 0',
      padding: '4px 8px', zIndex: 1002, boxSizing: 'border-box', pointerEvents: 'none',
    }}>
      {logs.map((entry, i) => (
        <div key={i} style={{ color: '#ddd', font: '12px/1.6 monospace' }}>
          <span style={{ color: '#888' }}>[{entry.time}]</span>{' '}
          <span dangerouslySetInnerHTML={{ __html: escapeHtml(entry.msg) }} />
        </div>
      ))}
    </div>
  );
}
