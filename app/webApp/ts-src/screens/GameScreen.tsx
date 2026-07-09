import { useEffect } from "react";
import { GameLayout, ChannelSubscription } from "../game/types";
import { NpcDialog } from "../game/npc/NpcDialog";
import { LoadingOverlay } from "../game/overlays/LoadingOverlay";
import { Preferences } from "../game/components/Preferences";
import { HUD } from "../game/components/HUD";
import { Inventory } from "../game/components/Inventory";
import { ShortcutBar } from "../game/components/ShortcutBar";
import { Console } from "../game/components/Console";
import { ServerLog } from "../game/components/ServerLog";
import { Notifications } from "../game/components/Notifications";
import { PauseMenu } from "../game/overlays/PauseMenu";
import { MacroEditor } from "../game/overlays/MacroEditor";
import { LayoutEditor } from "../game/layout/LayoutEditor";
import {
  defaultLayout,
  getWidget,
  resolveActiveLayout,
  widgetStyle,
  WIDGET_REGISTRY,
} from "../game/layout/LayoutEngine";
import { CodexModal } from "../game/components/CodexModal";
import { ChunkDebug } from "../game/components/ChunkDebug";
import { Character } from "../game/components/Character";
import { BiomeMap } from "../game/components/BiomeMap";
import { Craft } from "../game/components/Craft";
import { Trade } from "../game/components/Trade";
import { PlayerStatusBar } from "../game/components/PlayerStatusBar";
import { CombatTargetFrame } from "../game/components/CombatTargetFrame";
import { PlayerDownedOverlay } from "../game/components/PlayerDownedOverlay";
import { AttackPanel } from "../game/components/AttackPanel";
import { XpBar } from "../game/components/XpBar";
import { useGameContext } from "../game/GameContext";
import { getLastUser, clearLastPlayer } from "../lib/authStorage";

