import { useEffect, useMemo } from "react";
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
import { IngameMap } from "../game/components/IngameMap";
import { Craft } from "../game/components/Craft";
import { Trade } from "../game/components/Trade";
import { PlayerStatusBar } from "../game/components/PlayerStatusBar";
import { CombatTargetFrame } from "../game/components/CombatTargetFrame";
import { PlayerDownedOverlay } from "../game/components/PlayerDownedOverlay";
import { AttackPanel } from "../game/components/AttackPanel";
import { XpBar } from "../game/components/XpBar";
import { useGameContext } from "../game/GameContext";
import { getLastUser, clearLastPlayer } from "../lib/authStorage";
import { AggroIndicators } from "../game/components/AggroIndicators";
import { Statistics } from "../game/components/Statistics";
import { QuestJournal } from "../game/components/QuestJournal";
import { QuestTracker } from "../game/components/QuestTracker";

const resumePointerLock = () =>
  (
    (document.getElementById("renderCanvas") as HTMLCanvasElement | null)
      ?.requestPointerLock() as unknown as Promise<void>
  )?.catch(() => {});

export function GameScreen() {
  const {
    state,
    dispatch,
    consoleSubmittedRef,
    consoleStateRef,
    consoleInitialValueRef,
    consoleFocusRef,
    pendingLayoutUpdateRef,
    pendingPreferencesUpdateRef,
    pendingSlotUpdateRef,
    chunkDebugData,
  } = useGameContext();

  const activeLayout = resolveActiveLayout(state.layouts, state.activeLayout);

  const filteredAttackMeta = useMemo(() => {
    const charData = state.characterSyncData?.character;
    if (!charData || !state.classDefinitions) return state.attackMeta;
    const classDef = state.classDefinitions[charData.characterClass];
    if (!classDef) return {};
    const unlocked = new Set<string>();
    for (const [lvlStr, attacks] of Object.entries(classDef)) {
      if (parseInt(lvlStr) <= charData.level) {
        for (const { attack, level } of attacks) {
          unlocked.add(`${attack}:${level}`);
        }
      }
    }
    return Object.fromEntries(Object.entries(state.attackMeta).filter(([key]) => unlocked.has(key)));
  }, [state.attackMeta, state.characterSyncData, state.classDefinitions]);

  const handlePreferencesSave = (payload: {
    subscribedChannels: ChannelSubscription[];
    disabledCommands: string[];
    shadersEnabled: boolean;
    dynamicFogEnabled?: boolean;
    animatedFavicon: boolean;
    chunkDebugVisible: boolean;
    statisticsVisible: boolean;
    keybindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
    fieldOfView?: number;
  }) => {
    dispatch({
      type: "preferences_save",
      data: {
        ...payload,
        dynamicFogEnabled: payload.dynamicFogEnabled ?? state.preferences?.dynamicFogEnabled ?? true,
        fieldOfView: payload.fieldOfView ?? state.preferences?.fieldOfView ?? 70,
        macros: state.preferences?.macros ?? {},
        macroIcons: state.preferences?.macroIcons,
      },
    });
    if (window.mcState) {
      window.mcState.bindings = payload.keybindings;
      window.mcState.customCommands = payload.customCommands;
      (window.mcState as any).dynamicFogEnabled = payload.dynamicFogEnabled ?? true;
    }
    window.mc.applyFaviconPref?.(payload.animatedFavicon);
    pendingPreferencesUpdateRef.current = JSON.stringify(payload);
  };

  const handleMacrosSave = (
    macros: Record<string, string>,
    customCommands: Record<string, string[]>,
    macroIcons: Record<string, string>,
  ) => {
    const prefs = state.preferences;
    if (!prefs) return;
    dispatch({
      type: "preferences_save",
      data: {
        subscribedChannels: prefs.subscribedChannels,
        disabledCommands: prefs.disabledCommands,
        shadersEnabled: prefs.shadersEnabled,
        dynamicFogEnabled: prefs.dynamicFogEnabled ?? true,
        animatedFavicon: prefs.animatedFavicon ?? true,
        chunkDebugVisible: prefs.chunkDebugVisible ?? false,
        statisticsVisible: prefs.statisticsVisible ?? false,
        keybindings: prefs.keybindings || {},
        customCommands,
        macros,
        macroIcons,
        fieldOfView: prefs.fieldOfView,
      },
    });
    if (window.mcState) {
      window.mcState.macros = macros;
      window.mcState.customCommands = customCommands;
    }
    pendingPreferencesUpdateRef.current = JSON.stringify({
      subscribedChannels: prefs.subscribedChannels,
      disabledCommands: prefs.disabledCommands,
      shadersEnabled: prefs.shadersEnabled,
      dynamicFogEnabled: prefs.dynamicFogEnabled ?? true,
      animatedFavicon: prefs.animatedFavicon ?? true,
      chunkDebugVisible: prefs.chunkDebugVisible ?? false,
      statisticsVisible: prefs.statisticsVisible ?? false,
      keybindings: prefs.keybindings || {},
      customCommands,
      macros,
      macroIcons,
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
          <HUD data={state.hud} layoutStyle={widgetStyle(activeLayout, "HUD")} />
          {(state.preferences?.statisticsVisible ?? false) && (
            <Statistics data={state.hud} layoutStyle={widgetStyle(activeLayout, "STATISTICS")} />
          )}
          {state.ingameMapVisible && (
            <IngameMap
              playerX={state.hud?.x}
              playerZ={state.hud?.z}
              layoutStyle={widgetStyle(activeLayout, "INGAME_MAP")}
            />
          )}
          <ShortcutBar
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            attackMeta={filteredAttackMeta}
            spellMeta={state.spellMeta}
            slots={state.shortcutBar}
            selectedSlot={state.selectedSlot}
            macros={state.preferences?.macros ?? {}}
            macroIcons={state.preferences?.macroIcons ?? {}}
            onSlotDrop={(slot, content) => {
              pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
            }}
            layoutStyle={widgetStyle(activeLayout, "SHORTCUT_BAR")}
            playerStatus={state.playerStatus ?? undefined}
          />
          <AttackPanel
            attackMeta={filteredAttackMeta}
            spellMeta={state.spellMeta}
            layoutStyle={widgetStyle(activeLayout, "ATTACK_PANEL")}
            pinnedMacros={state.preferences?.customCommands?.["__pinned_macros__"] ?? []}
            playerStatus={state.playerStatus ?? undefined}
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
            <PlayerStatusBar
              status={state.playerStatus}
              npcProximity={state.npcProximity}
              layoutStyle={widgetStyle(activeLayout, "PLAYER_STATUS")}
            />
          )}
          <AggroIndicators
            npcProximity={state.npcProximity}
            layoutStyle={widgetStyle(activeLayout, "AGGRO_INDICATORS")}
          />
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
            focusRef={consoleFocusRef}
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
          <CodexModal
            open={state.codexOpen}
            onClose={() => {
              dispatch({ type: "codex_close" });
              resumePointerLock();
            }}
          />
          <Craft
            open={state.craftOpen}
            onClose={() => {
              dispatch({ type: "craft_close" });
              resumePointerLock();
            }}
            recipes={state.craftRecipes}
            knownRecipes={state.craftKnownRecipes}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Trade
            open={state.trade !== null}
            tradeId={state.trade?.tradeId ?? null}
            otherPlayer={state.trade?.otherPlayer ?? ""}
            myOffer={state.trade?.myOffer ?? {}}
            theirOffer={state.trade?.theirOffer ?? {}}
            myAccepted={state.trade?.myAccepted ?? false}
            theirAccepted={state.trade?.theirAccepted ?? false}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            onClose={(tradeId) => {
              if (tradeId) consoleSubmittedRef.current = `/tradecancel ${tradeId}`;
              dispatch({ type: "trade_close" });
              resumePointerLock();
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
            attackMeta={filteredAttackMeta}
            spellMeta={state.spellMeta}
            onClose={() => {
              dispatch({ type: "character_close" });
              resumePointerLock();
            }}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Preferences
            open={state.preferencesOpen}
            preferences={state.preferences}
            onSave={handlePreferencesSave}
            onClose={() => {
              dispatch({ type: "preferences_hide" });
              resumePointerLock();
            }}
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
            items={[
              {
                label: "Craft",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "craft_open" });
                },
              },
              {
                label: "Quests",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "quest_journal_open" });
                },
              },
              {
                label: "Character",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "character_open" });
                },
              },
              {
                label: "Map",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "ingame_map_toggle" });
                },
              },
              {
                label: "Macros",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "macro_editor_open" });
                },
              },
              {
                label: "Preferences",
                callback: () => {
                  dispatch({ type: "pause_menu_hide" });
                  dispatch({ type: "preferences_show" });
                },
              },
              {
                label: "Disconnect",
                variant: "danger",
                callback: () => {
                  window.mcState.intentionalDisconnect = true;
                  consoleSubmittedRef.current = "/disconnect";
                  dispatch({ type: "pause_menu_hide" });
                },
              },
              {
                label: "Refresh",
                variant: "outline",
                callback: () => {
                  const controller = navigator.serviceWorker?.controller ?? null;
                  if (controller) {
                    controller.postMessage({ type: "FORCE_UPDATE" });
                  } else {
                    window.location.reload();
                  }
                },
              },
            ]}
          />
          <MacroEditor
            open={state.macroEditorOpen}
            macros={state.preferences?.macros ?? {}}
            macroIcons={state.preferences?.macroIcons ?? {}}
            customCommands={state.preferences?.customCommands ?? {}}
            commands={state.preferences?.commands ?? []}
            attackKeys={Object.keys(filteredAttackMeta)}
            onSave={handleMacrosSave}
            onClose={() => {
              dispatch({ type: "macro_editor_close" });
              resumePointerLock();
            }}
          />
          <QuestJournal
            open={state.questJournalOpen}
            quests={state.quests}
            onClose={() => {
              dispatch({ type: "quest_journal_close" });
              resumePointerLock();
            }}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <QuestTracker
            visible={state.questTrackerVisible}
            quests={state.quests}
            layoutStyle={widgetStyle(activeLayout, "QUEST_TRACKER")}
          />
        </>
      )}
      <LoadingOverlay progress={state.chunkLoading} />
    </>
  );
}
