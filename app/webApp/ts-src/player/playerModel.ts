import type { Scene, StandardMaterial } from "@babylonjs/core";

// Linear interpolation between two bbmodel keyframes at normalised time t ∈ [0,1].
export function interpAxis(keyframes: BbModelKeyframe[], t: number, axis: string): number {
  if (!keyframes || keyframes.length === 0) return 0;
  if (keyframes.length === 1) return parseFloat(String(keyframes[0].data_points[0][axis] ?? 0));
  let prev = keyframes[0],
    next = keyframes[keyframes.length - 1];
  for (let i = 0; i < keyframes.length - 1; i++) {
    if (t >= keyframes[i].time && t <= keyframes[i + 1].time) {
      prev = keyframes[i];
      next = keyframes[i + 1];
      break;
    }
  }
  if (prev === next) return parseFloat(String(prev.data_points[0][axis] ?? 0));
  const span = next.time - prev.time;
  const f = span <= 0 ? 0 : (t - prev.time) / span;
  const v0 = parseFloat(String(prev.data_points[0][axis] ?? 0));
  const v1 = parseFloat(String(next.data_points[0][axis] ?? 0));
  return v0 + (v1 - v0) * f;
}

// Pixel coords [x0,y0,x1,y1] → BabylonJS Vector4(uMin,vMin,uMax,vMax).
// BabylonJS loads textures with invertY so pixel y=0 maps to v=1.
function skinUV(face: BbModelFace | undefined, W: number, H: number): unknown {
  if (!face?.uv) return new BABYLON.Vector4(0, 0, 0, 0);
  const [x0, y0, x1, y1] = face.uv;
  return new BABYLON.Vector4(
    Math.min(x0, x1) / W,
    1 - Math.max(y0, y1) / H,
    Math.max(x0, x1) / W,
    1 - Math.min(y0, y1) / H,
  );
}

// BabylonJS CreateBox face order: 0=front(+Z/south), 1=back(-Z/north),
// 2=right(+X/east), 3=left(-X/west), 4=top(+Y), 5=bottom(-Y)
function skinFaceUV(faces: BbModelElement["faces"], W: number, H: number): unknown[] {
  return [
    skinUV(faces.south, W, H),
    skinUV(faces.north, W, H),
    skinUV(faces.east, W, H),
    skinUV(faces.west, W, H),
    skinUV(faces.up, W, H),
    skinUV(faces.down, W, H),
  ];
}

const ANIM_GROUPS = ["head", "rightArm", "leftArm", "rightLeg", "leftLeg"] as const;
type AnimGroupName = (typeof ANIM_GROUPS)[number];

function extractWalkAnim(
  bbmodel: BbModel,
  bones: readonly string[],
): Record<string, { keyframes: BbModelKeyframe[]; length: number }> {
  const walkAnimDef =
    bbmodel.animations?.find((a) => a.name?.toLowerCase().includes("walk")) ??
    bbmodel.animations?.find((a) => Object.keys(a.animators).length > 1);
  if (!walkAnimDef) return {};

  const nameToUuid: Record<string, string> = {};
  bbmodel.groups.forEach((g) => {
    nameToUuid[g.name] = g.uuid;
  });

  const result: Record<string, { keyframes: BbModelKeyframe[]; length: number }> = {};
  for (const bname of bones) {
    const uuid = nameToUuid[bname];
    if (!uuid) continue;
    const animator = walkAnimDef.animators[uuid];
    if (!animator) continue;
    const kfs = animator.keyframes.filter((k) => k.channel === "rotation").sort((a, b) => a.time - b.time);
    if (kfs.length > 0) result[bname] = { keyframes: kfs, length: walkAnimDef.length || 1 };
  }
  return result;
}

