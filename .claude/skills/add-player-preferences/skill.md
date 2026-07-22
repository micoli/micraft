---
name: add-player-preferences
description: Step-by-step checklist to add a new boolean player preference (persisted server-side, shown in Preferences dialog). Use when adding a new toggle to player settings.
---

# Adding a Player Preference (boolean)

Touch these locations in order. All paths relative to repo root.

> **Four pitfalls** — all cause "preference not restored" bugs:
> 1. **Manual `pendingPreferencesUpdateRef` JSON blobs** (3 places): server resets field to default on next trigger. See step 9.
> 2. **`handlePreferencesSave` inline param type**: must add field to both the type AND the dispatch data (TS build error otherwise). See step 9.
> 3. **`handleMacrosSave` `preferences_save` dispatch**: explicit field list — must add field or TS build fails (`PreferencesSaveData` requires it). See step 9.
> 4. **`PlayerState` constructor on connect** (`GameLoop.kt` ~line 992): explicit field list that does NOT use `saved.copy()`. New fields default silently — must add `myPref = saved?.myPref ?: <default>`. See step 10.

## 1. Domain — `core/src/commonMain/kotlin/org/micoli/micraft/player/Player.kt`
Add field to `PlayerState` with a sensible default:
```kotlin
val myPref: Boolean = true,
```

## 2. Protocol (client→server) — `core/src/commonMain/kotlin/org/micoli/micraft/protocol/ClientMessage.kt`
Add to `PreferencesUpdate`:
```kotlin
val myPref: Boolean = true,
```

## 3. Protocol (server→client) — `core/src/commonMain/kotlin/org/micoli/micraft/protocol/ServerMessage.kt`
Add to `PreferencesSync`:
```kotlin
val myPref: Boolean = true,
```

## 4. Server read — `server/src/main/kotlin/org/micoli/micraft/game/GameLoop.kt`
In `buildPreferencesSync(session)`:
```kotlin
myPref = session.state.myPref,
```

## 5. Server write — `server/src/main/kotlin/org/micoli/micraft/game/GameLoop.kt`
In `handlePreferencesUpdate(session, msg)` inside `session.state.copy(...)`:
```kotlin
myPref = msg.myPref,
```

## 6. TypeScript type — `app/webApp/ts-src/game/types.ts`
In `PreferencesData` interface:
```typescript
myPref: boolean;
```

## 7. Hook — `app/webApp/ts-src/game/hooks/usePreferences.ts`

a. Add to `SavePayload` interface:
```typescript
myPref: boolean;
```

b. Add local state:
```typescript
const [localMyPref, setLocalMyPref] = useState(true);
```

c. Init in `useEffect([open])` body:
```typescript
setLocalMyPref(preferences.myPref ?? true);
```

d. Include in `handleSave` payload:
```typescript
myPref: localMyPref,
```

e. Add to return object:
```typescript
localMyPref,
setLocalMyPref,
```

## 8. UI — `app/webApp/ts-src/game/components/Preferences.tsx`
Add to the checkbox array in the graphics tab (~line 185):
```typescript
{
  state: pref.localMyPref,
  setter: pref.setLocalMyPref,
  label: "My preference description",
},
```

## 9. Manual PreferencesUpdate blobs — **easy to miss, breaks persistence**

Three places build the `PreferencesUpdate` JSON manually (bypassing the Preferences dialog flow). Each must include the new field, or the server resets it to the default on next trigger:

**`app/webApp/ts-src/screens/GameScreen.tsx` — `handleMacrosSave`**
```typescript
pendingPreferencesUpdateRef.current = JSON.stringify({
  ...
  autoTargetEnabled: prefs.autoTargetEnabled ?? true,  // ← add here
  ...
});
```

**`app/webApp/ts-src/game/GameUI.tsx` — `window.mc.toggleStatistics`**
```typescript
pendingPreferencesUpdateRef.current = JSON.stringify({
  ...
  autoTargetEnabled: prefs.autoTargetEnabled ?? true,  // ← add here
  ...
});
```

