import {
  BlockTypeSelect,
  BoldItalicUnderlineToggles,
  CodeToggle,
  CreateLink,
  DiffSourceToggleWrapper,
  InsertTable,
  InsertThematicBreak,
  ListsToggle,
  MDXEditor,
  Separator,
  UndoRedo,
  diffSourcePlugin,
  headingsPlugin,
  linkDialogPlugin,
  linkPlugin,
  listsPlugin,
  markdownShortcutPlugin,
  quotePlugin,
  tablePlugin,
  thematicBreakPlugin,
  toolbarPlugin,
} from "@mdxeditor/editor";
import "@mdxeditor/editor/style.css";
import { useState } from "react";

interface Props {
  markdown: string;
  onChange: (markdown: string) => void;
}

const TO_MARKDOWN_OPTIONS = {
  bullet: "-",
  emphasis: "*",
  strong: "*",
  listItemIndent: "one",
} as const;

export function MarkdownEditor({ markdown, onChange }: Props) {
  const [error, setError] = useState<string>();
  const dark = window.matchMedia("(prefers-color-scheme: dark)").matches;

  return (
    <>
      {error && <div className="notice error">Markdown parse error, switch to source mode: {error}</div>}
      <MDXEditor
        className={dark ? "dark-theme markdown-editor" : "markdown-editor"}
        contentEditableClassName="markdown"
        markdown={markdown}
        suppressHtmlProcessing
        toMarkdownOptions={TO_MARKDOWN_OPTIONS}
        onChange={(value, initialMarkdownNormalize) => {
          if (initialMarkdownNormalize) return;
          onChange(value);
        }}
        onError={({ error: message }) => setError(message)}
        plugins={[
          headingsPlugin(),
          listsPlugin(),
          quotePlugin(),
          thematicBreakPlugin(),
          linkPlugin(),
          linkDialogPlugin(),
          tablePlugin(),
          markdownShortcutPlugin(),
          diffSourcePlugin({ viewMode: "rich-text", diffMarkdown: markdown }),
          toolbarPlugin({
            toolbarContents: () => (
              <DiffSourceToggleWrapper>
                <UndoRedo />
                <Separator />
                <BlockTypeSelect />
                <BoldItalicUnderlineToggles />
                <CodeToggle />
                <Separator />
                <ListsToggle />
                <CreateLink />
                <InsertTable />
                <InsertThematicBreak />
              </DiffSourceToggleWrapper>
            ),
          }),
        ]}
      />
    </>
  );
}
