'use client';

import { useState, useMemo } from 'react';
import { Sliders, TrendingUp, TrendingDown } from 'lucide-react';

const BASE_SPREAD = 0.20;
const VOLUME_IMPACT = 0.80;
const LIQUIDITY_COEFF = 0.01;
const FULL_EFFECT_TRADERS = 10;
const FULL_EFFECT_PLAYERS = 10;
const GLOBAL_VOL_RANGE: [number, number] = [0.8, 1.3];

function calcSpread(
  buyRatio: number,
  playerCount: number,
  uniqueTraders: number,
  volumeZ: number,
): { bpd: number; spd: number; imbalance: number } {
  const halfSpread = BASE_SPREAD / 2;
  const imbalance = (buyRatio - 0.5) * 2;
  const imbalanceShift = Math.abs(imbalance) * 0.15 * VOLUME_IMPACT;
  const effectiveTraders = Math.min(uniqueTraders, FULL_EFFECT_TRADERS);
  const liquidity = 1 / (1 + volumeZ * 0 + effectiveTraders * LIQUIDITY_COEFF);
  const playerFactor = Math.tanh((playerCount * Math.atanh(0.99)) / FULL_EFFECT_PLAYERS);
  const clampedZ = Math.max(-2, Math.min(2, volumeZ));
  let globalMult = 1.0;
  if (clampedZ > 1) {
    globalMult = GLOBAL_VOL_RANGE[0] + (GLOBAL_VOL_RANGE[1] - GLOBAL_VOL_RANGE[0]) * (1 - (clampedZ - 1)) / 1;
  } else if (clampedZ < -1) {
    globalMult = GLOBAL_VOL_RANGE[1] - (GLOBAL_VOL_RANGE[1] - GLOBAL_VOL_RANGE[0]) * Math.abs(clampedZ + 1) / 1;
  }
  const multiplier = liquidity * playerFactor * globalMult;
  let bpd: number, spd: number;
  if (imbalance > 0) {
    bpd = halfSpread * (1 + imbalanceShift) * multiplier;
    spd = halfSpread * (1 - imbalanceShift * 0.7) * multiplier;
  } else {
    bpd = halfSpread * (1 - imbalanceShift * 0.7) * multiplier;
    spd = halfSpread * (1 + imbalanceShift) * multiplier;
  }
  return { bpd: Math.max(0.001, bpd), spd: Math.max(0.001, spd), imbalance };
}

interface QuickSimulatorProps {
  embedded?: boolean;
}

