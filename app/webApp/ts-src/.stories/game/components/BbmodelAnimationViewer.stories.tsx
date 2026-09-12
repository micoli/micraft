import { useEffect, useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { BbmodelAnimationViewer } from "../../../admin/components/BbmodelAnimationViewer";
import { animationsFromBbmodel } from "../../../lib/animationHelpers";

type HarnessProps = {
  showAxes: boolean;
  showGround: boolean;
  hideUI: boolean;
  background: boolean;
  modelName: string;
  angle: number | null;
};

function HarnessRender(args: Partial<HarnessProps>) {
  return (
    <Harness
      modelName={args.modelName ?? "elephant"}
      showAxes={!!args.showAxes}
      showGround={!!args.showGround}
      hideUI={!!args.hideUI}
      background={args.background ?? true}
      angle={args.angle ?? null}
    />
  );
}

const meta: Meta<typeof HarnessRender> = {
  title: "Game/Components/BbmodelAnimationViewer",
  parameters: { layout: "centered" },
  argTypes: {
    modelName: { control: "text" },
    showAxes: { control: "boolean" },
    showGround: { control: "boolean" },
    hideUI: { control: "boolean" },
    background: { control: "boolean" },
    angle: { control: "number" },
  },
};
export default meta;

type Story = StoryObj<typeof BbmodelAnimationViewer>;

function Harness({ showAxes, showGround, hideUI, background, modelName, angle }: HarnessProps) {
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);
  const [animFullName, setAnimFullName] = useState("");

  useEffect(() => {
    fetch(`/api/models/entities/${modelName}/${modelName}.bbmodel`)
      .then((r) => r.json() as Promise<BbModel>)
      .then((model) => {
        setBbmodel(model);
        const anims = animationsFromBbmodel(model);
        if (anims.length > 0) setAnimFullName(anims[0].fullName);
      })
      .catch(console.error);
  }, [modelName]);

  return (
    <BbmodelAnimationViewer
      bbmodel={bbmodel}
      animFullName={animFullName}
      showAxes={showAxes}
      showGround={showGround}
      hideUI={hideUI}
      background={background}
      angle={angle}
      width={360}
      height={460}
    />
  );
}

export const Elephant: Story = {
  args: { showAxes: false, showGround: false, hideUI: false, background: true, angle: null },
  render: HarnessRender,
};

export const WithScaleAndGround: Story = {
  args: { showAxes: true, showGround: true, hideUI: false, background: true, angle: null },
  render: HarnessRender,
};

export const FixedAngle: Story = {
  args: { showAxes: false, showGround: false, hideUI: false, background: true, angle: Math.PI / 4 },
  render: HarnessRender,
};

export const HiddenUI: Story = {
  args: { showAxes: false, showGround: false, hideUI: true, background: true },
  render: HarnessRender,
};

export const TransparentBackground: Story = {
  args: { showAxes: false, showGround: false, hideUI: true, background: false },
  render: HarnessRender,
};

export const AllEntities = {
  render: () => (
    <>
      {allModels.map((item) => (
        <Harness key={item} modelName={item} showAxes={false} showGround={false} hideUI={true} background={true} />
      ))}
    </>
  ),
};
const allModels = [
  "alpha_direwolf",
  "ancient_treant",
  "bandit_man",
  "bear",
  "blacksmith",
  "boar",
  "bog_hydra",
  "camel",
  "cat",
  "cat_baby",
  "cave_bat",
  "cow",
  "crab",
  "crocodile_man",
  "crystal_golem",
  "deer",
  "deer_baby",
  "desert_raider_man",
  "dolphin",
  "duck",
  "duck_baby",
  "eagle",
  "eel",
  "elephant",
  "fox",
  "frost_troll",
  "frost_wyrm",
  "ghost",
  "giant_ant",
  "giant_toad",
  "golem",
  "gorilla",
  "harpy",
  "hawk",
  "hedgehog",
  "hermit_man",
  "hyena",
  "ice_wolf",
  "jackal",
  "jellyfish",
  "kraken_spawn",
  "lion",
  "little_stone_dragon",
  "magma_golem",
  "mole",
  "moose",
  "mountain_goat",
  "mountain_goat_baby",
  "octopus",
  "owl",
  "parrot",
  "pharaoh_wraith",
  "pig",
  "pig_man",
  "plague_ogre",
  "polar_bear",
  "polar_bear_cub",
  "pterodactyl",
  "rabbit",
  "raccoon",
  "sand_worm",
  "scorpion",
  "seller",
  "shark",
  "skeleton",
  "snake_man",
  "snow_owl",
  "spider",
  "squid",
  "stone_lizard",
  "storm_roc",
  "tiger",
  "turtle",
  "vulture",
  "wildcat",
  "wolf",
  "wolf_baby",
  "wolf_man",
  "wolf_veteran",
  "yeti",
  "zombie",
];
