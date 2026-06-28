#!/usr/bin/env node
// Reformats all .bbmodel files in resources/blocks/ to pretty-printed JSON (2-space indent)
// Run: node scripts/format_bbmodels.mjs

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BLOCKS_DIR = path.resolve(__dirname, '..', 'resources/blocks');

let ok = 0, errors = 0;

for (const blockName of fs.readdirSync(BLOCKS_DIR)) {
  const blockDir = path.join(BLOCKS_DIR, blockName);
  if (!fs.statSync(blockDir).isDirectory()) continue;

  const bbmodelPath = path.join(blockDir, `${blockName}.bbmodel`);
  if (!fs.existsSync(bbmodelPath)) continue;

  try {
    const raw = fs.readFileSync(bbmodelPath, 'utf8');
    const parsed = JSON.parse(raw);
    const formatted = JSON.stringify(parsed, null, 2) + '\n';
    if (raw !== formatted) {
      fs.writeFileSync(bbmodelPath, formatted);
      console.log(`  formatted: ${blockName}`);
    } else {
      console.log(`  ok:        ${blockName}`);
    }
    ok++;
  } catch (e) {
    console.error(`  ERROR: ${blockName}: ${e.message}`);
    errors++;
  }
}

console.log(`\nDone: ${ok} files, ${errors} errors.`);
