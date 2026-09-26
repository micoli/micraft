import { useEffect, useLayoutEffect, useState } from "react";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";
import type { PanelEditFormData } from "../types";

interface Props {
  data: PanelEditFormData;
  onClose: () => void;
}

// Content editor for an interactive panel placeable — mirrors ActionBlockEditor.tsx's shape
// (Dialog handles Escape/modalOpen itself). Either an external https URL is set (the panel embeds
// it directly) or a set of local pages is edited (server-sanitized HTML, navigable via relative
// `page.html` links between them) — the two are mutually exclusive server-side.
export function PanelEditor({ data, onClose }: Props) {
  const [externalUrl, setExternalUrl] = useState(data.externalUrl ?? "");
  const [pages, setPages] = useState<Record<string, string>>(data.pages ?? {});
  const [activePage, setActivePage] = useState(Object.keys(data.pages ?? {})[0] ?? "index");
  const [newPageName, setNewPageName] = useState("");

  useLayoutEffect(() => {
    if (window.mcState) window.mcState.modalOpen = true;
  }, []);

  useEffect(() => {
    setExternalUrl(data.externalUrl ?? "");
    setPages(data.pages ?? {});
    setActivePage(Object.keys(data.pages ?? {})[0] ?? "index");
  }, [data]);

  const addPage = () => {
    const name = newPageName.trim();
    if (!name || pages[name] !== undefined) return;
    setPages((prev) => ({ ...prev, [name]: "" }));
    setActivePage(name);
    setNewPageName("");
  };

  const removePage = (name: string) => {
    setPages((prev) => {
      const next = { ...prev };
      delete next[name];
      return next;
    });
    if (activePage === name) setActivePage(Object.keys(pages)[0] ?? "index");
  };

  const save = () => {
    window.mc?.savePanel?.(
      JSON.stringify({
        placeableId: data.placeableId,
        externalUrl: externalUrl.trim(),
        pages: externalUrl.trim() ? {} : pages,
      }),
    );
    onClose();
  };

  return (
    <Dialog modal={true} open={true} onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        movable
        className="min-w-[520px] max-w-[640px] flex flex-col font-mono p-5 gap-3"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <DialogTitle className="text-center text-lg tracking-[0.2em]">PANEL</DialogTitle>

        <label className="flex flex-col gap-1 text-[11px] text-white/60">
          External URL <span className="text-white/30">— https, host must be allowlisted; blank = local pages</span>
          <input
            className="bg-black/30 border border-white/10 rounded px-2 py-1 text-white/90"
            value={externalUrl}
            onChange={(e) => setExternalUrl(e.target.value)}
            placeholder="https://example.com/…"
          />
        </label>

        {!externalUrl.trim() && (
          <div className="flex flex-col gap-2">
            <div className="flex gap-1 flex-wrap items-center">
              {Object.keys(pages).map((name) => (
                <div key={name} className="flex items-center">
                  <button
                    className={`px-2 py-0.5 text-[11px] rounded-l border border-white/10 ${
                      activePage === name ? "bg-white/20" : "bg-black/30"
                    }`}
                    onClick={() => setActivePage(name)}
                  >
                    {name}
                  </button>
                  <button
                    className="px-1.5 py-0.5 text-[11px] rounded-r border border-l-0 border-white/10 bg-black/30 text-white/40"
                    onClick={() => removePage(name)}
                  >
                    ×
                  </button>
                </div>
              ))}
              <input
                className="bg-black/30 border border-white/10 rounded px-1.5 py-0.5 text-[11px] w-24"
                value={newPageName}
                onChange={(e) => setNewPageName(e.target.value)}
                placeholder="new page"
                onKeyDown={(e) => e.key === "Enter" && addPage()}
              />
              <Button onClick={addPage} className="text-[11px] px-2 py-0.5">
                +
              </Button>
            </div>
            <textarea
              className="bg-black/30 border border-white/10 rounded px-2 py-1 text-white/90 h-48 font-mono text-[12px]"
              value={pages[activePage] ?? ""}
              onChange={(e) => setPages((prev) => ({ ...prev, [activePage]: e.target.value }))}
              placeholder="<p>Hello</p>"
            />
          </div>
        )}

        <div className="flex justify-end gap-2 mt-2">
          <Button onClick={onClose} className="text-white/60">
            Cancel
          </Button>
          <Button onClick={save}>Save</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
