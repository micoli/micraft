import { useEffect, useState } from 'react';
import { PreferencesData, CommandInfo } from './types';

const PROTECTED_CHANNELS = new Set(['system', 'game']);

type Tab = 'chat' | 'commands' | 'graphics';

interface SavePayload {
  subscribedChannels: string[];
  disabledCommands: string[];
  shadersEnabled: boolean;
}

interface Props {
  open: boolean;
  preferences: PreferencesData | null;
  onSave: (payload: SavePayload) => void;
  onClose: () => void;
}

const OVERLAY: React.CSSProperties = {
  position: 'fixed', inset: 0, zIndex: 3000,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  background: 'rgba(0,0,0,0.65)',
};

const DIALOG: React.CSSProperties = {
  background: '#1e1e1e',
  border: '1px solid #444',
  borderRadius: 8,
  padding: '20px 24px',
  minWidth: 420,
  maxWidth: 560,
  maxHeight: '80vh',
  display: 'flex',
  flexDirection: 'column',
  color: '#eee',
  fontFamily: 'monospace',
  boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
};

const TABS: React.CSSProperties = {
  display: 'flex', gap: 4, marginBottom: 16,
  borderBottom: '1px solid #444', paddingBottom: 8,
};

const TAB_BTN = (active: boolean): React.CSSProperties => ({
  background: active ? '#3a3a3a' : 'transparent',
  border: active ? '1px solid #666' : '1px solid transparent',
  borderRadius: 4,
  color: active ? '#fff' : '#aaa',
  cursor: 'pointer',
  padding: '4px 12px',
  fontSize: 13,
});

const SCROLL: React.CSSProperties = {
  overflowY: 'auto', flex: 1, paddingRight: 4, maxHeight: '50vh',
};

const ROW: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '5px 0', borderBottom: '1px solid #2a2a2a',
};

const FOOTER: React.CSSProperties = {
  display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16,
};

const BTN = (primary: boolean): React.CSSProperties => ({
  padding: '6px 16px', borderRadius: 4, cursor: 'pointer', fontSize: 13,
  background: primary ? '#2d6a2d' : '#3a3a3a',
  color: '#eee',
  border: primary ? '1px solid #4a9a4a' : '1px solid #555',
});

export function Preferences({ open, preferences, onSave, onClose }: Props) {
  const [tab, setTab] = useState<Tab>('chat');
  const [localSubscribed, setLocalSubscribed] = useState<Set<string>>(new Set());
  const [localDisabled, setLocalDisabled] = useState<Set<string>>(new Set());
  const [localShaders, setLocalShaders] = useState(true);

  useEffect(() => {
    if (open && preferences) {
      setLocalSubscribed(new Set(preferences.subscribedChannels));
      setLocalDisabled(new Set(preferences.disabledCommands));
      setLocalShaders(preferences.shadersEnabled);
      setTab('chat');
    }
  }, [open]);

  if (!open || !preferences) return null;

  const toggleChannel = (ch: string, checked: boolean) => {
    const next = new Set(localSubscribed);
    if (checked) next.add(ch); else next.delete(ch);
    setLocalSubscribed(next);
  };

  const toggleCommand = (cmd: CommandInfo, enabled: boolean) => {
    const next = new Set(localDisabled);
    if (!enabled) next.add(cmd.id); else next.delete(cmd.id);
    setLocalDisabled(next);
  };

  const handleSave = () => {
    onSave({
      subscribedChannels: Array.from(localSubscribed),
      disabledCommands: Array.from(localDisabled),
      shadersEnabled: localShaders,
    });
  };

  const sortedCommands = [...preferences.commands].sort((a, b) => a.command.localeCompare(b.command));

  return (
    <div style={OVERLAY} onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div style={DIALOG}>
        <h3 style={{ margin: '0 0 12px', fontSize: 16, color: '#fff' }}>Preferences</h3>

        <div style={TABS}>
          {(['chat', 'commands', 'graphics'] as Tab[]).map(t => (
            <button key={t} style={TAB_BTN(tab === t)} onClick={() => setTab(t)}>
              {t === 'chat' ? 'Chat' : t === 'commands' ? 'Commands' : 'Graphics'}
            </button>
          ))}
        </div>

        <div style={SCROLL}>
          {tab === 'chat' && preferences.knownChannels.map(ch => {
            const protected_ = PROTECTED_CHANNELS.has(ch);
            return (
              <div key={ch} style={ROW}>
                <input
                  type="checkbox"
                  checked={localSubscribed.has(ch)}
                  disabled={protected_}
                  onChange={e => toggleChannel(ch, e.target.checked)}
                />
                <span style={{ color: protected_ ? '#888' : '#eee' }}>
                  #{ch}
                  {protected_ && <span style={{ color: '#666', marginLeft: 6, fontSize: 11 }}>(protected)</span>}
                </span>
              </div>
            );
          })}

          {tab === 'commands' && sortedCommands.map(cmd => (
            <div key={cmd.id} style={ROW}>
              <input
                type="checkbox"
                checked={!localDisabled.has(cmd.id)}
                onChange={e => toggleCommand(cmd, e.target.checked)}
              />
              <span>
                <span style={{ color: '#7ec8e3' }}>{cmd.command}</span>
                {cmd.description && (
                  <span style={{ color: '#888', marginLeft: 8, fontSize: 12 }}>{cmd.description}</span>
                )}
              </span>
            </div>
          ))}

          {tab === 'graphics' && (
            <div style={ROW}>
              <input
                type="checkbox"
                checked={localShaders}
                onChange={e => setLocalShaders(e.target.checked)}
              />
              <span>Shaders (ambient occlusion, directional shading, fog)</span>
            </div>
          )}
        </div>

        <div style={FOOTER}>
          <button style={BTN(false)} onClick={onClose}>Cancel</button>
          <button style={BTN(true)} onClick={handleSave}>Save</button>
        </div>
      </div>
    </div>
  );
}
