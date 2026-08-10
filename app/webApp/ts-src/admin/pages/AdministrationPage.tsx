import { useState } from "react";
import { useT, type TranslationKey } from "../i18n";
import { BlocksTab } from "./BlocksTab";
import { ItemsTab } from "./ItemsTab";
import { BestiaryTab } from "./BestiaryTab";
import { SkinsTab } from "./SkinsTab";
import { AnimationsTab } from "./AnimationsTab";

type AdminTab = "blocks" | "items" | "bestiary" | "skins" | "animations";

const TAB_LABEL_KEYS: Record<AdminTab, TranslationKey> = {
  blocks: "administration.tabBlocks",
  items: "administration.tabItems",
  bestiary: "administration.tabBestiary",
  skins: "administration.tabSkins",
  animations: "administration.tabAnimations",
};

export function AdministrationPage() {
  const t = useT();
  const [tab, setTab] = useState<AdminTab>("blocks");

  return (
    <div className="flex flex-col h-full overflow-hidden -m-6">
      {/* Tab bar */}
      <div className="shrink-0 flex border-b border-[#2E3A4E] px-6 bg-[#1A222C]">
        {(Object.keys(TAB_LABEL_KEYS) as AdminTab[]).map((key) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
              tab === key ? "border-[#3C50E0] text-white" : "border-transparent text-[#8A99AF] hover:text-white"
            }`}
          >
            {t(TAB_LABEL_KEYS[key])}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-hidden">
        {tab === "blocks" && <BlocksTab />}
        {tab === "items" && <ItemsTab />}
        {tab === "bestiary" && <BestiaryTab />}
        {tab === "skins" && <SkinsTab />}
        {tab === "animations" && <AnimationsTab />}
      </div>
    </div>
  );
}
