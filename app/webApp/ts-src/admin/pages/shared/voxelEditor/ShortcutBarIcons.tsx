import { InstanceShortcutSlot } from "./useAdminShortcutBar";
import { OrgMicoliMicraftProtocolBlockInfo } from "../../../../generated/api/requests";
import { VoxelShortcutBarSlot } from "./VoxelShortcutBarSlot";

export function ShortcutBarIcons({
  shortcutBar,
  getOrdinal,
  blockDefs,
  getPreview,
  blockDefsReady,
  previewsReady,
  hoveredShortcutSlot,
  setHoveredShortcutSlot,
}: {
  shortcutBar: ReturnType<
    ({
      initialPages,
      onSelectBreak,
      onSelectBlock,
    }: {
      initialPages?: InstanceShortcutSlot[][];
      onSelectBreak: () => void;
      onSelectBlock: (blockName: string) => void;
    }) => {
      pages: InstanceShortcutSlot[][];
      slots: InstanceShortcutSlot[];
      pageCount: number;
      currentPage: number;
      selectedSlot: number;
      dragOver: number | null;
      selectSlot: (idx: number) => void;
      goToPage: (page: number) => void;
      handleDragEnter: (e: React.DragEvent, idx: number) => void;
      handleDragOver: (e: React.DragEvent, idx: number) => void;
      handleDragLeave: () => void;
      handleDrop: (e: React.DragEvent, idx: number) => void;
      handleContextMenu: (e: React.MouseEvent, idx: number) => void;
    }
  >;
  getOrdinal: (name: string) => number | null;
  blockDefs: OrgMicoliMicraftProtocolBlockInfo[];
  getPreview: (ordinal: number) => string | null;
  blockDefsReady: boolean;
  previewsReady: boolean;
  hoveredShortcutSlot: number | null;
  setHoveredShortcutSlot: (idx: number | null) => void;
}) {
  return (
    <div className="grid grid-cols-5 gap-1 justify-center">
      {shortcutBar.slots.map((slotBlock, idx) => (
        <VoxelShortcutBarSlot
          key={idx}
          shortcutBar={shortcutBar}
          idx={idx}
          slotBlock={slotBlock}
          getOrdinal={getOrdinal}
          blockDefs={blockDefs}
          getPreview={getPreview}
          blockDefsReady={blockDefsReady}
          previewsReady={previewsReady}
          hovered={hoveredShortcutSlot === idx}
          onHoverEnter={() => setHoveredShortcutSlot(idx)}
          onHoverLeave={() => setHoveredShortcutSlot(null)}
        />
      ))}
    </div>
  );
}
