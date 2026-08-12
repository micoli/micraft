import { useEffect, useRef } from "react";
import { basicSetup, EditorView } from "codemirror";
import { yamlSchema } from "codemirror-json-schema/yaml";

function useEditor(
  container: React.RefObject<HTMLDivElement | null>,
  content: string,
  schema: object | null,
  onChange: (v: string) => void,
) {
  const viewRef = useRef<EditorView | null>(null);

  useEffect(() => {
    if (!container.current) return;
    const schemaExtensions = schema ? yamlSchema(schema as Parameters<typeof yamlSchema>[0]) : [];
    const view = new EditorView({
      doc: content,
      extensions: [
        basicSetup,
        ...schemaExtensions,
        EditorView.theme({
          "&": { height: "100%", background: "#0E1726", color: "#CBD5E1" },
          ".cm-content": { fontFamily: "ui-monospace, SFMono-Regular, monospace", fontSize: "12px" },
          ".cm-gutters": { background: "#0E1726", borderRight: "1px solid #2E3A4E", color: "#4A5568" },
          ".cm-activeLineGutter": { background: "#1A222C" },
          ".cm-activeLine": { background: "#1A222C" },
          ".cm-selectionBackground": { background: "#3C50E0/30" },
          ".cm-cursor": { borderLeftColor: "#3C50E0" },
        }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) onChange(update.state.doc.toString());
        }),
      ],
      parent: container.current,
    });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [content, schema]);

  return viewRef;
}

export function Editor({
  content,
  schema,
  onChange,
}: {
  content: string;
  schema: object | null;
  onChange: (v: string) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  useEditor(containerRef, content, schema, onChange);
  return <div ref={containerRef} className="flex-1 overflow-auto h-full" />;
}
