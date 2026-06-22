import { useEffect, useRef } from 'react';
import { LogEntry } from './types';

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

export function ServerLog({ logs, visible }: { logs: LogEntry[]; visible: boolean }) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const userScrolledRef = useRef(false);

  // Auto-scroll to bottom on new messages unless user scrolled up
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || userScrolledRef.current) return;
    el.scrollTop = el.scrollHeight;
  }, [logs]);

  // Reset auto-scroll flag when panel becomes visible again
  useEffect(() => {
    if (visible) userScrolledRef.current = false;
  }, [visible]);

  if (!visible || logs.length === 0) return null;

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 4;
    userScrolledRef.current = !atBottom;
  }

  return (
    <div style={{
      position: 'fixed', bottom: 94, left: '50%', transform: 'translateX(-50%)',
      width: '60%', maxHeight: 200, background: 'rgba(0,0,0,0.55)',
      borderRadius: '4px 4px 0 0', padding: '4px 8px', zIndex: 1002,
      boxSizing: 'border-box', overflowY: 'auto',
    }}
      ref={scrollRef}
      onScroll={onScroll}
    >
      {logs.map((entry, i) => (
        <div key={i} style={{ color: '#ddd', font: '12px/1.6 monospace' }}>
          <span style={{ color: '#888' }}>[{entry.time}]</span>{' '}
          <span dangerouslySetInnerHTML={{ __html: escapeHtml(entry.msg) }} />
        </div>
      ))}
    </div>
  );
}
