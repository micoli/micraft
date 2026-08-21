import { Route, Routes } from "react-router";
import { Header } from "./Header";
import { SidebarComponent } from "./SidebarComponent";
import { TabBar } from "./TabBar";
import { useTabs } from "./TabsContext";
import { ROUTES } from "./const";

export function AdminShell({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const { tabs, activeTab } = useTabs();
  return (
    <div className="flex h-screen overflow-hidden bg-[#0E1726] text-white font-sans">
      <SidebarComponent collapsed={collapsed} onToggle={onToggle} />
      <div className="flex flex-col flex-1 overflow-hidden">
        <Header />
        <TabBar />
        <main className="flex-1 overflow-auto p-6">
          {/* one subtree per open tab, kept mounted (hidden, not unmounted) so switching tabs
              doesn't reinstantiate the page — only the explicit "refresh" button does, via the key.
              h-full: pages relying on a sized ancestor (e.g. GameAssetsViewerPage) need this
              wrapper to pass main's height through rather than collapsing to auto. */}
          {tabs.map((tab) => (
            <div key={tab.path} hidden={tab.path !== activeTab?.path} className="h-full">
              <Routes location={{ pathname: tab.lastPath }} key={`${tab.path}-${tab.refreshNonce}`}>
                {ROUTES.map(({ path, page }) => (
                  <Route key={path} path={path} element={page} />
                ))}
              </Routes>
            </div>
          ))}
        </main>
      </div>
    </div>
  );
}
