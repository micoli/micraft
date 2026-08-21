import { useEffect, useState } from "react";
import { BrowserRouter, Route, Routes } from "react-router";
import { I18nProvider } from "./i18n";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "./sidebar";
import { ClassesPage } from "./pages/classes/ClassesPage";
import { ConfigEditorPage } from "./pages/configEditor/ConfigEditorPage";
import { NpcsPage } from "./pages/npcs/NpcsPage";
import { PlayersPage } from "./pages/players/PlayersPage";
import { StatusPage } from "./pages/status/StatusPage";
import { UsersPage } from "./pages/user/UsersPage";
import { WorldsPage } from "./pages/worlds/WorldsPage";
import { GameAssetsViewerPage } from "./pages/GameAssetsViewerPage";
import { AdministrationPage } from "./pages/AdministrationPage";
import { WorldSimulatorPage } from "./pages/worldSimulator/WorldSimulatorPage";
import { InstancesPage } from "./pages/instance/InstancesPage";
import { ScenesPage } from "./pages/scene/ScenesPage";
import { Header } from "./Header";
import { SidebarComponent } from "./SidebarComponent";

export function AdminApp() {
  const [collapsed, setCollapsed] = useState(() => loadSidebarCollapsed());

  useEffect(() => saveSidebarCollapsed(collapsed), [collapsed]);

  return (
    <I18nProvider>
      <BrowserRouter>
        <div className="flex h-screen overflow-hidden bg-[#0E1726] text-white font-sans">
          <SidebarComponent collapsed={collapsed} onToggle={() => setCollapsed((current) => !current)} />
          <div className="flex flex-col flex-1 overflow-hidden">
            <Header />
            <main className="flex-1 overflow-auto p-6">
              <Routes>
                <Route path="/admin" element={<StatusPage />} />
                <Route path="/admin/users" element={<UsersPage />} />
                <Route path="/admin/players" element={<PlayersPage />} />
                <Route path="/admin/npcs" element={<NpcsPage />} />
                <Route path="/admin/classes" element={<ClassesPage />} />
                <Route path="/admin/config" element={<ConfigEditorPage />} />
                <Route path="/admin/worlds" element={<WorldsPage />} />
                <Route path="/admin/game-assets" element={<GameAssetsViewerPage />} />
                <Route path="/admin/administration" element={<AdministrationPage />} />
                <Route path="/admin/administration/:tab" element={<AdministrationPage />} />
                <Route path="/admin/administration/:tab/:itemKey" element={<AdministrationPage />} />
                <Route path="/admin/instances" element={<InstancesPage />} />
                <Route path="/admin/instances/:id" element={<InstancesPage />} />
                <Route path="/admin/scenes" element={<ScenesPage />} />
                <Route path="/admin/scenes/:id" element={<ScenesPage />} />
                <Route path="/admin/world-simulator" element={<WorldSimulatorPage />} />
              </Routes>
            </main>
          </div>
        </div>
      </BrowserRouter>
    </I18nProvider>
  );
}
