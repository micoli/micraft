import type { Translate } from "../../i18n";
import type { NpcChatTestMessage } from "./NpcChatDialog";

export function ActionHint({ message, t }: { message: NpcChatTestMessage; t: Translate }) {
  if (!message.action || message.action.type === "none") return null;
  if (message.action.type === "offer_quest") {
    return (
      <p className="mt-1 text-[11px] text-emerald-400">
        {t("npcChatTest.actionOfferQuest", message.questOffer?.title ?? message.action.questId ?? "?")}
      </p>
    );
  }
  if (message.action.type === "give_item") {
    return (
      <p className="mt-1 text-[11px] text-emerald-400">
        {t("npcChatTest.actionGiveItem", message.itemOffer?.displayName ?? message.action.itemId ?? "?")}
      </p>
    );
  }
  return null;
}
