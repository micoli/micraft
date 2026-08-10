import { type TranslationKey, useT } from "../../i18n";
import { useEffect, useState } from "react";
import { api, PlayerFile } from "../../api";
import { Link } from "react-router";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../../primitives/Tabs";
import { KeybindingsTab } from "./KeybindingsTab";
import { PreferencesTab } from "./PreferencesTab";
import { RpgTab } from "./RpgTab";

export function PlayerDetail({
  name,
  onBack,
  onRenamed,
}: {
  name: string;
  onBack: () => void;
  onRenamed: (newName: string) => void;
}) {
  const t = useT();
  const [file, setFile] = useState<PlayerFile | null>(null);
  const [errorKey, setErrorKey] = useState<TranslationKey | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [newName, setNewName] = useState(name);
  const [renameErr, setRenameErr] = useState<string | null>(null);

  useEffect(() => {
    setFile(null);
    setNewName(name);
    api.players
      .get(name)
      .then(setFile)
      .catch(() => setErrorKey("players.failedToLoad"));
  }, [name]);

  const doRename = async () => {
    if (!newName || newName === name) {
      setRenaming(false);
      return;
    }
    const r = await api.players.rename(name, newName);
    if (r.ok) {
      onRenamed(newName);
    } else {
      setRenameErr(t("players.renameFailed"));
    }
    setRenaming(false);
  };

  if (errorKey) return <div className="p-5 text-red-400 text-sm">{t(errorKey)}</div>;
  if (!file) return <div className="p-5 text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</div>;

  const hasRpg = !!file.state.characterData;

  return (
    <div>
      <div className="flex items-center gap-3 px-5 py-3.5 border-b border-[#2E3A4E]">
        <button onClick={onBack} className="text-[#4A5568] hover:text-white text-sm transition-colors">
          ←
        </button>
        {renaming ? (
          <div className="flex items-center gap-1.5 flex-1">
            <input
              autoFocus
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") doRename();
                if (e.key === "Escape") {
                  setRenaming(false);
                  setNewName(name);
                }
              }}
              className="bg-[#0E1726] border border-[#3C50E0] rounded px-2 py-0.5 text-sm text-white focus:outline-none"
            />
            <button onClick={doRename} className="text-xs text-[#3C50E0] hover:text-white transition-colors">
              ✓
            </button>
            <button
              onClick={() => {
                setRenaming(false);
                setNewName(name);
              }}
              className="text-xs text-[#4A5568] hover:text-white transition-colors"
            >
              ✕
            </button>
          </div>
        ) : (
          <>
            <span className="text-sm font-semibold text-white">{name}</span>
            <button
              onClick={() => setRenaming(true)}
              className="text-[10px] text-[#4A5568] hover:text-[#8A99AF] transition-colors"
            >
              ✎
            </button>
          </>
        )}
        {renameErr && <span className="text-xs text-red-400">{renameErr}</span>}
        <span className="text-[10px] font-medium bg-[#2E3A4E] text-[#8A99AF] px-2 py-0.5 rounded-full ml-auto">
          {hasRpg ? t("players.rpgClass", file.state.characterData!.characterClass) : t("players.classic")}
        </span>
        {file.state.email && (
          <Link
            to={`/admin/users?u=${encodeURIComponent(file.state.email)}`}
            className="text-[10px] text-[#818CF8] hover:text-white transition-colors font-mono truncate max-w-[180px]"
            title={t("players.owner", file.state.email)}
          >
            {file.state.email}
          </Link>
        )}
      </div>
      <Tabs defaultValue="prefs">
        <TabsList className="px-5 border-b border-[#2E3A4E] rounded-none bg-transparent gap-1">
          <TabsTrigger
            value="prefs"
            className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
          >
            {t("players.tabPreferences")}
          </TabsTrigger>
          <TabsTrigger
            value="kb"
            className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
          >
            {t("players.tabKeybindings")}
          </TabsTrigger>
          {hasRpg && (
            <TabsTrigger
              value="rpg"
              className="text-xs data-[state=active]:text-white data-[state=active]:border-b-2 data-[state=active]:border-[#3C50E0] rounded-none pb-2 px-1 text-[#8A99AF] transition-colors"
            >
              {t("players.tabRpg")}
            </TabsTrigger>
          )}
        </TabsList>
        <TabsContent value="prefs">
          <PreferencesTab
            file={file}
            onSave={async (prefs) => {
              const r = await api.players.savePreferences(name, prefs);
              if (!r.ok) throw new Error(t("common.saveFailed"));
            }}
          />
        </TabsContent>
        <TabsContent value="kb">
          <KeybindingsTab
            file={file}
            onSave={async (kb) => {
              const r = await api.players.saveKeybindings(name, kb);
              if (!r.ok) throw new Error(t("common.saveFailed"));
            }}
          />
        </TabsContent>
        {hasRpg && (
          <TabsContent value="rpg">
            <RpgTab
              file={file}
              onSave={async (rpg) => {
                const r = await api.players.saveRpg(name, rpg as Parameters<typeof api.players.saveRpg>[1]);
                if (!r.ok) throw new Error(t("common.saveFailed"));
              }}
            />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
