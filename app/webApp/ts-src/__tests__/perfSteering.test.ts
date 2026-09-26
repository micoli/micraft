import { describe, expect, it } from "vitest";
import { initialSteering, steer } from "../perf/helpers/steering";

describe("perf steering", () => {
  it("keeps going while the character makes progress", () => {
    const { state, action } = steer(initialSteering(0), { nowMs: 1_000, progressBlocks: 5, mode: "walk" });
    expect(action).toBe("none");
    expect(state.offsetDeg).toBe(0);
  });

  it("turns 45 degrees when a walk is stuck", () => {
    const { state, action } = steer(initialSteering(0), { nowMs: 1_000, progressBlocks: 0.2, mode: "walk" });
    expect(action).toBe("turn");
    expect(state.offsetDeg).toBe(45);
    expect(state.stuckEvents).toBe(1);
  });

  it("backs off before every fourth turn at the same obstacle", () => {
    let state = initialSteering(0);
    const actions = [];
    for (let i = 0; i < 4; i++) {
      const r = steer(state, { nowMs: 1_000 * (i + 1), progressBlocks: 0, mode: "walk" });
      state = r.state;
      actions.push(r.action);
    }
    expect(actions).toEqual(["turn", "turn", "turn", "backoff"]);
    expect(state.offsetDeg).toBe(180);
  });

  it("ascends when a flight is stuck, then also turns if climbing did not help", () => {
    let state = initialSteering(0);
    const actions = [];
    for (let i = 0; i < 4; i++) {
      const r = steer(state, { nowMs: 1_000 * (i + 1), progressBlocks: 0, mode: "fly" });
      state = r.state;
      actions.push(r.action);
    }
    expect(actions).toEqual(["ascend", "ascend", "ascend", "turn"]);
    expect(state.offsetDeg).toBe(45);
  });

  it("drifts back toward the target heading after moving freely for a while", () => {
    let state = steer(initialSteering(0), { nowMs: 0, progressBlocks: 0, mode: "walk" }).state;
    state = steer(state, { nowMs: 2_000, progressBlocks: 5, mode: "walk" }).state;
    const back = steer(state, { nowMs: 3_500, progressBlocks: 5, mode: "walk" });
    expect(back.action).toBe("return");
    expect(back.state.offsetDeg).toBe(0);
  });

  it("yaw combines the target heading with the current offset", () => {
    const { state } = steer(initialSteering(Math.PI), { nowMs: 1_000, progressBlocks: 0, mode: "walk" });
    expect(state.yaw).toBeCloseTo(Math.PI + Math.PI / 4);
  });
});
