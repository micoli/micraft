import { useEffect, useRef } from 'react';
import { LogEntry } from './types';

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const CHANNEL_COLORS: Record<string, string> = {
  system: '#aaa',
  game: '#f5c542',
  world: '#fff',
  around: '#90e0b0',
};

function channelColor(ch: string): string {
  if (ch.startsWith('dm:')) return '#7dd3fc';
  return CHANNEL_COLORS[ch] ?? '#c084fc';
}

interface Props {
  logs: LogEntry[];
  visible: boolean;
  subscribedChannels: string[];
  activeChannel: string;
  unreadChannels: string[];
  onChannelSelect: (channel: string) => void;
  layoutStyle?: React.CSSProperties;
}

export function ServerLog({ logs, visible, subscribedChannels, activeChannel, unreadChannels, onChannelSelect, layoutStyle }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const userScrolledRef = useRef(false);

  const filtered = logs.filter(e => e.channel === activeChannel);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el || userScrolledRef.current) return;
    el.scrollTop = el.scrollHeight;
  }, [filtered]);

  useEffect(() => {
    if (visible) userScrolledRef.current = false;
  }, [visible]);

  if (!visible) return null;

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 4;
    userScrolledRef.current = !atBottom;
  }

  const containerStyle: React.CSSProperties = layoutStyle
    ? { display: 'flex', flexDirection: 'column', maxHeight: '100%', background: 'rgba(0,0,0,0.55)', borderRadius: '4px 4px 0 0', zIndex: 1002, boxSizing: 'border-box', ...layoutStyle }
    : { position: 'fixed', bottom: 94, left: '50%', transform: 'translateX(-50%)', width: '60%', maxHeight: 200, display: 'flex', flexDirection: 'column', background: 'rgba(0,0,0,0.55)', borderRadius: '4px 4px 0 0', zIndex: 1002, boxSizing: 'border-box' };

  const tabBarStyle: React.CSSProperties = {
    display: 'flex', flexShrink: 0, overflowX: 'auto', borderBottom: '1px solid rgba(255,255,255,0.15)',
  };

  return (
    <div style={containerStyle}>
      <div style={tabBarStyle}>
        {subscribedChannels.map(ch => (
          <button
            key={ch}
            onMouseDown={e => { e.preventDefault(); onChannelSelect(ch); }}
            style={{
              padding: '2px 8px',
              background: ch === activeChannel ? 'rgba(255,255,255,0.15)' : 'transparent',
              border: 'none',
              borderRight: '1px solid rgba(255,255,255,0.1)',
              color: channelColor(ch),
              font: '11px monospace',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {unreadChannels.includes(ch) ? `* ${ch}` : ch}
          </button>
        ))}
      </div>
      <div
        style={{ flex: 1, overflowY: 'auto', padding: '4px 8px', minHeight: 0 }}
        ref={scrollRef}
        onScroll={onScroll}
      >
        {filtered.length === 0 && (
          <div style={{ color: '#555', font: '12px/1.6 monospace', fontStyle: 'italic' }}>No messages</div>
        )}
        {filtered.map((entry, i) => (
          <div key={i} style={{ color: '#ddd', font: '12px/1.6 monospace' }}>
            <span style={{ color: '#888' }}>[{entry.time}]</span>{' '}
            {entry.sender && <span style={{ color: channelColor(entry.channel), fontWeight: 'bold' }}>{escapeHtml(entry.sender)}: </span>}
            <span dangerouslySetInnerHTML={{ __html: escapeHtml(entry.msg) }} />
          </div>
        ))}
      </div>
    </div>
  );
}
