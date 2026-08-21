import { useEffect, useState } from "react";
import { BrowserRouter, Route, Routes } from "react-router";
import { I18nProvider } from "./i18n";
import { loadSidebarCollapsed, saveSidebarCollapsed } from "./sidebar";
import { Header } from "./Header";
import { SidebarComponent } from "./SidebarComponent";
import { ROUTES } from "./const";

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
                {ROUTES.map(({ path, page }) => (
                  <Route key={path} path={path} element={page} />
                ))}
              </Routes>
            </main>
          </div>
        </div>
      </BrowserRouter>
    </I18nProvider>
  );
}
