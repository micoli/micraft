/* eslint-disable @typescript-eslint/no-explicit-any */
import { useEffect, useRef, useState } from "react";

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) {
      resolve();
      return;
    }
    const s = document.createElement("script");
    s.src = src;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error(`Failed to load ${src}`));
    document.head.appendChild(s);
  });
}

async function ensureBabylon(): Promise<void> {
  if (!window.BABYLON) await loadScript("/babylon.js");
  if (!(window.BABYLON as any)?.OBJFileLoader && !(window.BABYLON as any)?.GLTFFileLoader) {
    await loadScript("/babylonjs.loaders.js");
  }
}

interface Props {
  url: string | null;
  format?: string;
}

export function GLBModelViewer({ url }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [animGroups, setAnimGroups] = useState<string[]>([]);
  const [playingIdx, setPlayingIdx] = useState<number | null>(null);
  const [animFilter, setAnimFilter] = useState("");
  const engineRef = useRef<any>(null);
  const groupsRef = useRef<any[]>([]);

  useEffect(() => {
    if (!url) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    let disposed = false;

    ensureBabylon()
      .then(() => {
        if (disposed) return;
        const B = window.BABYLON as any;

        const engine = new B.Engine(canvas, true, { preserveDrawingBuffer: false, antialias: true });
        const scene = new B.Scene(engine);
        scene.clearColor = new B.Color4(0.08, 0.08, 0.1, 1);

        const camera = new B.ArcRotateCamera("cam", Math.PI / 2, Math.PI / 3, 4, B.Vector3.Zero(), scene);
        camera.attachControl(canvas, true);
        camera.lowerRadiusLimit = 0.5;

        const light = new B.HemisphericLight("light", new B.Vector3(0, 1, 0), scene);
        light.intensity = 1.2;
        light.groundColor = new B.Color3(0.15, 0.15, 0.2);
        new B.DirectionalLight("dir", new B.Vector3(-1, -2, -1), scene).intensity = 0.6;

        const ext = "." + url.split(".").pop()!.toLowerCase();
        const rootUrl = url.substring(0, url.lastIndexOf("/") + 1);

        B.SceneLoader.OnPluginActivatedObservable.addOnce((loader: any) => {
          if (loader.preprocessUrlAsync) {
            const orig = loader.preprocessUrlAsync.bind(loader);
            loader.preprocessUrlAsync = (texUrl: string): Promise<string> => {
              if (/^https?:\/\//i.test(texUrl)) return orig(texUrl);
              const filename = texUrl.replace(/\\/g, "/").split("/").pop() ?? texUrl;
              return Promise.resolve(rootUrl + encodeURIComponent(filename));
            };
          }
        });

        const loadPromise =
          ext === ".glb"
            ? // Binary 3D asset (application/octet-stream) — not a JSON API, kept as a manual fetch.
              fetch(url)
                .then((r) => {
                  if (!r.ok) throw new Error(`HTTP ${r.status}`);
                  return r.arrayBuffer();
                })
                .then((buf) => B.SceneLoader.ImportMeshAsync("", rootUrl, new Uint8Array(buf), scene, null, ext))
            : B.SceneLoader.ImportMeshAsync("", rootUrl, url.split("/").pop(), scene, null, ext);

        loadPromise
          .then((result: any) => {
            if (disposed || !result) return;

            const meshes: any[] = result.meshes ?? [];
            if (meshes.length > 0) {
              let min = new B.Vector3(Infinity, Infinity, Infinity);
              let max = new B.Vector3(-Infinity, -Infinity, -Infinity);
              meshes.forEach((m: any) => {
                if (!m.getBoundingInfo) return;
                const b = m.getBoundingInfo().boundingBox;
                min = B.Vector3.Minimize(min, b.minimumWorld);
                max = B.Vector3.Maximize(max, b.maximumWorld);
              });
              const center = B.Vector3.Center(min, max);
              const size = max.subtract(min).length();
              camera.target = center;
              camera.radius = size * 1.2;
              camera.lowerRadiusLimit = size * 0.1;
            }

            const groups: any[] = result.animationGroups ?? [];
            groupsRef.current = groups;
            setAnimGroups(groups.map((g: any) => g.name as string));
            if (groups.length > 0) {
              groups[0].start(true);
              setPlayingIdx(0);
            }
          })
          .catch(console.error);

        engineRef.current = engine;
        engine.runRenderLoop(() => scene.render());
        const onResize = () => engine.resize();
        window.addEventListener("resize", onResize);
        return () => window.removeEventListener("resize", onResize);
      })
      .catch(console.error);

    return () => {
      disposed = true;
      groupsRef.current = [];
      setAnimGroups([]);
      setPlayingIdx(null);
      setAnimFilter("");
      if (engineRef.current) {
        engineRef.current.dispose();
        engineRef.current = null;
      }
    };
  }, [url]);

  const toggleAnim = (idx: number) => {
    const groups = groupsRef.current;
    if (!groups.length) return;
    if (playingIdx === idx) {
      groups[idx].stop();
      setPlayingIdx(null);
    } else {
      if (playingIdx !== null) groups[playingIdx]?.stop();
      groups[idx].start(true);
      setPlayingIdx(idx);
    }
  };

  if (!url) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#8A99AF] text-sm">Select an asset to preview</div>
    );
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <canvas ref={canvasRef} className="flex-1 w-full min-h-0 block" />
      {animGroups.length > 0 && (
        <div className="shrink-0 border-t border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border-b border-[#2E3A4E] px-4 py-1.5 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder="Filter animations…"
            value={animFilter}
            onChange={(e) => setAnimFilter(e.target.value)}
          />
          <div className="px-4 py-2 flex flex-wrap gap-2 max-h-[150px] overflow-y-auto">
            {animGroups
              .map((name, i) => ({ name, i }))
              .filter(({ name }) => name.toLowerCase().includes(animFilter.toLowerCase()))
              .map(({ name, i }) => (
                <button
                  key={i}
                  onClick={() => toggleAnim(i)}
                  className={`px-3 py-1 rounded text-xs font-mono border transition-colors ${
                    playingIdx === i
                      ? "bg-[#3C50E0] border-[#3C50E0] text-white"
                      : "bg-[#1A222C] border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white"
                  }`}
                >
                  {playingIdx === i ? "■ " : "▶ "}
                  {name}
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
