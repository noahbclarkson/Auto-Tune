'use client';

import { useState, useCallback, useMemo } from 'react';

// ─── Core spread calculation (mirrors market-engine.ts) ────────────────────────

function calculateSpread(params: {
  baseSpread: number;
  volumeImpact: number;
  playerImpact: number;
  playerCount: number;
  fullEffectPlayers: number;
  buyRatio: number; // 0–1
  liquidityTraders: number;
  globalVolMult: number; // 0.8–1.3
}) {
  const {
    baseSpread,
    volumeImpact,
    playerImpact,
    playerCount,
    fullEffectPlayers,
    buyRatio,
    liquidityTraders,
    globalVolMult,
  } = params;

  const halfSpread = baseSpread / 2;

  // Step 1: imbalance (−1 to +1)
  const imbalance = (buyRatio - 0.5) * 2;

  // Step 2: liquidity factor
  const liqCoeff = 0.01;
  const liquidity = 1 / (1 + halfSpread * liqCoeff * liquidityTraders);

  // Step 3: player scaling via tanh
  const atanh99 = 2.64665;
  const playerFactor = Math.tanh((playerCount * atanh99) / fullEffectPlayers);

  // BPD = halfSpread × imbalance × liquidity × playerFactor × globalVol
  // SPD = halfSpread × (2 − imbalance) × liquidity × playerFactor × globalVol
  // (when imbalance > 0, BPD wider, SPD narrower — buyers pay more)

  const rawBpd = halfSpread * Math.max(0.01, imbalance) * liquidity * playerFactor * globalVolMult;
  const rawSpd = halfSpread * Math.max(0.01, 2 - imbalance) * liquidity * playerFactor * globalVolMult;

  // Asymmetric: sell pressure multiplier
  const spMult = 1.0;
  const bpd = Math.min(rawBpd * spMult, 0.5);
  const spd = Math.min(rawSpd, 0.5);

  return {
    bpd: Math.max(0.001, bpd),
    spd: Math.max(0.001, spd),
    imbalance,
    playerFactor,
    liquidity,
  };
}

// ─── Spread Factor Cascade Diagram ─────────────────────────────────────────────

function FactorBar({ label, value, max, color, description }: {
  label: string;
  value: number;
  max: number;
  color: string;
  description: string;
}) {
  const pct = Math.min((value / max) * 100, 100);
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-xs">
        <span className="text-gray-400 font-medium">{label}</span>
        <span className={`font-mono font-semibold ${color}`}>{value.toFixed(3)}</span>
      </div>
      <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-300 ${color.replace('text-', 'bg-').replace('400', '500')}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-[10px] text-gray-600 leading-relaxed">{description}</p>
    </div>
  );
}

function SpreadArrow({ bpd, spd, basePrice }: { bpd: number; spd: number; basePrice: number }) {
  const totalBps = (bpd + spd) * 100;
  const buyPrice = basePrice * (1 + bpd);
  const sellPrice = basePrice * (1 - spd);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="text-center">
          <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-0.5">Buy Price</p>
          <p className="text-sm font-bold text-emerald-400 font-mono">${buyPrice.toFixed(2)}</p>
          <p className="text-[10px] text-gray-600">BPD {(bpd * 100).toFixed(2)}%</p>
        </div>
        <div className="flex flex-col items-center gap-1 px-3">
          <div className="flex items-center gap-0.5 text-[10px] text-gray-500">
            <span className="text-emerald-500">▲</span>
            <span className="font-mono">{((bpd) * 100).toFixed(2)}%</span>
          </div>
          <div className="w-16 h-px bg-gradient-to-r from-emerald-500 to-red-400" />
          <div className="flex items-center gap-0.5 text-[10px] text-gray-500">
            <span className="text-red-400">▼</span>
            <span className="font-mono">{((spd) * 100).toFixed(2)}%</span>
          </div>
        </div>
        <div className="text-center">
          <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-0.5">Sell Price</p>
          <p className="text-sm font-bold text-red-400 font-mono">${sellPrice.toFixed(2)}</p>
          <p className="text-[10px] text-gray-600">SPD {(spd * 100).toFixed(2)}%</p>
        </div>
      </div>
      <p className="text-center text-[10px] text-gray-600">
        Total spread: <span className="text-gray-400 font-mono">±{(totalBps / 2).toFixed(2)}%</span> · Midpoint:{' '}
        <span className="text-gray-400 font-mono">${basePrice.toFixed(2)}</span>
      </p>
    </div>
  );
}

