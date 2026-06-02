/**
 * Auto-Tune Market Engine - TypeScript Port
 *
 * Mirrors the Java MarketEngine (src/main/java/.../manager/MarketEngine.java)
 * and Rust simulation (scripts/market-simulation/src/engine.rs).
 *
 * Default values match the Rust simulation defaults (config.rs).
 */

// Precomputed constant: atanh(0.99) ≈ 2.6467
const ATANH_099 = Math.atanh(0.99);

export interface MarketConfig {
  /** Base spread between buy/sell (0.20 = 20%). */
  baseSpread: number;
  /** How much buy/sell imbalance widens the spread asymmetrically. */
  volumeImpact: number;
  /** How much player count compresses spreads (0–1). */
  playerImpact: number;
  /** Players needed for ~99% of the spread reduction effect. */
  fullEffectPlayers: number;
  /** Max price change per market tick (%). */
  maxPriceChangePercent: number;
  /** Multiplier applied to downward price moves caused by sell pressure. */
  sellPressureMultiplier: number;
  /** Dampening applied when price moves continue in the same direction. */
  trendDampening: number;
  /** Minimum tick change (%) that counts toward a trend streak. */
  trendStreakThresholdPercent: number;
  /** Lower bound for trend dampening. */
  trendDampeningFloor: number;
  /** Liquidity coefficient controlling how fast spreads tighten with volume. */
  liquidityCoeff: number;
  /** Distinct-trader count at which the full liquidity coefficient applies. */
  liquidityFullEffectTraders: number;
  /** Window for recency-weighted trade history (days). */
  tradeWindowDays: number;
}

/** Defaults match the Rust simulation (scripts/market-simulation/src/config.rs). */
export const DEFAULT_CONFIG: MarketConfig = {
  baseSpread: 0.20,
  volumeImpact: 0.8,
  playerImpact: 0.6,
  fullEffectPlayers: 10,
  maxPriceChangePercent: 1.5,
  sellPressureMultiplier: 1.0,
  trendDampening: 0.10,
  trendStreakThresholdPercent: 0.1,
  trendDampeningFloor: 0.25,
  liquidityCoeff: 0.01,
  liquidityFullEffectTraders: 10,
  tradeWindowDays: 7,
};

/**
 * Calculate player scaling using tanh with derived coefficient.
 * 
 * This creates a smooth curve that approaches 1.0 as player count increases,
 * with diminishing returns. The coefficient is derived so that at
 * fullEffectPlayers, the scaling is 0.99.
 * 
 * @param onlineCount - Current online player count
 * @param fullEffectPlayers - Players needed for 99% effect
 * @returns Scaling factor between 0 and ~1
 */
export function playerScaling(
  onlineCount: number,
  fullEffectPlayers: number = DEFAULT_CONFIG.fullEffectPlayers
): number {
  if (onlineCount === 0) {
    return 0.0;
  }
  const coefficient = ATANH_099 / fullEffectPlayers;
  return Math.tanh(onlineCount * coefficient);
}

/**
 * Calculate spread multiplier from z-score of recent bucket volume.
 *
 * |z| ≤ 1  → 1.0 (normal)
 * z  = +2  → 0.8 (high activity — 20% reduction)
 * z  = -2  → 1.3 (low activity  — 30% increase)
 *
 * Mirrors Java MarketEngine.calculateGlobalVolumeMultiplier and
 * Rust engine.rs MarketEngine::calculate_global_volume_multiplier.
 */
export function globalVolumeMultiplier(z: number): number {
  if (Math.abs(z) <= 1.0) return 1.0;

  if (z > 1.0) {
    const t = Math.min(z - 1.0, 1.0);
    return 1.0 - 0.2 * t; // 1.0 → 0.8
  }

  const t = Math.min(-z - 1.0, 1.0);
  return 1.0 + 0.3 * t; // 1.0 → 1.3
}