export function QuickSimulator({ embedded = false }: QuickSimulatorProps) {
  const [buyRatio, setBuyRatio] = useState(50);
  const [playerCount, setPlayerCount] = useState(10);
  const [uniqueTraders, setUniqueTraders] = useState(8);
  const [volumeZ, setVolumeZ] = useState(0);

  const { bpd, spd, imbalance } = useMemo(
    () => calcSpread(buyRatio / 100, playerCount, uniqueTraders, volumeZ),
    [buyRatio, playerCount, uniqueTraders, volumeZ],
  );

  const imbalanceLabel =
    imbalance > 0.3 ? 'Heavy buy pressure' :
    imbalance > 0.1 ? 'Moderate buy pressure' :
    imbalance < -0.3 ? 'Heavy sell pressure' :
    imbalance < -0.1 ? 'Moderate sell pressure' :
    'Balanced';

  const imbalanceColor =
    imbalance > 0.1 ? 'text-emerald-400' :
    imbalance < -0.1 ? 'text-rose-400' :
    'text-gray-400';

  const spreadColor =
    bpd + spd < 0.25 ? 'text-emerald-400' :
    bpd + spd < 0.40 ? 'text-amber-400' :
    'text-rose-400';

  return (
    <div className={`${embedded ? '' : 'rounded-xl border border-gray-800 bg-gray-900/40 p-5'}`}>
      <div className="flex items-center gap-2 mb-4">
        <Sliders className="w-4 h-4 text-emerald-400" />
        <span className="text-sm font-semibold text-white">
          {embedded ? 'Try it now — adjust the market' : 'Quick Simulator'}
        </span>
        {!embedded && (
          <span className="ml-auto text-xs text-gray-500 font-mono">5-factor spread engine</span>
        )}
      </div>

      {/* Result row */}
      <div className="flex items-center gap-3 mb-4">
        {/* Buy side */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-0.5">
            <TrendingUp className="w-3 h-3 text-emerald-400" />
            <span className="text-xs text-gray-400">Buy premium</span>
          </div>
          <span className="text-lg font-bold font-mono text-emerald-400">{(bpd * 100).toFixed(2)}%</span>
        </div>

        {/* Spread bar */}
        <div className="flex-1 flex flex-col items-center gap-1">
          <div className="flex items-center gap-0.5 w-full h-3">
            <div
              className="h-full bg-emerald-500/70 rounded-l-full min-w-0.5"
              style={{ width: `${Math.min(50, (bpd / (bpd + spd)) * 50)}%` }}
            />
            <div
              className="h-full bg-rose-500/70 rounded-r-full min-w-0.5"
              style={{ width: `${Math.min(50, (spd / (bpd + spd)) * 50)}%` }}
            />
          </div>
          <span className={`text-xs font-mono font-semibold ${spreadColor}`}>
            {(bpd + spd > 0 ? (bpd + spd) * 100 : 0).toFixed(1)}% total spread
          </span>
        </div>

        {/* Sell side */}
        <div className="flex-1 min-w-0 text-right">
          <div className="flex items-center justify-end gap-1.5 mb-0.5">
            <span className="text-xs text-gray-400">Sell discount</span>
            <TrendingDown className="w-3 h-3 text-rose-400" />
          </div>
          <span className="text-lg font-bold font-mono text-rose-400">{(spd * 100).toFixed(2)}%</span>
        </div>
      </div>

      {/* Imbalance indicator */}
      <div className={`text-xs text-center mb-4 font-medium ${imbalanceColor}`}>
        {imbalanceLabel}
      </div>

      {/* Sliders */}
      <div className="space-y-3">
        <div>
          <div className="flex justify-between items-center mb-1">
            <label className="text-xs text-gray-400">Buy ratio</label>
            <span className="text-xs font-mono text-gray-300">{buyRatio}%</span>
          </div>
          <input
            type="range" min="0" max="100" step="1"
            value={buyRatio}
            onChange={(e) => setBuyRatio(Number(e.target.value))}
            className="w-full h-1.5 bg-gray-700 rounded-full appearance-none cursor-pointer accent-emerald-500"
          />
          <div className="flex justify-between mt-0.5">
            <span className="text-[10px] text-gray-600">All sell</span>
            <span className="text-[10px] text-gray-600">All buy</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1">
            <label className="text-xs text-gray-400">Player count</label>
            <span className="text-xs font-mono text-gray-300">{playerCount}</span>
          </div>
          <input
            type="range" min="1" max="50" step="1"
            value={playerCount}
            onChange={(e) => setPlayerCount(Number(e.target.value))}
            className="w-full h-1.5 bg-gray-700 rounded-full appearance-none cursor-pointer accent-emerald-500"
          />
          <div className="flex justify-between mt-0.5">
            <span className="text-[10px] text-gray-600">1</span>
            <span className="text-[10px] text-gray-600">50</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1">
            <label className="text-xs text-gray-400">Active traders</label>
            <span className="text-xs font-mono text-gray-300">{uniqueTraders}</span>
          </div>
          <input
            type="range" min="1" max="30" step="1"
            value={uniqueTraders}
            onChange={(e) => setUniqueTraders(Number(e.target.value))}
            className="w-full h-1.5 bg-gray-700 rounded-full appearance-none cursor-pointer accent-emerald-500"
          />
          <div className="flex justify-between mt-0.5">
            <span className="text-[10px] text-gray-600">1</span>
            <span className="text-[10px] text-gray-600">30</span>
          </div>
        </div>
      </div>

      {/* Spread health note */}
      {embedded && (
        <div className="mt-4 pt-3 border-t border-gray-800">
          <p className="text-[11px] text-gray-500 leading-relaxed">
            The Auto-Tune engine runs this calculation every 5 minutes, adjusting spreads based on five factors:
            volume imbalance, liquidity, player count, global activity, and base spread. Try pushing buy ratio to
            100% to see the spread blow out — or set players to 1 to see how thin liquidity affects prices.
          </p>
        </div>
      )}
    </div>
  );
}
