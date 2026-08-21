import type { TranslationKey } from "./i18n";
import { ICONS } from "../primitives/icons";

export const NAV: {
  path: string;
  labelKey: TranslationKey;
  pageLabelKey: TranslationKey;
  icon: string;
  exact?: boolean;
}[] = [
  { path: "/admin", labelKey: "nav.status", pageLabelKey: "page.status", icon: ICONS.status, exact: true },
  { path: "/admin/users", labelKey: "nav.users", pageLabelKey: "page.users", icon: ICONS.users },
  { path: "/admin/players", labelKey: "nav.players", pageLabelKey: "page.players", icon: ICONS.players },
  { path: "/admin/npcs", labelKey: "nav.npcs", pageLabelKey: "page.npcs", icon: ICONS.npcs },
  { path: "/admin/classes", labelKey: "nav.classes", pageLabelKey: "page.classes", icon: ICONS.classes },
  { path: "/admin/config", labelKey: "nav.config", pageLabelKey: "page.config", icon: ICONS.config },
  { path: "/admin/worlds", labelKey: "nav.worlds", pageLabelKey: "page.worlds", icon: ICONS.worlds },
  {
    path: "/admin/administration",
    labelKey: "nav.administration",
    pageLabelKey: "page.administration",
    icon: ICONS.administration,
  },
  { path: "/admin/instances", labelKey: "nav.instances", pageLabelKey: "page.instances", icon: ICONS.instances },
  { path: "/admin/scenes", labelKey: "nav.scenes", pageLabelKey: "page.scenes", icon: ICONS.scenes },
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
