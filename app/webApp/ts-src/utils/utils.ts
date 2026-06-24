function registerCompleter(cmd: string, fn: (partial: string) => string[] | Promise<string[]>): void {
  window.__mcCommandCompleters[cmd] = fn;
  if (!window.__mcKnownCommands.includes(cmd)) window.__mcKnownCommands.push(cmd);
}

function registerServerCompleters(commands: Array<{ id: string; command: string; autocompleteArgs?: number[] }>): void {
  for (const cmd of commands) {
    if (cmd.autocompleteArgs?.length) {
      registerCompleter(cmd.command, async (partial: string) => {
        const player = (window as any).__mcPlayerName ?? "";
        try {
          const r = await fetch(
            `/api/autocomplete/${cmd.id}/0?partial=${encodeURIComponent(partial)}&player=${encodeURIComponent(player)}`,
          );
          if (!r.ok) return [];
          return (await r.json()) as string[];
        } catch {
          return [];
        }
      });
    } else {
      registerCompleter(cmd.command, () => []);
    }
  }
}

export function registerUtils(): void {
  window.__mcConnectedPlayers = [];
  (window as any).__mcNpcNames = [];
  window.__mcCommandCompleters = {};
  window.__mcKnownCommands = [];
  (window as any).__mcActiveChannel = "world";
  (window as any).__mcSubscribedChannels = ["world", "system", "game"];
  (window as any).__mcKnownChannels = [];

  window.mcGetUrlParam = (name: string): string => {
    const v = new URLSearchParams(window.location.search).get(name);
    return v === null ? "" : v;
  };

  window.mcReload = (): void => {
    window.location.reload();
  };

  window.mcSetConnectedPlayers = (namesJson: string): void => {
    try {
      window.__mcConnectedPlayers = JSON.parse(namesJson);
    } catch (_e) {
      /* keep empty */
    }
  };

  (window as any).mcSetNpcNames = (namesJson: string): void => {
    try {
      (window as any).__mcNpcNames = JSON.parse(namesJson);
    } catch (_e) {
      /* keep empty */
    }
  };

  window.mcRegisterCompleter = registerCompleter;
  (window as any).mcRegisterServerCompleters = registerServerCompleters;

  const itemTypes = ["cobblestone", "dirt", "sand", "gravel", "sandstone", "snowball", "flint"];
  registerCompleter("/give", (p) => itemTypes.filter((t) => t.startsWith(p.toLowerCase())));
  registerCompleter("/keyreload", () => []);
  registerCompleter("/kick", (p) => (window.__mcConnectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/shaders", (p) => ["on", "off"].filter((o) => o.startsWith(p)));
  registerCompleter("/time", (p) => Array.from({ length: 24 }, (_, i) => String(i)).filter((o) => o.startsWith(p)));
  registerCompleter("/save", () => []);
  registerCompleter("/who", () => []);
  registerCompleter("/yield", () => []);
  registerCompleter("/preferences", () => []);
  registerCompleter("/disconnect", () => []);
  registerCompleter("/teleport", (p) => (window.__mcConnectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/summon", (p) => (window.__mcConnectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/goto", (p) => {
    const players: string[] = (window.__mcConnectedPlayers || []).filter((n: string) => n.startsWith(p));
    const npcs: string[] = ((window as any).__mcNpcNames || []).filter((n: string) => n.startsWith(p));
    return [...players, ...npcs];
  });
  registerCompleter("/layouts", () => []);
  registerCompleter("/refetch", () => []);
  // /layout completer is overwritten by GameUI when layouts are synced
  registerCompleter("/layout", () => []);
  registerCompleter("/talk", (p) => (window.__mcConnectedPlayers || []).filter((n: string) => n.startsWith(p)));
  registerCompleter("/join", (p) => ((window as any).__mcKnownChannels || []).filter((c: string) => c.startsWith(p)));
  registerCompleter("/leave", (p) =>
    ((window as any).__mcSubscribedChannels || []).filter((c: string) => c.startsWith(p)),
  );
  registerCompleter("/createchat", () => []);
}