**`app/webApp/ts-src/game/GameUI.tsx` — `window.mc.toggleAttackPanel`**
```typescript
pendingPreferencesUpdateRef.current = JSON.stringify({
  ...
  autoTargetEnabled: prefs.autoTargetEnabled ?? true,  // ← add here
  ...
});
```

**`app/webApp/ts-src/screens/GameScreen.tsx` — `handleMacrosSave` `preferences_save` dispatch**

This dispatch has an explicit field list (not a spread). Add the field or TS build fails:
```typescript
dispatch("preferences_save", {
  data: {
    ...
    autoTargetEnabled: prefs.autoTargetEnabled ?? true,  // ← add here
    ...
  },
});
```

---

The Preferences dialog's `handlePreferencesSave` uses `{ ...payload }` — BUT this is only safe at runtime. TypeScript's view of the spread is based on the inline parameter type, which does NOT include `myPref`. This means:
1. TypeScript marks the `preferences_save` dispatch data as missing `myPref` (since `PreferencesSaveData` requires it) → **build error**.
2. Even without a build error, the explicit overrides after `...payload` in the dispatch data object would shadow the runtime-spread value if any key matches.

**Fix**: add `myPref` to the inline param type AND pass it explicitly after the spread:

**`app/webApp/ts-src/screens/GameScreen.tsx` — `handlePreferencesSave`**
```typescript
const handlePreferencesSave = (payload: {
  ...
  myPref: boolean;        // ← add to inline param type
  ...
}) => {
  dispatch("preferences_save", {
    data: {
      ...payload,
      ...
      myPref: payload.myPref,  // ← add explicit pass-through
    },
  });
  pendingPreferencesUpdateRef.current = JSON.stringify({
    ...payload,             // already includes myPref via spread
  });
};
```

## 10. Session state on connect — `server/src/main/kotlin/org/micoli/micraft/game/GameLoop.kt`

**Critical**: the connect handler builds `PlayerState(...)` with an EXPLICIT field list (~line 992). It does NOT call `saved.copy(...)`. New `PlayerState` fields are silently absent → always use the Kotlin default on connect, ignoring saved YAML.

Add inside that constructor call:
```kotlin
myPref = saved?.myPref ?: true,
```

Search for the block: look for `val state = PlayerState(` near `val saved = persistence?.loadPlayerState(playerName)`.

---

## 11. Client Kotlin (if preference affects WASM logic)
If the pref drives client-side Kotlin behavior (e.g. in `LocalPlayerController`):

a. `app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/LocalPlayerController.kt` — add field:
```kotlin
var myPref: Boolean = true
```

b. `app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/game/GameClient.kt` — in the `PreferencesSync` handler (~line 631):
```kotlin
localController.myPref = msg.myPref
```

## Build & verify (grep check)
```bash
make dev-restart-server   # server changes
make build-wasm           # Kotlin/Wasm changes
make build-js             # TS/React changes
make dc CMD="./gradlew :server:test"  # confirm tests pass
```

Before building, confirm `myPref` appears in all manual JSON blobs AND both dispatch sites:
```bash
# Manual pendingPreferencesUpdateRef blobs (expect 3+ hits with myPref):
grep -n "pendingPreferencesUpdateRef.current = JSON" app/webApp/ts-src/game/GameUI.tsx app/webApp/ts-src/screens/GameScreen.tsx

# preferences_save dispatches (expect myPref in data for each):
grep -n "preferences_save" app/webApp/ts-src/screens/GameScreen.tsx
```

Open Preferences dialog in-game → Graphics tab → verify checkbox appears and persists across reconnect.
**Also test**: save pref as disabled → toggle statistics or open macro editor → reopen Preferences → checkbox must still show disabled (validates the manual blob paths).
