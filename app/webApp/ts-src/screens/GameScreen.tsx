import { useMemo, useEffect, useRef, useState } from "react";
import { useParams, useNavigate } from "react-router";
import { startPreloading } from "../game/shared/blockPreviewCache";
import { getStoredToken, getLastLang, getAccountEmail, getLastPlayer } from "../lib/authStorage";
import { GameLayout, ChannelSubscription } from "../game/types";
import { NpcDialog } from "../game/npc/NpcDialog";
import { NpcShopDialog } from "../game/npc/NpcShopDialog";
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
import { getWidget, resolveActiveLayout, widgetStyle, WIDGET_REGISTRY } from "../game/layout/LayoutEngine";
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
import { AggroIndicators } from "../game/components/AggroIndicators";
import { Statistics } from "../game/components/Statistics";
import { QuestJournal } from "../game/components/QuestJournal";
import { QuestTracker } from "../game/components/QuestTracker";
import { BuffBar } from "../game/components/BuffBar";
import { MailboxOverlay } from "../game/overlays/MailboxOverlay";
import { CreativeBlockPanel } from "../game/components/CreativeBlockPanel";
import { setCreativeSelectedItem } from "../game/lib/creativeMode";

const resumePointerLock = () => {
  if (window.mcState.editMode === "creative") return;
  (
    (
      document.getElementById("renderCanvas") as HTMLCanvasElement | null
    )?.requestPointerLock() as unknown as Promise<void>
  )?.catch(() => {});
};

