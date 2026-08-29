import type { Decorator } from "@storybook/react";

// Minimal window.mcState / window.mcT so HUD components that read globals render in isolation.
export function stubMcState(extra: Record<string, unknown> = {}): Decorator {
  return function WithMcState(Story) {
    const w = window as unknown as {
      mcState?: Record<string, unknown>;
      mcT?: (k: string) => string;
      mc?: Record<string, unknown>;
    };
    w.mc = w.mc ?? {};
    w.mcState = {
      events: [],
      playerName: "alice",
      playerId: "player-1",
      codexBlocks: [],
      codexItems: {},
      scenes: [],
      ...extra,
    };
    w.mcT = (k: string) => k;
    return <Story />;
  };
}
