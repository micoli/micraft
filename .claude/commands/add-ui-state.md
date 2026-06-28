# add-ui-state

Add new state flowing from Kotlin to the React UI.

## Checklist

1. `McUiState` (Kotlin) — add field, expose as `StateFlow`
2. `WebUiBridge` — collect the flow → call `BabylonBindings.jsXxx(json)`
3. `BabylonBindingsUtil.kt` — `fun jsXxx(v: String) = js("mcXxx(v)")`
4. `GameUI.tsx` — `(window as any).mcXxx = (v) => dispatch({ type: 'xxx', data: ... })`
5. `GameUI.tsx` reducer — add case for `'xxx'`
