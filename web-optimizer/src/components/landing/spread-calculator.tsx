'use client';

import { useState, useMemo } from 'react';
import { Calculator } from 'lucide-react';

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
  // Step 1: half spread from base spread
  const halfSpread = BASE_SPREAD / 2;

  // Step 2: volume imbalance (asymmetric)
  const imbalance = (buyRatio - 0.5) * 2; // [-1, +1]
  const imbalanceShift = Math.abs(imbalance) * 0.15 * VOLUME_IMPACT;

  // Step 3: liquidity reduction
  const effectiveTraders = Math.min(uniqueTraders, FULL_EFFECT_TRADERS);
  const liquidity = 1 / (1 + volumeZ * 0 + effectiveTraders * LIQUIDITY_COEFF);

  // Step 4: player count scaling
  const playerFactor = Math.tanh((playerCount * Math.atanh(0.99)) / FULL_EFFECT_PLAYERS);

  // Step 5: global volume multiplier
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
    // More buys → wider BPD, narrower SPD
    bpd = halfSpread * (1 + imbalanceShift) * multiplier;
    spd = halfSpread * (1 - imbalanceShift * 0.7) * multiplier;
  } else {
    // More sells → wider SPD, narrower BPD
    bpd = halfSpread * (1 - imbalanceShift * 0.7) * multiplier;
    spd = halfSpread * (1 + imbalanceShift) * multiplier;
  }

  return {
    bpd: Math.max(0.001, bpd),
    spd: Math.max(0.001, spd),
    imbalance,
  };
}

