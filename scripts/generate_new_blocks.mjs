#!/usr/bin/env node
// Generates bbmodel + yaml + copies textures for new blocks into resources/new_blocks/
// Run: node scripts/generate_new_blocks.mjs

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const MIZUNOS = path.join(ROOT, 'resources/Mizunos-16-Craft-1.12-m8/assets/minecraft/textures/blocks');
const OUT_DIR = path.join(ROOT, 'resources/new_blocks');

// Block definition types:
// uniform: { type: 'uniform', texture }
// log:     { type: 'log', side, top }
// topside: { type: 'topside', bottom, top, side }  (grass-style, bottom/top/sides)
// multi4:  { type: 'multi4', north, eastwest, south, top, bottom }

const BLOCKS = [
  // --- Uniform ---
  { name: 'COBBLESTONE',        type: 'uniform', texture: 'cobblestone.png',        hardness: 6,   solid: true,  transparent: false },
  { name: 'MOSSY_COBBLESTONE',  type: 'uniform', texture: 'cobblestone_mossy.png',  hardness: 6,   solid: true,  transparent: false },
  { name: 'OAK_PLANKS',        type: 'uniform', texture: 'planks_oak.png',          hardness: 2,   solid: true,  transparent: false },
  { name: 'SPRUCE_PLANKS',     type: 'uniform', texture: 'planks_spruce.png',       hardness: 2,   solid: true,  transparent: false },
  { name: 'BIRCH_PLANKS',      type: 'uniform', texture: 'planks_birch.png',        hardness: 2,   solid: true,  transparent: false },
  { name: 'JUNGLE_PLANKS',     type: 'uniform', texture: 'planks_jungle.png',       hardness: 2,   solid: true,  transparent: false },
  { name: 'ACACIA_PLANKS',     type: 'uniform', texture: 'planks_acacia.png',       hardness: 2,   solid: true,  transparent: false },
  { name: 'BRICK',             type: 'uniform', texture: 'brick.png',               hardness: 6,   solid: true,  transparent: false },
  { name: 'GLASS',             type: 'uniform', texture: 'glass.png',               hardness: 0.3, solid: false, transparent: true  },
  { name: 'COAL_ORE',          type: 'uniform', texture: 'coal_ore.png',            hardness: 5,   solid: true,  transparent: false },
  { name: 'IRON_ORE',          type: 'uniform', texture: 'iron_ore.png',            hardness: 5,   solid: true,  transparent: false },
  { name: 'GOLD_ORE',          type: 'uniform', texture: 'gold_ore.png',            hardness: 5,   solid: true,  transparent: false },
  { name: 'DIAMOND_ORE',       type: 'uniform', texture: 'diamond_ore.png',         hardness: 5,   solid: true,  transparent: false },
  { name: 'REDSTONE_ORE',      type: 'uniform', texture: 'redstone_ore.png',        hardness: 5,   solid: true,  transparent: false },
  { name: 'LAPIS_ORE',         type: 'uniform', texture: 'lapis_ore.png',           hardness: 5,   solid: true,  transparent: false },
  { name: 'EMERALD_ORE',       type: 'uniform', texture: 'emerald_ore.png',         hardness: 5,   solid: true,  transparent: false },
  { name: 'QUARTZ_ORE',        type: 'uniform', texture: 'quartz_ore.png',          hardness: 5,   solid: true,  transparent: false },
  { name: 'IRON_BLOCK',        type: 'uniform', texture: 'iron_block.png',          hardness: 5,   solid: true,  transparent: false },
  { name: 'GOLD_BLOCK',        type: 'uniform', texture: 'gold_block.png',          hardness: 3,   solid: true,  transparent: false },
  { name: 'DIAMOND_BLOCK',     type: 'uniform', texture: 'diamond_block.png',       hardness: 5,   solid: true,  transparent: false },
  { name: 'COAL_BLOCK',        type: 'uniform', texture: 'coal_block.png',          hardness: 5,   solid: true,  transparent: false },
  { name: 'EMERALD_BLOCK',     type: 'uniform', texture: 'emerald_block.png',       hardness: 5,   solid: true,  transparent: false },
  { name: 'LAPIS_BLOCK',       type: 'uniform', texture: 'lapis_block.png',         hardness: 3,   solid: true,  transparent: false },
  { name: 'REDSTONE_BLOCK',    type: 'uniform', texture: 'redstone_block.png',      hardness: 5,   solid: true,  transparent: false },
  { name: 'OBSIDIAN',          type: 'uniform', texture: 'obsidian.png',            hardness: 50,  solid: true,  transparent: false },
  { name: 'ICE',               type: 'uniform', texture: 'ice.png',                 hardness: 0.5, solid: true,  transparent: true  },
  { name: 'PACKED_ICE',        type: 'uniform', texture: 'ice_packed.png',          hardness: 0.5, solid: true,  transparent: false },
  { name: 'NETHERRACK',        type: 'uniform', texture: 'netherrack.png',          hardness: 0.4, solid: true,  transparent: false },
  { name: 'SOUL_SAND',         type: 'uniform', texture: 'soul_sand.png',           hardness: 0.5, solid: true,  transparent: false },
  { name: 'GLOWSTONE',         type: 'uniform', texture: 'glowstone.png',           hardness: 0.3, solid: true,  transparent: false },
  { name: 'STONEBRICK',        type: 'uniform', texture: 'stonebrick.png',          hardness: 6,   solid: true,  transparent: false },
  { name: 'MOSSY_STONEBRICK',  type: 'uniform', texture: 'stonebrick_mossy.png',   hardness: 6,   solid: true,  transparent: false },
  { name: 'CRACKED_STONEBRICK',type: 'uniform', texture: 'stonebrick_cracked.png', hardness: 6,   solid: true,  transparent: false },
  { name: 'END_STONE',         type: 'uniform', texture: 'end_stone.png',           hardness: 3,   solid: true,  transparent: false },
  { name: 'NETHER_BRICK',      type: 'uniform', texture: 'nether_brick.png',        hardness: 2,   solid: true,  transparent: false },
  { name: 'CLAY',              type: 'uniform', texture: 'clay.png',                hardness: 0.6, solid: true,  transparent: false },
  { name: 'RED_SAND',          type: 'uniform', texture: 'red_sand.png',            hardness: 0.5, solid: true,  transparent: false },
  { name: 'MAGMA',             type: 'uniform', texture: 'magma.png',               hardness: 0.5, solid: true,  transparent: false },
  { name: 'PURPUR_BLOCK',      type: 'uniform', texture: 'purpur_block.png',        hardness: 6,   solid: true,  transparent: false },
  { name: 'SPONGE',            type: 'uniform', texture: 'sponge.png',              hardness: 0.6, solid: true,  transparent: false },
  { name: 'PRISMARINE',        type: 'uniform', texture: 'prismarine_rough.png',    hardness: 6,   solid: true,  transparent: false },
  { name: 'PRISMARINE_BRICKS', type: 'uniform', texture: 'prismarine_bricks.png',  hardness: 6,   solid: true,  transparent: false },
  { name: 'DARK_PRISMARINE',   type: 'uniform', texture: 'prismarine_dark.png',    hardness: 6,   solid: true,  transparent: false },
  { name: 'SEA_LANTERN',       type: 'uniform', texture: 'sea_lantern.png',         hardness: 0.3, solid: true,  transparent: false },
  { name: 'COARSE_DIRT',       type: 'uniform', texture: 'coarse_dirt.png',         hardness: 0.6, solid: true,  transparent: false },

  // --- Log-style (sides + top/bottom) ---
  { name: 'BIRCH_LOG',    type: 'log', side: 'log_birch.png',      top: 'log_birch_top.png',     hardness: 2, solid: true, transparent: false },
  { name: 'JUNGLE_LOG',   type: 'log', side: 'log_jungle.png',     top: 'log_jungle_top.png',    hardness: 2, solid: true, transparent: false },
  { name: 'ACACIA_LOG',   type: 'log', side: 'log_acacia.png',     top: 'log_acacia_top.png',    hardness: 2, solid: true, transparent: false },
  { name: 'BONE_BLOCK',   type: 'log', side: 'bone_block_side.png',top: 'bone_block_top.png',    hardness: 2, solid: true, transparent: false },
  { name: 'HAY_BLOCK',    type: 'log', side: 'hay_block_side.png', top: 'hay_block_top.png',     hardness: 0.5,solid:true, transparent: false },
  { name: 'QUARTZ_BLOCK', type: 'log', side: 'quartz_block_side.png',top:'quartz_block_top.png', hardness: 4, solid: true, transparent: false },
  { name: 'PURPUR_PILLAR',type: 'log', side: 'purpur_pillar.png',  top: 'purpur_pillar_top.png', hardness: 6, solid: true, transparent: false },

  // --- Top-side-bottom style (bottom / top / sides) ---
  { name: 'PUMPKIN',   type: 'topside', bottom: 'pumpkin_side.png', top: 'pumpkin_top.png', side: 'pumpkin_side.png',  hardness: 1,   solid: true, transparent: false },
  { name: 'MELON',     type: 'topside', bottom: 'melon_side.png',   top: 'melon_top.png',   side: 'melon_side.png',    hardness: 1,   solid: true, transparent: false },
  { name: 'TNT',       type: 'topside', bottom: 'tnt_bottom.png',   top: 'tnt_top.png',     side: 'tnt_side.png',      hardness: 0,   solid: true, transparent: false },
  { name: 'BOOKSHELF', type: 'topside', bottom: 'planks_oak.png',   top: 'planks_oak.png',  side: 'bookshelf.png',     hardness: 1.5, solid: true, transparent: false },

  // --- 4-face directional (N / E+W / S / top / bottom) ---
  { name: 'CRAFTING_TABLE', type: 'multi4',
    north: 'crafting_table_front.png', eastwest: 'crafting_table_side.png',
    south: 'crafting_table_front.png', top: 'crafting_table_top.png', bottom: 'planks_oak.png',
    hardness: 2.5, solid: true, transparent: false },
  { name: 'FURNACE', type: 'multi4',
    north: 'furnace_front_off.png', eastwest: 'furnace_side.png',
    south: 'furnace_side.png', top: 'furnace_top.png', bottom: 'furnace_top.png',
    hardness: 3.5, solid: true, transparent: false },
];

