import { useEffect, useMemo, useState } from "react";
import { getApiAdminNpcs, postApiAdminNpcsByIdChatTest } from "../../../generated/api/requests";
import { NpcAdminDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { SidebarList } from "../SidebarList";
import { NpcChatDialog, NpcChatTestMessage } from "./NpcChatDialog";

export function NpcChatTestPage() {
  const t = useT();
  const [npcs, setNpcs] = useState<NpcAdminDto[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [conversations, setConversations] = useState<Record<string, NpcChatTestMessage[]>>({});
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [language, setLanguage] = useState("en");

  useEffect(() => {
    getApiAdminNpcs({ throwOnError: true }).then((r) => setNpcs(r.data));
  }, []);

  const chatNpcs = useMemo(() => (npcs ?? []).filter((n) => n.hasChat && !n.isDead), [npcs]);
  const selected = chatNpcs.find((n) => n.id === selectedId) ?? null;
  const messages = selected ? (conversations[selected.id] ?? []) : [];

  const send = async (text: string) => {
    if (!selected) return;
    setError(null);
    const history = messages.map((m) => ({ role: m.role === "user" ? "user" : "assistant", content: m.text }));
    setConversations((prev) => ({
      ...prev,
      [selected.id]: [...(prev[selected.id] ?? []), { role: "user", text }],
    }));
    setSending(true);
    try {
      const res = await postApiAdminNpcsByIdChatTest({
        path: { id: selected.id },
        body: { message: text, history, language },
        throwOnError: true,
      });
      setConversations((prev) => ({
        ...prev,
        [selected.id]: [
          ...(prev[selected.id] ?? []),
          {
            role: "npc",
            text: res.data.reply,
            action: res.data.action,
            questOffer: res.data.questOffer,
            itemOffer: res.data.itemOffer,
          },
        ],
      }));
    } catch {
      setError(t("npcChatTest.error"));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex flex-col h-full gap-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-[#8A99AF]">{t("npcChatTest.intro")}</p>
        <label className="flex items-center gap-2 text-xs text-[#8A99AF] shrink-0">
          {t("npcChatTest.language")}
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            className="bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-2 py-1 text-sm text-white outline-none focus:border-[#3C50E0]"
          >
            <option value="en">English</option>
            <option value="fr">Français</option>
          </select>
        </label>
      </div>

      {npcs && chatNpcs.length === 0 && <p className="text-sm text-[#8A99AF]">{t("npcChatTest.noChatNpcs")}</p>}

      {chatNpcs.length > 0 && (
        <div className="flex gap-4 flex-1 min-h-0">
          <div className="w-64 shrink-0 rounded-xl border border-[#2E3A4E] overflow-hidden flex flex-col">
            <div className="px-4 py-2.5 bg-[#1C2434] text-[10px] uppercase tracking-widest text-[#8A99AF]">
              {t("npcChatTest.selectNpc")}
            </div>
            <SidebarList
              items={chatNpcs}
              selected={selected}
              getKey={(n) => n.id}
              getLabel={(n) => `${n.name} (${n.type})`}
              onSelect={(n) => setSelectedId(n.id)}
            />
          </div>
          <div className="flex-1 min-w-0">
            {selected && (
              <NpcChatDialog
                npcName={`${selected.name} (${selected.type})`}
                messages={messages}
                onSend={send}
                sending={sending}
                error={error}
                t={t}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
}
