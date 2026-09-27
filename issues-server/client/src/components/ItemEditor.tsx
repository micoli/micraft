import { useEffect, useState } from "react";
import { saveItem, type ItemDetail, type LabelIndex } from "../api.ts";
import { FilePath } from "./FilePath.tsx";
import { LabelsEditor, type LabelRow } from "./LabelsEditor.tsx";
import { MarkdownEditor } from "./MarkdownEditor.tsx";

interface Props {
  item: ItemDetail;
  labelIndex: LabelIndex;
  onCancel: () => void;
  onSaved: () => void;
}

export function ItemEditor({ item, labelIndex, onCancel, onSaved }: Props) {
  const [base, setBase] = useState(item);
  const [title, setTitle] = useState(item.title);
  const [labels, setLabels] = useState<LabelRow[]>(() => toRows(item.labels));
  const [body, setBody] = useState(item.body);
  const [bodyTouched, setBodyTouched] = useState(false);
  const [editorKey, setEditorKey] = useState(0);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string>();
  const [conflict, setConflict] = useState<ItemDetail>();

  const dirty = bodyTouched || title !== base.title || JSON.stringify(toRows(base.labels)) !== JSON.stringify(labels);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const save = async (version: string) => {
    setSaving(true);
    setMessage(undefined);
    const result = await saveItem(item.id, {
      version,
      title,
      labels: fromRows(labels),
      body,
    }).catch((error: unknown) => ({
      outcome: "error" as const,
      message: String(error),
    }));
    setSaving(false);
    if (result.outcome === "saved") return onSaved();
    if (result.outcome === "conflict") return setConflict(result.item);
    setMessage(result.message);
  };

  const reloadFromDisk = (fresh: ItemDetail) => {
    setBase(fresh);
    setTitle(fresh.title);
    setLabels(toRows(fresh.labels));
    setBody(fresh.body);
    setBodyTouched(false);
    setEditorKey((key) => key + 1);
    setConflict(undefined);
  };

  const cancel = () => {
    if (dirty && !confirm("Discard your changes?")) return;
    onCancel();
  };

  return (
    <div className="item-editor">
      <div className="detail-toolbar">
        <FilePath path={item.id} />
        <div className="actions">
          <button onClick={cancel} disabled={saving}>
            Cancel
          </button>
          <button className="primary" onClick={() => save(base.version)} disabled={saving || !dirty}>
            {saving ? "Saving…" : "Save"}
          </button>
        </div>
      </div>

      {conflict && (
        <div className="notice">
          <span>This file changed on disk since you opened it.</span>
          <button onClick={() => reloadFromDisk(conflict)}>Reload from disk (lose my changes)</button>
          <button onClick={() => save(conflict.version)}>Overwrite with my version</button>
        </div>
      )}
      {message && <div className="notice error">{message}</div>}

      <input className="title-input" value={title} onChange={(event) => setTitle(event.target.value)} />
      {item.kind !== "context" && <LabelsEditor rows={labels} labelIndex={labelIndex} onChange={setLabels} />}
      <MarkdownEditor
        key={editorKey}
        markdown={base.body}
        onChange={(markdown) => {
          setBody(markdown);
          setBodyTouched(true);
        }}
      />
    </div>
  );
}

function toRows(labels: Record<string, string>): LabelRow[] {
  return Object.entries(labels).map(([key, value]) => ({ key, value }));
}

function fromRows(rows: LabelRow[]): Record<string, string> {
  return Object.fromEntries(rows.filter((row) => row.key.trim()).map((row) => [row.key.trim(), row.value.trim()]));
}
