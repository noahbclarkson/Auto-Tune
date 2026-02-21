'use client';

import { getSpreadFactors, MarketConfig } from '@/lib/market-engine';
import { formatPrice } from '@/lib/utils';

interface PricePreviewProps {
  buyPrice: number;
  sellPrice: number;
  basePrice: number;
  bpd: number;
  spd: number;
  buyRatio: number;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  distinctTraders: number;
  config: MarketConfig;
}

function FactorBar({ label, value, isMultiplier = true }: { label: string; value: number; isMultiplier?: boolean }) {
  const pct = isMultiplier
    ? Math.min(value * 50, 100) // scale: 2.0 → 100%
    : Math.min(value, 100);
  const neutral = isMultiplier && Math.abs(value - 1.0) < 0.001;
  const good = isMultiplier ? value < 1.0 : value < 10;
  const color = neutral ? 'bg-gray-600' : good ? 'bg-emerald-600' : 'bg-amber-600';

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500 w-28 shrink-0">{label}</span>
      <div className="flex-1 h-1.5 rounded-full bg-gray-800 overflow-hidden">
        <div className={`h-full rounded-full ${color} transition-all`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`text-xs font-mono w-12 text-right ${neutral ? 'text-gray-500' : good ? 'text-emerald-400' : 'text-amber-400'}`}>
        ×{value.toFixed(2)}
      </span>
    </div>
  );
}

export function PricePreview({
  buyPrice,
  sellPrice,
  basePrice,
  bpd,
  spd,
  buyRatio,
  onlinePlayers,
  zScore,
  weightedVolume,
  distinctTraders,
  config,
}: PricePreviewProps) {
  const totalSpread = bpd + spd;
  const profitMargin = ((buyPrice - sellPrice) / basePrice) * 100;
  const factors = getSpreadFactors(buyRatio, onlinePlayers, zScore, weightedVolume, distinctTraders, config);

  // Spread bar positions
  const spreadBarW = Math.min(totalSpread * 150, 80); // visual percentage

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
        <h3 className="text-sm font-semibold text-white">Calculated Prices</h3>
        <span className="text-xs font-mono text-gray-500">base ${basePrice.toLocaleString()}</span>
      </div>

      <div className="p-4 space-y-4">
        {/* Main buy/sell display */}
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-lg border border-emerald-900/60 bg-emerald-950/20 px-4 py-3">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">Buy (ask)</p>
            <p className="text-2xl font-bold text-emerald-400 font-mono">{formatPrice(buyPrice)}</p>
            <p className="text-xs text-emerald-600 font-mono mt-0.5">+{(bpd * 100).toFixed(2)}% spread</p>
          </div>
          <div className="rounded-lg border border-rose-900/60 bg-rose-950/20 px-4 py-3">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">Sell (bid)</p>
            <p className="text-2xl font-bold text-rose-400 font-mono">{formatPrice(sellPrice)}</p>
            <p className="text-xs text-rose-700 font-mono mt-0.5">-{(spd * 100).toFixed(2)}% spread</p>
          </div>
        </div>

        {/* Visual spread bar */}
        <div className="rounded-lg border border-gray-800 bg-gray-950/50 px-3 py-2.5">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs text-rose-400 font-mono">{formatPrice(sellPrice)}</span>
            <span className="text-xs text-gray-500 font-mono">{(totalSpread * 100).toFixed(2)}% total spread</span>
            <span className="text-xs text-emerald-400 font-mono">{formatPrice(buyPrice)}</span>
          </div>
          <div className="relative h-2 rounded-full bg-gray-800">
            {/* Base price indicator */}
            <div className="absolute top-0 bottom-0 left-1/2 w-0.5 bg-gray-500 -translate-x-px rounded" />
            {/* Spread zones */}
            <div
              className="absolute top-0 bottom-0 left-1/2 rounded-r-full bg-emerald-600/50"
              style={{ width: `${spreadBarW / 2}%` }}
            />
            <div
              className="absolute top-0 bottom-0 rounded-l-full bg-rose-600/50"
              style={{ right: '50%', width: `${spreadBarW / 2}%` }}
            />
          </div>
        </div>

        {/* Stats row */}
        <div className="grid grid-cols-3 gap-2">
          {[
            { label: 'Total Spread', value: `${(totalSpread * 100).toFixed(2)}%` },
            { label: 'Server Margin', value: `${profitMargin.toFixed(2)}%`, highlight: true },
            { label: 'Player Scaling', value: `${factors.playerScalingPct.toFixed(0)}%` },
          ].map(({ label, value, highlight }) => (
            <div key={label} className="rounded-md border border-gray-800 bg-gray-800/30 px-2.5 py-2 text-center">
              <p className="text-xs text-gray-500 mb-0.5">{label}</p>
              <p className={`text-sm font-mono font-semibold ${highlight ? (profitMargin >= 0 ? 'text-emerald-400' : 'text-rose-400') : 'text-white'}`}>
                {value}
              </p>
            </div>
          ))}
        </div>

        {/* Spread factor breakdown */}
        <div className="rounded-lg border border-gray-800 bg-gray-950/40 px-3 py-3">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-2.5">BPD Factors (multiplicative)</p>
          <div className="space-y-2">
            <FactorBar label="Imbalance" value={factors.imbalanceMultiplier} />
            <FactorBar label="Liquidity" value={factors.liquidityFactor} />
            <FactorBar label="Players" value={factors.playerFactor} />
            <FactorBar label="Global vol" value={factors.globalVolumeFactor} />
          </div>
          <div className="mt-2.5 pt-2 border-t border-gray-800 flex justify-between">
            <span className="text-xs text-gray-500">Base half-spread</span>
            <span className="text-xs font-mono text-gray-400">{factors.baseHalfSpread.toFixed(2)}%</span>
          </div>
          <div className="flex justify-between mt-1">
            <span className="text-xs text-gray-400 font-medium">→ BPD</span>
            <span className="text-xs font-mono text-emerald-400 font-bold">{(bpd * 100).toFixed(3)}%</span>
          </div>
        </div>
      </div>
    </div>
  );
}
