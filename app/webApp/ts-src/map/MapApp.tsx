import { useRef } from "react";
import { Button } from "../primitives/Button";
import { MapSvgRenderer } from "./MapSvgRenderer";
import { Sidebar } from "./Sidebar";
import { useMapRenderer } from "./useMapRenderer";

export function MapApp() {
  const svgRef = useRef<SVGSVGElement>(null);
  const renderer = useMapRenderer(svgRef);

  return (
    <div className="flex h-screen overflow-hidden bg-[#1a1a1a] text-[#eee] font-mono">
      <Sidebar
        time={renderer.time}
        apiState={renderer.apiState}
        layers={renderer.layers}
        followTarget={renderer.followTarget}
        onLayerToggle={renderer.onLayerToggle}
        onSetFollow={renderer.onSetFollow}
        onFitAll={renderer.onFitAll}
      />

      <div className="flex-1 relative overflow-hidden">
        <MapSvgRenderer renderer={renderer} svgRef={svgRef} />

        <div className="absolute top-2.5 right-2.5 flex flex-col gap-1 z-10">
          <Button
            variant="secondary"
            size="sm"
            onClick={renderer.onZoomIn}
            title="Zoom in"
            className="w-8 h-8 p-0 text-lg"
          >
            +
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={renderer.onZoomOut}
            title="Zoom out"
            className="w-8 h-8 p-0 text-lg"
          >
            −
          </Button>
        </div>

        <div className="absolute bottom-2 left-2 text-[10px] text-[#667] pointer-events-none bg-black/40 px-1.5 py-0.5 rounded">
          {renderer.coords}
        </div>

        <div className="absolute bottom-2 right-2 text-[10px] text-[#555]">{renderer.status}</div>
      </div>
    </div>
  );
}
