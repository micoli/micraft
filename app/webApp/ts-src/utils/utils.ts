function registerCompleter(cmd: string, fn: (partial: string) => string[] | Promise<string[]>): void {
  window.mcState.commandCompleters[cmd] = fn;
  if (!window.mcState.knownCommands.includes(cmd)) window.mcState.knownCommands.push(cmd);
}

function registerServerCompleters(commands: Array<{ id: string; command: string; autocompleteArgs?: number[] }>): void {
  for (const cmd of commands) {
    if (cmd.autocompleteArgs?.length) {
      registerCompleter(cmd.command, async (partial: string) => {
        const tokens = partial.split(/\s+/);
        const endsWithSpace = /\s$/.test(partial);
        const filledCount = tokens.filter((t) => t.length > 0).length;
        const argIndex = endsWithSpace ? filledCount : Math.max(0, filledCount - 1);
        if (!cmd.autocompleteArgs!.includes(argIndex)) return [];
        const currentPartial = endsWithSpace ? "" : (tokens[tokens.length - 1] ?? "");
        const player = window.mcState.playerName ?? "";
        try {
          const r = await fetch(
            `/api/autocomplete/${cmd.id}/${argIndex}?partial=${encodeURIComponent(currentPartial)}&player=${encodeURIComponent(player)}`,
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

export function registerUtils(): Pick<
  McBindings,
  "getUrlParam" | "reload" | "setConnectedPlayers" | "setNpcNames" | "registerCompleter" | "registerServerCompleters"
> {
  window.mcState.connectedPlayers = [];
  window.mcState.npcNames = [];
  window.mcState.commandCompleters = {};
  window.mcState.knownCommands = [];
  window.mcState.activeChannel = "world";
  window.mcState.subscribedChannels = [
    { name: "world", autoFocus: false },
    { name: "system", autoFocus: false },
    { name: "game", autoFocus: false },
  ];
  window.mcState.knownChannels = [];

  const itemTypes = ["cobblestone", "dirt", "sand", "gravel", "sandstone", "snowball", "flint"];
  registerCompleter("/give", (p) => itemTypes.filter((t) => t.startsWith(p.toLowerCase())));
  registerCompleter("/keyreload", () => []);
  registerCompleter("/kick", (p) => (window.mcState.connectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/shaders", (p) => ["on", "off"].filter((o) => o.startsWith(p)));
  registerCompleter("/time", (p) => Array.from({ length: 24 }, (_, i) => String(i)).filter((o) => o.startsWith(p)));
  registerCompleter("/save", () => []);
  registerCompleter("/who", () => []);
  registerCompleter("/yield", () => []);
  registerCompleter("/preferences", () => []);
  registerCompleter("/disconnect", () => []);
  registerCompleter("/teleport", (p) => (window.mcState.connectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/summon", (p) => (window.mcState.connectedPlayers || []).filter((n) => n.startsWith(p)));
  registerCompleter("/goto", (p) => {
    const players: string[] = (window.mcState.connectedPlayers || []).filter((n: string) => n.startsWith(p));
    const npcs: string[] = (window.mcState.npcNames || []).filter((n: string) => n.startsWith(p));
    return [...players, ...npcs];
  });
  registerCompleter("/layouts", () => []);
  registerCompleter("/refetch", () => []);
  // /layout completer is overwritten by GameUI when layouts are synced
  registerCompleter("/layout", () => []);
  registerCompleter("/talk", (p) => (window.mcState.connectedPlayers || []).filter((n: string) => n.startsWith(p)));
  registerCompleter("/join", (p) => (window.mcState.knownChannels || []).filter((c: string) => c.startsWith(p)));
  registerCompleter("/leave", (p) =>
    (window.mcState.subscribedChannels || []).map((c) => c.name).filter((n) => n.startsWith(p)),
  );
  registerCompleter("/createchat", () => []);

  return {
    getUrlParam: (name: string): string => {
      const v = new URLSearchParams(window.location.search).get(name);
      return v === null ? "" : v;
    },

    reload: (): void => {
      window.location.reload();
    },

    setConnectedPlayers: (namesJson: string): void => {
      try {
        window.mcState.connectedPlayers = JSON.parse(namesJson);
      } catch (_e) {
        /* keep empty */
      }
    },

    setNpcNames: (namesJson: string): void => {
      try {
        window.mcState.npcNames = JSON.parse(namesJson);
      } catch (_e) {
        /* keep empty */
      }
    },

    registerCompleter,
    registerServerCompleters,
  };
}
