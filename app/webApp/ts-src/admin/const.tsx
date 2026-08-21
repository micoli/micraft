import type { TranslationKey } from "./i18n";
import { ICONS } from "../primitives/icons";
import { StatusPage } from "./pages/status/StatusPage";
import { UsersPage } from "./pages/user/UsersPage";
import { PlayersPage } from "./pages/players/PlayersPage";
import { NpcsPage } from "./pages/npcs/NpcsPage";
import { ClassesPage } from "./pages/classes/ClassesPage";
import { ConfigEditorPage } from "./pages/configEditor/ConfigEditorPage";
import { WorldsPage } from "./pages/worlds/WorldsPage";
import { GameAssetsViewerPage } from "./pages/gameAssetsViewer/GameAssetsViewerPage";
import { CodexPage } from "./pages/codex/CodexPage";
import { InstancesPage } from "./pages/instance/InstancesPage";
import { ScenesPage } from "./pages/scene/ScenesPage";
import { WorldSimulatorPage } from "./pages/worldSimulator/WorldSimulatorPage";

export const ROUTES = [
  { path: "/admin", page: <StatusPage /> },
  { path: "/admin/users", page: <UsersPage /> },
  { path: "/admin/players", page: <PlayersPage /> },
  { path: "/admin/players/:playerName", page: <PlayersPage /> },
  { path: "/admin/npcs", page: <NpcsPage /> },
  { path: "/admin/classes", page: <ClassesPage /> },
  { path: "/admin/config", page: <ConfigEditorPage /> },
  { path: "/admin/worlds", page: <WorldsPage /> },
  { path: "/admin/game-assets", page: <GameAssetsViewerPage /> },
  { path: "/admin/codex", page: <CodexPage /> },
  { path: "/admin/codex/:tab", page: <CodexPage /> },
  { path: "/admin/codex/:tab/:itemKey", page: <CodexPage /> },
  { path: "/admin/instances", page: <InstancesPage /> },
  { path: "/admin/instances/:id", page: <InstancesPage /> },
  { path: "/admin/scenes", page: <ScenesPage /> },
  { path: "/admin/scenes/:id", page: <ScenesPage /> },
  { path: "/admin/world-simulator", page: <WorldSimulatorPage /> },
];
export interface NavItem {
  path: string;
  labelKey: TranslationKey;
  pageLabelKey: TranslationKey;
  icon: string;
  exact?: boolean;
}

export const NAV: NavItem[] = [
  { path: "/admin", labelKey: "nav.status", pageLabelKey: "page.status", icon: ICONS.status, exact: true },
  { path: "/admin/worlds", labelKey: "nav.worlds", pageLabelKey: "page.worlds", icon: ICONS.worlds },
  { path: "/admin/users", labelKey: "nav.users", pageLabelKey: "page.users", icon: ICONS.users },
  { path: "/admin/players", labelKey: "nav.players", pageLabelKey: "page.players", icon: ICONS.players },
  { path: "/admin/npcs", labelKey: "nav.npcs", pageLabelKey: "page.npcs", icon: ICONS.npcs },
  { path: "/admin/classes", labelKey: "nav.classes", pageLabelKey: "page.classes", icon: ICONS.classes },
  { path: "/admin/config", labelKey: "nav.config", pageLabelKey: "page.config", icon: ICONS.config },
  { path: "/admin/instances", labelKey: "nav.instances", pageLabelKey: "page.instances", icon: ICONS.instances },
  { path: "/admin/scenes", labelKey: "nav.scenes", pageLabelKey: "page.scenes", icon: ICONS.scenes },
  {
    path: "/admin/codex",
    labelKey: "nav.codex",
    pageLabelKey: "page.codex",
    icon: ICONS.codex,
  },
  {
    path: "/admin/world-simulator",
    labelKey: "nav.worldSimulator",
    pageLabelKey: "page.worldSimulator",
    icon: ICONS.simulator,
  },
  {
    path: "/admin/game-assets",
    labelKey: "nav.gameAssets",
    pageLabelKey: "page.gameAssets",
    icon: ICONS.gameAssets,
  },
];

/** Nav item a pathname belongs to — a tab's identity is this, not the raw (param-bearing) pathname. */
export function resolveNavItem(pathname: string): NavItem | undefined {
  return (
    NAV.find((item) => item.path === pathname) ??
    NAV.find((item) => item.path !== "/admin" && pathname.startsWith(item.path))
  );
}