/**
 * Per-item liquidity reduction scaled by distinct trader count.
 *
 * The effective coefficient is scaled proportionally to how many unique traders
 * are active (capped at fullEffectTraders), so items with more unique participants
 * get tighter spreads faster.
 *
 * Mirrors Java MarketEngine.calculateSpread and Rust engine.rs.
 *
 * @param totalWeightedVolume - Sum of weighted buy + sell volumes
 * @param distinctTraders     - Number of unique traders in the window
 * @param liquidityCoeff      - Base coefficient from config
 * @param liquidityFullEffectTraders - Trader count for full coefficient effect
 */
export function liquidityReduction(
  totalWeightedVolume: number,
  distinctTraders: number = 1,
  liquidityCoeff: number = DEFAULT_CONFIG.liquidityCoeff,
  liquidityFullEffectTraders: number = DEFAULT_CONFIG.liquidityFullEffectTraders,
): number {
  const clampedTraders = Math.min(distinctTraders, liquidityFullEffectTraders);
  const effectiveCoeff =
    liquidityFullEffectTraders > 0
      ? (liquidityCoeff / liquidityFullEffectTraders) * clampedTraders
      : 0;
  return 1.0 / (1.0 + totalWeightedVolume * effectiveCoeff);
}

/**
 * Calculate BPD (Buy Price Delta) and SPD (Sell Price Delta).
 *
 * Pipeline (matches Java + Rust):
 *   1. Base half-spread
 *   2. Volume imbalance shift
 *   3. Per-item liquidity reduction (trader-scaled)
 *   4. Player count reduction
 *   5. Global volume multiplier (z-score)
 *
 * @param buyRatio            - Fraction of trades that are buys (0–1)
 * @param onlineCount         - Current online player count
 * @param zScore              - Z-score of recent market activity
 * @param totalWeightedVolume - Weighted volume for liquidity calc
 * @param distinctTraders     - Unique traders contributing to volume
 * @param config              - Market configuration parameters
 */
export function calculateSpread(
  buyRatio: number,
  onlineCount: number,
  zScore: number,
  totalWeightedVolume: number,
  distinctTraders: number = 1,
  config: MarketConfig = DEFAULT_CONFIG,
): { bpd: number; spd: number } {
  const halfSpread = config.baseSpread / 2.0;
  const imbalance = (buyRatio - 0.5) * 2.0;

  let bpd = halfSpread + Math.max(0, imbalance) * halfSpread * config.volumeImpact;
  let spd = halfSpread + Math.max(0, -imbalance) * halfSpread * config.volumeImpact;

  const liq = liquidityReduction(
    totalWeightedVolume,
    distinctTraders,
    config.liquidityCoeff,
    config.liquidityFullEffectTraders,
  );
  bpd *= liq;
  spd *= liq;

  const ps = playerScaling(onlineCount, config.fullEffectPlayers);
  const playerFactor = 1.0 - config.playerImpact * ps;
  bpd *= playerFactor;
  spd *= playerFactor;

  const gvm = globalVolumeMultiplier(zScore);
  bpd *= gvm;
  spd *= gvm;

  return { bpd, spd };
}

/**
 * Calculate buy and sell prices given base price and market conditions.
 */
export function calculatePrices(
  basePrice: number,
  buyRatio: number,
  onlineCount: number,
  zScore: number,
  totalWeightedVolume: number,
  distinctTraders: number = 1,
  config: MarketConfig = DEFAULT_CONFIG,
): { buyPrice: number; sellPrice: number; bpd: number; spd: number } {
  const { bpd, spd } = calculateSpread(
    buyRatio, onlineCount, zScore, totalWeightedVolume, distinctTraders, config,
  );
  return {
    buyPrice: basePrice * (1 + bpd),
    sellPrice: basePrice * (1 - spd),
    bpd,
    spd,
  };
}

/**
 * Linear recency weighting: weight = max(0, 1 - age/window).
 * 
 * Transactions older than the window get zero weight.
 * Recent transactions get full weight.
 * 
 * @param ageDays - Age of the transaction in days
 * @param windowDays - Trade window in days
 * @returns Weight between 0 and 1
 */
export function recencyWeight(
  ageDays: number,
  windowDays: number = DEFAULT_CONFIG.tradeWindowDays
): number {
  return Math.max(0.0, 1.0 - ageDays / windowDays);
}