function Slider({
  label,
  value,
  min,
  max,
  step,
  onChange,
  format,
  description,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  format: (v: number) => string;
  description?: string;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <label className="text-xs font-medium text-gray-400">{label}</label>
        <span className="text-xs font-mono text-emerald-400">{format(value)}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        className="w-full h-1.5 bg-gray-800 rounded-full appearance-none cursor-pointer accent-emerald-500"
      />
      {description && <p className="text-[10px] text-gray-600">{description}</p>}
    </div>
  );
}

// ─── Main Component ────────────────────────────────────────────────────────────

const BASE_PRICE = 100;

export function SpreadSimulator() {
  const [baseSpread, setBaseSpread] = useState(0.20);
  const [volumeImpact, setVolumeImpact] = useState(0.80);
  const [playerImpact, setPlayerImpact] = useState(0.60);
  const [playerCount, setPlayerCount] = useState(10);
  const [fullEffectPlayers, setFullEffectPlayers] = useState(10);
  const [buyRatio, setBuyRatio] = useState(0.65); // 65% buys = sell-heavy economy
  const [liquidityTraders, setLiquidityTraders] = useState(8);
  const [globalVolMult, setGlobalVolMult] = useState(1.0);

  const result = useMemo(
    () =>
      calculateSpread({
        baseSpread,
        volumeImpact,
        playerImpact,
        playerCount,
        fullEffectPlayers,
        buyRatio,
        liquidityTraders,
        globalVolMult,
      }),
    [baseSpread, volumeImpact, playerImpact, playerCount, fullEffectPlayers, buyRatio, liquidityTraders, globalVolMult],
  );

  const buyRatioPct = Math.round(buyRatio * 100);
  const sellRatioPct = 100 - buyRatioPct;

  return (
    <div className="rounded-2xl border border-gray-800 bg-gray-900/80 overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-gray-800 bg-gray-950/60">
        <div className="flex items-center gap-2 mb-1">
          <div className="w-5 h-5 rounded bg-emerald-600/20 border border-emerald-600/30 flex items-center justify-center">
            <span className="text-emerald-400 text-[10px] font-bold">∑</span>
          </div>
          <h3 className="text-sm font-semibold text-white">Interactive Spread Simulator</h3>
        </div>
        <p className="text-xs text-gray-500">
          Adjust the factors below to see how they compound into buy and sell prices.
        </p>
      </div>

      <div className="grid lg:grid-cols-2 gap-0">
        {/* Left: Controls */}
        <div className="p-5 space-y-4 border-b lg:border-b-0 lg:border-r border-gray-800">
          {/* Buy/Sell ratio — special treatment */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-gray-400">Buy / Sell Ratio</label>
              <span className="text-xs font-mono">
                <span className="text-emerald-400">{buyRatioPct}%</span>
                <span className="text-gray-600"> / </span>
                <span className="text-red-400">{sellRatioPct}%</span>
              </span>
            </div>
            <div className="h-3 bg-gray-800 rounded-full overflow-hidden flex">
              <div
                className="bg-emerald-500 transition-all duration-300"
                style={{ width: `${buyRatioPct}%` }}
              />
              <div
                className="bg-red-500 transition-all duration-300"
                style={{ width: `${sellRatioPct}%` }}
              />
            </div>
            <input
              type="range"
              min={0.1}
              max={0.99}
              step={0.01}
              value={buyRatio}
              onChange={(e) => setBuyRatio(parseFloat(e.target.value))}
              className="w-full h-1.5 bg-gray-800 rounded-full appearance-none cursor-pointer accent-emerald-500"
            />
            <p className="text-[10px] text-gray-600">
              Buy-heavy market → wide BPD, narrow SPD · Sell-heavy → the reverse
            </p>
          </div>

          <div className="space-y-3">
            <Slider
              label="Base Spread"
              value={baseSpread}
              min={0.05}
              max={0.50}
              step={0.01}
              onChange={setBaseSpread}
              format={(v) => (v * 100).toFixed(0) + '%'}
              description="Starting spread before other factors (default: 20%)"
            />

            <Slider
              label="Player Count"
              value={playerCount}
              min={1}
              max={50}
              step={1}
              onChange={setPlayerCount}
              format={(v) => `${v} players`}
              description="More players → tighter spreads via tanh curve"
            />

            <Slider
              label="Volume Traders"
              value={liquidityTraders}
              min={1}
              max={30}
              step={1}
              onChange={setLiquidityTraders}
              format={(v) => `${v} traders`}
              description="Unique traders per item → more liquidity = tighter spreads"
            />

            <Slider
              label="Global Volume Multiplier"
              value={globalVolMult}
              min={0.8}
              max={1.3}
              step={0.01}
              onChange={setGlobalVolMult}
              format={(v) => v.toFixed(2) + '×'}
              description="0.80 = busy market (tighter), 1.30 = quiet market (wider)"
            />
          </div>

          {/* Reset */}
          <button
            onClick={() => {
              setBaseSpread(0.20);
              setBuyRatio(0.65);
              setPlayerCount(10);
              setLiquidityTraders(8);
              setGlobalVolMult(1.0);
            }}
            className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
          >
            Reset to defaults
          </button>
        </div>

        {/* Right: Visualization */}
        <div className="p-5 space-y-4">
          {/* Factor cascade */}
          <div className="space-y-3">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Factor Cascade</p>
            <div className="space-y-2">
              <FactorBar
                label="Half-Spread (base)"
                value={baseSpread / 2}
                max={0.25}
                color="text-gray-400"
                description="baseSpread ÷ 2"
              />
              <FactorBar
                label="Imbalance × Player × Liq"
                value={result.imbalance * result.playerFactor * result.liquidity}
                max={1}
                color={result.imbalance > 0 ? 'text-emerald-400' : 'text-red-400'}
                description={`imbalance ${result.imbalance.toFixed(2)} × pf ${result.playerFactor.toFixed(2)} × liq ${result.liquidity.toFixed(2)}`}
              />
              <FactorBar
                label="Global Volume ×"
                value={globalVolMult}
                max={1.3}
                color={globalVolMult < 1 ? 'text-emerald-400' : 'text-amber-400'}
                description={globalVolMult < 1 ? 'Busy market (tighter)' : 'Quiet market (wider)'}
              />
            </div>

            {/* Final BPD/SPD */}
            <div className="rounded-lg bg-gray-950 border border-gray-800 p-4">
              <SpreadArrow bpd={result.bpd} spd={result.spd} basePrice={BASE_PRICE} />
            </div>
          </div>

          {/* Compact factor table */}
          <div className="rounded-lg border border-gray-800 overflow-hidden">
            <table className="w-full text-[11px]">
              <thead>
                <tr className="border-b border-gray-800 bg-gray-950/40">
                  <th className="px-3 py-1.5 text-left text-gray-500 font-medium">Factor</th>
                  <th className="px-3 py-1.5 text-right text-gray-500 font-medium">Value</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/50">
                {[
                  { label: 'Buy Ratio', value: `${buyRatioPct}%` },
                  { label: 'Player Scaling', value: result.playerFactor.toFixed(4) },
                  { label: 'Liquidity', value: result.liquidity.toFixed(4) },
                  { label: 'BPD (×100)', value: `${(result.bpd * 100).toFixed(3)}%` },
                  { label: 'SPD (×100)', value: `${(result.spd * 100).toFixed(3)}%` },
                  { label: 'Total Half-Spread', value: `${((result.bpd + result.spd) / 2 * 100).toFixed(3)}%` },
                ].map(({ label, value }) => (
                  <tr key={label} className="hover:bg-gray-800/30 transition-colors">
                    <td className="px-3 py-1.5 text-gray-400">{label}</td>
                    <td className="px-3 py-1.5 text-right font-mono text-gray-200">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
