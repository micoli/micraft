import { actionRegistry, UiState } from "./UIStateRegistry";
import { createDispatch, createReducer, RegistryAction, RegistryDispatch } from "../lib/redux";

export type { UiState };
export type UiDispatch = RegistryDispatch<typeof actionRegistry>;

export const reducer = createReducer<UiState, typeof actionRegistry>(actionRegistry);

export function makeUiDispatch(reactDispatch: (action: RegistryAction<typeof actionRegistry>) => void): UiDispatch {
  return createDispatch(reactDispatch);
}
