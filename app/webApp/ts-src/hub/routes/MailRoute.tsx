import { useState } from "react";
import { MailboxOverlay } from "../../game/overlays/MailboxOverlay";
import type { MailData } from "../../game/types";
import { useHubMessage, useHubSend, useHubSocket } from "../state/HubSocketProvider";
import type { MailDeletedMsg, MailReceivedMsg, MailSyncMsg, MailUpdateMsg } from "../lib/hubCodec";

/**
 * Reuses the exact in-game `MailboxOverlay` (inbox/read/compose, attachment drag, autocomplete) —
 * only its transport differs: `onEvent` here encodes straight onto the `/hub` socket instead of
 * pushing onto the wasm bridge's event queue (there's no wasm runtime on this page to drain it).
 * `chrome="page"` drops the floating Dialog/Portal chrome so it fills `<main>` like every other
 * hub screen instead of floating over it. `open` stays true and `onClose` is a no-op — the hub has
 * no 3D scene to reveal underneath.
 */
export function MailRoute() {
  const [mails, setMails] = useState<MailData[]>([]);
  const { inventory, wallet, itemMeta } = useHubSocket();
  const send = useHubSend();

  useHubMessage<MailSyncMsg>("MailSync", (p) => setMails(p.mails));
  useHubMessage<MailReceivedMsg>("MailReceived", (p) => setMails((prev) => [...prev, p.mail]));
  useHubMessage<MailUpdateMsg>("MailUpdate", (p) =>
    setMails((prev) => prev.map((m) => (m.id === p.mail.id ? p.mail : m))),
  );
  useHubMessage<MailDeletedMsg>("MailDeleted", (p) => setMails((prev) => prev.filter((m) => m.id !== p.mailId)));

  function onEvent(type: string, payload: string) {
    switch (type) {
      case "mail_send": {
        const data = JSON.parse(payload) as {
          to: string;
          subject: string;
          body: string;
          attachments: Record<string, number>;
          copperAmount: number;
        };
        send("SendMail", data);
        break;
      }
      case "mail_seen":
        send("MarkMailSeen", { mailId: payload });
        break;
      case "mail_delete":
        send("DeleteMail", { mailId: payload });
        break;
      case "mail_claim":
        send("ClaimMailAttachments", { mailId: payload });
        break;
    }
  }

  return (
    <MailboxOverlay
      open
      mails={mails}
      inventory={inventory}
      itemMeta={itemMeta}
      wallet={wallet}
      onClose={() => {}}
      onEvent={onEvent}
      chrome="page"
    />
  );
}
