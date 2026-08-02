import { basicSetup, EditorView } from "codemirror";
import { useEffect, useRef, useState } from "react";
import { useT } from "../i18n";
import { TUNING_FIELDS, type NpcTuning } from "./types";

// ── NPC manager tunables ──────────────────────────────────────────────────────

interface TuningProps {
  /** Values in force in the simulation. */
  value: NpcTuning;
  /** Values the live server runs with — the diff baseline. */
  base: NpcTuning | null;
  onChange: (tuning: NpcTuning) => void;
  onApply: () => void;
}

/**
 * Form over the flat tunable record. Every field that departs from the live server is flagged, so an
 * override is always visible and never an oversight.
 */
export function TuningEditor({ value, base, onChange, onApply }: TuningProps) {
  const t = useT();
  const changed = (key: keyof NpcTuning) => base != null && base[key] !== value[key];
  const changedCount = base ? TUNING_FIELDS.filter((f) => changed(f.key)).length : 0;

  return (
    <div className="flex h-full flex-col">
      <div className="mb-2 flex items-center gap-2">
        <span className="text-[11px] text-[#8A99AF]">
          {changedCount === 0 ? t("sim.rules.identical") : t("sim.rules.overridden", changedCount)}
        </span>
        <button
          type="button"
          disabled={!base}
          onClick={() => base && onChange({ ...base })}
          className="ml-auto rounded bg-[#2E3A4E] px-2 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          {t("sim.rules.resetToLive")}
        </button>
        <button
          type="button"
          onClick={onApply}
          className="rounded bg-[#3C50E0] px-2.5 py-1 text-[11px] font-medium text-white hover:bg-[#3C50E0]/80"
        >
          {t("sim.rules.applyHot")}
        </button>
      </div>

      <div className="flex-1 overflow-auto rounded border border-[#2E3A4E] bg-[#0E1726] p-2">
        {TUNING_FIELDS.map((field) => (
          <label key={field.key} className="flex items-center gap-2 border-b border-[#1A222C] py-1 last:border-0">
            <span className={"flex-1 text-[11px] " + (changed(field.key) ? "text-[#FACC15]" : "text-[#8A99AF]")}>
              {t(field.labelKey)}
              {changed(field.key) && base && (
                <span className="ml-1 text-[10px] text-[#4A5568]">
                  {t("sim.rules.liveValue", String(base[field.key]))}
                </span>
              )}
            </span>
            <input
              type="number"
              step={field.step}
              value={value[field.key]}
              onChange={(e) => onChange({ ...value, [field.key]: Number(e.target.value) })}
              className="w-28 rounded border border-[#2E3A4E] bg-[#1A222C] px-2 py-1 text-right text-[11px] text-white"
            />
          </label>
        ))}
      </div>
    </div>
  );
}

// ── Per-type definition overrides ─────────────────────────────────────────────

interface OverridesProps {
  npcTypes: string[];
  onApply: (overrides: Record<string, unknown>) => void;
}

const OVERRIDE_TEMPLATE = `{
  "wolf": {
    "wanderSpeed": 6.0,
    "animal": null
  }
}`;

/**
 * JSON editor for per-type overrides (hp, aggro, diet, gestationDays…). Applies to NPCs spawned
 * afterwards: live instances keep their definition until they respawn, exactly as on the live server.
 */
export function OverridesEditor({ npcTypes, onApply }: OverridesProps) {
  const t = useT();
  const container = useRef<HTMLDivElement | null>(null);
  const viewRef = useRef<EditorView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!container.current) return;
    const view = new EditorView({
      doc: "{}",
      extensions: [
        basicSetup,
        EditorView.theme({
          "&": { height: "100%", background: "#0E1726", color: "#CBD5E1" },
          ".cm-content": { fontFamily: "ui-monospace, SFMono-Regular, monospace", fontSize: "12px" },
          ".cm-gutters": {
            background: "#0E1726",
            borderRight: "1px solid #2E3A4E",
            color: "#4A5568",
          },
          ".cm-activeLine": { background: "#1A222C" },
          ".cm-activeLineGutter": { background: "#1A222C" },
        }),
      ],
      parent: container.current,
    });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, []);

  const apply = () => {
    const text = viewRef.current?.state.doc.toString() ?? "{}";
    try {
      const parsed = JSON.parse(text) as Record<string, unknown>;
      const unknown = Object.keys(parsed).filter((k) => !npcTypes.includes(k));
      if (unknown.length > 0) {
        setError(t("sim.rules.unknownTypes", unknown.join(", ")));
        return;
      }
      setError(null);
      onApply(parsed);
    } catch (e) {
      setError(t("sim.rules.invalidJson", (e as Error).message));
    }
  };

  const insertTemplate = () => {
    const view = viewRef.current;
    if (!view) return;
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: OVERRIDE_TEMPLATE },
    });
  };

  return (
    <div className="flex h-full flex-col">
      <div className="mb-2 flex items-center gap-2">
        <span className="text-[11px] text-[#8A99AF]">{t("sim.rules.overridesHint")}</span>
        <button
          type="button"
          onClick={insertTemplate}
          className="ml-auto rounded bg-[#2E3A4E] px-2 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
        >
          {t("sim.rules.example")}
        </button>
        <button
          type="button"
          onClick={apply}
          className="rounded bg-[#3C50E0] px-2.5 py-1 text-[11px] font-medium text-white hover:bg-[#3C50E0]/80"
        >
          {t("sim.rules.apply")}
        </button>
      </div>
      {error && (
        <p className="mb-2 rounded border border-red-500/40 bg-red-500/10 px-2 py-1 text-[11px] text-red-300">
          {error}
        </p>
      )}
      <div ref={container} className="flex-1 overflow-hidden rounded border border-[#2E3A4E]" />
      <p className="mt-1.5 text-[10px] text-[#8A99AF]">{t("sim.rules.availableTypes", npcTypes.join(", ") || "—")}</p>
    </div>
  );
}
