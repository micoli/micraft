import { useEffect, useRef, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";
import { Input } from "../../primitives/Input";
import { ItemIcon } from "../shared/ItemIcon";
import { MailData } from "../types";

type View = "inbox" | "compose" | "read";

interface AttachmentSlot {
  itemType: string;
  count: number;
}

interface MailboxOverlayProps {
  open: boolean;
  mails: MailData[];
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  wallet?: number;
  onClose: () => void;
}

function formatCopper(copper: number): string {
  if (copper <= 0) return "0c";
  const g = Math.floor(copper / 100);
  const s = Math.floor((copper % 100) / 10);
  const c = copper % 10;
  const parts: string[] = [];
  if (g > 0) parts.push(`${g}g`);
  if (s > 0) parts.push(`${s}s`);
  if (c > 0 || parts.length === 0) parts.push(`${c}c`);
  return parts.join(" ");
}

function fmtDate(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function sendEvent(type: string, payload: string) {
  window.mcState.events.push(`${type}:${payload}`);
}

export function MailboxOverlay({ open, mails, inventory, itemMeta, wallet = 0, onClose }: MailboxOverlayProps) {
  const [view, setView] = useState<View>("inbox");
  const [selected, setSelected] = useState<MailData | null>(null);
  const [playerNames, setPlayerNames] = useState<string[]>([]);
  const [claimedIds, setClaimedIds] = useState<Set<string>>(new Set());

  // Compose state — attachments/suggestions stay outside the form: attachments are populated by
  // pointer drag-drop from the inventory panel, not typed input, and suggestions are a derived
  // autocomplete list, not submitted field data.
  const [attachments, setAttachments] = useState<AttachmentSlot[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [suggestionIdx, setSuggestionIdx] = useState(-1);
  const [invFilter, setInvFilter] = useState("");
  const toRef = useRef<HTMLInputElement>(null);

  const composeForm = useForm({
    defaultValues: { to: "", subject: "", body: "", copperAmount: 0 },
    onSubmit: ({ value }) => {
      const toTrimmed = value.to.trim();
      if (!toTrimmed || !value.subject.trim()) return;
      const attachMap: Record<string, number> = {};
      for (const a of attachments) {
        if (a.itemType && a.count > 0) {
          attachMap[a.itemType] = (attachMap[a.itemType] ?? 0) + a.count;
        }
      }
      sendEvent(
        "mail_send",
        JSON.stringify({
          to: toTrimmed,
          subject: value.subject.trim(),
          body: value.body,
          attachments: attachMap,
          copperAmount: value.copperAmount,
        }),
      );
      setView("inbox");
    },
  });

  useEffect(() => {
    if (open && view === "compose" && playerNames.length === 0) {
      fetch("/api/players/names")
        .then((r) => r.json())
        .then((names: string[]) => setPlayerNames(names))
        .catch(() => {});
    }
  }, [open, view, playerNames.length]);

  useEffect(() => {
    if (!open) {
      setView("inbox");
      setSelected(null);
    }
  }, [open]);

  function openRead(mail: MailData) {
    setSelected(mail);
    setView("read");
    if (!mail.seen) sendEvent("mail_seen", mail.id);
  }

  function openCompose() {
    composeForm.reset();
    setAttachments([]);
    setInvFilter("");
    setView("compose");
    setTimeout(() => toRef.current?.focus(), 50);
  }

  function handleDelete(mail: MailData) {
    sendEvent("mail_delete", mail.id);
    setView("inbox");
    setSelected(null);
  }

  function handleClaim(mail: MailData) {
    sendEvent("mail_claim", mail.id);
    setClaimedIds((prev) => new Set(prev).add(mail.id));
  }

  function updateSuggestions(val: string) {
    setSuggestionIdx(-1);
    if (val.length >= 1) {
      const filtered = playerNames.filter((n) => n.toLowerCase().includes(val.toLowerCase()));
      setSuggestions(filtered.slice(0, 8));
      setShowSuggestions(filtered.length > 0);
    } else {
      setShowSuggestions(false);
    }
  }

  function pickSuggestion(name: string) {
    composeForm.setFieldValue("to", name);
    setShowSuggestions(false);
    setSuggestionIdx(-1);
  }

  function handleToKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!showSuggestions) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSuggestionIdx((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSuggestionIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter" || e.key === "Tab") {
      const pick = suggestionIdx >= 0 ? suggestions[suggestionIdx] : suggestions[0];
      if (pick) {
        e.preventDefault();
        pickSuggestion(pick);
      }
    } else if (e.key === "Escape") {
      setShowSuggestions(false);
    }
  }

  // Attachment drag-drop from inventory
  function onAttachSlotPointerUp(e: React.PointerEvent, idx: number) {
    const item = window.__mcDragItem as string | null;
    if (!item) return;
    e.stopPropagation();
    const maxCount = inventory[item] ?? 0;
    if (maxCount <= 0) return;
    const already = attachments.reduce((sum, a) => (a.itemType === item ? sum + a.count : sum), 0);
    const available = maxCount - already;
    if (available <= 0) return;
    setAttachments((prev) => {
      const next = [...prev];
      next[idx] = { itemType: item, count: Math.min(1, available) };
      return next;
    });
  }

  function removeAttachment(idx: number) {
    setAttachments((prev) => prev.filter((_, i) => i !== idx));
  }

  function updateAttachCount(idx: number, count: number) {
    setAttachments((prev) => {
      const next = [...prev];
      const item = next[idx];
      if (!item) return prev;
      const maxOwned = inventory[item.itemType] ?? 0;
      const already = prev.reduce((sum, a, i) => (i !== idx && a.itemType === item.itemType ? sum + a.count : sum), 0);
      next[idx] = { ...item, count: Math.max(1, Math.min(count, maxOwned - already)) };
      return next;
    });
  }

  function addFromInventory(itemType: string) {
    const maxCount = inventory[itemType] ?? 0;
    if (maxCount <= 0) return;
    const already = attachments.reduce((sum, a) => (a.itemType === itemType ? sum + a.count : sum), 0);
    if (already >= maxCount) return;
    const existingIdx = attachments.findIndex((a) => a.itemType === itemType);
    if (existingIdx >= 0) {
      setAttachments((prev) => {
        const next = [...prev];
        next[existingIdx] = { ...next[existingIdx], count: next[existingIdx].count + 1 };
        return next;
      });
    } else {
      setAttachments((prev) => [...prev, { itemType, count: 1 }]);
    }
  }

  const unread = mails.filter((m) => !m.seen).length;
  const sorted = [...mails].sort((a, b) => b.sentAt - a.sentAt);
  const slots: (AttachmentSlot | null)[] = [...attachments, null];

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        className="w-[900px] max-w-[96vw] max-h-[80vh] flex flex-col gap-0 p-0 z-[2001]"
        overlayClassName="z-[2000]"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            if (view !== "inbox") setView("inbox");
            else onClose();
          }
        }}
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-3 border-b border-white/10">
          <DialogTitle className="font-mono text-lg flex-1">
            ✉ Mailbox
            {unread > 0 && (
              <span className="ml-2 text-xs bg-red-600 text-white rounded-full px-2 py-0.5">{unread}</span>
            )}
          </DialogTitle>
          {view !== "inbox" && (
            <Button variant="ghost" className="text-xs" onClick={() => setView("inbox")}>
              ← Back
            </Button>
          )}
          {view === "inbox" && (
            <Button variant="secondary" className="text-xs font-mono" onClick={openCompose}>
              + New
            </Button>
          )}
          <Button variant="ghost" className="text-xs" onClick={onClose}>
            ✕
          </Button>
        </div>

        {/* Inbox */}
        {view === "inbox" && (
          <div className="flex-1 overflow-y-auto">
            {sorted.length === 0 ? (
              <div className="text-center text-white/40 font-mono py-12">No messages</div>
            ) : (
              <table className="w-full text-sm font-mono">
                <tbody>
                  {sorted.map((mail) => (
                    <tr
                      key={mail.id}
                      className="border-b border-white/5 hover:bg-white/5 cursor-pointer transition-colors"
                      onClick={() => openRead(mail)}
                    >
                      <td className="px-4 py-2 w-4 text-center">
                        {!mail.seen && <span className="text-blue-400">●</span>}
                      </td>
                      <td className="px-2 py-2 text-white/70 w-32 truncate">{mail.from}</td>
                      <td className="px-2 py-2 text-white flex-1 truncate">
                        {mail.subject}
                        {Object.keys(mail.attachments ?? {}).length > 0 && (
                          <span className="ml-2 text-yellow-400 text-xs">📎</span>
                        )}
                      </td>
                      <td className="px-4 py-2 text-white/40 text-xs whitespace-nowrap">{fmtDate(mail.sentAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* Read */}
        {view === "read" && selected && (
          <div className="flex-1 flex flex-col overflow-hidden p-5 gap-3">
            <div className="flex gap-6 text-sm font-mono text-white/60">
              <span>
                <span className="text-white/30">From:</span> {selected.from}
              </span>
              <span>
                <span className="text-white/30">Date:</span> {fmtDate(selected.sentAt)}
              </span>
            </div>
            <div className="font-mono text-base font-semibold">{selected.subject}</div>
            <div className="flex-1 overflow-y-auto text-sm text-white/80 whitespace-pre-wrap bg-black/30 rounded p-3 font-mono">
              {selected.body}
            </div>
            {(Object.keys(selected.attachments ?? {}).length > 0 || (selected.copperAmount ?? 0) > 0) && (
              <div className="border-t border-white/10 pt-3">
                <div className="text-xs text-white/40 font-mono mb-2">Attachments</div>
                {(selected.copperAmount ?? 0) > 0 && (
                  <div className="flex items-center gap-1.5 bg-black/40 rounded px-2 py-1 text-xs font-mono mb-2 w-fit">
                    <span>💰</span>
                    <span className="text-yellow-300">{formatCopper(selected.copperAmount!)}</span>
                  </div>
                )}
                {Object.keys(selected.attachments ?? {}).length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {Object.entries(selected.attachments ?? {}).map(([type, count]) => {
                      const meta = itemMeta[type];
                      return (
                        <div
                          key={type}
                          className="flex items-center gap-1.5 bg-black/40 rounded px-2 py-1 text-xs font-mono"
                        >
                          {meta && (
                            <div
                              className="w-5 h-5 rounded-sm flex-shrink-0"
                              style={{
                                background: meta.bg,
                                boxShadow: "inset -2px -2px 0 rgba(0,0,0,0.3),inset 2px 2px 0 rgba(255,255,255,0.15)",
                              }}
                            />
                          )}
                          <span className="text-white/70">{meta?.label ?? type}</span>
                          <span className="text-white/50">×{count}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
                {!selected.attachmentsClaimed && !claimedIds.has(selected.id) && (
                  <Button variant="secondary" className="mt-2 text-xs font-mono" onClick={() => handleClaim(selected)}>
                    Claim items
                  </Button>
                )}
                {(selected.attachmentsClaimed || claimedIds.has(selected.id)) && (
                  <div className="mt-1 text-xs text-green-400/70 font-mono">✓ Items claimed</div>
                )}
              </div>
            )}
            <div className="flex justify-end pt-1">
              <Button
                variant="outline"
                className="text-xs font-mono text-red-400 border-red-400/30 hover:bg-red-400/10"
                onClick={() => handleDelete(selected)}
              >
                Delete
              </Button>
            </div>
          </div>
        )}

        {/* Compose */}
        {view === "compose" && (
          <div className="flex-1 flex flex-row overflow-hidden">
            {/* Left: compose form */}
            <div className="flex-1 flex flex-col overflow-hidden p-5 gap-3 min-w-0">
              <div className="relative">
                <composeForm.Field name="to">
                  {(field) => (
                    <Input
                      ref={toRef}
                      placeholder="To (player name)"
                      value={field.state.value}
                      onChange={(e) => {
                        field.handleChange(e.target.value);
                        updateSuggestions(e.target.value);
                      }}
                      onKeyDown={handleToKeyDown}
                      onBlur={() => {
                        field.handleBlur();
                        setTimeout(() => setShowSuggestions(false), 150);
                      }}
                      onFocus={() =>
                        field.state.value.length >= 1 && suggestions.length > 0 && setShowSuggestions(true)
                      }
                      className="font-mono text-sm"
                      autoComplete="off"
                    />
                  )}
                </composeForm.Field>
                {showSuggestions && (
                  <div className="absolute top-full left-0 right-0 z-10 bg-black/90 border border-white/20 rounded mt-1 max-h-40 overflow-y-auto">
                    {suggestions.map((name, i) => (
                      <div
                        key={name}
                        className={`px-3 py-1.5 text-sm font-mono cursor-pointer text-white/80 transition-colors ${i === suggestionIdx ? "bg-white/20" : "hover:bg-white/10"}`}
                        onMouseDown={() => pickSuggestion(name)}
                        onMouseEnter={() => setSuggestionIdx(i)}
                      >
                        {name}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <composeForm.Field name="subject">
                {(field) => (
                  <Input
                    placeholder="Subject"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                    maxLength={120}
                    className="font-mono text-sm"
                  />
                )}
              </composeForm.Field>
              <composeForm.Field name="body">
                {(field) => (
                  <textarea
                    placeholder="Message…"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                    maxLength={4000}
                    className="flex-1 bg-[#111] border border-[#555] rounded text-[#eee] font-mono text-sm p-3 outline-none focus:border-[#888] resize-none min-h-[120px]"
                  />
                )}
              </composeForm.Field>
              {/* Attachment slots */}
              <div>
                <div className="text-xs text-white/40 font-mono mb-2">
                  Attachments — click inventory or drag, click × to remove
                </div>
                <div className="flex flex-wrap gap-2">
                  {slots.map((slot, idx) =>
                    slot ? (
                      <div
                        key={idx}
                        className="flex items-center gap-1 bg-black/50 border border-white/20 rounded px-2 py-1 text-xs font-mono"
                        onPointerUp={(e) => onAttachSlotPointerUp(e, idx)}
                      >
                        <ItemIcon itemId={slot.itemType} fallbackBg={itemMeta[slot.itemType]?.bg ?? "#555"} size={20} />
                        <span className="text-white/70">{itemMeta[slot.itemType]?.label ?? slot.itemType}</span>
                        <input
                          type="number"
                          min={1}
                          max={inventory[slot.itemType] ?? 1}
                          value={slot.count}
                          onChange={(e) => updateAttachCount(idx, parseInt(e.target.value) || 1)}
                          className="w-12 bg-transparent border border-white/20 rounded text-center text-white text-xs py-0 px-1 outline-none"
                          onClick={(e) => e.stopPropagation()}
                        />
                        <button
                          className="ml-1 text-white/40 hover:text-white/80 transition-colors"
                          onClick={() => removeAttachment(idx)}
                        >
                          ×
                        </button>
                      </div>
                    ) : (
                      <div
                        key={`empty-${idx}`}
                        className="w-10 h-10 border-2 border-dashed border-white/20 rounded flex items-center justify-center text-white/20 text-lg hover:border-white/40 transition-colors"
                        onPointerUp={(e) => onAttachSlotPointerUp(e, idx)}
                        title="Drop item here"
                      >
                        +
                      </div>
                    ),
                  )}
                </div>
              </div>
              {/* Copper */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-white/40 font-mono">💰 Copper</span>
                <composeForm.Field name="copperAmount">
                  {(field) => (
                    <input
                      type="number"
                      min={0}
                      max={wallet}
                      value={field.state.value || ""}
                      placeholder="0"
                      onChange={(e) => field.handleChange(Math.max(0, Math.min(wallet, parseInt(e.target.value) || 0)))}
                      onBlur={field.handleBlur}
                      className="w-24 bg-black/40 border border-white/20 text-white/80 font-mono text-xs rounded px-2 py-1 text-center focus:outline-none focus:border-white/50"
                      onKeyDown={(e) => e.stopPropagation()}
                    />
                  )}
                </composeForm.Field>
                <composeForm.Subscribe selector={(state) => state.values.copperAmount}>
                  {(copperAmount) =>
                    copperAmount > 0 && (
                      <span className="text-xs text-yellow-300 font-mono">{formatCopper(copperAmount)}</span>
                    )
                  }
                </composeForm.Subscribe>
                <span className="text-xs text-white/30 font-mono ml-auto">Disponible : {formatCopper(wallet)}</span>
              </div>
              <div className="flex justify-end gap-2 pt-1">
                <Button variant="ghost" className="text-xs font-mono" onClick={() => setView("inbox")}>
                  Cancel
                </Button>
                <composeForm.Subscribe selector={(state) => [state.values.to, state.values.subject] as const}>
                  {([to, subject]) => (
                    <Button
                      variant="primary"
                      className="text-xs font-mono"
                      onClick={() => composeForm.handleSubmit()}
                      disabled={!to.trim() || !subject.trim()}
                    >
                      Send ✈
                    </Button>
                  )}
                </composeForm.Subscribe>
              </div>
            </div>
            {/* Right: player inventory */}
            <div className="w-52 flex-shrink-0 border-l border-white/10 flex flex-col gap-2 p-3">
              <div className="text-[10px] text-white/45 uppercase tracking-wider font-mono">Inventaire</div>
              <input
                type="text"
                value={invFilter}
                onChange={(e) => setInvFilter(e.target.value)}
                placeholder="Filtrer…"
                className="bg-black/40 border border-white/20 text-white/80 font-mono text-[11px] rounded px-1.5 py-0.5 focus:outline-none focus:border-white/50 placeholder-white/30"
                onKeyDown={(e) => e.stopPropagation()}
              />
              <div className="flex flex-col gap-1 overflow-y-auto flex-1">
                {Object.entries(inventory)
                  .filter(([, qty]) => qty > 0)
                  .filter(([type]) => {
                    if (!invFilter) return true;
                    const iq = invFilter.toLowerCase();
                    const label = itemMeta[type]?.label ?? type;
                    return label.toLowerCase().includes(iq) || type.toLowerCase().includes(iq);
                  })
                  .sort((a, b) => a[0].localeCompare(b[0]))
                  .map(([type, qty]) => {
                    const meta = itemMeta[type];
                    const attached = attachments.reduce((sum, a) => (a.itemType === type ? sum + a.count : sum), 0);
                    const available = qty - attached;
                    return (
                      <button
                        key={type}
                        className={`flex items-center gap-2 bg-white/5 hover:bg-white/10 rounded px-2 py-1 text-left transition-colors ${available <= 0 ? "opacity-40 cursor-not-allowed" : "cursor-pointer"}`}
                        onClick={() => addFromInventory(type)}
                        disabled={available <= 0}
                        title={available <= 0 ? "Déjà tout attaché" : `Ajouter ${meta?.label ?? type}`}
                      >
                        <div className="w-6 h-6 flex items-center justify-center flex-shrink-0">
                          <ItemIcon itemId={type} fallbackBg={meta?.bg ?? "#555"} size={22} />
                        </div>
                        <span className="flex-1 text-[11px] text-white/80 truncate font-mono">
                          {meta?.label ?? type}
                        </span>
                        <span className="text-[10px] text-white/40 flex-shrink-0 font-mono">×{available}</span>
                      </button>
                    );
                  })}
                {Object.keys(inventory).length === 0 && (
                  <p className="text-xs text-white/40 text-center py-4 font-mono">Inventaire vide.</p>
                )}
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