export function SpreadCalculator() {
  const [buyRatio, setBuyRatio] = useState(50); // 0-100
  const [playerCount, setPlayerCount] = useState(10);
  const [uniqueTraders, setUniqueTraders] = useState(8);
  const [volumeZ, setVolumeZ] = useState(0); // -2 to +2

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

  const imbalanceBg =
    imbalance > 0.1 ? 'bg-emerald-950/50 border-emerald-800/50' :
    imbalance < -0.1 ? 'bg-rose-950/50 border-rose-800/50' :
    'bg-gray-900/50 border-gray-800/50';

  return (
    <div className="mt-10 rounded-xl border border-gray-800 bg-gray-900/40 p-6">
      <div className="flex items-center gap-2 mb-5">
        <Calculator className="w-4 h-4 text-emerald-400" />
        <h2 className="text-base font-semibold text-white">Interactive Spread Calculator</h2>
      </div>
      <p className="text-sm text-gray-400 mb-6 leading-relaxed">
        Adjust the market conditions below to see how the spread changes in real time. This mirrors the
        exact formula used by the Auto-Tune engine — five sequential factors applied every 5-minute tick.
      </p>

      {/* Result display */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        <div className="rounded-lg border border-emerald-800/50 bg-emerald-950/30 p-4 text-center">
          <p className="text-xs text-emerald-400 uppercase tracking-wider font-medium mb-1">Buy premium (BPD)</p>
          <p className="text-2xl font-bold font-mono text-white">{(bpd * 100).toFixed(2)}%</p>
          <p className="text-xs text-emerald-300/60 mt-0.5">${(10 * (1 + bpd)).toFixed(4)} per unit</p>
        </div>
        <div className="rounded-lg border border-gray-800/50 bg-gray-900/30 p-4 text-center">
          <p className="text-xs text-gray-400 uppercase tracking-wider font-medium mb-1">Imbalance</p>
          <p className={`text-2xl font-bold font-mono mt-1 ${imbalanceColor}`}>
            {imbalance > 0 ? '+' : ''}{(imbalance * 100).toFixed(1)}%
          </p>
          <p className={`text-xs mt-0.5 font-medium ${imbalanceColor}`}>{imbalanceLabel}</p>
        </div>
        <div className="rounded-lg border border-rose-800/50 bg-rose-950/30 p-4 text-center">
          <p className="text-xs text-rose-400 uppercase tracking-wider font-medium mb-1">Sell discount (SPD)</p>
          <p className="text-2xl font-bold font-mono text-white">{(spd * 100).toFixed(2)}%</p>
          <p className="text-xs text-rose-300/60 mt-0.5">${(10 * (1 - spd)).toFixed(4)} per unit</p>
        </div>
      </div>

      {/* Spread bar */}
      <div className="mb-6">
        <div className="flex items-center gap-2 text-xs text-gray-500 mb-1.5">
          <span className="text-emerald-400 font-mono w-12 text-right">{(bpd * 100).toFixed(1)}%</span>
          <span className="flex-1 flex">
            <span
              className="bg-emerald-500/70 h-2 rounded-l-full"
              style={{ width: `${Math.min(50, (bpd / (bpd + spd)) * 50)}%` }}
            />
            <span
              className="bg-rose-500/70 h-2 rounded-r-full"
              style={{ width: `${Math.min(50, (spd / (bpd + spd)) * 50)}%` }}
            />
          </span>
          <span className="text-rose-400 font-mono w-12">{(spd * 100).toFixed(1)}%</span>
        </div>
      </div>

      {/* Controls */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs text-gray-400">Buy ratio</label>
            <span className={`text-xs font-mono font-semibold ${imbalanceColor}`}>{buyRatio}%</span>
          </div>
          <input
            type="range"
            min="0" max="100" step="1"
            value={buyRatio}
            onChange={(e) => setBuyRatio(Number(e.target.value))}
            className="w-full accent-emerald-500"
          />
          <div className="flex justify-between text-[10px] text-gray-600 mt-1">
            <span>100% sell</span><span>50/50</span><span>100% buy</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs text-gray-400">Player count</label>
            <span className="text-xs font-mono text-gray-300">{playerCount}</span>
          </div>
          <input
            type="range"
            min="1" max="50" step="1"
            value={playerCount}
            onChange={(e) => setPlayerCount(Number(e.target.value))}
            className="w-full accent-emerald-500"
          />
          <div className="flex justify-between text-[10px] text-gray-600 mt-1">
            <span>1</span><span>25</span><span>50</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs text-gray-400">Unique traders</label>
            <span className="text-xs font-mono text-gray-300">{uniqueTraders}</span>
          </div>
          <input
            type="range"
            min="1" max="20" step="1"
            value={uniqueTraders}
            onChange={(e) => setUniqueTraders(Number(e.target.value))}
            className="w-full accent-emerald-500"
          />
          <div className="flex justify-between text-[10px] text-gray-600 mt-1">
            <span>1 (thin)</span><span>20 (deep)</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label className="text-xs text-gray-400">Volume z-score</label>
            <span className={`text-xs font-mono font-semibold ${
              volumeZ > 1 ? 'text-emerald-400' : volumeZ < -1 ? 'text-rose-400' : 'text-gray-300'
            }`}>
              {volumeZ > 1 ? 'high (+0.8×)' : volumeZ < -1 ? 'low (+1.3×)' : 'normal'}
            </span>
          </div>
          <input
            type="range"
            min="-2" max="2" step="0.1"
            value={volumeZ}
            onChange={(e) => setVolumeZ(Number(e.target.value))}
            className="w-full accent-emerald-500"
          />
          <div className="flex justify-between text-[10px] text-gray-600 mt-1">
            <span>−2 (quiet)</span><span>0</span><span>+2 (busy)</span>
          </div>
        </div>
      </div>

      <p className="text-xs text-gray-600 mt-5 leading-relaxed">
        Formula: <code className="text-gray-500">BPD = halfSpread × imbalanceShift × liquidity × playerFactor × globalMult</code>.
        Buy/sell pressure asymmetry is built in — the same imbalance magnitude produces a wider BPD premium than SPD discount.
        Based on Auto-Tune defaults: baseSpread=0.20, volumeImpact=0.80, liquidityCoeff=0.01, fullEffectPlayers=10.
      </p>
    </div>
  );
}
