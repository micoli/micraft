# add-keybinding

Add a new keybinding action (keyboard shortcut) that players can configure in Preferences.

Requires: action name (snake_case), default key(s) (e.g. `KeyJ`), group (`movement` | `combat` | `flight` | `ui` | `hotbar`), and what the action does in the client.

---

## Checklist

### 1. Default key mapping — server source of truth

`resources/config/keybindings.yaml` — add under the right section:

```yaml
ui:
  my_action: [KeyJ]
```

This is what `defaultKeyBindings()` returns and what the Preferences panel shows.

### 2. TS default bindings (fallback before server responds)

`app/webApp/ts-src/game/input/keyboard.ts` — add to `MC_DEFAULT_BINDINGS`:

```typescript
my_action: ["KeyJ"],
```

### 3. Keydown event dispatch — **mandatory, easy to miss**

`app/webApp/ts-src/game/input/keyboard.ts` — single-key actions are **hardcoded** in the keydown handler (lines ~188–203). Add after the last `combat_attack` line:

```typescript
if (b.my_action?.some((k) => matchesEvent(k, e))) window.mcState.events.push("my_action");
```

Without this the key press is never turned into an event, even if the binding is correctly declared. This is the most common missing step.

### 4. Preferences panel grouping

`app/webApp/ts-src/game/hooks/usePreferences.ts` — add action name to the right array in `ACTION_GROUPS`:

```typescript
ui: [
  ...
  "my_action",
],
```

Without this the action lands in "other" and won't appear grouped correctly.

### 5. McBindings type (only if action needs a new JS function)

If the action calls a new JS function via `mc.*`, add its signature to `McBindings` in `app/webApp/ts-src/global.d.ts`.

### 6. JS implementation (only if action needs a new JS function)

Create `app/webApp/ts-src/game/<feature>/<feature>.ts` exporting `registerXxx(): Pick<McBindings, "myFn">`.
Register in `app/webApp/ts-src/index.ts`:
```typescript
import { registerXxx } from "./game/<feature>/<feature>";
// ...
window.mc = {
  ...registerXxx(),
  // ...
}
```

### 7. Kotlin/Wasm interop (only if action needs a new JS function)

`app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/babylon/BabylonBindingsInput.kt`:

```kotlin
fun jsMyAction(scene: JsAny, camera: JsAny): Unit = js("mc.myFn(scene, camera)")
```

### 8. Client event handler

`app/webApp/src/wasmJsMain/kotlin/org/micoli/micraft/LocalPlayerController.kt` — in the `when { }` block inside the `jsConsumeEvents()` loop:

```kotlin
event == "my_action" -> jsMyAction(scene, camera)
// or, for a slash command shortcut:
event == "my_action" -> outMessages.trySend(ClientMessage.Command("/mycommand"))
```

### 9. Server HTTP endpoint (only if action POSTs data to server)

Create `server/src/main/kotlin/org/micoli/micraft/http/MyActionController.kt`:
- Pattern: see `ScreenshotController.kt`
- Register in `Application.kt`: `MyActionController(dataPath).register(this)`
- Add test in `server/src/test/kotlin/org/micoli/micraft/http/MyActionControllerTest.kt`

### 10. keybindings.yaml in data/config (optional override reference)

`data/config/keybindings.yaml` — add commented reference so operators know the key exists:

```yaml
# ui:
  # my_action: - "KeyJ"
```

---

## After changes

```bash
make dc CMD="./gradlew :spotlessApply"
make npm-format
make dc CMD="./gradlew :server:test"
touch run.lock         # restart server
make build-wasm        # rebuild TS/Wasm bundle
```
