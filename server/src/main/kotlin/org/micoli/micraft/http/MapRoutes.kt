package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.GameLoop
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.weather.WeatherZoneInfo

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
    val weatherZones: List<WeatherZoneInfo> = emptyList(),
)

@Serializable
data class VoronoiCellInfo(val x: Int, val z: Int, val biome: String, val color: String)

@Serializable
data class ChunkTerrainInfo(
    val cx: Int,
    val cz: Int,
    /** Flat 16×16 array (index = lx*16+lz), hex color or null for air/no color. */
    val colors: List<String?>,
    val avgHeight: Int? = null,
)

@Serializable
data class HouseMapInfo(val x: Int, val z: Int, val type: String, val width: Int, val depth: Int)

@Serializable
data class RoadSegmentInfo(val x1: Float, val z1: Float, val x2: Float, val z2: Float)

fun Route.mapRoutes(gameLoop: GameLoop) {
    if (!(System.getenv("MICRAFT_MAP_ENABLED") != "0")) {
        return
    }
    get("/api/map/state") {
        val players =
            gameLoop.getPlayerStates().map { s ->
                PlayerMapInfo(s.id, s.name, s.pos.x, s.pos.y, s.pos.z, s.orientation.yaw)
            }
        val npcs =
            gameLoop.getNpcStates().map { n ->
                NpcMapInfo(n.id, n.name, n.type, n.pos.x, n.pos.y, n.pos.z, n.yaw)
            }
        val response =
            MapStateResponse(gameLoop.getGameTicks(), players, npcs, gameLoop.getWeatherZones())
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    get("/api/map/terrain") {
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(gameLoop.terrainCache.cachedJson, ContentType.Application.Json)
    }

    get("/api/map/voronoi") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: (50 * 16)
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val cells = gen?.voronoi?.cells(cx, cz, radius)?.map { cell ->
            val rgb = BlockRegistry.get(cell.biome.surface).minimapColor
            val color = "#%02x%02x%02x".format(rgb[0], rgb[1], rgb[2])
            VoronoiCellInfo(cell.seedX, cell.seedZ, cell.biome.id, color)
        } ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(cells), ContentType.Application.Json)
    }

    get("/api/map/houses") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 800
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val houses =
            gen?.houseZones
                ?.housesInArea(cx - radius, cz - radius, cx + radius, cz + radius)
                ?.map { HouseMapInfo(it.anchorX, it.anchorZ, it.typeCfg.id, it.width, it.depth) }
                ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(houses), ContentType.Application.Json)
    }

    get("/api/map/roads") {
        val cx = call.request.queryParameters["cx"]?.toIntOrNull() ?: 0
        val cz = call.request.queryParameters["cz"]?.toIntOrNull() ?: 0
        val radius = call.request.queryParameters["radius"]?.toIntOrNull() ?: 800
        val gen = gameLoop.getChunkGenerator() as? ProceduralChunkGenerator
        val segments =
            gen?.roadVoronoi
                ?.roadVertexSegmentsInArea(cx - radius, cz - radius, cx + radius, cz + radius)
                ?.map { RoadSegmentInfo(it.x1.toFloat(), it.z1.toFloat(), it.x2.toFloat(), it.z2.toFloat()) }
                ?: emptyList()
        call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(Json.encodeToString(segments), ContentType.Application.Json)
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
  .layer-toggles { display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px; padding-bottom: 10px; border-bottom: 1px solid #333; }
  .layer-toggles label { font-size: 11px; color: #bbb; display: flex; align-items: center; gap: 6px; cursor: pointer; user-select: none; }
  .layer-toggles label:hover { color: #eee; }
  .layer-toggles input[type=checkbox] { accent-color: #6af; width: 13px; height: 13px; cursor: pointer; }
  .layer-toggles input[type=checkbox]:disabled { opacity: 0.35; cursor: default; }
  .layer-toggles label.disabled { color: #555; cursor: default; }
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
  <h2>Layers</h2>
  <div class="layer-toggles">
    <label><input type="checkbox" id="layer-voronoi" checked> Biome borders</label>
    <label><input type="checkbox" id="layer-contours" checked> Contour lines</label>
    <label><input type="checkbox" id="layer-vegetation" checked> Vegetation</label>
    <label><input type="checkbox" id="layer-houses" checked> Houses</label>
    <label><input type="checkbox" id="layer-players" checked> Players</label>
    <label><input type="checkbox" id="layer-npcs" checked> NPCs</label>
    <label><input type="checkbox" id="layer-routes" checked> Routes</label>
  </div>
  <h3>Players</h3>
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
let voronoiCells = [];
let housesData = [];
let roadsData = [];
let housesFetchCenter = { x: NaN, z: NaN };
let roadsFetchCenter = { x: NaN, z: NaN };

const layers = { voronoi: true, contours: true, vegetation: true, houses: true, players: true, npcs: true, routes: true };
['voronoi', 'contours', 'vegetation', 'houses', 'players', 'npcs', 'routes'].forEach(function(k) {
  document.getElementById('layer-' + k).addEventListener('change', function(e) {
    layers[k] = e.target.checked;
    draw();
  });
});

// Camera: world-space center + pixels-per-block
let camera = { x: 0, z: 0, pxPerBlock: 2 };
let followTarget = null;
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
  var minX, maxX, minZ, maxZ;
  if (terrainData.length > 0) {
    minX = Math.min.apply(null, terrainData.map(function(c) { return c.cx; })) * 16;
    maxX = (Math.max.apply(null, terrainData.map(function(c) { return c.cx; })) + 1) * 16;
    minZ = Math.min.apply(null, terrainData.map(function(c) { return c.cz; })) * 16;
    maxZ = (Math.max.apply(null, terrainData.map(function(c) { return c.cz; })) + 1) * 16;
  } else {
    var entities = state.players.concat(state.npcs);
    if (entities.length > 0) {
      minX = Math.min.apply(null, entities.map(function(e) { return e.x; })) - 50;
      maxX = Math.max.apply(null, entities.map(function(e) { return e.x; })) + 50;
      minZ = Math.min.apply(null, entities.map(function(e) { return e.z; })) - 50;
      maxZ = Math.max.apply(null, entities.map(function(e) { return e.z; })) + 50;
    } else {
      minX = -100; maxX = 100; minZ = -100; maxZ = 100;
    }
  }
  camera.x = (minX + maxX) / 2;
  camera.z = (minZ + maxZ) / 2;
  var pad = 40;
  camera.pxPerBlock = Math.min(
    canvas.width / (maxX - minX + pad),
    canvas.height / (maxZ - minZ + pad)
  );
  terrainDirty = true;
}

function zoomAt(factor, mx, mz) {
  var wc = canvasToWorld(mx, mz);
  camera.pxPerBlock = Math.max(0.05, Math.min(64, camera.pxPerBlock * factor));
  camera.x = wc[0] - (mx - canvas.width / 2) / camera.pxPerBlock;
  camera.z = wc[1] - (mz - canvas.height / 2) / camera.pxPerBlock;
  terrainDirty = true;
  draw();
}

canvas.addEventListener('wheel', function(e) {
  e.preventDefault();
  if (followTarget) { followTarget = null; updateSidebar(); }
  var rect = canvas.getBoundingClientRect();
  zoomAt(e.deltaY < 0 ? 1.25 : 0.8, e.clientX - rect.left, e.clientY - rect.top);
}, { passive: false });

canvas.addEventListener('mousedown', function(e) {
  if (e.button !== 0) return;
  isDragging = true;
  dragLast = { x: e.clientX, y: e.clientY };
  canvas.classList.add('dragging');
  if (followTarget) { followTarget = null; updateSidebar(); }
});
window.addEventListener('mousemove', function(e) {
  if (isDragging) {
    var dx = e.clientX - dragLast.x;
    var dy = e.clientY - dragLast.y;
    camera.x -= dx / camera.pxPerBlock;
    camera.z -= dy / camera.pxPerBlock;
    dragLast = { x: e.clientX, y: e.clientY };
    terrainDirty = true;
    draw();
  }
  var rect = canvas.getBoundingClientRect();
  var wc = canvasToWorld(e.clientX - rect.left, e.clientY - rect.top);
  document.getElementById('map-coords').textContent = 'x:' + Math.round(wc[0]) + '  z:' + Math.round(wc[1]);
});
window.addEventListener('mouseup', function() {
  isDragging = false;
  canvas.classList.remove('dragging');
});

document.getElementById('zoom-in-btn').addEventListener('click', function() {
  zoomAt(1.5, canvas.width / 2, canvas.height / 2);
});
document.getElementById('zoom-out-btn').addEventListener('click', function() {
  zoomAt(0.67, canvas.width / 2, canvas.height / 2);
});

document.getElementById('fit-btn').addEventListener('click', function() {
  followTarget = null;
  autoFitView();
  updateSidebar();
  draw();
});

document.getElementById('player-list').addEventListener('click', function(e) {
  var entry = e.target.closest('.entity-entry[data-id]');
  if (entry) setFollow('player', entry.dataset.id);
});
document.getElementById('npc-list').addEventListener('click', function(e) {
  var entry = e.target.closest('.entity-entry[data-id]');
  if (entry) setFollow('npc', entry.dataset.id);
});

function setFollow(type, id) {
  if (followTarget && followTarget.type === type && followTarget.id === id) {
    followTarget = null;
  } else {
    followTarget = { type: type, id: id };
    var entity = type === 'player'
      ? state.players.find(function(p) { return p.id === id; })
      : state.npcs.find(function(n) { return n.id === id; });
    if (entity) { camera.x = entity.x; camera.z = entity.z; terrainDirty = true; }
  }
  updateSidebar();
  draw();
}

function renderTerrain() {
  var W = terrainCanvas.width, H = terrainCanvas.height;
  tCtx.clearRect(0, 0, W, H);
  var size = Math.ceil(Math.max(1, camera.pxPerBlock));
  for (var ci = 0; ci < terrainData.length; ci++) {
    var chunk = terrainData[ci];
    for (var lx = 0; lx < 16; lx++) {
      for (var lz = 0; lz < 16; lz++) {
        var color = chunk.colors[lx * 16 + lz];
        if (!color) continue;
        var pc = worldToCanvas(chunk.cx * 16 + lx, chunk.cz * 16 + lz);
        if (pc[0] + size < 0 || pc[0] >= W || pc[1] + size < 0 || pc[1] >= H) continue;
        tCtx.fillStyle = color;
        tCtx.fillRect(pc[0], pc[1], size, size);
      }
    }
  }
}

var VEGETATION_TINT = {
  forest: 'rgba(30,120,30,0.28)',
  plains: 'rgba(80,160,80,0.14)',
  tundra: 'rgba(180,210,200,0.12)'
};

function drawVegetation() {
  if (!voronoiCells.length) return;
  var estR = Math.sqrt(3200 * 3200 / voronoiCells.length) * 0.65 * camera.pxPerBlock;
  for (var i = 0; i < voronoiCells.length; i++) {
    var cell = voronoiCells[i];
    var tint = VEGETATION_TINT[cell.biome];
    if (!tint) continue;
    var pc = worldToCanvas(cell.x, cell.z);
    ctx.fillStyle = tint;
    ctx.beginPath();
    ctx.arc(pc[0], pc[1], estR, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawVoronoiBorders() {
  if (voronoiCells.length < 2) return;
  var n = voronoiCells.length;
  var estR = Math.sqrt(3200 * 3200 / n);
  var maxDist = estR * 3.2;
  ctx.strokeStyle = 'rgba(0,0,0,0.38)';
  ctx.lineWidth = 1;
  for (var i = 0; i < n; i++) {
    for (var j = i + 1; j < n; j++) {
      var ci = voronoiCells[i], cj = voronoiCells[j];
      var dx = cj.x - ci.x, dz = cj.z - ci.z;
      var dist = Math.sqrt(dx * dx + dz * dz);
      if (dist > maxDist) continue;
      var mx = (ci.x + cj.x) / 2, mz = (ci.z + cj.z) / 2;
      var px = -dz / dist, pz = dx / dist;
      var L = dist * 0.55;
      var a = worldToCanvas(mx - px * L, mz - pz * L);
      var b = worldToCanvas(mx + px * L, mz + pz * L);
      ctx.beginPath();
      ctx.moveTo(a[0], a[1]);
      ctx.lineTo(b[0], b[1]);
      ctx.stroke();
    }
  }
}

function drawContours() {
  if (!terrainData.length) return;
  var heightMap = {};
  for (var i = 0; i < terrainData.length; i++) {
    var c = terrainData[i];
    if (c.avgHeight != null) heightMap[c.cx + ',' + c.cz] = c.avgHeight;
  }
  ctx.strokeStyle = 'rgba(220,220,220,0.45)';
  ctx.lineWidth = 0.6;
  for (var i = 0; i < terrainData.length; i++) {
    var chunk = terrainData[i];
    if (chunk.avgHeight == null) continue;
    var hBand = Math.floor(chunk.avgHeight / 10);
    var rh = heightMap[(chunk.cx + 1) + ',' + chunk.cz];
    if (rh != null && Math.floor(rh / 10) !== hBand) {
      var a = worldToCanvas((chunk.cx + 1) * 16, chunk.cz * 16);
      var b = worldToCanvas((chunk.cx + 1) * 16, (chunk.cz + 1) * 16);
      ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke();
    }
    var bh = heightMap[chunk.cx + ',' + (chunk.cz + 1)];
    if (bh != null && Math.floor(bh / 10) !== hBand) {
      var a = worldToCanvas(chunk.cx * 16, (chunk.cz + 1) * 16);
      var b = worldToCanvas((chunk.cx + 1) * 16, (chunk.cz + 1) * 16);
      ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke();
    }
  }
}

function drawRoads() {
  if (!roadsData.length) return;
  ctx.strokeStyle = 'rgba(180,140,60,0.85)';
  ctx.lineWidth = Math.max(1.5, camera.pxPerBlock * 3);
  ctx.lineCap = 'round';
  for (var i = 0; i < roadsData.length; i++) {
    var seg = roadsData[i];
    var a = worldToCanvas(seg.x1, seg.z1);
    var b = worldToCanvas(seg.x2, seg.z2);
    ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke();
  }
  ctx.lineCap = 'butt';
}

function drawHouses() {
  var ppb = camera.pxPerBlock;
  for (var i = 0; i < housesData.length; i++) {
    var h = housesData[i];
    var a = worldToCanvas(h.x, h.z);
    var w = h.width * ppb, d = h.depth * ppb;
    ctx.fillStyle = 'rgba(255,200,100,0.32)';
    ctx.fillRect(a[0], a[1], w, d);
    ctx.strokeStyle = '#c80';
    ctx.lineWidth = 1;
    ctx.strokeRect(a[0], a[1], w, d);
  }
}

function draw() {
  var W = canvas.width, H = canvas.height;

  if (followTarget) {
    var entity = followTarget.type === 'player'
      ? state.players.find(function(p) { return p.id === followTarget.id; })
      : state.npcs.find(function(n) { return n.id === followTarget.id; });
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

  if (layers.vegetation) drawVegetation();
  if (layers.voronoi) drawVoronoiBorders();
  if (layers.contours) drawContours();
  if (layers.routes) drawRoads();
  if (layers.houses) drawHouses();

  // Grid lines + labels
  var ppb = camera.pxPerBlock;
  var worldLeft = canvasToWorld(0, 0)[0], worldTop = canvasToWorld(0, 0)[1];
  var worldRight = canvasToWorld(W, H)[0], worldBottom = canvasToWorld(W, H)[1];
  var gridStep = Math.pow(10, Math.ceil(Math.log10(80 / ppb)));

  ctx.strokeStyle = 'rgba(255,255,255,0.06)';
  ctx.lineWidth = 0.5;
  ctx.fillStyle = 'rgba(255,255,255,0.28)';
  ctx.font = '9px monospace';

  for (var gx = Math.ceil(worldLeft / gridStep) * gridStep; gx <= worldRight; gx += gridStep) {
    var cx = worldToCanvas(gx, 0)[0];
    ctx.beginPath(); ctx.moveTo(cx, 0); ctx.lineTo(cx, H); ctx.stroke();
    ctx.fillText(String(Math.round(gx)), cx + 2, 10);
  }
  for (var gz = Math.ceil(worldTop / gridStep) * gridStep; gz <= worldBottom; gz += gridStep) {
    var cz = worldToCanvas(0, gz)[1];
    ctx.beginPath(); ctx.moveTo(0, cz); ctx.lineTo(W, cz); ctx.stroke();
    ctx.fillText(String(Math.round(gz)), 2, cz - 2);
  }

  // Weather zones
  var WEATHER_COLORS = { RAIN: 'rgba(80,120,255,0.18)', STORM: 'rgba(100,0,200,0.22)', SNOW: 'rgba(200,230,255,0.2)', FOG: 'rgba(150,150,150,0.18)' };
  var WEATHER_STROKE = { RAIN: 'rgba(80,120,255,0.55)', STORM: 'rgba(100,0,200,0.55)', SNOW: 'rgba(200,230,255,0.6)', FOG: 'rgba(140,140,140,0.5)' };
  var zones = state.weatherZones || [];
  for (var zi = 0; zi < zones.length; zi++) {
    var z = zones[zi];
    var wc = worldToCanvas(z.cx, z.cz);
    var rPx = z.radius * camera.pxPerBlock;
    ctx.beginPath(); ctx.arc(wc[0], wc[1], rPx, 0, Math.PI * 2);
    ctx.fillStyle = WEATHER_COLORS[z.type] || 'rgba(128,128,128,0.15)'; ctx.fill();
    ctx.strokeStyle = WEATHER_STROKE[z.type] || 'rgba(128,128,128,0.5)'; ctx.lineWidth = 1; ctx.stroke();
    ctx.fillStyle = '#ccc'; ctx.font = '10px monospace';
    ctx.fillText(z.type, wc[0] - 12, wc[1] + 3);
  }

  if (layers.npcs) {
    for (var ni = 0; ni < state.npcs.length; ni++) {
      var n = state.npcs[ni];
      var nc = worldToCanvas(n.x, n.z);
      var tracked = followTarget && followTarget.type === 'npc' && followTarget.id === n.id;
      if (tracked) {
        ctx.strokeStyle = '#ffcc44'; ctx.lineWidth = 2;
        ctx.strokeRect(nc[0] - 8, nc[1] - 8, 16, 16);
      }
      ctx.fillStyle = '#fa6';
      ctx.beginPath(); ctx.arc(nc[0], nc[1], 5, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = '#fa6'; ctx.font = '10px monospace';
      ctx.fillText(n.type, nc[0] + 7, nc[1] + 4);
    }
  }

  if (layers.players) {
    for (var pi = 0; pi < state.players.length; pi++) {
      var p = state.players[pi];
      var pc = worldToCanvas(p.x, p.z);
      var yawRad = (p.yaw * Math.PI) / 180;
      var tracked = followTarget && followTarget.type === 'player' && followTarget.id === p.id;
      if (tracked) {
        ctx.strokeStyle = '#44aaff'; ctx.lineWidth = 2;
        ctx.strokeRect(pc[0] - 10, pc[1] - 10, 20, 20);
      }
      ctx.save();
      ctx.translate(pc[0], pc[1]);
      ctx.strokeStyle = '#6af'; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(Math.sin(yawRad) * 12, -Math.cos(yawRad) * 12); ctx.stroke();
      ctx.fillStyle = '#6af';
      ctx.beginPath(); ctx.arc(0, 0, 6, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
      ctx.fillStyle = '#8cf'; ctx.font = 'bold 11px monospace';
      ctx.fillText(p.name, pc[0] + 9, pc[1] + 4);
    }
  }
}

function ticksToTime(ticks) {
  var DAY = 72000;
  var t = ((ticks % DAY) + DAY) % DAY;
  var h = Math.floor(t / 3000);
  var m = Math.floor((t % 3000) / 50);
  return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
}

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function updateSidebar() {
  document.getElementById('time').textContent = '⏰ ' + ticksToTime(state.gameTicks);

  var pl = document.getElementById('player-list');
  if (state.players.length === 0) {
    pl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    pl.innerHTML = state.players.map(function(p) {
      var tracked = followTarget && followTarget.type === 'player' && followTarget.id === p.id;
      return '<div class="entity-entry' + (tracked ? ' tracking' : '') + '" data-id="' + esc(p.id) + '">' +
        '<span class="entity-name">' + esc(p.name) + '</span>' +
        (tracked ? ' <span class="follow-badge">● follow</span>' : '') + '<br>' +
        '<span class="coords">' + Math.round(p.x) + ' ' + Math.round(p.y) + ' ' + Math.round(p.z) + '</span></div>';
    }).join('');
  }

  var nl = document.getElementById('npc-list');
  if (state.npcs.length === 0) {
    nl.innerHTML = '<span style="color:#555">none</span>';
  } else {
    nl.innerHTML = state.npcs.map(function(n) {
      var tracked = followTarget && followTarget.type === 'npc' && followTarget.id === n.id;
      return '<div class="entity-entry' + (tracked ? ' tracking npc-tracking' : '') + '" data-id="' + esc(n.id) + '">' +
        '<span class="npc-name">' + esc(n.name) + '</span> <span style="color:#888">(' + esc(n.type) + ')</span>' +
        (tracked ? ' <span class="follow-badge npc">● follow</span>' : '') + '<br>' +
        '<span class="coords">' + Math.round(n.x) + ' ' + Math.round(n.y) + ' ' + Math.round(n.z) + '</span></div>';
    }).join('');
  }
}

async function pollState() {
  try {
    var r = await fetch('/api/map/state');
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
    var r = await fetch('/api/map/terrain');
    if (r.ok) {
      terrainData = await r.json();
      terrainDirty = true;
      if (!autoFitDone && terrainData.length > 0) {
        autoFitDone = true;
        autoFitView();
        fetchHouses();
        fetchRoads();
      }
      draw();
    }
  } catch (e) { /* non-critical */ }
}

async function fetchVoronoi() {
  try {
    var r = await fetch('/api/map/voronoi?cx=0&cz=0&radius=3200');
    if (r.ok) { voronoiCells = await r.json(); draw(); }
  } catch (e) { /* non-critical */ }
}

async function fetchHouses() {
  var cx = Math.round(camera.x), cz = Math.round(camera.z);
  var ddx = cx - housesFetchCenter.x, ddz = cz - housesFetchCenter.z;
  if (!isNaN(housesFetchCenter.x) && Math.sqrt(ddx*ddx + ddz*ddz) < 300) return;
  housesFetchCenter = { x: cx, z: cz };
  try {
    var r = await fetch('/api/map/houses?cx=' + cx + '&cz=' + cz + '&radius=1200');
    if (r.ok) { housesData = await r.json(); draw(); }
  } catch (e) { /* non-critical */ }
}

async function fetchRoads() {
  var cx = Math.round(camera.x), cz = Math.round(camera.z);
  var ddx = cx - roadsFetchCenter.x, ddz = cz - roadsFetchCenter.z;
  if (!isNaN(roadsFetchCenter.x) && Math.sqrt(ddx*ddx + ddz*ddz) < 300) return;
  roadsFetchCenter = { x: cx, z: cz };
  try {
    var r = await fetch('/api/map/roads?cx=' + cx + '&cz=' + cz + '&radius=1200');
    if (r.ok) { roadsData = await r.json(); draw(); }
  } catch (e) { /* non-critical */ }
}

pollState();
pollTerrain();
fetchVoronoi();
setInterval(pollState, 1000);
setInterval(pollTerrain, 5000);
setInterval(fetchHouses, 10000);
setInterval(fetchRoads, 10000);
</script>
</body>
</html>
"""
        .trimIndent()
