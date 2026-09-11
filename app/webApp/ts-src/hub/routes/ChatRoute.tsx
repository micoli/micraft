import { useState } from "react";
import { ServerLog } from "../../game/components/ServerLog";
import type { ChannelSubscription, LogEntry } from "../../game/types";
import { useHubMessage, useHubSend } from "../state/HubSocketProvider";
import type { ChannelsSyncMsg, ChatMessageMsg } from "../lib/hubCodec";

const LOG_MAX = 200;

function timeNow(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}:${String(now.getSeconds()).padStart(2, "0")}`;
}

/**
 * Reuses the exact in-game `ServerLog` (channel tabs, colored senders, unread markers) as-is —
 * it's pure props already. Only the message-composer input is hub-specific: the game's `Console`
 * is built around slash-command autocomplete and the wasm intent pipeline (`useConsole`), which
 * doesn't apply here — the hub only ever sends `ChatSend`, so a plain input replaces it rather
 * than forcing that machinery to fit. Restricted server-side to non-spatial channels (no "around")
 * — see HUB_CHAT_CHANNELS in HubConnection.kt.
 */
export function ChatRoute() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [subscribedChannels, setSubscribedChannels] = useState<ChannelSubscription[]>([]);
  const [knownChannels, setKnownChannels] = useState<string[]>([]);
  const [activeChannel, setActiveChannel] = useState("world");
  const [unreadChannels, setUnreadChannels] = useState<string[]>([]);
  const [text, setText] = useState("");
  const send = useHubSend();

  useHubMessage<ChannelsSyncMsg>("ChannelsSync", (p) => {
    setSubscribedChannels(p.subscribedChannels);
    setKnownChannels(p.knownChannels);
  });

  useHubMessage<ChatMessageMsg>("ChatMessage", (p) => {
    setLogs((prev) =>
      [...prev, { time: timeNow(), msg: p.message, channel: p.channel, sender: p.sender }].slice(-LOG_MAX),
    );
    setUnreadChannels((prev) =>
      p.channel !== activeChannel && !prev.includes(p.channel) ? [...prev, p.channel] : prev,
    );
  });

  function handleChannelSelect(channel: string) {
    setActiveChannel(channel);
    setUnreadChannels((prev) => prev.filter((c) => c !== channel));
  }

  function handleSend() {
    const trimmed = text.trim();
    if (!trimmed) return;
    send("ChatSend", { channel: activeChannel, text: trimmed });
    setText("");
  }

  return (
    <div className="flex flex-col h-full">
      <ServerLog
        logs={logs}
        visible
        subscribedChannels={subscribedChannels}
        activeChannel={activeChannel}
        unreadChannels={unreadChannels}
        onChannelSelect={handleChannelSelect}
        layoutStyle={{ flex: 1, minHeight: 0, width: "100%", maxWidth: "none" }}
      />
      <div className="flex gap-2 p-3 border-t border-white/10">
        <input
          type="text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSend()}
          placeholder={`Message #${activeChannel}…`}
          className="flex-1 bg-black/40 border border-white/20 rounded px-3 py-1.5 text-sm text-white font-mono outline-none focus:border-white/50"
        />
        <button
          onClick={handleSend}
          disabled={!text.trim()}
          className="px-3 py-1.5 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded text-white font-mono"
        >
          Send
        </button>
      </div>
      {knownChannels.length === 0 && (
        <div className="px-3 pb-2 text-[10px] text-white/30 font-mono">Loading channels…</div>
      )}
    </div>
  );
}
