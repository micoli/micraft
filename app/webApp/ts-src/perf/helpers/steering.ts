/** Horizontal blocks the Character must cover over one progress window to count as moving. */
export const STUCK_BLOCKS = 1;
const TURN_DEG = 45;
const BACKOFF_EVERY = 4;
const TURN_WHILE_FLYING_EVERY = 4;
const RETURN_AFTER_MS = 1_500;
/** Consecutive stuck readings after which detouring is given up and the Character jumps ahead. */
export const TELEPORT_AFTER = 6;

export type SteerAction = "none" | "turn" | "backoff" | "ascend" | "return" | "teleport";

export interface SteeringState {
  /** The heading this window is meant to follow, in radians. */
  targetYaw: number;
  /** Detour from the target heading, in degrees (0..315). */
  offsetDeg: number;
  /** The yaw to look at now: target heading plus detour. */
  yaw: number;
  /** Consecutive stuck readings at the current obstacle. */
  attempts: number;
  /** When movement last resumed after a detour; null while stuck. */
  freeSinceMs: number | null;
  stuckEvents: number;
  teleports: number;
}

export interface SteerInput {
  nowMs: number;
  progressBlocks: number;
  mode: "walk" | "fly";
}

const withOffset = (state: SteeringState, offsetDeg: number): SteeringState => {
  const wrapped = ((offsetDeg % 360) + 360) % 360;
  return { ...state, offsetDeg: wrapped, yaw: state.targetYaw + (wrapped * Math.PI) / 180 };
};

export function initialSteering(targetYaw: number): SteeringState {
  return { targetYaw, offsetDeg: 0, yaw: targetYaw, attempts: 0, freeSinceMs: null, stuckEvents: 0, teleports: 0 };
}

/**
 * Obstacle avoidance for the perf traversal: when progress stalls, detour (turn like a wall
 * follower, back off now and then, or climb while flying); once moving freely again, drift back
 * toward the target heading one step at a time. A dead end the detours cannot escape ends in a
 * teleport ahead along the target heading, so the window still streams new terrain.
 */
export function steer(state: SteeringState, input: SteerInput): { state: SteeringState; action: SteerAction } {
  if (input.progressBlocks < STUCK_BLOCKS) {
    const stuck = { ...state, attempts: state.attempts + 1, freeSinceMs: null, stuckEvents: state.stuckEvents + 1 };
    if (stuck.attempts >= TELEPORT_AFTER) {
      return { state: withOffset({ ...stuck, attempts: 0, teleports: stuck.teleports + 1 }, 0), action: "teleport" };
    }
    if (input.mode === "fly") {
      if (stuck.attempts % TURN_WHILE_FLYING_EVERY !== 0) return { state: stuck, action: "ascend" };
      return { state: withOffset(stuck, stuck.offsetDeg + TURN_DEG), action: "turn" };
    }
    const action = stuck.attempts % BACKOFF_EVERY === 0 ? "backoff" : "turn";
    return { state: withOffset(stuck, stuck.offsetDeg + TURN_DEG), action };
  }

  const moving = { ...state, attempts: 0 };
  if (moving.freeSinceMs === null) return { state: { ...moving, freeSinceMs: input.nowMs }, action: "none" };
  if (moving.offsetDeg === 0 || input.nowMs - moving.freeSinceMs < RETURN_AFTER_MS) {
    return { state: moving, action: "none" };
  }
  const towardTarget = moving.offsetDeg <= 180 ? moving.offsetDeg - TURN_DEG : moving.offsetDeg + TURN_DEG;
  return { state: { ...withOffset(moving, towardTarget), freeSinceMs: input.nowMs }, action: "return" };
}
