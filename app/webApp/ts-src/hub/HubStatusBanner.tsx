import { Button } from "../primitives/Button";
import { useHubSocket } from "./state/HubSocketProvider";
import { useT } from "./i18n";

/** Connection-status strip below the header — hidden once the socket is open. */
export function HubStatusBanner() {
  const { status, socket } = useHubSocket();
  const t = useT();
  if (status === "open") return null;
  if (status === "superseded") {
    return (
      <div className="bg-yellow-950/60 border-b border-yellow-800/60 text-yellow-300 text-xs px-4 py-2 flex items-center justify-between">
        <span>
          <strong>{t("shell.supersededTitle")}</strong> — {t("shell.supersededBody")}
        </span>
        <Button size="sm" variant="outline" onClick={() => socket?.reconnect()}>
          {t("shell.reconnect")}
        </Button>
      </div>
    );
  }
  return (
    <div className="bg-[#1a1a2e] border-b border-blue-900/60 text-blue-400/80 text-xs px-4 py-2 flex items-center gap-1.5">
      <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-400/80 animate-pulse" />
      {status === "connecting" && t("shell.connecting")}
      {status === "reconnecting" && t("shell.reconnecting")}
      {status === "closed" && t("shell.disconnected")}
    </div>
  );
}
