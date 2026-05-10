'use client';

import { MarketConfig } from '@/lib/market-engine';

interface StabilityForecastProps {
  buyRatio: number;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  distinctTraders: number;
  config: MarketConfig;
}

interface Warning {
  severity: 'info' | 'warn' | 'alert';
  message: string;
  detail: string;
}

function getStabilityScore(
  buyRatio: number,
  onlinePlayers: number,
  zScore: number,
  weightedVolume: number,
  distinctTraders: number,
  config: MarketConfig,
): { score: number; label: string; color: string; bgColor: string; borderColor: string } {
  let score = 95;

  // Extreme buy ratio — markets biased one direction
  if (buyRatio < 0.2 || buyRatio > 0.85) score -= 15;
  else if (buyRatio < 0.3 || buyRatio > 0.75) score -= 8;
  else if (buyRatio < 0.4 || buyRatio > 0.65) score -= 3;

  // Very few players — price signals noisy
  if (onlinePlayers < 3) score -= 20;
  else if (onlinePlayers < 5) score -= 10;
  else if (onlinePlayers < 8) score -= 4;

  // Extreme z-score — high volatility environment
  if (Math.abs(zScore) > 2.0) score -= 15;
  else if (Math.abs(zScore) > 1.5) score -= 8;
  else if (Math.abs(zScore) > 1.0) score -= 3;

  // Very high spread — wider spreads = slower convergence
  if (config.baseSpread > 0.50) score -= 5;
  else if (config.baseSpread > 0.35) score -= 2;

  // Low volume with few traders — liquidity risk
  if (weightedVolume < 20 && distinctTraders < 3) score -= 8;
  else if (weightedVolume < 50 && distinctTraders < 5) score -= 3;

  // Low liquidity coefficient = less spread compression = slightly more volatile
  if (config.liquidityCoeff < 0.005) score -= 4;
  else if (config.liquidityCoeff < 0.008) score -= 2;

  score = Math.max(0, Math.min(100, score));

  if (score >= 85) return { score, label: 'Stable', color: 'text-emerald-400', bgColor: 'bg-emerald-950/30', borderColor: 'border-emerald-800/50' };
  if (score >= 65) return { score, label: 'Caution', color: 'text-amber-400', bgColor: 'bg-amber-950/30', borderColor: 'border-amber-800/50' };
  return { score, label: 'volatile', color: 'text-rose-400', bgColor: 'bg-rose-950/30', borderColor: 'border-rose-800/50' };
}

function getWarnings(
  buyRatio: number,
  onlinePlayers: number,
  zScore: number,
  weightedVolume: number,
  distinctTraders: number,
  config: MarketConfig,
): Warning[] {
  const warnings: Warning[] = [];

  if (buyRatio < 0.2) {
    warnings.push({
      severity: 'alert',
      message: 'Extreme sell pressure',
      detail: `${(buyRatio * 100).toFixed(0)}% of trades are sells. Prices will fall fast. Consider reducing sell pressure or adding buy incentives.`,
    });
  } else if (buyRatio > 0.85) {
    warnings.push({
      severity: 'alert',
      message: 'Extreme buy pressure',
      detail: `${(buyRatio * 100).toFixed(0)}% of trades are buys. Prices will spike rapidly. Consider reducing max-price-change-percent.`,
    });
  } else if (buyRatio < 0.35) {
    warnings.push({
      severity: 'warn',
      message: 'Seller-heavy market',
      detail: `${(buyRatio * 100).toFixed(0)}% buy ratio. Natural seller bias will push prices below base. This is normal on farm-heavy servers — not a bug.`,
    });
  } else if (buyRatio > 0.70) {
    warnings.push({
      severity: 'warn',
      message: 'Buyer-heavy market',
      detail: `${(buyRatio * 100).toFixed(0)}% buy ratio. Sustained buying pressure will push prices above base. Watch for exploitative bulk-buying.`,
    });
  }

  if (onlinePlayers < 3) {
    warnings.push({
      severity: 'alert',
      message: 'Very low player count',
      detail: `Only ${onlinePlayers} player${onlinePlayers !== 1 ? 's' : ''} online. Price signals are noisy with few trades. Market may be erratic.`,
    });
  } else if (onlinePlayers < 6) {
    warnings.push({
      severity: 'warn',
      message: 'Low player activity',
      detail: `${onlinePlayers} players online. Player scaling is reducing price changes — market is sluggish.`,
    });
  }

  if (Math.abs(zScore) > 2.0) {
    warnings.push({
      severity: 'warn',
      message: `Extreme market activity (${zScore > 0 ? '+' : ''}${zScore.toFixed(1)}σ)`,
      detail: `Market activity is far from normal. Spreads will be wide and prices volatile.`,
    });
  }

  if (weightedVolume < 20 && distinctTraders < 3) {
    warnings.push({
      severity: 'warn',
      message: 'Thin liquidity',
      detail: `Very low volume (${weightedVolume.toFixed(0)}) with ${distinctTraders} trader${distinctTraders !== 1 ? 's' : ''}. Spreads will be wide, prices sensitive to individual trades.`,
    });
  }

  if (config.maxPriceChangePercent > 3.0) {
    warnings.push({
      severity: 'info',
      message: 'High max price change',
      detail: `${config.maxPriceChangePercent}% per tick cap. Prices can move quickly — good for new servers, risky for established economies.`,
    });
  }

  if (config.baseSpread > 0.40) {
    warnings.push({
      severity: 'info',
      message: 'Wide base spread',
      detail: `${(config.baseSpread * 100).toFixed(0)}% base spread. Buy/sell gap is large — good for server profit, less attractive for active traders.`,
    });
  }

  return warnings;
}