function padHex(n) {
  return n.toString(16).padStart(2, '0');
}

function makeUuid(blockIdx) {
  const h = padHex(blockIdx + 1);
  return `bb0001${h}-0000-0000-0000-000000000${h.padStart(5,'0')}`;
}

function uniqueTextures(files) {
  const seen = new Set();
  return files.filter(f => f && !seen.has(f) && seen.add(f));
}

function makeBbmodel(block, blockIdx) {
  const name = block.name;
  const uuid = makeUuid(blockIdx);
  const baseId = (blockIdx + 1) * 10;

  let textureFiles, faces;

  if (block.type === 'uniform') {
    textureFiles = [block.texture];
    faces = { north: 0, south: 0, east: 0, west: 0, up: 0, down: 0 };
  } else if (block.type === 'log') {
    textureFiles = [block.side, block.top];
    faces = { north: 0, south: 0, east: 0, west: 0, up: 1, down: 1 };
  } else if (block.type === 'topside') {
    // unique textures in order: bottom, top, side (dedup in case bottom===side etc.)
    const all = [block.bottom, block.top, block.side];
    textureFiles = uniqueTextures(all);
    const idxOf = f => textureFiles.indexOf(f);
    faces = {
      north: idxOf(block.side),
      south: idxOf(block.side),
      east:  idxOf(block.side),
      west:  idxOf(block.side),
      up:    idxOf(block.top),
      down:  idxOf(block.bottom),
    };
  } else if (block.type === 'multi4') {
    const all = [block.north, block.eastwest, block.south, block.top, block.bottom];
    textureFiles = uniqueTextures(all);
    const idxOf = f => textureFiles.indexOf(f);
    faces = {
      north: idxOf(block.north),
      south: idxOf(block.south),
      east:  idxOf(block.eastwest),
      west:  idxOf(block.eastwest),
      up:    idxOf(block.top),
      down:  idxOf(block.bottom),
    };
  }

  const textures = textureFiles.map((file, i) => ({
    id: baseId + i,
    name: file.replace('.png', ''),
    path: `api/models/blocks/${name}/${file}`,
    source: '',
  }));

  const makeFace = (idx) => ({ texture: idx, uv: [0, 0, 16, 16] });

  return {
    meta: { format_version: '4.0', model_format: 'free' },
    name,
    resolution: { width: 16, height: 16 },
    elements: [{
      uuid,
      name,
      render_type: 'solid',
      from: [0, 0, 0],
      to: [16, 16, 16],
      faces: {
        north: makeFace(faces.north),
        south: makeFace(faces.south),
        east:  makeFace(faces.east),
        west:  makeFace(faces.west),
        up:    makeFace(faces.up),
        down:  makeFace(faces.down),
      },
    }],
    textures,
  };
}

