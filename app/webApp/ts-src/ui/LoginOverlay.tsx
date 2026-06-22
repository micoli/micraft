import { useState, useEffect, useRef, KeyboardEvent } from 'react';

function getUsers(): Record<string, string[]> {
  try { return JSON.parse(localStorage.getItem('micraft_users') || '{}'); } catch { return {}; }
}
function saveUsers(u: Record<string, string[]>) {
  try { localStorage.setItem('micraft_users', JSON.stringify(u)); } catch {}
}
function getLastPlayer(username: string): string {
  try { return localStorage.getItem('micraft_last_player_' + username) || ''; } catch { return ''; }
}
function saveLastPlayer(username: string, playerName: string) {
  try { localStorage.setItem('micraft_last_player_' + username, playerName); } catch {}
}

const inputStyle: React.CSSProperties = {
  width: '100%', boxSizing: 'border-box', padding: '8px 10px',
  background: '#111', border: '1px solid #555', borderRadius: 4,
  color: '#eee', font: '15px monospace', outline: 'none',
};
const btnPrimary: React.CSSProperties = {
  marginTop: 16, width: '100%', padding: 10,
  background: '#4a8fff', border: 'none', borderRadius: 4,
  color: '#fff', font: 'bold 15px monospace', cursor: 'pointer',
};
const btnSecondary: React.CSSProperties = {
  marginTop: 8, width: '100%', padding: 8,
  background: 'transparent', border: '1px solid #555', borderRadius: 4,
  color: '#aaa', font: '14px monospace', cursor: 'pointer',
};

interface Props {
  visible: boolean;
  loginResultRef: React.MutableRefObject<string>;
  onHide: () => void;
}

export function LoginOverlay({ visible, loginResultRef, onHide }: Props) {
  const [step, setStep] = useState<1 | 2>(1);
  const [username, setUsername] = useState('');
  const [chars, setChars] = useState<string[]>([]);
  const [selected, setSelected] = useState('');
  const [newChar, setNewChar] = useState('');
  const usernameInputRef = useRef<HTMLInputElement>(null);
  const newCharInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    try {
      const last = localStorage.getItem('micraft_last_user') || '';
      if (last) setUsername(last);
    } catch {}
  }, []);

  useEffect(() => {
    if (visible && step === 1) {
      setTimeout(() => usernameInputRef.current?.focus(), 50);
    }
  }, [visible, step]);

  function goStep2() {
    if (!username.trim()) { usernameInputRef.current?.focus(); return; }
    try { localStorage.setItem('micraft_last_user', username.trim()); } catch {}
    const users = getUsers();
    const playerChars = users[username.trim()] || [];
    const lastPlayer = getLastPlayer(username.trim());
    setChars(playerChars);
    setNewChar(playerChars.length === 0 ? username.trim() : '');
    setSelected(lastPlayer || (playerChars[0] ?? '__new__'));
    setStep(2);
    setTimeout(() => {
      const first = document.querySelector<HTMLInputElement>('input[name="mc-char"]:checked');
      if (first) first.focus(); else newCharInputRef.current?.focus();
    }, 50);
  }

  function doPlay() {
    const user = username.trim();
    let playerName: string;
    if (selected === '__new__' || !selected) {
      playerName = newChar.trim();
      if (!playerName) { newCharInputRef.current?.focus(); return; }
      const users = getUsers();
      if (!users[user]) users[user] = [];
      if (!users[user].includes(playerName)) users[user].push(playerName);
      saveUsers(users);
    } else {
      playerName = selected;
    }
    saveLastPlayer(user, playerName);
    loginResultRef.current = user + '\t' + playerName;
    onHide();
  }

  if (!visible) return null;

  return (
    <div style={{
      position: 'fixed', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'rgba(0,0,0,0.82)', zIndex: 2000,
    }}>
      <div style={{
        background: '#1a1a1a', border: '1px solid #444', borderRadius: 8,
        padding: '32px 40px', minWidth: 320, fontFamily: 'monospace', color: '#eee',
      }}>
        <div style={{ fontSize: 28, fontWeight: 'bold', textAlign: 'center', marginBottom: 24, color: '#6af' }}>
          MiCraft
        </div>

        {step === 1 && (
          <div>
            <label style={{ display: 'block', fontSize: 13, color: '#aaa', marginBottom: 6 }}>Username</label>
            <input
              ref={usernameInputRef}
              style={inputStyle}
              type="text"
              placeholder="Enter your username"
              value={username}
              onChange={e => setUsername(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => { if (e.key === 'Enter') goStep2(); }}
            />
            <button style={btnPrimary} onClick={goStep2}>Continue</button>
          </div>
        )}

        {step === 2 && (
          <div onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
            if (e.key === 'Enter' && selected !== '__new__') { e.stopPropagation(); doPlay(); }
          }}>
            <div style={{ fontSize: 14, color: '#aaa', marginBottom: 14 }}>
              Welcome, {username}! Choose your character:
            </div>
            <div style={{ marginBottom: 12 }}>
              {chars.map((name, i) => (
                <div key={name} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' }}>
                  <input
                    type="radio" name="mc-char" value={name} id={`mc-char-${i}`}
                    checked={selected === name}
                    onChange={() => setSelected(name)}
                  />
                  <label htmlFor={`mc-char-${i}`} style={{ fontSize: 14, cursor: 'pointer' }}>{name}</label>
                </div>
              ))}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                <input
                  type="radio" name="mc-char" value="__new__" id="mc-char-new"
                  checked={selected === '__new__'}
                  onChange={() => setSelected('__new__')}
                />
                <label htmlFor="mc-char-new" style={{ fontSize: 13, color: '#aaa', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                  + New character:
                </label>
                <input
                  ref={newCharInputRef}
                  type="text"
                  placeholder="Character name"
                  style={{ flex: 1, padding: '5px 8px', background: '#111', border: '1px solid #555', borderRadius: 4, color: '#eee', font: '14px monospace', outline: 'none' }}
                  value={newChar}
                  onChange={e => setNewChar(e.target.value)}
                  onFocus={() => setSelected('__new__')}
                  onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => { if (e.key === 'Enter') doPlay(); }}
                />
              </div>
            </div>
            <button style={btnPrimary} onClick={doPlay}>Play</button>
            <button style={btnSecondary} onClick={() => { setStep(1); setTimeout(() => usernameInputRef.current?.focus(), 50); }}>
              ← Back
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
