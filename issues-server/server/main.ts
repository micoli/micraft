import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { extname, join, normalize, resolve, sep } from "node:path";
import { DEFAULT_PATHS, Repository, type ItemFilter, type ItemUpdate } from "./repository.ts";

// Inside the dev container the published port only reaches the server on all interfaces.
const HOST = process.env.ISSUES_HOST ?? (existsSync("/.dockerenv") ? "0.0.0.0" : "127.0.0.1");
const PORT = Number(process.env.ISSUES_PORT ?? 4380);
const ROOT = resolve(import.meta.dirname, process.env.ISSUES_ROOT ?? "../..");
const STATIC_DIR = resolve(import.meta.dirname, "..", process.env.ISSUES_STATIC_DIR ?? "client/dist");
const MAX_BODY_BYTES = 1024 * 1024;
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

const CONTENT_TYPES: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".json": "application/json",
};

const repository = new Repository(ROOT, {
  scratchDir: process.env.ISSUES_SCRATCH_DIR ?? DEFAULT_PATHS.scratchDir,
  adrDir: process.env.ISSUES_ADR_DIR ?? DEFAULT_PATHS.adrDir,
  contextFile: process.env.ISSUES_CONTEXT_FILE ?? DEFAULT_PATHS.contextFile,
});

createServer((request, response) => {
  handle(request, response).catch((error: unknown) => {
    console.error(error);
    sendJson(response, 500, { error: "internal error" });
  });
}).listen(PORT, HOST, () => {
  console.log(`issues-server on http://${HOST}:${PORT} (root ${ROOT}, ${JSON.stringify(repository.paths)})`);
});

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? "/", "http://localhost");
  if (!isLocalHost(request.headers.host)) return sendJson(response, 403, { error: "forbidden host" });
  if (request.method === "PUT") return handlePut(url, request, response);
  if (request.method !== "GET") return sendJson(response, 405, { error: "method not allowed" });
  if (!url.pathname.startsWith("/api/")) return serveStatic(url.pathname, response);

  if (url.pathname === "/api/config") return sendJson(response, 200, repository.paths);
  if (url.pathname === "/api/efforts") return sendJson(response, 200, await repository.efforts());
  if (url.pathname === "/api/labels") return sendJson(response, 200, await repository.labels());
  if (url.pathname === "/api/items") return sendJson(response, 200, await repository.list(toFilter(url.searchParams)));

  const itemId = /^\/api\/items\/(.+)$/.exec(url.pathname)?.[1];
  if (!itemId) return sendJson(response, 404, { error: "not found" });

  const item = await repository.get(decodeURIComponent(itemId));
  if (!item) return sendJson(response, 404, { error: "item not found" });
  return sendJson(response, 200, item);
}

async function handlePut(url: URL, request: IncomingMessage, response: ServerResponse): Promise<void> {
  const itemId = /^\/api\/items\/(.+)$/.exec(url.pathname)?.[1];
  if (!itemId) return sendJson(response, 404, { error: "not found" });
  if (!request.headers["content-type"]?.startsWith("application/json")) {
    return sendJson(response, 415, { error: "expected application/json" });
  }

  const update = await readJson<ItemUpdate>(request);
  if (!update) return sendJson(response, 400, { error: "invalid JSON body" });

  const result = await repository.save(decodeURIComponent(itemId), update);
  if (result.outcome === "not-found") return sendJson(response, 404, { error: "item not found" });
  if (result.outcome === "invalid") return sendJson(response, 400, { error: result.reason });
  if (result.outcome === "conflict") {
    return sendJson(response, 409, {
      error: "file changed on disk since it was loaded",
      item: result.item,
    });
  }
  return sendJson(response, 200, result.item);
}

async function readJson<T>(request: IncomingMessage): Promise<T | undefined> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    size += (chunk as Buffer).length;
    if (size > MAX_BODY_BYTES) return undefined;
    chunks.push(chunk as Buffer);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8")) as T;
  } catch {
    return undefined;
  }
}

function isLocalHost(host: string | undefined): boolean {
  if (!host) return false;
  return LOCAL_HOSTS.has(host.replace(/:\d+$/, ""));
}

function toFilter(params: URLSearchParams): ItemFilter {
  const labels: Record<string, string> = {};
  for (const [key, value] of params) {
    if (key.startsWith("label.") && value) labels[key.slice("label.".length)] = value;
  }
  return {
    kind: params.get("kind") || undefined,
    effort: params.get("effort") || undefined,
    q: params.get("q") || undefined,
    labels,
  };
}

async function serveStatic(pathname: string, response: ServerResponse): Promise<void> {
  const requested = normalize(join(STATIC_DIR, pathname));
  const file =
    requested.startsWith(STATIC_DIR + sep) && extname(requested) ? requested : join(STATIC_DIR, "index.html");
  try {
    const content = await readFile(file);
    response.writeHead(200, {
      "Content-Type": CONTENT_TYPES[extname(file)] ?? "application/octet-stream",
    });
    response.end(content);
  } catch {
    response.writeHead(404, { "Content-Type": "text/plain" });
    response.end("client not built: run `npm run build` in issues-server/ or use `npm run dev`");
  }
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
  });
  response.end(JSON.stringify(body));
}