function makeYaml(block) {
  const h = block.hardness;
  return [
    `hardness: ${h}`,
    `solid: ${block.solid}`,
    `transparent: ${block.transparent}`,
    `minimapColor:`,
    `- 128`,
    `- 128`,
    `- 128`,
    `modelElement: ""`,
    `liquid: false`,
    `viscosity: 0`,
    `replaceable: false`,
    `vegetationHost: false`,
    `treeAllowed: true`,
    '',
  ].join('\n');
}

function getTextureFiles(block) {
  if (block.type === 'uniform') return [block.texture];
  if (block.type === 'log') return uniqueTextures([block.side, block.top]);
  if (block.type === 'topside') return uniqueTextures([block.bottom, block.top, block.side]);
  if (block.type === 'multi4') return uniqueTextures([block.north, block.eastwest, block.south, block.top, block.bottom]);
  return [];
}

let generated = 0;
let errors = 0;

for (let i = 0; i < BLOCKS.length; i++) {
  const block = BLOCKS[i];
  const blockDir = path.join(OUT_DIR, block.name);
  fs.mkdirSync(blockDir, { recursive: true });

  // Copy textures
  const texFiles = getTextureFiles(block);
  for (const tex of texFiles) {
    const src = path.join(MIZUNOS, tex);
    const dst = path.join(blockDir, tex);
    if (!fs.existsSync(src)) {
      console.error(`  MISSING texture: ${tex} (block ${block.name})`);
      errors++;
      continue;
    }
    fs.copyFileSync(src, dst);
  }

  // Write bbmodel
  const bbmodel = makeBbmodel(block, i);
  fs.writeFileSync(
    path.join(blockDir, `${block.name}.bbmodel`),
    JSON.stringify(bbmodel),
  );

  // Write yaml
  fs.writeFileSync(
    path.join(blockDir, `${block.name}.yaml`),
    makeYaml(block),
  );

  console.log(`  ${block.name} (${texFiles.join(', ')})`);
  generated++;
}

console.log(`\nDone: ${generated} blocks generated, ${errors} texture errors.`);
