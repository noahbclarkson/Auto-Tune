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
  const ps = playerScaling(onlinePlayers, config.fullEffectPlayers);
  
  for (let i = 0; i < nTicks; i++) {
    // Trade ratio: -1 (all sells) to +1 (all buys)
    const tradeRatio = (buyProbability - 0.5) * 2.0;
    
    // Price change as percentage
    const changePct = tradeRatio * ps * (config.maxPriceChangePercent / 100.0);
    
    // Apply change
    const newPrice = prices[prices.length - 1] * (1 + changePct);
    prices.push(Math.max(0.01, newPrice)); // Floor at 0.01
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
