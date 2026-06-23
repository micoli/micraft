#!/usr/bin/env node
/**
 * Exports all skin presets from js/formats/minecraft/skin.ts as bbmodel files,
 * with embedded textures fetched from Mojang's bedrock-samples repository.
 * Usage: node scripts/export_skin_presets.mjs [output-dir]
 */

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import zlib from 'zlib';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKIN_TS = path.join(__dirname, '../../blockbench/js/formats/minecraft/skin.ts');
const OUT_DIR = process.argv[2] ? path.resolve(process.argv[2]) : path.join(__dirname, '../skin_presets');
const BEDROCK_TEXTURES = 'https://raw.githubusercontent.com/Mojang/bedrock-samples/preview/resource_pack/textures/';

// ─── Parsing ─────────────────────────────────────────────────────────────────

function extractPresets(source) {
	const presets = {};
	const regex = /^skin_presets\.(\w+)\s*=/gm;
	let match;

	while ((match = regex.exec(source)) !== null) {
		const name = match[1];
		let i = match.index + match[0].length;
		while (i < source.length && source[i] !== '{') i++;

		const block = extractBlock(source, i);
		presets[name] = parsePresetBlock(block);
	}

	return presets;
}

function extractBlock(source, start) {
	let depth = 0;
	let i = start;
	let inString = false;
	let stringChar = null;
	let inTemplate = false;

	while (i < source.length) {
		const ch = source[i];

		if (inTemplate) {
			if (ch === '`') inTemplate = false;
			else if (ch === '\\') i++;
		} else if (inString) {
			if (ch === stringChar) inString = false;
			else if (ch === '\\') i++;
		} else {
			if (ch === '`') { inTemplate = true; }
			else if (ch === '"' || ch === "'") { inString = true; stringChar = ch; }
			else if (ch === '{') { depth++; }
			else if (ch === '}') {
				depth--;
				if (depth === 0) return source.slice(start, i + 1);
			}
		}
		i++;
	}
	throw new Error('Unmatched brace at position ' + start);
}

function extractTemplateLiteral(source, start) {
	let i = start + 1;
	while (i < source.length) {
		const ch = source[i];
		if (ch === '`') return source.slice(start + 1, i);
		if (ch === '\\') i++;
		i++;
	}
	throw new Error('Unterminated template literal at position ' + start);
}

