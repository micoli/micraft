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
  #sidebar { width: 220px; min-width: 220px; background: #111; padding: 12px; overflow-y: auto; border-right: 1px solid #333; }
  #sidebar h2 { font-size: 13px; color: #aaa; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 1px; }
  #sidebar h3 { font-size: 11px; color: #888; margin: 10px 0 4px; text-transform: uppercase; }
  .player-entry { font-size: 11px; padding: 4px 0; border-bottom: 1px solid #222; }
  .player-name { color: #6af; font-weight: bold; }
  .npc-name { color: #fa6; font-weight: bold; }
  .coords { color: #777; font-size: 10px; }
  #time { font-size: 12px; color: #ccc; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #333; }
  #canvas-wrap { flex: 1; display: flex; align-items: center; justify-content: center; }
  canvas { display: block; }
  #status { position: fixed; bottom: 8px; right: 8px; font-size: 10px; color: #555; }
</style>
</head>
<body>
<div id="sidebar">
  <div id="time">⏰ --:--</div>
  <h2>Players</h2>
  <div id="player-list"><span style="color:#555">none</span></div>
  <h3>NPCs</h3>
  <div id="npc-list"><span style="color:#555">none</span></div>
</div>
<div id="canvas-wrap"><canvas id="map"></canvas></div>
<div id="status">connecting...</div>

<script>
const canvas = document.getElementById('map');
const ctx = canvas.getContext('2d');
const wrap = document.getElementById('canvas-wrap');
let state = { gameTicks: 0, players: [], npcs: [] };
let terrainData = [];

const terrainCanvas = document.createElement('canvas');
const tCtx = terrainCanvas.getContext('2d');
let terrainDirty = true;
let lastMinX, lastMinZ, lastScale, lastOffX, lastOffZ;

function resize() {
  canvas.width = wrap.clientWidth - 4;
  canvas.height = wrap.clientHeight - 4;
  terrainCanvas.width = canvas.width;
  terrainCanvas.height = canvas.height;
  terrainDirty = true;
  draw();
}
window.addEventListener('resize', resize);
resize();

function ticksToTime(ticks) {
  const DAY = 72000;
  const t = ((ticks % DAY) + DAY) % DAY;
  const h = Math.floor(t / 3000);
  const m = Math.floor((t % 3000) / 50);
  return String(h).padStart(2,'0') + ':' + String(m).padStart(2,'0');
}

function computeViewport() {
  const W = canvas.width, H = canvas.height;
  const entities = [...state.players.map(p=>({x:p.x,z:p.z})), ...state.npcs.map(n=>({x:n.x,z:n.z}))];

  let minX, maxX, minZ, maxZ;
  if (entities.length > 0) {
    minX = Math.min(...entities.map(e=>e.x));
    maxX = Math.max(...entities.map(e=>e.x));
    minZ = Math.min(...entities.map(e=>e.z));
    maxZ = Math.max(...entities.map(e=>e.z));
  } else if (terrainData.length > 0) {
    minX = Math.min(...terrainData.map(c=>c.cx)) * 16;
    maxX = (Math.max(...terrainData.map(c=>c.cx)) + 1) * 16;
    minZ = Math.min(...terrainData.map(c=>c.cz)) * 16;
    maxZ = (Math.max(...terrainData.map(c=>c.cz)) + 1) * 16;
  } else {
    minX = -50; maxX = 50; minZ = -50; maxZ = 50;
  }

  const padX = Math.max((maxX - minX) * 0.2, 20);
  const padZ = Math.max((maxZ - minZ) * 0.2, 20);
  minX -= padX; maxX += padX; minZ -= padZ; maxZ += padZ;

  const spanX = maxX - minX, spanZ = maxZ - minZ;
  const scale = Math.min(W / spanX, H / spanZ);
  const offX = (W - spanX * scale) / 2;
  const offZ = (H - spanZ * scale) / 2;
  return { minX, minZ, scale, offX, offZ };
}

function toCanvas(wx, wz, vp) {
  return [(wx - vp.minX) * vp.scale + vp.offX, (wz - vp.minZ) * vp.scale + vp.offZ];
}

function renderTerrain(vp) {
  tCtx.clearRect(0, 0, terrainCanvas.width, terrainCanvas.height);
  const bs = Math.max(1, vp.scale);
  for (const chunk of terrainData) {
    for (let lx = 0; lx < 16; lx++) {
      for (let lz = 0; lz < 16; lz++) {
        const color = chunk.colors[lx * 16 + lz];
        if (!color) continue;
        const [cx, cz] = toCanvas(chunk.cx * 16 + lx, chunk.cz * 16 + lz, vp);
        tCtx.fillStyle = color;
        tCtx.fillRect(cx, cz, bs, bs);
      }
    }
  }
}

function vpChanged(vp) {
  return vp.minX !== lastMinX || vp.minZ !== lastMinZ ||
         vp.scale !== lastScale || vp.offX !== lastOffX || vp.offZ !== lastOffZ;
}

function draw() {
  const W = canvas.width, H = canvas.height;
  const vp = computeViewport();

  if (terrainDirty || vpChanged(vp)) {
    renderTerrain(vp);
    lastMinX = vp.minX; lastMinZ = vp.minZ;
    lastScale = vp.scale; lastOffX = vp.offX; lastOffZ = vp.offZ;
    terrainDirty = false;
  }

  ctx.fillStyle = '#111';
  ctx.fillRect(0, 0, W, H);
  ctx.drawImage(terrainCanvas, 0, 0);

  const spanX = (W - 2 * vp.offX) / vp.scale;
  const spanZ = (H - 2 * vp.offZ) / vp.scale;
  ctx.strokeStyle = 'rgba(255,255,255,0.08)';
  ctx.lineWidth = 0.5;
  const gridStep = Math.pow(10, Math.round(Math.log10(Math.max(spanX, spanZ) / 5)));
  const gx0 = Math.ceil(vp.minX / gridStep) * gridStep;
  const gz0 = Math.ceil(vp.minZ / gridStep) * gridStep;
  for (let gx = gx0; gx <= vp.minX + spanX; gx += gridStep) {
    const [cx] = toCanvas(gx, vp.minZ, vp);
    ctx.beginPath(); ctx.moveTo(cx, 0); ctx.lineTo(cx, H); ctx.stroke();
  }
  for (let gz = gz0; gz <= vp.minZ + spanZ; gz += gridStep) {
    const [,cz] = toCanvas(vp.minX, gz, vp);
    ctx.beginPath(); ctx.moveTo(0, cz); ctx.lineTo(W, cz); ctx.stroke();
  }

  for (const n of state.npcs) {
    const [cx, cz] = toCanvas(n.x, n.z, vp);
    ctx.fillStyle = '#fa6';
    ctx.beginPath(); ctx.arc(cx, cz, 5, 0, Math.PI * 2); ctx.fill();
    ctx.font = '10px monospace';
    ctx.fillText(n.type, cx + 7, cz + 4);
  }

  for (const p of state.players) {
    const [cx, cz] = toCanvas(p.x, p.z, vp);
    const yawRad = (p.yaw * Math.PI) / 180;
    ctx.save();
    ctx.translate(cx, cz);
    ctx.strokeStyle = '#6af';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(Math.sin(yawRad) * 12, -Math.cos(yawRad) * 12);
    ctx.stroke();
    ctx.fillStyle = '#6af';
    ctx.beginPath(); ctx.arc(0, 0, 6, 0, Math.PI * 2); ctx.fill();
    ctx.restore();
    ctx.fillStyle = '#8cf';
    ctx.font = 'bold 11px monospace';
    ctx.fillText(p.name, cx + 9, cz + 4);
  }
}

function updateSidebar() {
  document.getElementById('time').textContent = '⏰ ' + ticksToTime(state.gameTicks);

  const pl = document.getElementById('player-list');
  if (state.players.length === 0) {
    pl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    pl.innerHTML = state.players.map(p =>
      '<div class="player-entry"><span class="player-name">' + esc(p.name) + '</span><br>' +
      '<span class="coords">' + Math.round(p.x) + ' ' + Math.round(p.y) + ' ' + Math.round(p.z) + '</span></div>'
    ).join('');
  }

  const nl = document.getElementById('npc-list');
  if (state.npcs.length === 0) {
    nl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    nl.innerHTML = state.npcs.map(n =>
      '<div class="player-entry"><span class="npc-name">' + esc(n.name) + '</span> <span style="color:#888">(' + esc(n.type) + ')</span><br>' +
      '<span class="coords">' + Math.round(n.x) + ' ' + Math.round(n.y) + ' ' + Math.round(n.z) + '</span></div>'
    ).join('');
  }
}

function esc(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
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
