// Babylon queries the deprecated WEBGL_debug_renderer_info extension on every Engine construction
// purely to log the GPU renderer name — no functional use in this codebase. Left alone, every
// short-lived Engine (e.g. one per hovered Block3DPreview tooltip) spams Firefox's deprecation
// warning plus Babylon's own version log. Blocking the single extension is inert everywhere else.
let _patched = false;

export function suppressDeprecatedWebglWarnings(): void {
  if (_patched) return;
  _patched = true;
  // Also silences Babylon's own "BJS - Babylon.js vX - WebGL2" info log printed on every Engine
  // construction — same short-lived-tooltip-engine spam source as above.
  if (window.BABYLON?.Logger) {
    window.BABYLON.Logger.LogLevels = window.BABYLON.Logger.WarningLogLevel;
  }
  for (const proto of [window.WebGLRenderingContext?.prototype, window.WebGL2RenderingContext?.prototype]) {
    if (!proto) continue;
    const orig = proto.getExtension;
    proto.getExtension = function (this: WebGLRenderingContext, name: string) {
      if (name === "WEBGL_debug_renderer_info") return null;
      return orig.call(this, name);
    };
  }
}
