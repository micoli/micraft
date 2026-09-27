import type { LabelIndex } from "../api.ts";

export interface LabelRow {
  key: string;
  value: string;
}

interface Props {
  rows: LabelRow[];
  labelIndex: LabelIndex;
  onChange: (rows: LabelRow[]) => void;
}

const LABEL_KEY = /^[A-Z][A-Za-z -]{0,30}$/;

export function LabelsEditor({ rows, labelIndex, onChange }: Props) {
  const update = (index: number, patch: Partial<LabelRow>) =>
    onChange(rows.map((row, current) => (current === index ? { ...row, ...patch } : row)));

  return (
    <div className="labels-editor">
      <datalist id="label-keys">
        {Object.keys(labelIndex).map((key) => (
          <option key={key} value={key} />
        ))}
      </datalist>
      {rows.map((row, index) => (
        <div key={index} className="label-row">
          <input
            className={row.key && !LABEL_KEY.test(row.key) ? "invalid" : ""}
            list="label-keys"
            placeholder="Key"
            value={row.key}
            onChange={(event) => update(index, { key: event.target.value })}
          />
          <input
            list={`label-values-${index}`}
            placeholder="Value"
            value={row.value}
            onChange={(event) => update(index, { value: event.target.value })}
          />
          <datalist id={`label-values-${index}`}>
            {Object.keys(labelIndex[row.key] ?? {}).map((value) => (
              <option key={value} value={value} />
            ))}
          </datalist>
          <button title="Remove label" onClick={() => onChange(rows.filter((_, current) => current !== index))}>
            ✕
          </button>
        </div>
      ))}
      <button className="add-label" onClick={() => onChange([...rows, { key: "", value: "" }])}>
        + Add label
      </button>
    </div>
  );
}
