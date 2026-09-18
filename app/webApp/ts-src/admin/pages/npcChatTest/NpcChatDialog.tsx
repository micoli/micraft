import { useState } from "react";
import { ItemOfferSummaryDto, NpcChatActionDto, QuestOfferSummaryDto } from "../../apiTypes";
import type { Translate } from "../../i18n";
import { ActionHint } from "./ActionHint";

export interface NpcChatTestMessage {
  role: "user" | "npc";
  text: string;
  action?: NpcChatActionDto;
  questOffer?: QuestOfferSummaryDto | null;
  itemOffer?: ItemOfferSummaryDto | null;
}

export function NpcChatDialog({
  npcName,
  messages,
  onSend,
  sending,
  error,
  t,
}: {
  npcName: string;
  messages: NpcChatTestMessage[];
  onSend: (text: string) => void;
  sending: boolean;
  error: string | null;
  t: Translate;
}) {
  const [draft, setDraft] = useState("");

  const submit = () => {
    const text = draft.trim();
    if (!text || sending) return;
    onSend(text);
    setDraft("");
  };

  return (
    <div className="flex flex-col h-full rounded-xl border border-[#2E3A4E] overflow-hidden bg-[#111827]">
      <div className="px-4 py-3 border-b border-[#2E3A4E] bg-[#1C2434]">
        <p className="text-sm font-semibold text-white">{npcName}</p>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.length === 0 && <p className="text-sm text-[#8A99AF]">{t("npcChatTest.emptyConversation")}</p>}
        {messages.map((m, i) => (
          <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[80%] rounded-lg px-3 py-2 text-sm ${
                m.role === "user" ? "bg-[#3C50E0] text-white" : "bg-[#1C2434] text-white"
              }`}
            >
              <p className="text-[10px] uppercase tracking-widest opacity-60 mb-0.5">
                {m.role === "user" ? t("npcChatTest.you") : npcName}
              </p>
              <p className="whitespace-pre-wrap">{m.text}</p>
              <ActionHint message={m} t={t} />
            </div>
          </div>
        ))}
        {sending && <p className="text-xs text-[#8A99AF]">{t("npcChatTest.sending")}</p>}
      </div>

      {error && <p className="px-4 py-2 text-xs text-red-400 border-t border-[#2E3A4E]">{error}</p>}

      <div className="flex items-center gap-2 p-3 border-t border-[#2E3A4E]">
        <input
          type="text"
          value={draft}
          placeholder={t("npcChatTest.messagePlaceholder")}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") submit();
          }}
          disabled={sending}
          className="flex-1 bg-[#1C2434] border border-[#2E3A4E] rounded-lg px-3 py-1.5 text-sm text-white placeholder-[#8A99AF] outline-none focus:border-[#3C50E0] disabled:opacity-50"
        />
        <button
          onClick={submit}
          disabled={sending || !draft.trim()}
          className="px-3 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] disabled:opacity-40 disabled:hover:bg-[#3C50E0] text-white transition-colors shrink-0"
        >
          {t("npcChatTest.send")}
        </button>
      </div>
    </div>
  );
}
