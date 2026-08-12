import type { TranslationKey } from "./i18n";
import { ICONS } from "../primitives/icons";

export const NAV: { path: string; labelKey: TranslationKey; icon: string; exact?: boolean }[] = [
  { path: "/admin", labelKey: "nav.status", icon: ICONS.status, exact: true },
  { path: "/admin/users", labelKey: "nav.users", icon: ICONS.users },
  { path: "/admin/players", labelKey: "nav.players", icon: ICONS.players },
  { path: "/admin/npcs", labelKey: "nav.npcs", icon: ICONS.npcs },
  { path: "/admin/classes", labelKey: "nav.classes", icon: ICONS.classes },
  { path: "/admin/config", labelKey: "nav.config", icon: ICONS.config },
  { path: "/admin/worlds", labelKey: "nav.worlds", icon: ICONS.worlds },
  { path: "/admin/game-assets", labelKey: "nav.gameAssets", icon: ICONS.gameAssets },
  { path: "/admin/administration", labelKey: "nav.administration", icon: ICONS.administration },
  { path: "/admin/instances", labelKey: "nav.instances", icon: ICONS.instances },
  { path: "/admin/world-simulator", labelKey: "nav.worldSimulator", icon: ICONS.simulator },
];

export const PAGE_LABEL_KEYS: Record<string, TranslationKey> = {
  "/admin": "page.status",
  "/admin/users": "page.users",
  "/admin/players": "page.players",
  "/admin/npcs": "page.npcs",
  "/admin/classes": "page.classes",
  "/admin/config": "page.config",
  "/admin/worlds": "page.worlds",
  "/admin/game-assets": "page.gameAssets",
  "/admin/administration": "page.administration",
  "/admin/instances": "page.instances",
  "/admin/world-simulator": "page.worldSimulator",
};