function parsePresetBlock(block) {
	const preset = {};

	for (const key of ['model_java', 'model_bedrock', 'model']) {
		const re = new RegExp(`\\b${key}\\s*:\\s*\``);
		const m = re.exec(block);
		if (m) {
			const pos = m.index + m[0].length - 1;
			preset[key] = extractTemplateLiteral(block, pos);
		}
	}

	const varRe = /\bvariants\s*:\s*\{/;
	const varMatch = varRe.exec(block);
	if (varMatch) {
		const varBlock = extractBlock(block, varMatch.index + varMatch[0].length - 1);
		preset.variants = parseVariantsBlock(varBlock);
	}

	return preset;
}

function parseVariantsBlock(block) {
	const variants = {};
	const re = /\b(\w+)\s*:\s*\{/g;
	let m;
	while ((m = re.exec(block)) !== null) {
		const varName = m[1];
		const inner = extractBlock(block, m.index + m[0].length - 1);
		const modelRe = /\bmodel\s*:\s*`/;
		const modelMatch = modelRe.exec(inner);
		if (modelMatch) {
			const pos = modelMatch.index + modelMatch[0].length - 1;
			variants[varName] = { model: extractTemplateLiteral(inner, pos) };
		}
	}
	return variants;
}

// ─── PNG utilities ────────────────────────────────────────────────────────────

// CRC32 table
const CRC_TABLE = (() => {
	const t = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
		t[n] = c;
	}
	return t;
})();

function crc32(buf) {
	let c = 0xFFFFFFFF;
	for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
	return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
	const typeBuf = Buffer.from(type, 'ascii');
	const lenBuf = Buffer.alloc(4);
	lenBuf.writeUInt32BE(data.length, 0);
	const crcInput = Buffer.concat([typeBuf, data]);
	const crcBuf = Buffer.alloc(4);
	crcBuf.writeUInt32BE(crc32(crcInput), 0);
	return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function blankPng(width, height) {
	const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);

	const ihdrData = Buffer.alloc(13);
	ihdrData.writeUInt32BE(width, 0);
	ihdrData.writeUInt32BE(height, 4);
	ihdrData[8]  = 8; // bit depth
	ihdrData[9]  = 6; // colour type: RGBA
	ihdrData[10] = 0; // compression
	ihdrData[11] = 0; // filter
	ihdrData[12] = 0; // interlace

	// Each row: filter byte 0 + width*4 zero bytes (fully transparent)
	const raw = Buffer.alloc(height * (1 + width * 4), 0);
	const compressed = zlib.deflateSync(raw);

	return Buffer.concat([sig, chunk('IHDR', ihdrData), chunk('IDAT', compressed), chunk('IEND', Buffer.alloc(0))]);
}

function bufferToDataURL(buf, mime = 'image/png') {
	return `data:${mime};base64,${buf.toString('base64')}`;
}

// ─── Texture fetching ─────────────────────────────────────────────────────────

const textureCache = new Map();

async function fetchTexture(texturePath) {
	if (textureCache.has(texturePath)) return textureCache.get(texturePath);

	const url = BEDROCK_TEXTURES + texturePath;
	try {
		const res = await fetch(url);
		if (!res.ok) throw new Error(`HTTP ${res.status}`);
		const buf = Buffer.from(await res.arrayBuffer());
		const dataUrl = bufferToDataURL(buf);
		textureCache.set(texturePath, dataUrl);
		return dataUrl;
	} catch (err) {
		process.stderr.write(`    fetch failed for ${texturePath}: ${err.message}\n`);
		return null;
	}
}

// Fetch all external_textures; composites multiple onto one canvas if needed.
// For simplicity, returns the first texture (skins use one texture layer).
async function resolveTexture(model) {
	if (model.external_textures && model.external_textures.length > 0) {
		return fetchTexture(model.external_textures[0]); // null on failure
	}
	return null;
}

// ─── bbmodel conversion ───────────────────────────────────────────────────────

function newUuid() {
	return crypto.randomUUID();
}

const FACE_NAMES = ['north', 'east', 'south', 'west', 'up', 'down'];

function boxUvFaces() {
	const faces = {};
	for (const f of FACE_NAMES) faces[f] = { uv: [0, 0, 0, 0], texture: 0 };
	return faces;
}

function convertPerFaceUV(uvData) {
	const faces = {};
	for (const face of FACE_NAMES) {
		if (uvData && uvData[face]) {
			const fd = uvData[face];
			const [u, v] = fd.uv;
			const [sw, sh] = fd.uv_size ?? [0, 0];
			const u2 = u + sw, v2 = v + sh;
			const finalUv = (face === 'up' || face === 'down') ? [u2, v2, u, v] : [u, v, u2, v2];
			faces[face] = { uv: finalUv, texture: 0 };
		} else {
			faces[face] = { uv: [0, 0, 0, 0], texture: null };
		}
	}
	return faces;
}

function buildBbmodel(presetName, modelJsonStr, textureDataUrl) {
	const model = JSON.parse(modelJsonStr);
	const width  = model.texturewidth  ?? 64;
	const height = model.textureheight ?? 64;

	const elements = [];
	const groups   = [];
	const boneMap  = {};

	for (const bone of (model.bones ?? [])) {
		const groupUuid = newUuid();
		const pivot = bone.pivot    ?? [0, 0, 0];
		const rot   = bone.rotation ?? [0, 0, 0];

		groups.push({
			name:       bone.name,
			origin:     [-pivot[0], pivot[1], pivot[2]],
			rotation:   [-rot[0], -rot[1], rot[2]],
			color:      bone.color ?? 0,
			uuid:       groupUuid,
			export:     true,
			isOpen:     true,
			locked:     false,
			visibility: true,
			autouv:     0,
			children:   [],
		});

		const cubeUuids = [];

		for (const cube of (bone.cubes ?? [])) {
			const cubeUuid  = newUuid();
			const isBoxUv   = Array.isArray(cube.uv);
			const origin    = cube.origin ?? [0, 0, 0];
			const size      = cube.size   ?? [0, 0, 0];

			const el = {
				name:                  cube.name ?? bone.name,
				box_uv:                isBoxUv,
				rescale:               false,
				locked:                false,
				render_order:          'default',
				allow_mirror_modeling: true,
				from:    [-(origin[0] + size[0]), origin[1], origin[2]],
				to:      [-origin[0], origin[1] + size[1], origin[2] + size[2]],
				autouv:  0,
				color:   bone.color ?? 0,
				visibility: cube.visibility !== false,
				faces:   isBoxUv ? boxUvFaces() : convertPerFaceUV(cube.uv),
				type:    'cube',
				uuid:    cubeUuid,
			};

			if (cube.inflate)       el.inflate   = cube.inflate;
			if (cube.mirror === true) el.mirror_uv = true;
			if (isBoxUv)            el.uv_offset = cube.uv;

			elements.push(el);
			cubeUuids.push(cubeUuid);
		}

		boneMap[bone.name] = { uuid: groupUuid, cubeUuids };
	}

	function makeOutlinerNode(boneName) {
		const entry = boneMap[boneName];
		const node  = { uuid: entry.uuid, isOpen: true, children: [...entry.cubeUuids] };
		for (const bone of (model.bones ?? [])) {
			if (bone.parent === boneName) node.children.push(makeOutlinerNode(bone.name));
		}
		return node;
	}

	const outliner = [];
	for (const bone of (model.bones ?? [])) {
		if (!bone.parent) outliner.push(makeOutlinerNode(bone.name));
	}

	const textureName = model.name ?? presetName;

	return {
		meta: {
			format_version: '5.0',
			model_format:   'free',
			box_uv:         elements.some(e => e.box_uv),
		},
		name: model.name ?? presetName,
		resolution: { width, height },
		elements,
		groups,
		outliner,
		...(textureDataUrl ? {
			textures: [{
				name:     textureName,
				path:     '',
				uuid:     newUuid(),
				saved:    false,
				internal: true,
				source:   textureDataUrl,
			}],
		} : {}),
	};
}

// ─── Main ─────────────────────────────────────────────────────────────────────

const source = fs.readFileSync(SKIN_TS, 'utf8');
const presets = extractPresets(source);

fs.mkdirSync(OUT_DIR, { recursive: true });

let exported = 0;
let failed   = 0;

async function processPreset(name, suffix, modelJson) {
	try {
		const model    = JSON.parse(modelJson);
		const texUrl   = await resolveTexture(model);
		const bbmodel  = buildBbmodel(name + suffix, modelJson, texUrl);
		const filename = `${name}${suffix}.bbmodel`;
		fs.writeFileSync(path.join(OUT_DIR, filename), JSON.stringify(bbmodel, null, '\t'));
		process.stdout.write(`  ✓ ${filename}\n`);
		exported++;
	} catch (err) {
		process.stderr.write(`  ✗ ${name}${suffix}: ${err.message}\n`);
		failed++;
	}
}

const tasks = [];

for (const [name, preset] of Object.entries(presets)) {
	if (preset.model_java && preset.model_bedrock) {
		tasks.push(processPreset(name, '_java',    preset.model_java));
		tasks.push(processPreset(name, '_bedrock', preset.model_bedrock));
	} else if (preset.model) {
		tasks.push(processPreset(name, '', preset.model));
	} else if (preset.variants) {
		for (const [varName, variant] of Object.entries(preset.variants)) {
			tasks.push(processPreset(name, `_${varName}`, variant.model));
		}
	} else {
		process.stderr.write(`  ? ${name}: no model data found, skipping\n`);
	}
}

await Promise.all(tasks);

console.log(`\nExported ${exported} file(s)${failed ? `, ${failed} failed` : ''} → ${OUT_DIR}`);
