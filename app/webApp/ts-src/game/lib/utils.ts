import { getApiAutocompleteByCommandIdByArgIndex } from "../../generated/api/requests";
import type { Suggestion } from "../types";

function registerCompleter(cmd: string, fn: (partial: string) => Suggestion[] | Promise<Suggestion[]>): void {
  window.mcState.commandCompleters[cmd] = fn;
  if (!window.mcState.knownCommands.includes(cmd)) window.mcState.knownCommands.push(cmd);
}

function playerSuggestions(partial: string): Suggestion[] {
  return (window.mcState.connectedPlayers || [])
    .filter((p) => p.name.startsWith(partial))
    .map((p) => ({ label: p.name, value: "@" + p.id }));
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
          const { data } = await getApiAutocompleteByCommandIdByArgIndex({
            path: { commandId: cmd.id, argIndex },
            query: { partial: currentPartial, player },
          });
          return data ?? [];
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
  "reload" | "setConnectedPlayers" | "setNpcNames" | "registerCompleter" | "registerServerCompleters"
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
  const viewModes = [
    "FIRST_PERSON",
    "FIRST_PERSON_NO_ARMS",
    "THIRD_PERSON",
    "THIRD_PERSON_ORBIT",
    "THIRD_PERSON_ORBIT_CURSOR",
  ];
  registerCompleter("/view_mode", (p) => viewModes.filter((m) => m.toLowerCase().startsWith(p.toLowerCase())));
  registerCompleter("/kick", playerSuggestions);
  registerCompleter("/shaders", (p) => ["on", "off"].filter((o) => o.startsWith(p)));
  registerCompleter("/time", (p) => Array.from({ length: 24 }, (_, i) => String(i)).filter((o) => o.startsWith(p)));
  registerCompleter("/save", () => []);
  registerCompleter("/who", () => []);
  registerCompleter("/yield", () => []);
  registerCompleter("/preferences", () => []);
  registerCompleter("/disconnect", () => []);
  registerCompleter("/teleport", playerSuggestions);
  registerCompleter("/summon", playerSuggestions);
  registerCompleter("/goto", (p) => {
    const npcs: string[] = (window.mcState.npcNames || []).filter((n: string) => n.startsWith(p));
    return [...playerSuggestions(p), ...npcs];
  });
  registerCompleter("/layouts", () => []);
  registerCompleter("/refetch", () => []);
  // /layout completer is overwritten by GameUI when layouts are synced
  registerCompleter("/layout", () => []);
  registerCompleter("/talk", playerSuggestions);
  registerCompleter("/join", (p) => (window.mcState.knownChannels || []).filter((c: string) => c.startsWith(p)));
  registerCompleter("/leave", (p) =>
    (window.mcState.subscribedChannels || []).map((c) => c.name).filter((n) => n.startsWith(p)),
  );
  registerCompleter("/createchat", () => []);

  return {
    reload: (): void => {
      window.location.reload();
    },

    setConnectedPlayers: (namesJson: string): void => {
      try {
        window.mcState.connectedPlayers = JSON.parse(namesJson);
      } catch {
        /* keep empty */
      }
    },

    setNpcNames: (namesJson: string): void => {
      try {
        window.mcState.npcNames = JSON.parse(namesJson);
      } catch {
        /* keep empty */
      }
    },

    registerCompleter,
    registerServerCompleters,
  };
}
