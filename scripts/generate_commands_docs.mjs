#!/usr/bin/env node
/**
 * Generates the slash commands section in README.md from *Command.kt source files.
 * Reads `command`, `description`, `usage`, `options` fields via regex.
 * Replaces content between <!-- BEGIN_COMMANDS --> and <!-- END_COMMANDS --> markers.
 *
 * Usage: node scripts/generate_commands_docs.mjs
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const README = join(ROOT, 'README.md');

function findKtFiles(dir, results = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      findKtFiles(full, results);
    } else if (entry.endsWith('Command.kt')) {
      results.push(full);
    }
  }
  return results;
}

function extractString(src, field) {
  const m = src.match(new RegExp(`override\\s+val\\s+${field}\\s*=\\s*"([^"]*)"`, 's'));
  if (m) return m[1];
  // multi-line string concatenation
  const m2 = src.match(new RegExp(`override\\s+val\\s+${field}\\s*=\\s*\n\\s*"([^"]*)"`, 's'));
  return m2 ? m2[1] : null;
}

function extractOptions(src) {
  // static listOf("a", "b", ...)
  const m = src.match(/override\s+val\s+options\s*=\s*listOf\(([^)]*)\)/s);
  if (m) {
    return [...m[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
  }
  // dynamic (e.g. ItemType.entries) — leave empty, note in description
  if (/override\s+val\s+options/.test(src)) return ['dynamic'];
  return [];
}

function parseCommand(file, isPlugin) {
  const src = readFileSync(file, 'utf8');
  const command = extractString(src, 'command');
  if (!command) return null;
  const description =
    extractString(src, 'description') ||
    // multi-line description
    (() => {
      const m = src.match(/override\s+val\s+description\s*=\s*\n?\s*"([^"]*)"\s*\+?\s*\n?\s*"([^"]*)"/s);
      return m ? m[1] + m[2] : null;
    })() ||
    '';
  const usage = extractString(src, 'usage') || command;
  const options = extractOptions(src);
  return { command, description, usage, options, isPlugin };
}

function buildTable(commands) {
  const rows = commands
    .sort((a, b) => a.command.localeCompare(b.command))
    .map((c) => {
      const opts = c.options.length ? c.options.join(', ') : '—';
      const desc = c.description.replace(/\n/g, ' ');
      return `| \`${c.command.replaceAll('|','\\|')}\` | \`${c.usage.replaceAll('|','\\|')}\` | ${desc.replaceAll('|','\\|')} | ${opts.replaceAll('|','\\|')} |`;
    });
  return [
    '| Command | Usage | Description | Options / Autocomplete |',
    '|---------|-------|-------------|------------------------|',
    ...rows,
  ].join('\n');
}

const coreDir = join(ROOT, 'server/src/main/kotlin');
const pluginsDir = join(ROOT, 'plugins');

const coreFiles = findKtFiles(coreDir).filter((f) => !f.includes('/test/'));
const pluginFiles = readdirSync(pluginsDir).flatMap((name) => {
  const serverDir = join(pluginsDir, name, 'server');
  try {
    return findKtFiles(serverDir);
  } catch {
    return [];
  }
});

const core = coreFiles.map((f) => parseCommand(f, false)).filter(Boolean);
const plugins = pluginFiles.map((f) => parseCommand(f, true)).filter(Boolean);

const section = [
  '<!-- BEGIN_COMMANDS -->',
  '',
  '### Core commands',
  '',
  buildTable(core),
  '',
  '### Plugin commands',
  '',
  buildTable(plugins),
  '',
  '<!-- END_COMMANDS -->',
].join('\n');

let readme = readFileSync(README, 'utf8');
if (!readme.includes('<!-- BEGIN_COMMANDS -->') || !readme.includes('<!-- END_COMMANDS -->')) {
  console.error('ERROR: sentinel markers <!-- BEGIN_COMMANDS --> / <!-- END_COMMANDS --> not found in README.md');
  process.exit(1);
}
const replaced = readme.replace(
  /<!-- BEGIN_COMMANDS -->[\s\S]*?<!-- END_COMMANDS -->/,
  section,
);
writeFileSync(README, replaced);
console.log(`Done. ${core.length} core + ${plugins.length} plugin commands written to README.md`);
