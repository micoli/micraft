import { useRef, useEffect } from "react";
import { EditorState } from "@codemirror/state";
import { EditorView, keymap } from "@codemirror/view";
import { defaultKeymap } from "@codemirror/commands";
import { javascript } from "@codemirror/lang-javascript";
import { syntaxHighlighting, HighlightStyle } from "@codemirror/language";
import { autocompletion, completionKeymap } from "@codemirror/autocomplete";
import type { CompletionContext, CompletionResult } from "@codemirror/autocomplete";
import { tags } from "@lezer/highlight";
import type { CommandInfo } from "../types";

const jexlHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: "#c792ea" },
  { tag: tags.string, color: "#c3e88d" },
  { tag: tags.number, color: "#f78c6c" },
  { tag: [tags.comment, tags.lineComment, tags.blockComment], color: "#546e7a", fontStyle: "italic" },
  { tag: tags.bool, color: "#ff5370" },
  { tag: tags.null, color: "#ff5370" },
  { tag: tags.operator, color: "#89ddff" },
  { tag: tags.punctuation, color: "#89ddff" },
  { tag: tags.function(tags.variableName), color: "#82aaff" },
  { tag: tags.variableName, color: "#86efac" },
  { tag: tags.propertyName, color: "#f07178" },
]);

const jexlTheme = EditorView.theme(
  {
    "&": { backgroundColor: "#0e0e0e", color: "#86efac", fontSize: "12px", fontFamily: "monospace", height: "100%" },
    ".cm-scroller": { overflow: "auto" },
    ".cm-content": { padding: "8px 12px", caretColor: "#86efac" },
    ".cm-gutters": { backgroundColor: "#0a0a0a", border: "none", color: "#555", paddingRight: "4px" },
    ".cm-activeLineGutter": { backgroundColor: "#161616" },
    ".cm-activeLine": { backgroundColor: "#161616" },
    ".cm-selectionBackground, ::selection": { backgroundColor: "#2d5a2d !important" },
    ".cm-cursor": { borderLeftColor: "#86efac" },
    ".cm-tooltip": { backgroundColor: "#1e1e1e", border: "1px solid #444", borderRadius: "4px" },
    ".cm-tooltip-autocomplete": { backgroundColor: "#1e1e1e" },
    ".cm-tooltip-autocomplete ul li": { padding: "3px 8px", color: "#ccc" },
    ".cm-tooltip-autocomplete ul li[aria-selected]": { backgroundColor: "#1d4a2d", color: "#86efac" },
    ".cm-completionLabel": { fontFamily: "monospace", fontSize: "11px" },
    ".cm-completionDetail": { color: "#888", fontSize: "10px", marginLeft: "8px" },
  },
  { dark: true },
);

interface JexlEditorProps {
  value: string;
  onChange: (v: string) => void;
  commands: CommandInfo[];
  attackKeys: string[];
}

type MacroContextVar = { name: string; type: string; children?: string[] };

export function JexlEditor({ value, onChange, commands, attackKeys }: JexlEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  const commandsRef = useRef(commands);
  const attackKeysRef = useRef(attackKeys);
  const contextVarsRef = useRef<MacroContextVar[]>([]);

  useEffect(() => {
    onChangeRef.current = onChange;
  });
  useEffect(() => {
    commandsRef.current = commands;
  }, [commands]);
  useEffect(() => {
    attackKeysRef.current = attackKeys;
  }, [attackKeys]);

  useEffect(() => {
    fetch("/api/macros/context")
      .then((r) => r.json())
      .then((data: MacroContextVar[]) => {
        contextVarsRef.current = data;
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!containerRef.current) return;

    const completionSource = (context: CompletionContext): CompletionResult | null => {
      const text = context.state.doc.sliceString(0, context.pos);
      const sendMatch = text.match(/send\(["']([^"']*)$/);
      if (sendMatch) {
        const partial = sendMatch[1];
        return {
          from: context.pos - partial.length,
          options: commandsRef.current
            .filter((c) => c.command.startsWith(partial))
            .map((c) => ({ label: c.command, detail: c.description, type: "function" })),
        };
      }
      const actionMatch = text.match(/action\(["']([^"']*)$/);
      if (actionMatch) {
        const partial = actionMatch[1];
        return {
          from: context.pos - partial.length,
          options: attackKeysRef.current
            .filter((k) => k.startsWith(partial))
            .map((k) => ({ label: k, type: "keyword" })),
        };
      }
      const positionPropMatch = text.match(/(\w+)\.(\w*)$/);
      if (positionPropMatch) {
        const [, parentName, partial] = positionPropMatch;
        const parent = contextVarsRef.current.find((v) => v.name === parentName);
        const children = parent?.children ?? [];
        if (children.length > 0) {
          return {
            from: context.pos - partial.length,
            options: children
              .filter((p) => p.startsWith(partial))
              .map((p) => ({ label: p, detail: "Float", type: "variable" })),
          };
        }
      }
      const varMatch = text.match(/(?:^|[^.\w])(\w*)$/);
      if (varMatch) {
        const partial = varMatch[1];
        if (partial.length === 0 && !context.explicit) return null;
        const options = contextVarsRef.current
          .filter((v) => v.name.startsWith(partial))
          .map((v) => ({ label: v.name, detail: v.type, type: "variable" }));
        if (options.length === 0) return null;
        return { from: context.pos - partial.length, options };
      }
      return null;
    };

    const state = EditorState.create({
      doc: value,
      extensions: [
        javascript(),
        syntaxHighlighting(jexlHighlightStyle),
        autocompletion({ override: [completionSource] }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) onChangeRef.current(update.state.doc.toString());
        }),
        keymap.of([...completionKeymap, ...defaultKeymap]),
        jexlTheme,
        EditorView.lineWrapping,
      ],
    });

    const view = new EditorView({ state, parent: containerRef.current });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current !== value) {
      view.dispatch({ changes: { from: 0, to: current.length, insert: value } });
    }
  }, [value]);

  return (
    <div
      ref={containerRef}
      className="flex-1 min-h-0 overflow-hidden rounded border border-[#333] focus-within:border-[#555]"
    />
  );
}
