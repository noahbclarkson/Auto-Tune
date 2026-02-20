/**
 * Auto-Tune Market Engine - JavaScript Port
 * 
 * Ported from Python: scripts/market_curves.py
 * 
 * This implements the complete market pricing algorithm used by the Auto-Tune
 * Minecraft Paper plugin for adaptive market pricing.
 */

// Precomputed constant for tanh scaling
const ATANH_099 = Math.atanh(0.99); // ~2.6467

export interface MarketConfig {
  baseSpread: number;          // 0.30 - base spread between buy/sell
  volumeImpact: number;        // 0.5 - how much buy/sell imbalance affects spread
  playerImpact: number;        // 0.7 - how much player count reduces spread
  fullEffectPlayers: number;   // 20 - players needed for 99% effect
  maxPriceChangePercent: number; // 3.0 - max price change per tick
  liquidityCoeff: number;      // 0.05 - per-item liquidity coefficient
  tradeWindowDays: number;     // 7 - days for recency weighting
}

export const DEFAULT_CONFIG: MarketConfig = {
  baseSpread: 0.30,
  volumeImpact: 0.5,
  playerImpact: 0.7,
  fullEffectPlayers: 20,
  maxPriceChangePercent: 3.0,
  liquidityCoeff: 0.05,
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
 * |z| <= 1: multiplier = 1.0 (normal spread)
 * z = +2: multiplier = 0.5 (half spread - high activity)
 * z = -2: multiplier = 2.0 (double spread - low activity)
 * 
 * Linear interpolation between 1-2 SD, capped beyond 2 SD.
 * 
 * @param z - Z-score of recent bucket volume
 * @returns Multiplier to apply to spread
 */
export function globalVolumeMultiplier(z: number): number {
  // Flat zone: |z| <= 1
  if (Math.abs(z) <= 1.0) {
    return 1.0;
  }
  
  // High volume (z > 1): reduce spread
  if (z > 1.0) {
    const t = Math.min(z - 1.0, 1.0); // Clamp to 0-1
    return 1.0 - 0.5 * t; // Goes from 1.0 to 0.5
  }
  
  // Low volume (z < -1): increase spread
  const t = Math.min(-z - 1.0, 1.0); // Clamp to 0-1
  return 1.0 + t; // Goes from 1.0 to 2.0
}

/**
 * Per-item liquidity reduction: tighter spreads for heavily traded items.
 * 
 * Uses a simple inverse function: 1 / (1 + volume * coeff)
 * As volume increases, the result approaches 0.
 * 
 * @param totalWeightedVolume - Sum of weighted buy + sell volumes
 * @param liquidityCoeff - Coefficient controlling reduction rate
 * @returns Multiplier to apply to spread (0-1)
 */
export function liquidityReduction(
  totalWeightedVolume: number,
  liquidityCoeff: number = DEFAULT_CONFIG.liquidityCoeff
): number {
  return 1.0 / (1.0 + totalWeightedVolume * liquidityCoeff);
}

/**
 * Calculate BPD (Buy Price Delta) and SPD (Sell Price Delta).
 * 
 * This is the core spread calculation that considers:
 * 1. Base spread (e.g., 30%)
 * 2. Volume imbalance (buy/sell ratio)
 * 3. Player count scaling
 * 4. Global volume adjustment (z-score)
 * 5. Per-item liquidity
 * 
 * @param buyRatio - Fraction of trades that are buys (0-1)
 * @param onlineCount - Current online player count
 * @param zScore - Z-score of recent market activity
 * @param totalWeightedVolume - Weighted volume for liquidity calc
 * @param config - Market configuration parameters
 * @returns Object with bpd and spd (both as decimals, e.g., 0.15 = 15%)
 */
export function calculateSpread(
  buyRatio: number,
  onlineCount: number,
  zScore: number,
  totalWeightedVolume: number,
  config: MarketConfig = DEFAULT_CONFIG
): { bpd: number; spd: number } {
  // Start with half-spread each side
  const halfSpread = config.baseSpread / 2.0;
  
  // Calculate imbalance: -1 (all sells) to +1 (all buys)
  const imbalance = (buyRatio - 0.5) * 2.0;
  
  // Apply volume impact to spread asymmetry
  // More buys → higher BPD, more sells → higher SPD
  let bpd = halfSpread + Math.max(0, imbalance) * halfSpread * config.volumeImpact;
  let spd = halfSpread + Math.max(0, -imbalance) * halfSpread * config.volumeImpact;
  
  // Apply per-item liquidity reduction
  const liq = liquidityReduction(totalWeightedVolume, config.liquidityCoeff);
  bpd *= liq;
  spd *= liq;
  
  // Apply player count scaling (more players = tighter spreads)
  const ps = playerScaling(onlineCount, config.fullEffectPlayers);
  const playerFactor = 1.0 - config.playerImpact * ps;
  bpd *= playerFactor;
  spd *= playerFactor;
  
  // Apply global volume multiplier
  const gvm = globalVolumeMultiplier(zScore);
  bpd *= gvm;
  spd *= gvm;
  
  return { bpd, spd };
}

/**
 * Calculate buy and sell prices given base price and market conditions.
 * 
 * @param basePrice - The item's base/reference price
 * @param buyRatio - Fraction of trades that are buys (0-1)
 * @param onlineCount - Current online player count
 * @param zScore - Z-score of recent market activity
 * @param totalWeightedVolume - Weighted volume for liquidity calc
 * @param config - Market configuration parameters
 * @returns Object with buyPrice, sellPrice, bpd, spd
 */
export function calculatePrices(
  basePrice: number,
  buyRatio: number,
  onlineCount: number,
  zScore: number,
  totalWeightedVolume: number,
  config: MarketConfig = DEFAULT_CONFIG
): { buyPrice: number; sellPrice: number; bpd: number; spd: number } {
  const { bpd, spd } = calculateSpread(
    buyRatio,
    onlineCount,
    zScore,
    totalWeightedVolume,
    config
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
 * @param config - Market configuration
 * @param onlineCount - Player count to use
 * @param zScore - Z-score to use
 * @param totalWeightedVolume - Volume to use
 * @returns Array of { buyRatio, bpd, spd, totalSpread } objects
 */
export function generateSpreadCurve(
  config: MarketConfig = DEFAULT_CONFIG,
  onlineCount: number = 10,
  zScore: number = 0,
  totalWeightedVolume: number = 0
): Array<{ buyRatio: number; bpd: number; spd: number; totalSpread: number }> {
  const points: Array<{ buyRatio: number; bpd: number; spd: number; totalSpread: number }> = [];
  
  for (let i = 0; i <= 100; i++) {
    const buyRatio = i / 100;
    const { bpd, spd } = calculateSpread(buyRatio, onlineCount, zScore, totalWeightedVolume, config);
    points.push({
      buyRatio,
      bpd: bpd * 100, // Convert to percentage
      spd: spd * 100,
      totalSpread: (bpd + spd) * 100,
    });
  }
  
  return points;
}