export type TrendDirection = 'UP' | 'DOWN' | 'STABLE';

interface TrendState {
  direction: TrendDirection;
  streak: number;
}

function calculatePriceChangePercent(
  buyProbability: number,
  onlinePlayers: number,
  config: MarketConfig,
  trend: TrendState,
): number {
  const tradeRatio = (buyProbability - 0.5) * 2.0;
  const ps = playerScaling(onlinePlayers, config.fullEffectPlayers);
  let changePct = tradeRatio * ps * (config.maxPriceChangePercent / 100.0);

  if (changePct < 0) {
    changePct *= config.sellPressureMultiplier;
  }

  if (trend.streak > 0 && config.trendDampening > 0) {
    const continuingStreak =
      (changePct > 0 && trend.direction === 'UP') ||
      (changePct < 0 && trend.direction === 'DOWN');
    if (continuingStreak) {
      const rawDampening = 1.0 / (1.0 + trend.streak * config.trendDampening);
      changePct *= Math.max(rawDampening, config.trendDampeningFloor);
    }
  }

  return changePct;
}

function updateTrendState(oldPrice: number, newPrice: number, trend: TrendState, config: MarketConfig): TrendState {
  if (oldPrice <= 0) return { direction: 'STABLE', streak: 0 };

  const pctChange = (newPrice - oldPrice) / oldPrice;
  const threshold = config.trendStreakThresholdPercent / 100.0;
  const nextDirection: TrendDirection =
    pctChange > threshold ? 'UP' : pctChange < -threshold ? 'DOWN' : 'STABLE';

  if (nextDirection === 'STABLE') {
    return { direction: 'STABLE', streak: 0 };
  }
  if (nextDirection === trend.direction) {
    return { direction: nextDirection, streak: trend.streak + 1 };
  }
  return { direction: nextDirection, streak: 1 };
}

/**
 * Simulate price evolution over multiple ticks given constant buy probability.
 * 
 * This models how prices would evolve if the buy probability stays constant
 * over time, scaled by player count.
 * 
 * @param nTicks - Number of ticks to simulate
 * @param initialPrice - Starting price
 * @param buyProbability - Probability of buy vs sell (0-1)
 * @param onlinePlayers - Number of online players
 * @param config - Market configuration
 * @returns Array of prices over time (length = nTicks + 1)
 */
export function simulatePrice(
  nTicks: number,
  initialPrice: number,
  buyProbability: number,
  onlinePlayers: number,
  config: MarketConfig = DEFAULT_CONFIG
): number[] {
  const prices: number[] = [initialPrice];
  let trend: TrendState = { direction: 'STABLE', streak: 0 };
  
  for (let i = 0; i < nTicks; i++) {
    const oldPrice = prices[prices.length - 1];
    const changePct = calculatePriceChangePercent(buyProbability, onlinePlayers, config, trend);
    const newPrice = Math.max(0.01, oldPrice * (1 + changePct)); // Floor at 0.01
    prices.push(newPrice);
    trend = updateTrendState(oldPrice, newPrice, trend, config);
  }
  
  return prices;
}

/**
 * Generate spread curve data for visualization.
 *
 * @param config              - Market configuration
 * @param onlineCount         - Player count to use
 * @param zScore              - Z-score to use
 * @param totalWeightedVolume - Volume to use for liquidity calc
 * @param distinctTraders     - Unique trader count for liquidity calc
 */
export function generateSpreadCurve(
  config: MarketConfig = DEFAULT_CONFIG,
  onlineCount: number = 10,
  zScore: number = 0,
  totalWeightedVolume: number = 0,
  distinctTraders: number = 5,
): Array<{ buyRatio: number; bpd: number; spd: number; totalSpread: number }> {
  const points = [];
  for (let i = 0; i <= 100; i++) {
    const buyRatio = i / 100;
    const { bpd, spd } = calculateSpread(
      buyRatio, onlineCount, zScore, totalWeightedVolume, distinctTraders, config,
    );
    points.push({ buyRatio, bpd: bpd * 100, spd: spd * 100, totalSpread: (bpd + spd) * 100 });
  }
  return points;
}

