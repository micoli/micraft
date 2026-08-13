import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { ClipAxesInput } from "../../primitives/clipAxesInput";
import type { ClipAxis, ClipPlaneState } from "../../admin/pages/shared/voxelEditor/clipAxis";

const meta: Meta<typeof ClipAxesInput> = {
  title: "Primitives/ClipAxesInput",
  component: ClipAxesInput,
};
export default meta;

type Story = StoryObj<typeof ClipAxesInput>;

const clipBounds = {
  x: [0, 16] as const,
  y: [0, 128] as const,
  z: [0, 16] as const,
};

function ClipAxesInputStory({ axis }: { axis: ClipAxis }) {
  const [clipPlanes, setClipPlanes] = useState<Record<ClipAxis, ClipPlaneState>>({
    x: { enabled: true, flipped: false, pos: 8 },
    y: { enabled: false, flipped: false, pos: 64 },
    z: { enabled: false, flipped: true, pos: 8 },
  });
  return (
    <div className="w-56 bg-black/60 p-3 rounded-lg border border-white/20">
      <ClipAxesInput axis={axis} clipPlanes={clipPlanes} clipBounds={clipBounds} setClipPlanes={setClipPlanes} />
    </div>
  );
}

export const XAxis: Story = {
  render: () => <ClipAxesInputStory axis="x" />,
};

function AllAxesStory() {
  const [clipPlanes, setClipPlanes] = useState<Record<ClipAxis, ClipPlaneState>>({
    x: { enabled: true, flipped: false, pos: 8 },
    y: { enabled: false, flipped: false, pos: 64 },
    z: { enabled: true, flipped: true, pos: 4 },
  });
  return (
    <div className="w-56 bg-black/60 p-3 rounded-lg border border-white/20 flex flex-col gap-1">
      {(["x", "y", "z"] as const).map((axis) => (
        <ClipAxesInput
          key={axis}
          axis={axis}
          clipPlanes={clipPlanes}
          clipBounds={clipBounds}
          setClipPlanes={setClipPlanes}
        />
      ))}
    </div>
  );
}

export const AllAxes: Story = {
  render: () => <AllAxesStory />,
};
