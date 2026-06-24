package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.GameLoop

@Serializable
data class PlayerMapInfo(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
)

@Serializable
data class NpcMapInfo(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
)

@Serializable
data class MapStateResponse(
    val gameTicks: Long,
    val players: List<PlayerMapInfo>,
    val npcs: List<NpcMapInfo>,
)

@Serializable
data class ChunkTerrainInfo(
    val cx: Int,
    val cz: Int,
    /** Flat 16×16 array (index = lx*16+lz), hex color or null for air/no color. */
    val colors: List<String?>,
)

fun Route.mapRoutes(gameLoop: GameLoop) {
    get("/api/map/state") {
        val players =
            gameLoop.getPlayerStates().map { s ->
                PlayerMapInfo(s.id, s.name, s.pos.x, s.pos.y, s.pos.z, s.orientation.yaw)
            }
        val npcs =
            gameLoop.getNpcStates().map { n ->
                NpcMapInfo(n.id, n.name, n.type, n.pos.x, n.pos.y, n.pos.z, n.yaw)
            }
        val response = MapStateResponse(gameLoop.getGameTicks(), players, npcs)
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    get("/api/map/terrain") {
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(gameLoop.terrainCache.cachedJson, ContentType.Application.Json)
    }

    get("/map") { call.respondText(MAP_HTML, ContentType.Text.Html) }
}

private val MAP_HTML =
    """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>MicCraft Map</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { background: #1a1a1a; color: #eee; font-family: monospace; display: flex; height: 100vh; overflow: hidden; }
  #sidebar { width: 220px; min-width: 220px; background: #111; padding: 12px; overflow-y: auto; border-right: 1px solid #333; display: flex; flex-direction: column; }
  #sidebar h2 { font-size: 13px; color: #aaa; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 1px; }
  #sidebar h3 { font-size: 11px; color: #888; margin: 10px 0 4px; text-transform: uppercase; }
  .entity-entry { font-size: 11px; padding: 5px 6px; border-bottom: 1px solid #222; cursor: pointer; border-radius: 3px; transition: background 0.1s; user-select: none; }
  .entity-entry:hover { background: #1e2a3a; }
  .entity-entry.tracking { background: #0d2040; border-left: 2px solid #6af; padding-left: 4px; }
  .entity-entry.tracking.npc-tracking { border-left-color: #fa6; }
  .entity-name { color: #6af; font-weight: bold; }
  .npc-name { color: #fa6; font-weight: bold; }
  .coords { color: #777; font-size: 10px; }
  .follow-badge { font-size: 9px; color: #6af; vertical-align: middle; }
  .follow-badge.npc { color: #fa6; }
  #time { font-size: 12px; color: #ccc; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #333; }
  #sidebar-footer { margin-top: auto; padding-top: 12px; border-top: 1px solid #333; }
  #fit-btn { width: 100%; padding: 5px; background: #1a2a3a; border: 1px solid #446; color: #aaf; font-family: monospace; font-size: 11px; cursor: pointer; border-radius: 3px; }
  #fit-btn:hover { background: #253a50; }
  #canvas-wrap { flex: 1; position: relative; overflow: hidden; }
  #map { display: block; cursor: grab; }
  #map.dragging { cursor: grabbing; }
  #map-controls { position: absolute; top: 10px; right: 10px; display: flex; flex-direction: column; gap: 4px; z-index: 10; }
  .ctrl-btn { width: 32px; height: 32px; background: rgba(15,15,25,0.88); border: 1px solid #444; color: #ccc; font-size: 20px; cursor: pointer; border-radius: 4px; display: flex; align-items: center; justify-content: center; user-select: none; line-height: 1; }
  .ctrl-btn:hover { background: rgba(40,60,90,0.9); color: #fff; }
  #map-coords { position: absolute; bottom: 8px; left: 8px; font-size: 10px; color: #667; pointer-events: none; background: rgba(0,0,0,0.4); padding: 2px 5px; border-radius: 3px; }
  #status { position: absolute; bottom: 8px; right: 8px; font-size: 10px; color: #555; }
</style>
</head>
<body>
<div id="sidebar">
  <div id="time">⏰ --:--</div>
  <h2>Players</h2>
  <div id="player-list"><span style="color:#555">none</span></div>
  <h3>NPCs</h3>
  <div id="npc-list"><span style="color:#555">none</span></div>
  <div id="sidebar-footer">
    <button id="fit-btn">⊡ Fit All</button>
  </div>
</div>
<div id="canvas-wrap">
  <canvas id="map"></canvas>
  <div id="map-controls">
    <div class="ctrl-btn" id="zoom-in-btn" title="Zoom in">+</div>
    <div class="ctrl-btn" id="zoom-out-btn" title="Zoom out">−</div>
  </div>
  <div id="map-coords">x: — z: —</div>
  <div id="status">connecting...</div>
</div>

<script>
const canvas = document.getElementById('map');
const ctx = canvas.getContext('2d');
const wrap = document.getElementById('canvas-wrap');

let state = { gameTicks: 0, players: [], npcs: [] };
let terrainData = [];

// Camera: world-space center + pixels-per-block
let camera = { x: 0, z: 0, pxPerBlock: 2 };
let followTarget = null; // { type: 'player'|'npc', id: string } | null
let autoFitDone = false;

// Terrain offscreen cache
const terrainCanvas = document.createElement('canvas');
const tCtx = terrainCanvas.getContext('2d');
let terrainDirty = true;

// Drag state
let isDragging = false;
let dragLast = { x: 0, y: 0 };

function worldToCanvas(wx, wz) {
  return [
    (wx - camera.x) * camera.pxPerBlock + canvas.width / 2,
    (wz - camera.z) * camera.pxPerBlock + canvas.height / 2,
  ];
}

function canvasToWorld(cx, cz) {
  return [
    (cx - canvas.width / 2) / camera.pxPerBlock + camera.x,
    (cz - canvas.height / 2) / camera.pxPerBlock + camera.z,
  ];
}

function resize() {
  canvas.width = wrap.clientWidth;
  canvas.height = wrap.clientHeight;
  terrainCanvas.width = canvas.width;
  terrainCanvas.height = canvas.height;
  terrainDirty = true;
  draw();
}
window.addEventListener('resize', resize);
resize();

function autoFitView() {
  let minX, maxX, minZ, maxZ;
  if (terrainData.length > 0) {
    minX = Math.min(...terrainData.map(c => c.cx)) * 16;
    maxX = (Math.max(...terrainData.map(c => c.cx)) + 1) * 16;
    minZ = Math.min(...terrainData.map(c => c.cz)) * 16;
    maxZ = (Math.max(...terrainData.map(c => c.cz)) + 1) * 16;
  } else {
    const entities = [...state.players, ...state.npcs];
    if (entities.length > 0) {
      minX = Math.min(...entities.map(e => e.x)) - 50;
      maxX = Math.max(...entities.map(e => e.x)) + 50;
      minZ = Math.min(...entities.map(e => e.z)) - 50;
      maxZ = Math.max(...entities.map(e => e.z)) + 50;
    } else {
      minX = -100; maxX = 100; minZ = -100; maxZ = 100;
    }
  }
  camera.x = (minX + maxX) / 2;
  camera.z = (minZ + maxZ) / 2;
  const pad = 40;
  camera.pxPerBlock = Math.min(
    canvas.width / (maxX - minX + pad),
    canvas.height / (maxZ - minZ + pad)
  );
  terrainDirty = true;
}

function zoomAt(factor, mx, mz) {
  const [wx, wz] = canvasToWorld(mx, mz);
  camera.pxPerBlock = Math.max(0.05, Math.min(64, camera.pxPerBlock * factor));
  // Keep world point under cursor fixed
  camera.x = wx - (mx - canvas.width / 2) / camera.pxPerBlock;
  camera.z = wz - (mz - canvas.height / 2) / camera.pxPerBlock;
  terrainDirty = true;
  draw();
}

// Mouse wheel zoom
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  if (followTarget) { followTarget = null; updateSidebar(); }
  const rect = canvas.getBoundingClientRect();
  zoomAt(e.deltaY < 0 ? 1.25 : 0.8, e.clientX - rect.left, e.clientY - rect.top);
}, { passive: false });

// Drag pan
canvas.addEventListener('mousedown', e => {
  if (e.button !== 0) return;
  isDragging = true;
  dragLast = { x: e.clientX, y: e.clientY };
  canvas.classList.add('dragging');
  if (followTarget) { followTarget = null; updateSidebar(); }
});
window.addEventListener('mousemove', e => {
  if (isDragging) {
    const dx = e.clientX - dragLast.x;
    const dy = e.clientY - dragLast.y;
    camera.x -= dx / camera.pxPerBlock;
    camera.z -= dy / camera.pxPerBlock;
    dragLast = { x: e.clientX, y: e.clientY };
    terrainDirty = true;
    draw();
  }
  const rect = canvas.getBoundingClientRect();
  const [wx, wz] = canvasToWorld(e.clientX - rect.left, e.clientY - rect.top);
  document.getElementById('map-coords').textContent = 'x:' + Math.round(wx) + '  z:' + Math.round(wz);
});
window.addEventListener('mouseup', () => {
  isDragging = false;
  canvas.classList.remove('dragging');
});

// Zoom buttons
document.getElementById('zoom-in-btn').addEventListener('click', () => {
  zoomAt(1.5, canvas.width / 2, canvas.height / 2);
});
document.getElementById('zoom-out-btn').addEventListener('click', () => {
  zoomAt(0.67, canvas.width / 2, canvas.height / 2);
});

// Fit all button
document.getElementById('fit-btn').addEventListener('click', () => {
  followTarget = null;
  autoFitView();
  updateSidebar();
  draw();
});

// Entity follow — delegated listeners
document.getElementById('player-list').addEventListener('click', e => {
  const entry = e.target.closest('.entity-entry[data-id]');
  if (entry) setFollow('player', entry.dataset.id);
});
document.getElementById('npc-list').addEventListener('click', e => {
  const entry = e.target.closest('.entity-entry[data-id]');
  if (entry) setFollow('npc', entry.dataset.id);
});

function setFollow(type, id) {
  if (followTarget && followTarget.type === type && followTarget.id === id) {
    followTarget = null; // toggle off
  } else {
    followTarget = { type, id };
    const entity = type === 'player'
      ? state.players.find(p => p.id === id)
      : state.npcs.find(n => n.id === id);
    if (entity) { camera.x = entity.x; camera.z = entity.z; terrainDirty = true; }
  }
  updateSidebar();
  draw();
}

function renderTerrain() {
  const W = terrainCanvas.width, H = terrainCanvas.height;
  tCtx.clearRect(0, 0, W, H);
  const size = Math.ceil(Math.max(1, camera.pxPerBlock));
  for (const chunk of terrainData) {
    for (let lx = 0; lx < 16; lx++) {
      for (let lz = 0; lz < 16; lz++) {
        const color = chunk.colors[lx * 16 + lz];
        if (!color) continue;
        const [px, pz] = worldToCanvas(chunk.cx * 16 + lx, chunk.cz * 16 + lz);
        if (px + size < 0 || px >= W || pz + size < 0 || pz >= H) continue;
        tCtx.fillStyle = color;
        tCtx.fillRect(px, pz, size, size);
      }
    }
  }
}

function draw() {
  const W = canvas.width, H = canvas.height;

  // Update follow target position
  if (followTarget) {
    const entity = followTarget.type === 'player'
      ? state.players.find(p => p.id === followTarget.id)
      : state.npcs.find(n => n.id === followTarget.id);
    if (entity && (entity.x !== camera.x || entity.z !== camera.z)) {
      camera.x = entity.x;
      camera.z = entity.z;
      terrainDirty = true;
    }
  }

  if (terrainDirty) {
    renderTerrain();
    terrainDirty = false;
  }

  ctx.fillStyle = '#111';
  ctx.fillRect(0, 0, W, H);
  ctx.drawImage(terrainCanvas, 0, 0);

  // Grid lines + labels
  const ppb = camera.pxPerBlock;
  const [worldLeft, worldTop] = canvasToWorld(0, 0);
  const [worldRight, worldBottom] = canvasToWorld(W, H);
  const gridStep = Math.pow(10, Math.ceil(Math.log10(80 / ppb)));

  ctx.strokeStyle = 'rgba(255,255,255,0.06)';
  ctx.lineWidth = 0.5;
  ctx.fillStyle = 'rgba(255,255,255,0.28)';
  ctx.font = '9px monospace';

  for (let gx = Math.ceil(worldLeft / gridStep) * gridStep; gx <= worldRight; gx += gridStep) {
    const [cx] = worldToCanvas(gx, 0);
    ctx.beginPath(); ctx.moveTo(cx, 0); ctx.lineTo(cx, H); ctx.stroke();
    ctx.fillText(String(Math.round(gx)), cx + 2, 10);
  }
  for (let gz = Math.ceil(worldTop / gridStep) * gridStep; gz <= worldBottom; gz += gridStep) {
    const [, cz] = worldToCanvas(0, gz);
    ctx.beginPath(); ctx.moveTo(0, cz); ctx.lineTo(W, cz); ctx.stroke();
    ctx.fillText(String(Math.round(gz)), 2, cz - 2);
  }

  // NPCs
  for (const n of state.npcs) {
    const [cx, cz] = worldToCanvas(n.x, n.z);
    const tracked = followTarget && followTarget.type === 'npc' && followTarget.id === n.id;
    if (tracked) {
      ctx.strokeStyle = '#ffcc44'; ctx.lineWidth = 2;
      ctx.strokeRect(cx - 8, cz - 8, 16, 16);
    }
    ctx.fillStyle = '#fa6';
    ctx.beginPath(); ctx.arc(cx, cz, 5, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = '#fa6'; ctx.font = '10px monospace';
    ctx.fillText(n.type, cx + 7, cz + 4);
  }

  // Players
  for (const p of state.players) {
    const [cx, cz] = worldToCanvas(p.x, p.z);
    const yawRad = (p.yaw * Math.PI) / 180;
    const tracked = followTarget && followTarget.type === 'player' && followTarget.id === p.id;
    if (tracked) {
      ctx.strokeStyle = '#44aaff'; ctx.lineWidth = 2;
      ctx.strokeRect(cx - 10, cz - 10, 20, 20);
    }
    ctx.save();
    ctx.translate(cx, cz);
    ctx.strokeStyle = '#6af'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(Math.sin(yawRad) * 12, -Math.cos(yawRad) * 12); ctx.stroke();
    ctx.fillStyle = '#6af';
    ctx.beginPath(); ctx.arc(0, 0, 6, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    ctx.fillStyle = '#8cf'; ctx.font = 'bold 11px monospace';
    ctx.fillText(p.name, cx + 9, cz + 4);
  }
}

function ticksToTime(ticks) {
  const DAY = 72000;
  const t = ((ticks % DAY) + DAY) % DAY;
  const h = Math.floor(t / 3000);
  const m = Math.floor((t % 3000) / 50);
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
}

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function updateSidebar() {
  document.getElementById('time').textContent = '⏰ ' + ticksToTime(state.gameTicks);

  const pl = document.getElementById('player-list');
  if (state.players.length === 0) {
    pl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    pl.innerHTML = state.players.map(p => {
      const tracked = followTarget && followTarget.type === 'player' && followTarget.id === p.id;
      return '<div class="entity-entry' + (tracked ? ' tracking' : '') + '" data-id="' + esc(p.id) + '">' +
        '<span class="entity-name">' + esc(p.name) + '</span>' +
        (tracked ? ' <span class="follow-badge">● follow</span>' : '') + '<br>' +
        '<span class="coords">' + Math.round(p.x) + ' ' + Math.round(p.y) + ' ' + Math.round(p.z) + '</span></div>';
    }).join('');
  }

  const nl = document.getElementById('npc-list');
  if (state.npcs.length === 0) {
    nl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    nl.innerHTML = state.npcs.map(n => {
      const tracked = followTarget && followTarget.type === 'npc' && followTarget.id === n.id;
      return '<div class="entity-entry' + (tracked ? ' tracking npc-tracking' : '') + '" data-id="' + esc(n.id) + '">' +
        '<span class="npc-name">' + esc(n.name) + '</span> <span style="color:#888">(' + esc(n.type) + ')</span>' +
        (tracked ? ' <span class="follow-badge npc">● follow</span>' : '') + '<br>' +
        '<span class="coords">' + Math.round(n.x) + ' ' + Math.round(n.y) + ' ' + Math.round(n.z) + '</span></div>';
    }).join('');
  }
}

async function pollState() {
  try {
    const r = await fetch('/api/map/state');
    if (r.ok) {
      state = await r.json();
      draw();
      updateSidebar();
      document.getElementById('status').textContent = 'updated ' + new Date().toLocaleTimeString();
    }
  } catch (e) {
    document.getElementById('status').textContent = 'error: ' + e.message;
  }
}

async function pollTerrain() {
  try {
    const r = await fetch('/api/map/terrain');
    if (r.ok) {
      terrainData = await r.json();
      terrainDirty = true;
      if (!autoFitDone && terrainData.length > 0) {
        autoFitDone = true;
        autoFitView();
      }
      draw();
    }
  } catch (e) { /* non-critical */ }
}

pollState();
pollTerrain();
setInterval(pollState, 1000);
setInterval(pollTerrain, 5000);
</script>
</body>
</html>
"""
        .trimIndent()
