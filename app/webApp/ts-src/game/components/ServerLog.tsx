import { cn } from "../../primitives/cn";
import { useServerLog } from "../hooks/useServerLog";
import { ChannelSubscription, LogEntry } from "../types";

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const COMBAT_PALETTE = [
  "#f87171",
  "#fb923c",
  "#facc15",
  "#4ade80",
  "#22d3ee",
  "#818cf8",
  "#e879f9",
  "#f472b6",
  "#38bdf8",
  "#a78bfa",
];

function nameToColor(name: string): string {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return COMBAT_PALETTE[h % COMBAT_PALETTE.length];
}

function renderCombatMsg(raw: string): string {
  const TOKEN = /\[(p|m):([^\]]*)\]/g;
  let out = "",
    last = 0;
  for (const m of raw.matchAll(TOKEN)) {
    out += escapeHtml(raw.slice(last, m.index!));
    out += `<span style="color:${nameToColor(m[2])};font-weight:bold">${escapeHtml(m[2])}</span>`;
    last = m.index! + m[0].length;
  }
  return out + escapeHtml(raw.slice(last));
}

const CHANNEL_COLORS: Record<string, string> = {
  system: "#aaa",
  game: "#f5c542",
  world: "#fff",
  around: "#90e0b0",
};

function channelColor(ch: string): string {
  if (ch.startsWith("dm:")) return "#7dd3fc";
  return CHANNEL_COLORS[ch] ?? "#c084fc";
}

interface Props {
  logs: LogEntry[];
  visible: boolean;
  subscribedChannels: ChannelSubscription[];
  activeChannel: string;
  unreadChannels: string[];
  onChannelSelect: (channel: string) => void;
  layoutStyle?: React.CSSProperties;
}

export function ServerLog({
  logs,
  visible,
  subscribedChannels,
  activeChannel,
  unreadChannels,
  onChannelSelect,
  layoutStyle,
}: Props) {
  const { scrollRef, filtered, onScroll } = useServerLog({ logs, visible, activeChannel });

  if (!visible) return null;

  return (
    <div
      className={cn(
        "flex flex-col max-h-full bg-black/55 rounded-t z-[1002] box-border",
        !layoutStyle && "fixed bottom-[94px] left-1/2 -translate-x-1/2 w-[60%] max-h-[200px]",
      )}
      style={layoutStyle}
    >
      <div className="flex flex-shrink-0 overflow-x-auto border-b border-white/15">
        {subscribedChannels.map((ch) => (
          <button
            key={ch.name}
            onMouseDown={(e) => {
              e.preventDefault();
              onChannelSelect(ch.name);
            }}
            className={cn(
              "px-2 py-0.5 border-none border-r border-white/10 font-mono text-[11px] cursor-pointer whitespace-nowrap transition-colors",
              ch.name === activeChannel ? "bg-white/15" : "bg-transparent hover:bg-white/5",
            )}
            style={{ color: channelColor(ch.name) }}
          >
            {unreadChannels.includes(ch.name) ? `* ${ch.name}` : ch.name}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto px-2 py-1 min-h-0" ref={scrollRef} onScroll={onScroll}>
        {filtered.length === 0 && (
          <div className="text-white/30 font-mono text-xs italic leading-relaxed">No messages</div>
        )}
        {filtered.map((entry, i) => (
          <div key={i} className="text-white/85 font-mono text-xs leading-relaxed">
            <span className="text-white/50">[{entry.time}]</span>{" "}
            {entry.sender && (
              <span className="font-bold" style={{ color: channelColor(entry.channel) }}>
                {escapeHtml(entry.sender)}:{" "}
              </span>
            )}
            <span
              dangerouslySetInnerHTML={{
                __html: entry.channel === "combat" ? renderCombatMsg(entry.msg) : escapeHtml(entry.msg),
              }}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
