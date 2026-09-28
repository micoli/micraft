import { lazy, Suspense, useState } from "react";
import { itemUrl, useApi, type ItemDetail as Item, type LabelIndex } from "../api.ts";
import { Dependencies } from "./Dependencies.tsx";
import { FilePath } from "./FilePath.tsx";
import { LabelBadges } from "./LabelBadges.tsx";
import { Markdown } from "./Markdown.tsx";
import { SkillPrompts } from "./SkillPrompts.tsx";

const ItemEditor = lazy(() => import("./ItemEditor.tsx").then((module) => ({ default: module.ItemEditor })));

interface Props {
  id: string | null;
  revision: number;
  labelIndex: LabelIndex;
  rootPrefixes: string[];
  onNavigate: (id: string) => void;
  onSaved: () => void;
}

export function ItemDetail({ id, revision, labelIndex, rootPrefixes, onNavigate, onSaved }: Props) {
  const { data: item, error } = useApi<Item>(id ? itemUrl(id) : null, revision);
  const [editingId, setEditingId] = useState<string | null>(null);

  if (!id) return <article className="item-detail empty">Select an item.</article>;
  if (error)
    return (
      <article className="item-detail error">
        Cannot load {id}: {error}
      </article>
    );
  if (!item || item.id !== id) return <article className="item-detail empty">Loading…</article>;

  if (editingId === id) {
    return (
      <article className="item-detail">
        <Suspense fallback={<p className="empty">Loading editor…</p>}>
          <ItemEditor
            item={item}
            labelIndex={labelIndex}
            onCancel={() => setEditingId(null)}
            onSaved={() => {
              setEditingId(null);
              onSaved();
            }}
          />
        </Suspense>
      </article>
    );
  }

  return (
    <article className="item-detail">
      <div className="detail-toolbar">
        <FilePath path={item.id} />
        <div className="actions">
          <SkillPrompts item={item} />
          <button className="primary" onClick={() => setEditingId(id)}>
            Edit
          </button>
        </div>
      </div>
      <header>
        <h1>{item.title}</h1>
        <LabelBadges labels={item.labels} />
        <Dependencies title="Blocked by" ids={item.blockedBy} onNavigate={onNavigate} />
        <Dependencies title="Blocks" ids={item.blocks} onNavigate={onNavigate} />
      </header>
      <Markdown source={item.body} basePath={item.id} rootPrefixes={rootPrefixes} onNavigate={onNavigate} />
    </article>
  );
}