const SEVERITY_STYLES = {
  info: {
    badge: 'bg-gray-800 text-gray-300',
    border: 'border-gray-700',
    icon: '●',
    iconColor: 'text-gray-400',
  },
  warn: {
    badge: 'bg-amber-900/50 text-amber-300',
    border: 'border-amber-800/40',
    icon: '▲',
    iconColor: 'text-amber-400',
  },
  alert: {
    badge: 'bg-rose-900/50 text-rose-300',
    border: 'border-rose-800/40',
    icon: '⚠',
    iconColor: 'text-rose-400',
  },
};

export function StabilityForecast({
  buyRatio,
  onlinePlayers,
  zScore,
  weightedVolume,
  distinctTraders,
  config,
}: StabilityForecastProps) {
  const { score, label, color, bgColor, borderColor } = getStabilityScore(
    buyRatio, onlinePlayers, zScore, weightedVolume, distinctTraders, config,
  );
  const warnings = getWarnings(buyRatio, onlinePlayers, zScore, weightedVolume, distinctTraders, config);

  return (
    <div className={`rounded-xl border ${borderColor} ${bgColor} overflow-hidden`}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800">
        <h3 className="text-sm font-semibold text-white">Stability Forecast</h3>
        <div className="flex items-center gap-2">
          <div className="w-20 h-1.5 rounded-full bg-gray-800 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all ${score >= 85 ? 'bg-emerald-500' : score >= 65 ? 'bg-amber-500' : 'bg-rose-500'}`}
              style={{ width: `${score}%` }}
            />
          </div>
          <span className={`text-sm font-bold ${color} font-mono`}>{score}%</span>
          <span className={`text-xs font-medium ${color}`}>{label}</span>
        </div>
      </div>

      <div className="p-4 space-y-3">
        {/* Key insight */}
        <div className="rounded-lg bg-gray-900/60 border border-gray-800 px-3 py-2.5">
          <p className="text-xs text-gray-400 leading-relaxed">
            <span className="text-emerald-400 font-medium">From 840-config simulation:</span>{' '}
            Auto-Tune's engine is mathematically stable across its full parameter range.
            All tested configs stayed under 0.05 avg volatility. Price convergence depends
            more on <span className="text-gray-300">player activity</span> than on
            parameter tuning.
          </p>
        </div>

        {/* Warnings */}
        {warnings.length > 0 ? (
          <div className="space-y-2">
            <p className="text-xs text-gray-500 uppercase tracking-wider">Conditions to watch</p>
            {warnings.map((w, i) => {
              const style = SEVERITY_STYLES[w.severity];
              return (
                <div
                  key={i}
                  className={`flex items-start gap-2.5 rounded-lg border ${style.border} bg-gray-900/40 px-3 py-2.5`}
                >
                  <span className={`text-sm mt-0.5 ${style.iconColor}`}>{style.icon}</span>
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5 mb-0.5">
                      <span className={`text-xs font-semibold ${style.iconColor}`}>{w.message}</span>
                    </div>
                    <p className="text-xs text-gray-400 leading-relaxed">{w.detail}</p>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="flex items-center gap-2 rounded-lg bg-emerald-950/30 border border-emerald-800/30 px-3 py-2.5">
            <span className="text-emerald-400 text-sm">✓</span>
            <p className="text-xs text-emerald-300">
              Market conditions are balanced. No stability concerns with these parameters.
            </p>
          </div>
        )}

        {/* Circuit breaker note */}
        <div className="flex items-start gap-2 rounded-lg border border-amber-800/30 bg-amber-950/20 px-3 py-2.5">
          <span className="text-amber-400 text-sm mt-0.5">⛓</span>
          <p className="text-xs text-amber-200/80 leading-relaxed">
            <strong className="text-amber-300">Loan circuit breaker:</strong> Interest pauses when
            debt exceeds <span className="font-mono text-amber-300">GDP × 30</span>.
            Set <span className="font-mono">loans.tier3-ratio</span> in config.yml (default: 30, range: 20–40).
            Never disable it — pre-breaker simulations showed catastrophic compound loops.
          </p>
        </div>
      </div>
    </div>
  );
}
