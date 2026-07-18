import type { Scene, Vector4 } from "@babylonjs/core";

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
function skinUV(face: BbModelFace | undefined, W: number, H: number): Vector4 {
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
function skinFaceUV(el: BbModelElement, W: number, H: number): Vector4[] {
  const faces = el.faces;
  if (el.box_uv && el.uv_offset) {
    const bw = Math.round(Math.abs(el.to[0] - el.from[0]));
    const bh = Math.round(Math.abs(el.to[1] - el.from[1]));
    const bd = Math.round(Math.abs(el.to[2] - el.from[2]));
    const [u, v] = el.uv_offset;
    const fakeUV = (x0: number, y0: number, x1: number, y1: number): BbModelFace => ({ uv: [x0, y0, x1, y1] });
    return [
      skinUV(fakeUV(u + 2 * bd + bw, v + bd, u + 2 * bd + 2 * bw, v + bd + bh), W, H), // south
      skinUV(fakeUV(u + bd, v + bd, u + bd + bw, v + bd + bh), W, H), // north
      skinUV(fakeUV(u, v + bd, u + bd, v + bd + bh), W, H), // east
      skinUV(fakeUV(u + bd + bw, v + bd, u + 2 * bd + bw, v + bd + bh), W, H), // west
      skinUV(fakeUV(u + bd, v, u + bd + bw, v + bd), W, H), // up
      skinUV(fakeUV(u + bd + bw, v, u + bd + 2 * bw, v + bd), W, H), // down
    ];
  }
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

export function registerPlayerModel(): Pick<
  McBindings,
  | "initPlayerModel"
  | "isPlayerBbmodelReady"
  | "createPlayerModelNow"
  | "createPlayerModelFromBbmodel"
  | "setPlayerTransform"
  | "setPlayerVisible"
  | "setPlayerAlpha"
  | "disposePlayerModel"
> {
  window.mcState.skinUV = skinUV;
  window.mcState.skinFaceUV = skinFaceUV;

  function createPlayerModelFromBbmodel(
    bbmodel: BbModel,
    scene: Scene,
    skin: string = "player",
    boneAliases?: Record<string, string>,
  ): McPlayerModel {
    if (!scene.__mcSceneId) scene.__mcSceneId = Math.random().toString(36).slice(2);
    const cacheKey = `${scene.__mcSceneId}_${skin}`;
    if (bbmodel.textures?.length > 0 && !window.mcState.skinMatCache[cacheKey]) {
      const texDef = bbmodel.textures[0];
      const src = texDef.source;
      const tex = new BABYLON.Texture(src, scene, true, true, BABYLON.Texture.NEAREST_SAMPLINGMODE);
      tex.hasAlpha = false;
      tex.wrapU = BABYLON.Texture.CLAMP_ADDRESSMODE;
      tex.wrapV = BABYLON.Texture.CLAMP_ADDRESSMODE;
      const mat = new BABYLON.StandardMaterial(`skinMat_${skin}`, scene);
      mat.diffuseTexture = tex;
      mat.specularColor = new BABYLON.Color3(0, 0, 0);
      window.mcState.skinMatCache[cacheKey] = mat;
    }
    const mat = window.mcState.skinMatCache[cacheKey];
    const W = bbmodel.resolution.width;
    const H = bbmodel.resolution.height;
    const SCALE = 1 / 16;

    const groupMap: Record<string, BbModelGroup> = {};
    bbmodel.groups.forEach((g) => {
      groupMap[g.uuid] = g;
    });

    // Map each element UUID to its direct parent group UUID
    const elToGroupUuid: Record<string, string | null> = {};
    function walkOutliner(nodes: BbModel["outliner"], parentUuid: string | null): void {
      if (!nodes) return;
      for (const node of nodes) {
        if (typeof node === "string") {
          elToGroupUuid[node] = parentUuid;
          continue;
        }
        walkOutliner(
          (node as { uuid: string; children?: BbModel["outliner"] }).children ?? [],
          (node as { uuid: string }).uuid,
        );
      }
    }
    walkOutliner(bbmodel.outliner, null);

    const root = new BABYLON.TransformNode("playerRoot", scene);
    const pivotNodes: McPlayerModel["pivotNodes"] = {};
    const DEG = Math.PI / 180;

    const reverseAliases: Record<string, string> = {};
    if (boneAliases) {
      for (const [role, actual] of Object.entries(boneAliases)) reverseAliases[actual] = role;
    }

    // Create a TransformNode for every group (applying its base rotation)
    const allGroupNodes: Record<
      string,
      { node: InstanceType<typeof BABYLON.TransformNode>; origin: [number, number, number] }
    > = {};
    bbmodel.groups.forEach((g) => {
      const node = new BABYLON.TransformNode(`grp_${g.name}`, scene);
      node.parent = root;
      node.position = new BABYLON.Vector3(g.origin[0] * SCALE, g.origin[1] * SCALE, g.origin[2] * SCALE);
      if (g.rotation)
        node.rotation = new BABYLON.Vector3(g.rotation[0] * DEG, g.rotation[1] * DEG, g.rotation[2] * DEG);
      allGroupNodes[g.uuid] = { node, origin: g.origin };
      if ((ANIM_GROUPS as readonly string[]).includes(g.name)) {
        pivotNodes[g.name as AnimGroupName] = { node, origin: g.origin };
      }
      const aliasName = reverseAliases[g.name];
      if (aliasName && (ANIM_GROUPS as readonly string[]).includes(aliasName)) {
        pivotNodes[aliasName as AnimGroupName] = { node, origin: g.origin };
      }
    });

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
          faceUV: skinFaceUV(el, W, H),
        },
        scene,
      );
      mesh.material = mat;
      mesh.isPickable = false;

      const cx = ((fx + tx) / 2) * SCALE,
        cy = ((fy + ty) / 2) * SCALE,
        cz = ((fz + tz) / 2) * SCALE;
      const groupUuid = elToGroupUuid[el.uuid];
      const pg = groupUuid ? allGroupNodes[groupUuid] : null;
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
      equippedArmors: {},
    };
  }

  return {
    initPlayerModel: (skin: string): void => {
      if (window.mcState.playerBbmodels[skin]) return;
      fetch(`/api/models/skins/${skin}/${skin}.bbmodel`)
        .then((r) => r.json())
        .then((data: BbModel) => {
          window.mcState.playerBbmodels[skin] = data;
          console.log(`[MiCraft] Player model ${skin} loaded`);
        })
        .catch((e) => {
          console.error(`[MiCraft] Failed to load player model ${skin}`, e);
        });
    },

    isPlayerBbmodelReady: (skin: string): boolean => !!window.mcState?.playerBbmodels?.[skin],

    createPlayerModelNow: (scene: Scene, skin: string): McPlayerModel =>
      createPlayerModelFromBbmodel(window.mcState.playerBbmodels[skin]!, scene, skin),

    createPlayerModelFromBbmodel,

    setPlayerTransform: (
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
    },

    setPlayerVisible: (model: McPlayerModel, visible: boolean): void => {
      model.root.setEnabled(visible);
    },

    setPlayerAlpha: (model: McPlayerModel, alpha: number): void => {
      model.root.getChildMeshes(true).forEach((m) => {
        m.visibility = alpha;
      });
    },

    disposePlayerModel: (model: McPlayerModel): void => {
      model.root.getChildMeshes(true).forEach((m) => m.dispose());
      Object.values(model.pivotNodes).forEach((p) => p.node.dispose());
      model.root.dispose();
    },
  };
}
