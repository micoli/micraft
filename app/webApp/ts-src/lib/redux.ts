// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ActionRegistry<S> = Record<string, (state: S, ...args: any[]) => S>;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type RegistryPayload<R extends ActionRegistry<any>, K extends keyof R> = Parameters<R[K]>[1];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type RegistryDispatch<R extends ActionRegistry<any>> = <K extends keyof R>(
  type: K,
  ...args: [RegistryPayload<R, K>] extends [undefined] ? [] : [RegistryPayload<R, K>]
) => void;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type RegistryAction<R extends ActionRegistry<any>> = {
  [K in keyof R]: { type: K; payload: RegistryPayload<R, K> };
}[keyof R];

export function createReducer<S, R extends ActionRegistry<S>>(registry: R) {
  return (state: S, action: RegistryAction<R>): S =>
    (registry[action.type] as (s: S, p: unknown) => S)(state, (action as { type: string; payload: unknown }).payload);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function createDispatch<R extends ActionRegistry<any>>(
  reactDispatch: (action: RegistryAction<R>) => void,
): RegistryDispatch<R> {
  return (type, ...args) => reactDispatch({ type, payload: args[0] } as RegistryAction<R>);
}
