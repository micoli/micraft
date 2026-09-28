import { useMemo, type MouseEvent } from "react";
import { Marked } from "marked";

const markdown = new Marked({
  renderer: {
    codespan({ text }) {
      if (!/^[\w./-]+\.md$/.test(text)) return false;
      return `<a class="md-ref" href="${text}"><code>${text}</code></a>`;
    },
  },
});

interface Props {
  source: string;
  basePath: string;
  rootPrefixes: string[];
  onNavigate: (id: string) => void;
}

export function Markdown({ source, basePath, rootPrefixes, onNavigate }: Props) {
  const html = useMemo(() => markdown.parse(source, { async: false }), [source]);

  const handleClick = (event: MouseEvent<HTMLDivElement>) => {
    const anchor = (event.target as HTMLElement).closest("a");
    const href = anchor?.getAttribute("href");
    if (!href || /^[a-z]+:|^#/i.test(href)) return;

    const target = resolveRepoPath(basePath, href.split("#")[0], rootPrefixes);
    if (!target.endsWith(".md")) return;
    event.preventDefault();
    onNavigate(target);
  };

  return <div className="markdown" onClick={handleClick} dangerouslySetInnerHTML={{ __html: html }} />;
}

function resolveRepoPath(basePath: string, href: string, rootPrefixes: string[]): string {
  if (href.startsWith("/")) return href.slice(1);
  if (rootPrefixes.includes(href.split("/")[0])) return href;
  return new URL(href, `http://repo/${basePath}`).pathname.slice(1);
}