/**
 * Returns the multiplicative contribution of each spread factor.
 * Useful for building a breakdown panel in the UI.
 */
export function getSpreadFactors(
  buyRatio: number,
  onlineCount: number,
  zScore: number,
  totalWeightedVolume: number,
  distinctTraders: number = 1,
  config: MarketConfig = DEFAULT_CONFIG,
) {
  const halfSpread = config.baseSpread / 2.0;
  const imbalance = (buyRatio - 0.5) * 2.0;

  // BPD after imbalance step
  const bpdAfterImbalance = halfSpread + Math.max(0, imbalance) * halfSpread * config.volumeImpact;

  const liq = liquidityReduction(
    totalWeightedVolume, distinctTraders, config.liquidityCoeff, config.liquidityFullEffectTraders,
  );
  const ps = playerScaling(onlineCount, config.fullEffectPlayers);
  const playerFactor = 1.0 - config.playerImpact * ps;
  const gvm = globalVolumeMultiplier(zScore);

  return {
    baseHalfSpread: halfSpread * 100,
    imbalanceMultiplier: halfSpread > 0 ? bpdAfterImbalance / halfSpread : 1,
    liquidityFactor: liq,
    playerFactor,
    globalVolumeFactor: gvm,
    playerScalingPct: ps * 100,
  };
}

// ─── Market Events ───────────────────────────────────────────────────────────

/** Mirrors Java MarketEvent.EventType and Rust events.rs */
export type MarketEventType =
  | 'DEMAND_SURGE'
  | 'SUPPLY_GLUT'
  | 'INFLATION_BOOST'
  | 'DEFLATION_DROP'
  | 'GOLD_RUSH'
  | 'CUSTOM';

/** Mirrors Java MarketEvent record */
export interface MarketEvent {
  id: string;
  name: string;
  type: MarketEventType;
  /** Material patterns — exact names or wildcard patterns (PREFIX_*, *_SUFFIX, *MIDDLE*) */
  materials: string[];
  /** Price change multiplier (e.g. 2.0 = 2× price change velocity) */
  priceChangeMultiplier: number;
  /** Human-readable description for UI */
  description?: string;
}

/**
 * Returns true if a material name matches a wildcard pattern.
 * Mirrors Java MarketEvent.matchesPattern():
 *   "GOLD_*"   → starts with "GOLD_"
 *   "*_INGOT"  → ends with "_INGOT"
 *   "*GOLD*"   → contains "GOLD"
 */
export function matchesMaterialPattern(material: string, pattern: string): boolean {
  const m = material.toUpperCase();
  const p = pattern.toUpperCase();

  if (m === p) return true;
  if (p.endsWith('_*')) return m.startsWith(p.slice(0, -2) + '_');
  if (p.startsWith('*_')) return m.endsWith('_' + p.slice(2));
  if (p.startsWith('*') && p.endsWith('*')) return m.includes(p.slice(1, -1));
  return false;
}

/**
 * Returns true if this event matches the given material.
 */
export function eventMatchesMaterial(event: MarketEvent, material: string): boolean {
  if (!event.materials || event.materials.length === 0) return false;
  return event.materials.some((pat) => matchesMaterialPattern(material, pat));
}

/**
 * Computes the additional price-change effect from an event.
 * Mirrors Java MarketEventService.computeEventEffect() exactly.
 *
 * Effects are additive — they add to (or subtract from) the natural price change.
 * Multiple matching events stack additively.
 *
 * @param type      - Event type
 * @param multiplier - Event multiplier (e.g. 2.0)
 * @param priceChangePercent - Natural price change % BEFORE the event
 * @returns Additional price change % caused by the event
 */
