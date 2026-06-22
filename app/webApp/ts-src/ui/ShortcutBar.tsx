import { useState, useRef } from 'react';

const ITEM_META: Record<string, { label: string; bg: string }> = {
  COBBLESTONE: { label: 'COB', bg: '#7A7A7A' },
  DIRT:        { label: 'DRT', bg: '#8B5A2B' },
  SAND:        { label: 'SND', bg: '#D5C89A' },
  GRAVEL:      { label: 'GRV', bg: '#9A9A9A' },
  SANDSTONE:   { label: 'SST', bg: '#C8B46C' },
  SNOWBALL:    { label: 'SNW', bg: '#DCE8F5' },
  FLINT:       { label: 'FLT', bg: '#4A4A52' },
};

interface Props {
  inventory: Record<string, number>;
  slots: (string | null)[];
  selectedSlot: number;
  onSlotDrop: (slot: number, itemType: string | null) => void;
}

export function ShortcutBar({ inventory, slots, selectedSlot, onSlotDrop }: Props) {
  const [dragOver, setDragOver] = useState<number | null>(null);
  const draggingSlot = useRef<number | null>(null);

  const handleSlotDragStart = (e: React.DragEvent, slotIdx: number, itemType: string) => {
    draggingSlot.current = slotIdx;
    e.dataTransfer.setData('text/plain', itemType);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleSlotDragEnd = (e: React.DragEvent) => {
    if (e.dataTransfer.dropEffect === 'none' && draggingSlot.current !== null) {
      onSlotDrop(draggingSlot.current, null);
    }
    draggingSlot.current = null;
  };

  const handleDragOver = (e: React.DragEvent, slotIdx: number) => {
    if (slotIdx === 0) return; // slot 1 (hand) — not a drop target
    e.preventDefault();
    setDragOver(slotIdx);
  };

  const handleDragLeave = () => setDragOver(null);

  const handleDrop = (e: React.DragEvent, slotIdx: number) => {
    if (slotIdx === 0) return;
    e.preventDefault();
    setDragOver(null);
    const itemType = e.dataTransfer.getData('text/plain');
    onSlotDrop(slotIdx, itemType || null);
  };

  const handleContextMenu = (e: React.MouseEvent, slotIdx: number) => {
    if (slotIdx === 0) return;
    e.preventDefault();
    onSlotDrop(slotIdx, null);
  };

  return (
    <div style={{
      position: 'fixed', bottom: 20, left: '50%', transform: 'translateX(-50%)',
      display: 'flex', gap: 4, pointerEvents: 'all', zIndex: 999,
      background: 'rgba(0,0,0,0.6)', border: '1px solid rgba(255,255,255,0.2)',
      borderRadius: 6, padding: '6px 10px',
    }}>
      {slots.map((itemType, idx) => {
        const isSelected = idx === selectedSlot;
        const isHand = idx === 0;
        const meta = itemType ? ITEM_META[itemType] : null;
        const count = itemType ? (inventory[itemType] ?? 0) : 0;
        const isDropTarget = dragOver === idx;

        return (
          <div
            key={idx}
            draggable={!isHand && !!itemType}
            onDragStart={!isHand && itemType ? (e) => handleSlotDragStart(e, idx, itemType) : undefined}
            onDragEnd={!isHand && !!itemType ? handleSlotDragEnd : undefined}
            onDragOver={(e) => handleDragOver(e, idx)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, idx)}
            onContextMenu={(e) => handleContextMenu(e, idx)}
            style={{
              width: 52, height: 52,
              background: isDropTarget ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.72)',
              border: isSelected
                ? '2px solid rgba(255,215,0,0.9)'
                : '2px solid rgba(255,255,255,0.35)',
              borderRadius: 4,
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
              position: 'relative', cursor: isHand ? 'default' : itemType ? 'grab' : 'pointer',
              boxShadow: isSelected ? '0 0 6px rgba(255,215,0,0.5)' : 'none',
            }}
          >
            {/* Slot number label */}
            <div style={{
              position: 'absolute', top: 1, left: 3,
              color: 'rgba(255,255,255,0.45)', font: '8px monospace',
            }}>
              {idx === 9 ? '0' : String(idx + 1)}
            </div>

            {isHand ? (
              <div style={{ color: 'rgba(255,255,255,0.6)', font: '16px monospace' }}>✋</div>
            ) : meta ? (
              <>
                <div style={{
                  width: 26, height: 26, borderRadius: 3, background: meta.bg,
                  boxShadow: 'inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)',
                }} />
                <div style={{ color: 'rgba(255,255,255,0.7)', font: '8px monospace', marginTop: 2, letterSpacing: '0.5px' }}>
                  {meta.label}
                </div>
                {count > 0 && (
                  <div style={{ position: 'absolute', bottom: 2, right: 4, color: '#fff', font: 'bold 9px monospace', textShadow: '1px 1px 0 #000' }}>
                    {count}
                  </div>
                )}
              </>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
