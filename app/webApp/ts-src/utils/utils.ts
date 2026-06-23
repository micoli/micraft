function registerCompleter(cmd: string, fn: (partial: string) => string[]): void {
  window.__mcCommandCompleters[cmd] = fn;
  if (!window.__mcKnownCommands.includes(cmd)) window.__mcKnownCommands.push(cmd);
}

export function registerUtils(): void {
  window.__mcConnectedPlayers = [];
  window.__mcCommandCompleters = {};
  window.__mcKnownCommands = [];

  window.mcGetUrlParam = (name: string): string => {
    const v = new URLSearchParams(window.location.search).get(name);
    return v === null ? '' : v;
  };

  window.mcReload = (): void => { window.location.reload(); };

  window.mcSetConnectedPlayers = (namesJson: string): void => {
    try { window.__mcConnectedPlayers = JSON.parse(namesJson); } catch (_e) { /* keep empty */ }
  };

  window.mcRegisterCompleter = registerCompleter;

  const itemTypes = ['cobblestone', 'dirt', 'sand', 'gravel', 'sandstone', 'snowball', 'flint'];
  registerCompleter('/give', (p) => itemTypes.filter(t => t.startsWith(p.toLowerCase())));
  registerCompleter('/keyreload', () => []);
  registerCompleter('/kick', (p) => (window.__mcConnectedPlayers || []).filter(n => n.startsWith(p)));
  registerCompleter('/shaders', (p) => ['on', 'off'].filter(o => o.startsWith(p)));
  registerCompleter('/time', (p) => Array.from({length: 24}, (_, i) => String(i)).filter(o => o.startsWith(p)));
  registerCompleter('/save', () => []);
  registerCompleter('/who', () => []);
  registerCompleter('/yield', () => []);
  registerCompleter('/disconnect', () => []);
  registerCompleter('/teleport', (p) => (window.__mcConnectedPlayers || []).filter(n => n.startsWith(p)));
  registerCompleter('/summon', (p) => (window.__mcConnectedPlayers || []).filter(n => n.startsWith(p)));
  registerCompleter('/goto', (p) => (window.__mcConnectedPlayers || []).filter(n => n.startsWith(p)));
  registerCompleter('/layouts', () => []);
  // /layout completer is overwritten by GameUI when layouts are synced
  registerCompleter('/layout', () => []);
}
