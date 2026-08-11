import { useEffect } from "react";
import { useNavigate, useLocation } from "react-router";

export function RouterBridge({
  navigateRef,
  isGameRouteRef,
}: {
  navigateRef: React.MutableRefObject<((to: string) => void) | null>;
  isGameRouteRef: React.MutableRefObject<boolean>;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  useEffect(() => {
    navigateRef.current = navigate;
  }, [navigate, navigateRef]);
  useEffect(() => {
    const isGame = pathname.startsWith("/game/");
    isGameRouteRef.current = isGame;
    const vis = isGame ? "visible" : "hidden";
    const canvas = document.getElementById("renderCanvas") as HTMLCanvasElement | null;
    if (canvas) canvas.style.visibility = vis;
    const minimap = document.getElementById("mc-minimap");
    if (minimap) (minimap as HTMLElement).style.visibility = vis;
    if (pathname === "/chars") window.mcState.intentionalDisconnect = true;
  }, [pathname, isGameRouteRef]);
  return null;
}