export function GameScreen() {
  const {
    state,
    dispatch,
    consoleSubmittedRef,
    consoleStateRef,
    consoleInitialValueRef,
    pendingLayoutUpdateRef,
    pendingPreferencesUpdateRef,
    pendingSlotUpdateRef,
    chunkDebugData,
  } = useGameContext();

  const activeLayout = resolveActiveLayout(state.layouts, state.activeLayout);

  const handlePreferencesSave = (payload: {
    subscribedChannels: ChannelSubscription[];
    disabledCommands: string[];
    shadersEnabled: boolean;
    animatedFavicon: boolean;
    chunkDebugVisible: boolean;
    keybindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
    fieldOfView?: number;
  }) => {
    dispatch({ type: "preferences_save", ...payload });
    if (window.mcState) {
      window.mcState.bindings = payload.keybindings;
      window.mcState.customCommands = payload.customCommands;
    }
    window.mc.applyFaviconPref?.(payload.animatedFavicon);
    pendingPreferencesUpdateRef.current = JSON.stringify(payload);
  };

  const handleMacrosSave = (macros: Record<string, string>, customCommands: Record<string, string[]>) => {
    const prefs = state.preferences;
    if (!prefs) return;
    dispatch({
      type: "preferences_save",
      subscribedChannels: prefs.subscribedChannels,
      disabledCommands: prefs.disabledCommands,
      shadersEnabled: prefs.shadersEnabled,
      animatedFavicon: prefs.animatedFavicon ?? true,
      chunkDebugVisible: prefs.chunkDebugVisible ?? false,
      keybindings: prefs.keybindings || {},
      customCommands,
      macros,
    });
    if (window.mcState) {
      window.mcState.macros = macros;
      window.mcState.customCommands = customCommands;
    }
    pendingPreferencesUpdateRef.current = JSON.stringify({
      subscribedChannels: prefs.subscribedChannels,
      disabledCommands: prefs.disabledCommands,
      shadersEnabled: prefs.shadersEnabled,
      animatedFavicon: prefs.animatedFavicon ?? true,
      chunkDebugVisible: prefs.chunkDebugVisible ?? false,
      keybindings: prefs.keybindings || {},
      customCommands,
      macros,
    });
    dispatch({ type: "macro_editor_close" });
  };

  const handleLayoutSave = (layouts: GameLayout[], newActiveLayout: string) => {
    dispatch({ type: "layout_editor_save", layouts, activeLayout: newActiveLayout });
    pendingLayoutUpdateRef.current = JSON.stringify({ layouts, activeLayout: newActiveLayout });
  };

  const minimapLayoutStyle: React.CSSProperties = {
    ...widgetStyle(activeLayout, "MINIMAP"),
    zIndex: 999,
    pointerEvents: "none",
  };

  return (
    <>
      <div
        id="mc-minimap-host"
        className="border-2 border-white/25 shadow-[0_2px_8px_rgba(0,0,0,0.5)] rounded-md overflow-hidden"
        style={{
          ...minimapLayoutStyle,
          display: state.disconnectMsg || state.chunkLoading ? "none" : undefined,
        }}
      />

      {!state.disconnectMsg &&
        !state.chunkLoading &&
        (() => {
          const mw = getWidget(activeLayout, "MINIMAP") ?? WIDGET_REGISTRY.find((w) => w.type === "MINIMAP")!;
          const chunks = chunkDebugData?.chunks ?? [];
          const total = Math.max(chunks.length, 1);
          const loaded = chunks.filter((c) => c.state === "loaded").length;
          const loading = chunks.filter((c) => c.state === "loading").length;
          const loadedPct = (loaded / total) * 100;
          const loadingPct = (loading / total) * 100;
          const missingPct = Math.max(0, 100 - loadedPct - loadingPct);
          return (
            <div
              style={{
                position: "fixed",
                left: `calc(${mw.x} / 48 * 100vw)`,
                top: `calc(${mw.y + mw.h} / 48 * 100vh)`,
                width: `calc(${mw.w} / 48 * 100vw)`,
                height: "5px",
                zIndex: 999,
                pointerEvents: "none",
                display: "flex",
                overflow: "hidden",
                borderRadius: "0 0 3px 3px",
              }}
            >
              <div style={{ width: `${loadedPct}%`, background: "#16a34a", transition: "width 150ms ease-out" }} />
              <div style={{ width: `${loadingPct}%`, background: "#ea580c", transition: "width 150ms ease-out" }} />
              <div style={{ width: `${missingPct}%`, background: "#7f1d1d" }} />
            </div>
          );
        })()}

      {!state.disconnectMsg && (state.chunkLoading || (state.preferences?.chunkDebugVisible ?? false)) && (
        <ChunkDebug data={chunkDebugData} layoutStyle={widgetStyle(activeLayout, "CHUNK_DEBUG")} />
      )}

      {!state.disconnectMsg && !state.chunkLoading && (
        <>
          <HUD data={state.hud} mode={state.hudMode} layoutStyle={widgetStyle(activeLayout, "HUD")} />
          {state.biomeMapVisible && (
            <BiomeMap
              playerX={state.hud?.x}
              playerZ={state.hud?.z}
              layoutStyle={widgetStyle(activeLayout, "INGAME_MAP")}
            />
          )}
          <ShortcutBar
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            attackMeta={state.attackMeta}
            slots={state.shortcutBar}
            selectedSlot={state.selectedSlot}
            macros={state.preferences?.macros ?? {}}
            onSlotDrop={(slot, content) => {
              pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
            }}
            layoutStyle={widgetStyle(activeLayout, "SHORTCUT_BAR")}
          />
          <AttackPanel
            attackMeta={state.attackMeta}
            layoutStyle={widgetStyle(activeLayout, "ATTACK_PANEL")}
            pinnedMacros={state.preferences?.customCommands?.["__pinned_macros__"] ?? []}
          />
          <Inventory
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            visible={state.hotbarVisible}
            layoutStyle={widgetStyle(activeLayout, "INVENTORY")}
          />
          <ServerLog
            logs={state.logs}
            visible={state.logVisible || state.consoleOpen}
            subscribedChannels={state.subscribedChannels}
            activeChannel={state.activeChannel}
            unreadChannels={state.unreadChannels}
            onChannelSelect={(ch) => {
              dispatch({ type: "active_channel_select", channel: ch });
              window.mcState.activeChannel = ch;
            }}
            layoutStyle={widgetStyle(activeLayout, "CHAT_HISTORY")}
          />
          <Notifications notif={state.notif?.msg ? state.notif : null} />
          {state.healthBarVisible && state.playerStatus && (
            <PlayerStatusBar status={state.playerStatus} layoutStyle={widgetStyle(activeLayout, "PLAYER_STATUS")} />
          )}
          {state.combatTarget && (
            <CombatTargetFrame target={state.combatTarget} layoutStyle={widgetStyle(activeLayout, "COMBAT_TARGET")} />
          )}
          <XpBar layoutStyle={widgetStyle(activeLayout, "XP_BAR")} />
          {state.playerDowned && <PlayerDownedOverlay />}
          <Console
            open={state.consoleOpen}
            onClose={() => dispatch({ type: "console_hide" })}
            submittedRef={consoleSubmittedRef}
            stateRef={consoleStateRef}
            initialValueRef={consoleInitialValueRef}
            layoutStyle={widgetStyle(activeLayout, "INPUT_BOX")}
          />
          <LayoutEditor
            open={state.layoutEditorOpen}
            layouts={state.layouts}
            activeLayout={state.activeLayout}
            onSave={handleLayoutSave}
            onClose={() => dispatch({ type: "layout_editor_hide" })}
          />
          <NpcDialog data={state.npcDialog} onClose={() => dispatch({ type: "npc_dialog_close" })} />
          <CodexModal open={state.codexOpen} onClose={() => dispatch({ type: "codex_close" })} />
          <Craft
            open={state.craftOpen}
            onClose={() => dispatch({ type: "craft_close" })}
            recipes={state.craftRecipes}
            knownRecipes={state.craftKnownRecipes}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Trade
            open={state.tradeOpen}
            tradeId={state.tradeId}
            otherPlayer={state.tradeOtherPlayer ?? ""}
            myOffer={state.tradeMyOffer}
            theirOffer={state.tradeTheirOffer}
            myAccepted={state.tradeMyAccepted}
            theirAccepted={state.tradeTheirAccepted}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onClose={(tradeId) => {
              if (tradeId) consoleSubmittedRef.current = `/tradecancel ${tradeId}`;
              dispatch({ type: "trade_close" });
            }}
            onAccept={(tradeId) => {
              consoleSubmittedRef.current = `/tradeaccept ${tradeId}`;
            }}
            onOffer={(tradeId, offer) => {
              consoleSubmittedRef.current = `/tradeoffer ${tradeId} ${JSON.stringify(offer)}`;
            }}
          />
          <Character
            open={state.characterOpen}
            characterSyncData={state.characterSyncData}
            onClose={() => dispatch({ type: "character_close" })}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Preferences
            open={state.preferencesOpen}
            preferences={state.preferences}
            onSave={handlePreferencesSave}
            onClose={() => dispatch({ type: "preferences_hide" })}
          />
          <PauseMenu
            open={state.pauseMenuOpen}
            onClose={() => {
              dispatch({ type: "pause_menu_hide" });
              (
                (
                  document.getElementById("renderCanvas") as HTMLCanvasElement | null
                )?.requestPointerLock() as unknown as Promise<void>
              )?.catch?.(() => {});
            }}
            onPreferences={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "preferences_show" });
            }}
            onMacros={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "macro_editor_open" });
            }}
            onCharacter={() => {
              dispatch({ type: "pause_menu_hide" });
              dispatch({ type: "character_open" });
            }}
            onDisconnect={() => {
              window.mcState.intentionalDisconnect = true;
              consoleSubmittedRef.current = "/disconnect";
              dispatch({ type: "pause_menu_hide" });
            }}
          />
          <MacroEditor
            open={state.macroEditorOpen}
            macros={state.preferences?.macros ?? {}}
            customCommands={state.preferences?.customCommands ?? {}}
            commands={state.preferences?.commands ?? []}
            attackKeys={Object.keys(state.attackMeta ?? {})}
            onSave={handleMacrosSave}
            onClose={() => dispatch({ type: "macro_editor_close" })}
          />
        </>
      )}
      <LoadingOverlay progress={state.chunkLoading} />
    </>
  );
}
