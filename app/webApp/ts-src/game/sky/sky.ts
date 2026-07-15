export function registerSky(): Pick<McBindings, "updateSkyTime"> {
  // Sky color keyframes: [normalizedTime, [r, g, b]]
  const SKY_STOPS: Array<[number, [number, number, number]]> = [
    [0.0, [0.03, 0.03, 0.1]], // midnight
    [0.2, [0.03, 0.03, 0.1]], // pre-dawn
    [0.25, [0.95, 0.7, 0.4]], // dawn
    [0.33, [0.53, 0.81, 0.98]], // morning
    [0.67, [0.53, 0.81, 0.98]], // afternoon
    [0.75, [0.9, 0.55, 0.2]], // dusk
    [0.8, [0.03, 0.03, 0.1]], // evening
    [1.0, [0.03, 0.03, 0.1]], // midnight
  ];

  function lerpSky(t: number): [number, number, number] {
    for (let i = 1; i < SKY_STOPS.length; i++) {
      const [t0, c0] = SKY_STOPS[i - 1];
      const [t1, c1] = SKY_STOPS[i];
      if (t <= t1) {
        const f = (t - t0) / (t1 - t0);
        return [c0[0] + (c1[0] - c0[0]) * f, c0[1] + (c1[1] - c0[1]) * f, c0[2] + (c1[2] - c0[2]) * f];
      }
    }
    return SKY_STOPS[SKY_STOPS.length - 1][1];
  }

  let sun: any = null;
  let moon: any = null;
  const DIST = 200;

  return {
    updateSkyTime: (scene: any, t: number): void => {
      if (!sun) {
        sun = BABYLON.MeshBuilder.CreateSphere("mc_sun", { diameter: 12, segments: 4 }, scene);
        sun.isPickable = false;
        const m = new BABYLON.StandardMaterial("mc_sun_mat", scene);
        m.emissiveColor = new BABYLON.Color3(1, 0.95, 0.7);
        m.disableLighting = true;
        (m as any).fogEnabled = false;
        sun.material = m;
      }
      if (!moon) {
        moon = BABYLON.MeshBuilder.CreateSphere("mc_moon", { diameter: 9, segments: 4 }, scene);
        moon.isPickable = false;
        const m = new BABYLON.StandardMaterial("mc_moon_mat", scene);
        m.emissiveColor = new BABYLON.Color3(0.85, 0.85, 0.95);
        m.disableLighting = true;
        (m as any).fogEnabled = false;
        moon.material = m;
      }

      // Sun: angle=0 → +X horizon (dawn at t=0.25), angle=π/2 → overhead (noon at t=0.5)
      const sunAngle = (t - 0.25) * Math.PI * 2;
      const cam = scene.activeCamera;
      const cx: number = cam ? cam.position.x : 0;
      const cy: number = cam ? cam.position.y : 0;
      const cz: number = cam ? cam.position.z : 0;
      sun.position = new BABYLON.Vector3(cx + Math.cos(sunAngle) * DIST, cy + Math.sin(sunAngle) * DIST, cz);
      moon.position = new BABYLON.Vector3(cx - Math.cos(sunAngle) * DIST, cy - Math.sin(sunAngle) * DIST, cz);

      // Sky and fog color
      const [r, g, b] = lerpSky(t);
      scene.clearColor = new BABYLON.Color4(r, g, b, 1);
      scene.fogColor = new BABYLON.Color3(r, g, b);
      const _mats = (window.mcState as any).blockMaterials as Record<string, any> | undefined;
      if (_mats) {
        const _fv = new BABYLON.Vector3(r, g, b);
        for (const _mat of Object.values(_mats))
          if (typeof (_mat as any).setVector3 === "function") (_mat as any).setVector3("fogColor", _fv);
      }

      // Ambient light: brightest at noon (sunHeight=1), dim at night (floor 0.15)
      const sunHeight = Math.sin(sunAngle);
      const intensity = Math.max(0.15, Math.min(1.0, 0.15 + 0.85 * sunHeight));
      const caveFactor: number = (window.mcState as any).caveFactor ?? 1.0;
      const hemi = window.mcState.hemiLight;
      if (hemi) hemi.intensity = intensity;
      window.mc?.setAmbient(scene, Math.max(0.08, intensity * caveFactor));
    },
  };
}
