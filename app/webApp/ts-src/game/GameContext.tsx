import { createContext, useContext } from "react";
import { UiState, UiDispatch } from "./UIReducer";
import { ChunkDebugData } from "./components/ChunkDebug";

export interface GameContextValue {
  state: UiState;
  dispatch: UiDispatch;
  loginResultRef: React.MutableRefObject<string>;
  consoleSubmittedRef: React.MutableRefObject<string | null>;
  consoleStateRef: React.MutableRefObject<{
    history: string[];
    histIdx: number;
    playerName: string;
    tabIdx: number;
    tabMatches: string[];
  }>;
  consoleInitialValueRef: React.MutableRefObject<string>;
  consoleFocusRef: React.MutableRefObject<boolean>;
  pendingLayoutUpdateRef: React.MutableRefObject<string>;
  pendingPreferencesUpdateRef: React.MutableRefObject<string>;
  pendingSlotUpdateRef: React.MutableRefObject<string[]>;
  chunkDebugData: ChunkDebugData | null;
}

export const GameContext = createContext<GameContextValue | null>(null);

export function useGameContext(): GameContextValue {
  const ctx = useContext(GameContext);
  if (!ctx) throw new Error("useGameContext must be used within GameUI");
  return ctx;
}