export function GameScreen() {
  const {
    state,
    dispatch,
    loginResultRef,
    consoleSubmittedRef,
    consoleStateRef,
    consoleInitialValueRef,
    consoleFocusRef,
    pendingLayoutUpdateRef,
    pendingPreferencesUpdateRef,
    pendingSlotUpdateRef,
    chunkDebugData,
  } = useGameContext();

  const { accountEmail: encodedEmail } = useParams<{ accountEmail: string; charId: string }>();
  const navigate = useNavigate();
  const reconnectAttempted = useRef(false);
  const [creativeSelectedItem, setCreativeSelectedItemState] = useState<string | null>(null);

  useEffect(() => {
    startPreloading();
  }, []);

  useEffect(() => {
    const zone = state.adminZone;
    if (!zone) {
      window.mc.hideZoneBounds?.();
      return;
    }
    const scene = window.mcState.engine?.scenes?.[0];
    if (scene) window.mc.showZoneBounds?.(scene, zone.yMin, zone.yMax, JSON.stringify(zone.chunks));
    return () => window.mc.hideZoneBounds?.();
  }, [state.adminZone]);

  useEffect(() => {
    if (reconnectAttempted.current || !encodedEmail) return;
    reconnectAttempted.current = true;
    if (loginResultRef.current) return;
    const email = decodeURIComponent(encodedEmail);
    const token = getStoredToken();
    const lang = getLastLang();
    const accountKey = getAccountEmail() || email;
    const charName = getLastPlayer(accountKey) || getLastPlayer(email);
    if (charName) {
      loginResultRef.current = `${email}\t${charName}\t${lang}\t${token}`;
    } else {
      navigate("/auth");
    }
  }, [encodedEmail, loginResultRef, navigate]);

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

  const setPendingPrefs = (partial: Partial<import("../game/types").PreferencesData>) => {
    if (!state.preferences) return;
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { knownChannels, commands, defaultKeybindings, macroIcons, ...serverFields } = {
      ...state.preferences,
      ...partial,
    };
    pendingPreferencesUpdateRef.current = JSON.stringify(serverFields);
  };

  const handlePreferencesSave = (payload: {
    subscribedChannels: ChannelSubscription[];
    disabledCommands: string[];
    shadersEnabled: boolean;
    dynamicFogEnabled?: boolean;
    animatedFavicon: boolean;
    chunkDebugVisible: boolean;
    statisticsVisible: boolean;
    attackPanelVisible: boolean;
    autoTargetEnabled: boolean;
    keybindings: Record<string, string[]>;
    customCommands: Record<string, string[]>;
    fieldOfView?: number;
  }) => {
    dispatch("preferences_save", {
      data: {
        ...payload,
        dynamicFogEnabled: payload.dynamicFogEnabled ?? state.preferences?.dynamicFogEnabled ?? true,
        fieldOfView: payload.fieldOfView ?? state.preferences?.fieldOfView ?? 70,
        macros: state.preferences?.macros ?? {},
        macroIcons: state.preferences?.macroIcons,
        attackPanelVisible: payload.attackPanelVisible,
        autoTargetEnabled: payload.autoTargetEnabled,
      },
    });
    if (window.mcState) {
      window.mcState.bindings = payload.keybindings;
      window.mcState.customCommands = payload.customCommands;
      window.mcState.dynamicFogEnabled = payload.dynamicFogEnabled ?? true;
    }
    window.mc.applyFaviconPref?.(payload.animatedFavicon);
    setPendingPrefs(payload);
  };

  const handleInventorySortChange = (sortA: string, sortB: string) => {
    if (!state.preferences) return;
    dispatch("preferences_save", { data: { ...state.preferences, inventorySortA: sortA, inventorySortB: sortB } });
    setPendingPrefs({ inventorySortA: sortA, inventorySortB: sortB });
  };

  const handleMacrosSave = (
    macros: Record<string, string>,
    customCommands: Record<string, string[]>,
    macroIcons: Record<string, string>,
  ) => {
    if (!state.preferences) return;
    const partial = { macros, customCommands, macroIcons };
    dispatch("preferences_save", { data: { ...state.preferences, ...partial } });
    if (window.mcState) {
      window.mcState.macros = macros;
      window.mcState.customCommands = customCommands;
    }
    setPendingPrefs(partial);
    dispatch("macro_editor_close");
  };

  const handleLayoutSave = (layouts: GameLayout[], newActiveLayout: string) => {
    dispatch("layout_editor_save", { layouts, activeLayout: newActiveLayout });
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
        className={
          state.adminZone
            ? "border-2 border-red-500 shadow-[0_0_10px_rgba(255,0,0,0.6)] rounded-md overflow-hidden"
            : "border-2 border-white/25 shadow-[0_2px_8px_rgba(0,0,0,0.5)] rounded-md overflow-hidden"
        }
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
              playerYaw={state.hud?.yaw}
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
            currentPage={state.currentPage}
            nonEmptyPages={state.nonEmptyPages}
            macros={state.preferences?.macros ?? {}}
            macroIcons={state.preferences?.macroIcons ?? {}}
            onSlotDrop={(slot, content) => {
              pendingSlotUpdateRef.current.push(JSON.stringify({ slot, content: content ?? null }));
            }}
            layoutStyle={widgetStyle(activeLayout, "SHORTCUT_BAR")}
            playerStatus={state.playerStatus ?? undefined}
          />
          {(state.preferences?.attackPanelVisible ?? false) && (
            <AttackPanel
              attackMeta={filteredAttackMeta}
              spellMeta={state.spellMeta}
              layoutStyle={widgetStyle(activeLayout, "ATTACK_PANEL")}
              pinnedMacros={state.preferences?.customCommands?.["__pinned_macros__"] ?? []}
              playerStatus={state.playerStatus ?? undefined}
            />
          )}
          <Inventory
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            visible={state.hotbarVisible}
            layoutStyle={widgetStyle(activeLayout, "INVENTORY")}
            preferences={state.preferences}
            onSortChange={handleInventorySortChange}
            wallet={state.wallet}
          />
          <ServerLog
            logs={state.logs}
            visible={state.logVisible || state.consoleOpen}
            subscribedChannels={state.subscribedChannels}
            activeChannel={state.activeChannel}
            unreadChannels={state.unreadChannels}
            onChannelSelect={(ch) => {
              dispatch("active_channel_select", { channel: ch });
              window.mcState.activeChannel = ch;
            }}
            layoutStyle={widgetStyle(activeLayout, "CHAT_HISTORY")}
          />
          <CreativeBlockPanel
            visible={state.editMode === "creative"}
            selectedItem={creativeSelectedItem}
            onSelectItem={(item) => {
              setCreativeSelectedItemState(item);
              setCreativeSelectedItem(item);
            }}
          />
          <Notifications notif={state.notif?.msg ? state.notif : null} />
          {state.healthBarVisible && state.playerStatus && (
            <PlayerStatusBar
              status={state.playerStatus}
              godMode={state.godMode}
              npcProximity={state.npcProximity}
              layoutStyle={widgetStyle(activeLayout, "PLAYER_STATUS")}
            />
          )}
          <BuffBar effects={state.activeEffects} layoutStyle={widgetStyle(activeLayout, "BUFF_BAR")} />
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
            onClose={() => dispatch("console_hide")}
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
            onClose={() => dispatch("layout_editor_hide")}
          />
          {state.npcDialog?.type === "seller" ? (
            <NpcShopDialog
              data={state.npcDialog}
              wallet={state.wallet}
              itemMeta={state.itemMeta}
              inventory={state.inventory}
              onClose={() => dispatch("npc_dialog_close")}
              onBuy={(npcId, orders) => {
                for (const { itemType, qty } of orders)
                  window.mcState.events.push(`cmd:/npcbuy ${npcId} ${itemType} ${qty}`);
              }}
              onSell={(npcId, orders) => {
                for (const { itemType, qty } of orders)
                  window.mcState.events.push(`cmd:/npcsell ${npcId} ${itemType} ${qty}`);
              }}
            />
          ) : (
            <NpcDialog data={state.npcDialog} onClose={() => dispatch("npc_dialog_close")} />
          )}
          <CodexModal
            open={state.codexOpen}
            onClose={() => {
              dispatch("codex_close");
              resumePointerLock();
            }}
          />
          <Craft
            open={state.craftOpen}
            onClose={() => {
              dispatch("craft_close");
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
              dispatch("trade_close");
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
              dispatch("character_close");
              resumePointerLock();
            }}
            onCommand={(cmd) => {
              consoleSubmittedRef.current = cmd;
            }}
          />
          <Preferences
            open={state.preferencesOpen}
            preferences={state.preferences}
            initialTab={state.preferencesTab}
            onSave={handlePreferencesSave}
            onClose={() => {
              dispatch("preferences_hide");
              resumePointerLock();
            }}
          />
          <PauseMenu
            open={state.pauseMenuOpen}
            onClose={() => {
              dispatch("pause_menu_hide");
              resumePointerLock();
            }}
            items={[
              {
                icon: "🛠",
                label: "Craft",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("craft_open");
                },
              },
              {
                icon: "📚",
                label: "Quests",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("quest_journal_open");
                },
              },
              {
                icon: "✉",
                label:
                  state.mails.filter((m) => !m.seen).length > 0
                    ? `Mail (${state.mails.filter((m) => !m.seen).length})`
                    : "Mail",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("mailbox_open");
                },
              },
              {
                icon: "🎭",
                label: "Character",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("character_open");
                },
              },
              {
                icon: "📓",
                label: "Codex",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("codex_open");
                },
              },
              {
                icon: "🗺️",
                label: "Map",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("ingame_map_toggle");
                },
              },
              {
                icon: "💻",
                label: "Macros",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("macro_editor_open");
                },
              },
              {
                icon: "¶",
                label: "Preferences",
                callback: () => {
                  dispatch("pause_menu_hide");
                  dispatch("preferences_show", undefined);
                },
              },
              {
                icon: "🔄",
                label: "Refresh",
                variant: "outline",
                callback: () => {
                  window.location.reload();
                },
              },
              {
                icon: "🚫",
                label: "Disconnect",
                variant: "danger",
                callback: () => {
                  window.mcState.intentionalDisconnect = true;
                  consoleSubmittedRef.current = "/disconnect";
                  dispatch("pause_menu_hide");
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
              dispatch("macro_editor_close");
              resumePointerLock();
            }}
          />
          <QuestJournal
            open={state.questJournalOpen}
            quests={state.quests}
            playerLevel={state.characterSyncData?.character?.level ?? 1}
            onClose={() => {
              dispatch("quest_journal_close");
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
          <MailboxOverlay
            open={state.mailboxOpen}
            mails={state.mails}
            inventory={state.inventory}
            itemMeta={state.itemMeta}
            wallet={state.wallet}
            onClose={() => {
              dispatch("mailbox_close");
              resumePointerLock();
            }}
          />
        </>
      )}
      <LoadingOverlay progress={state.chunkLoading} />
    </>
  );
}