export function computeEventEffect(
  type: MarketEventType,
  multiplier: number,
  priceChangePercent: number,
): number {
  const extra = priceChangePercent * (multiplier - 1.0);

  switch (type) {
    case 'DEMAND_SURGE': {
      // Amplifies upward movements, suppresses downward
      if (priceChangePercent >= 0) {
        return extra; // amplify rising prices
      } else {
        // Push toward zero (less downward)
        return -priceChangePercent * (1.0 - 1.0 / multiplier);
      }
    }
    case 'SUPPLY_GLUT': {
      // Amplifies downward movements, suppresses upward
      if (priceChangePercent <= 0) {
        return extra; // amplify falling prices
      } else {
        // Push toward zero (less upward)
        return -priceChangePercent * (1.0 - 1.0 / multiplier);
      }
    }
    case 'INFLATION_BOOST': {
      // Always add upward drift regardless of direction
      return Math.abs(priceChangePercent) * (multiplier - 1.0);
    }
    case 'DEFLATION_DROP': {
      // Always add downward drift
      return -Math.abs(priceChangePercent) * (multiplier - 1.0);
    }
    case 'GOLD_RUSH':
    case 'CUSTOM': {
      // Symmetric: amplify whatever direction the natural change is going
      return extra;
    }
    default:
      return 0.0;
  }
}

/**
 * Returns the combined event effect for a material across all active events.
 * Mirrors Java MarketEventService.applyEventMultiplier().
 */
export function applyEventMultipliers(
  events: MarketEvent[],
  material: string,
  priceChangePercent: number,
): number {
  if (!events || events.length === 0) return priceChangePercent;

  let result = priceChangePercent;
  for (const event of events) {
    if (!eventMatchesMaterial(event, material)) continue;
    const effect = computeEventEffect(event.type, event.priceChangeMultiplier, priceChangePercent);
    result += effect;
  }
  return result;
}

/**
 * Returns the net multiplicative multiplier for a material (for display).
 * Use for UI display — not for actual price calculations.
 */
export function getNetEventMultiplier(events: MarketEvent[], material: string): number {
  let net = 1.0;
  for (const event of events) {
    if (eventMatchesMaterial(event, material)) {
      net *= event.priceChangeMultiplier;
    }
  }
  return net;
}

/**
 * Human-readable description of what an event type does.
 */
export const EVENT_TYPE_DESCRIPTIONS: Record<MarketEventType, string> = {
  DEMAND_SURGE: 'Buy prices boosted — good time to sell',
  SUPPLY_GLUT: 'Sell prices boosted — players get more for items',
  INFLATION_BOOST: 'All prices drift upward (inflation)',
  DEFLATION_DROP: 'All prices drift downward (deflation)',
  GOLD_RUSH: 'Specific items more valuable to buy',
  CUSTOM: 'Custom multiplier',
};

/**
 * Color mapping for event types (for UI badges).
 */
export const EVENT_TYPE_COLORS: Record<MarketEventType, string> = {
  DEMAND_SURGE: 'text-amber-400',
  SUPPLY_GLUT: 'text-rose-400',
  INFLATION_BOOST: 'text-emerald-400',
  DEFLATION_DROP: 'text-sky-400',
  GOLD_RUSH: 'text-yellow-400',
  CUSTOM: 'text-purple-400',
};

/**
 * Simulate price evolution with optional active market events.
 * When events are provided, their effects are applied each tick.
 */
export function simulatePriceWithEvents(
  nTicks: number,
  initialPrice: number,
  buyProbability: number,
  onlinePlayers: number,
  config: MarketConfig = DEFAULT_CONFIG,
  activeEvents: MarketEvent[] = [],
  material = 'DIAMOND',
): number[] {
  const prices: number[] = [initialPrice];
  let trend: TrendState = { direction: 'STABLE', streak: 0 };

  for (let i = 0; i < nTicks; i++) {
    const oldPrice = prices[prices.length - 1];
    let changePct = calculatePriceChangePercent(buyProbability, onlinePlayers, config, trend);

    // Apply market events (amplify/dampen the natural change)
    if (activeEvents.length > 0) {
      changePct = applyEventMultipliers(activeEvents, material, changePct);
    }

    const newPrice = Math.max(0.01, oldPrice * (1 + changePct)); // Floor at $0.01
    prices.push(newPrice);
    trend = updateTrendState(oldPrice, newPrice, trend, config);
  }

  return prices;
}