export function registerPlayerModel(): void {
  window.__mcSkinUV = skinUV;
  window.__mcSkinFaceUV = skinFaceUV;

  window.mcInitPlayerModel = (skin: string): void => {
    window.__mc = window.__mc || ({} as any);
    window.__mc.playerBbmodels = window.__mc.playerBbmodels || {};
    if (window.__mc.playerBbmodels[skin]) return;
    fetch(`/api/models/skins/${skin}/${skin}.bbmodel`)
      .then((r) => r.json())
      .then((data: BbModel) => {
        window.__mc.playerBbmodels[skin] = data;
        console.log(`[MiCraft] Player model ${skin} loaded`);
      })
      .catch((e) => {
        console.error(`[MiCraft] Failed to load player model ${skin}`, e);
      });
  };

  window.mcIsPlayerBbmodelReady = (skin: string): boolean => !!window.__mc?.playerBbmodels?.[skin];

  window.mcCreatePlayerModelNow = (scene: Scene, skin: string): McPlayerModel =>
    createPlayerModelFromBbmodel(window.__mc.playerBbmodels[skin]!, scene, skin);

  function createPlayerModelFromBbmodel(bbmodel: BbModel, scene: Scene, skin: string = "player"): McPlayerModel {
    window.__mc = window.__mc || ({} as any);
    if (!window.__mc.skinMatCache) (window.__mc as any).skinMatCache = {};
    const s = scene as any;
    if (!s.__mcSceneId) s.__mcSceneId = Math.random().toString(36).slice(2);
    const cacheKey = `${s.__mcSceneId}_${skin}`;
    if (bbmodel.textures?.length > 0 && !window.__mc.skinMatCache[cacheKey]) {
      const texDef = bbmodel.textures[0];
      const src = texDef.source;
      const tex = new BABYLON.Texture(src, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      tex.hasAlpha = false;
      tex.wrapU = BABYLON.Texture.CLAMP_ADDRESSMODE;
      tex.wrapV = BABYLON.Texture.CLAMP_ADDRESSMODE;
      const mat = new BABYLON.StandardMaterial(`skinMat_${skin}`, scene);
      mat.diffuseTexture = tex;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      window.__mc.skinMatCache[cacheKey] = mat;
    }
    const mat = window.__mc.skinMatCache[cacheKey];
    const W = bbmodel.resolution.width;
    const H = bbmodel.resolution.height;
    const SCALE = 1 / 16;

    const groupMap: Record<string, BbModelGroup> = {};
    bbmodel.groups.forEach((g) => {
      groupMap[g.uuid] = g;
    });

    // Map each element UUID to its nearest animated ancestor group name
    const elToGroup: Record<string, AnimGroupName | null> = {};
    function walkOutliner(nodes: BbModel["outliner"], animAncestor: AnimGroupName | null): void {
      if (!nodes) return;
      for (const node of nodes) {
        if (typeof node === "string") {
          elToGroup[node] = animAncestor;
          continue;
        }
        const g = groupMap[(node as any).uuid];
        const gname = g?.name as AnimGroupName | undefined;
        const next = gname && (ANIM_GROUPS as readonly string[]).includes(gname) ? gname : animAncestor;
        walkOutliner((node as any).children, next);
      }
    }
    walkOutliner(bbmodel.outliner, null);

    const root = new BABYLON.TransformNode("playerRoot", scene);
    const pivotNodes: McPlayerModel["pivotNodes"] = {};

    for (const gname of ANIM_GROUPS) {
      const g = bbmodel.groups.find((gr) => gr.name === gname);
      if (!g) continue;
      const node = new BABYLON.TransformNode(`player_${gname}`, scene);
      node.parent = root;
      node.position = new BABYLON.Vector3(g.origin[0] * SCALE, g.origin[1] * SCALE, g.origin[2] * SCALE);
      pivotNodes[gname] = { node, origin: g.origin };
    }

    for (const el of bbmodel.elements) {
      const [fx, fy, fz] = el.from,
        [tx, ty, tz] = el.to;
      if (Math.abs(tx - fx) < 0.001 || Math.abs(ty - fy) < 0.001 || Math.abs(tz - fz) < 0.001) continue;
      const mesh = BABYLON.MeshBuilder.CreateBox(
        el.name,
        {
          width: Math.abs(tx - fx) * SCALE,
          height: Math.abs(ty - fy) * SCALE,
          depth: Math.abs(tz - fz) * SCALE,
          faceUV: skinFaceUV(el.faces, W, H) as any,
        },
        scene,
      );
      mesh.material = mat;
      mesh.isPickable = false;

      const cx = ((fx + tx) / 2) * SCALE,
        cy = ((fy + ty) / 2) * SCALE,
        cz = ((fz + tz) / 2) * SCALE;
      const pg = elToGroup[el.uuid] ? pivotNodes[elToGroup[el.uuid]!] : null;
      if (pg) {
        mesh.parent = pg.node;
        mesh.position = new BABYLON.Vector3(
          cx - pg.origin[0] * SCALE,
          cy - pg.origin[1] * SCALE,
          cz - pg.origin[2] * SCALE,
        );
      } else {
        mesh.parent = root;
        mesh.position = new BABYLON.Vector3(cx, cy, cz);
      }
    }

    return {
      root,
      headNode: pivotNodes["head"]?.node ?? null,
      pivotNodes,
      walkAnim: extractWalkAnim(bbmodel, [...ANIM_GROUPS, "head"]),
    };
  }
  window.mcCreatePlayerModelFromBbmodel = createPlayerModelFromBbmodel;

  window.mcSetPlayerTransform = (
    model: McPlayerModel,
    x: number,
    y: number,
    z: number,
    yaw: number,
    headPitch: number,
    isWalking: boolean,
  ): void => {
    model.root.position.x = x;
    model.root.position.y = y;
    model.root.position.z = z;
    model.root.rotation.y = yaw + Math.PI;

    const pn = model.pivotNodes;
    if (!pn) return;
    const DEG = Math.PI / 180;
    const headPivot = pn["head"]?.node ?? null;
    const wa = model.walkAnim ?? {};

    if (isWalking) {
      const animLen = wa["rightArm"]?.length ?? 1;
      const t = (Date.now() % (animLen * 1000)) / (animLen * 1000);
      for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
        if (!pn[bname]) continue;
        pn[bname].node.rotation.x = (wa[bname] ? interpAxis(wa[bname].keyframes, t, "x") : 0) * DEG;
      }
      if (headPivot) {
        const hb = wa["head"];
        headPivot.rotation.x = headPitch + (hb ? interpAxis(hb.keyframes, t, "x") : 0) * DEG;
        headPivot.rotation.y = (hb ? interpAxis(hb.keyframes, t, "y") : 0) * DEG;
      }
    } else {
      for (const bname of ["rightArm", "leftArm", "rightLeg", "leftLeg"] as const) {
        if (pn[bname]) pn[bname].node.rotation.x = 0;
      }
      if (headPivot) {
        headPivot.rotation.x = headPitch;
        headPivot.rotation.y = 0;
      }
    }
  };

  window.mcSetPlayerVisible = (model: McPlayerModel, visible: boolean): void => {
    model.root.setEnabled(visible);
  };

  window.mcSetPlayerAlpha = (model: McPlayerModel, alpha: number): void => {
    model.root.getChildMeshes(true).forEach((m) => {
      m.visibility = alpha;
    });
  };

  window.mcDisposePlayerModel = (model: McPlayerModel): void => {
    model.root.getChildMeshes(true).forEach((m) => m.dispose());
    Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
    model.root.dispose();
  };
}
