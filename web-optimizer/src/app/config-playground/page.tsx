'use client';

import React, { useState, useCallback, useMemo } from 'react';
import { DEFAULT_CONFIG, calculateSpread, type MarketConfig } from '@/lib/market-engine';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import { Sliders, RotateCcw, Info, Copy, Check, AlertTriangle, TrendingUp, TrendingDown, Minus, Zap } from 'lucide-react';

type Regime = 'BALANCED' | 'BUYER_HEAVY' | 'SELLER_HEAVY' | 'VOLATILE' | 'THIN_LIQUIDITY';

const REGIME_INFO: Record<Regime, { label: string; color: string; bg: string; desc: string; Icon: React.ElementType }> = {
  BALANCED: {
    label: 'Balanced', color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/30',
    desc: 'Buy/sell pressure is roughly equal. Prices are stable and fair for both sides.',
    Icon: Minus,
  },
  BUYER_HEAVY: {
    label: 'Buyer Heavy', color: 'text-blue-400', bg: 'bg-blue-500/10 border-blue-500/30',
    desc: 'More players want to buy than sell. Prices are rising — buyers pay a premium.',
    Icon: TrendingUp,
  },
  SELLER_HEAVY: {
    label: 'Seller Heavy', color: 'text-amber-400', bg: 'bg-amber-500/10 border-amber-500/30',
    desc: 'More players want to sell than buy. Prices are falling — sellers receive less.',
    Icon: TrendingDown,
  },
  VOLATILE: {
    label: 'Volatile', color: 'text-red-400', bg: 'bg-red-500/10 border-red-500/30',
    desc: 'High price swings possible. Max change is large and volume is active — prices can move sharply each tick.',
    Icon: Zap,
  },
  THIN_LIQUIDITY: {
    label: 'Thin Liquidity', color: 'text-orange-400', bg: 'bg-orange-500/10 border-orange-500/30',
    desc: 'Few players, low volume. Spreads are wide — each trade has outsized price impact.',
    Icon: AlertTriangle,
  },
};

function computeRegime(
  buyRatio: number, baseSpread: number, maxPriceChange: number,
  players: number, traders: number, volume: number, zScore: number
): Regime {
  const liquidityScore = traders / Math.max(players, 1);
  const isThinLiquidity = players <= 4 || liquidityScore < 0.4;
  const isVolatile = maxPriceChange >= 3.0 && (zScore >= 1.0 || volume >= 200);

  if (isVolatile && !isThinLiquidity) return 'VOLATILE';
  if (isThinLiquidity) return 'THIN_LIQUIDITY';
  if (buyRatio >= 0.62) return 'BUYER_HEAVY';
  if (buyRatio <= 0.38) return 'SELLER_HEAVY';
  return 'BALANCED';
}

function RiskWarning({ warning }: { warning: string }) {
  return (
    <div className="flex items-start gap-2 p-2.5 rounded-lg bg-red-950/30 border border-red-900/40">
      <AlertTriangle className="w-3.5 h-3.5 text-red-400 mt-0.5 shrink-0" />
      <p className="text-[11px] text-red-300/80 leading-relaxed">{warning}</p>
    </div>
  );
}

function computeRiskWarnings(baseSpread: number, maxPriceChange: number, players: number, traders: number, volume: number): string[] {
  const warnings: string[] = [];
  if (baseSpread >= 0.40) warnings.push('base-spread ≥ 40% is extreme — only appropriate for experimental or very small servers.');
  if (baseSpread >= 0.30 && players <= 5) warnings.push('Wide base spread with few players can make all items feel overpriced and discourage trading.');
  if (maxPriceChange >= 3.0) warnings.push('max-price-change ≥ 3% allows large price swings per tick. High-volume servers may see erratic pricing.');
  if (players <= 3 && traders <= 2) warnings.push('With 2–3 players, spreads will be very wide and price discovery is unreliable. Consider reducing base-spread to 15–20%.');
  if (traders > players) warnings.push('Active traders exceed online players — likely a data entry issue. Check the active traders count.');
  if (volume <= 10 && players >= 10) warnings.push('Volume is very low for the player count — economy may be stagnating. Check if trades are completing.');
  return warnings;
}

const PRESETS: Record<string, { label: string; desc: string; config: Partial<MarketConfig>; players: number; traders: number; volume: number; zScore: number; note?: string }> = {
  small: {
    label: 'Small Server', note: 'Casual economy, few players',
    desc: '5 players, casual trading',
    config: { baseSpread: 0.25 },
    players: 5, traders: 4, volume: 50, zScore: 0,
  },
  vanilla: {
    label: 'Standard Mix', note: 'Balanced player types',
    desc: '10 players, balanced economy',
    config: { baseSpread: 0.20 },
    players: 10, traders: 8, volume: 100, zScore: 0,
  },
  busy: {
    label: 'Busy Trading Hub', note: 'High activity',
    desc: '25 players, high activity',
    config: { baseSpread: 0.15 },
    players: 25, traders: 20, volume: 300, zScore: 0.5,
  },
  stressed: {
    label: 'Low Activity', note: 'Warning: thin liquidity',
    desc: '3 players, thin liquidity',
    config: { baseSpread: 0.30 },
    players: 3, traders: 2, volume: 10, zScore: -1.5,
  },
  mm_healthy: {
    label: 'Healthy Economy', note: 'MM + GuildBuyers',
    desc: '2 MarketMakers + GuildBuyers, 14 players',
    config: { baseSpread: 0.20, maxPriceChangePercent: 1.5 },
    players: 14, traders: 12, volume: 250, zScore: 0.3,
  },
  guild_economy: {
    label: 'Guild Economy', note: 'Guild-heavy player base',
    desc: '2 MMs + 2 GBs, 16 players, high demand',
    config: { baseSpread: 0.18 },
    players: 16, traders: 14, volume: 350, zScore: 0.5,
  },
};

function spreadColor(bpd: number, spd: number): string {
  const avg = ((bpd + spd) * 100) / 2;
  if (avg < 4) return 'text-emerald-400';
  if (avg < 8) return 'text-amber-400';
  return 'text-red-400';
}

function SpreadBar({ bpd, spd }: { bpd: number; spd: number }) {
  const total = bpd + spd;
  const buyPct = (bpd / total) * 100;
  const sellPct = (spd / total) * 100;
  const mid = 50;

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-xs text-zinc-400 mb-1">
        <span className="text-emerald-400">BPD {(bpd * 100).toFixed(2)}%</span>
        <span className="text-zinc-500">spread</span>
        <span className="text-amber-400">SPD {(spd * 100).toFixed(2)}%</span>
      </div>
      <div className="relative h-3 rounded-full overflow-hidden bg-zinc-800">
        <div
          className="absolute left-0 top-0 h-full bg-gradient-to-r from-emerald-600 to-emerald-400 transition-all duration-300"
          style={{ width: `${buyPct}%` }}
        />
        <div
          className="absolute right-0 top-0 h-full bg-gradient-to-l from-amber-500 to-amber-400 transition-all duration-300"
          style={{ width: `${sellPct}%` }}
        />
        <div
          className="absolute top-0 h-full w-px bg-white/60"
          style={{ left: `${mid}%`, transform: 'translateX(-50%)' }}
        />
      </div>
      <div className="flex justify-between text-[10px] text-zinc-500">
        <span>buy side</span>
        <span>sell side</span>
      </div>
    </div>
  );
}

function SliderRow({
  label,
  description,
  value,
  min,
  max,
  step,
  format,
  onChange,
}: {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  format: (v: number) => string;
  onChange: (v: number) => void;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <label className="text-xs font-medium text-zinc-300">{label}</label>
        <span className="text-xs font-mono text-emerald-400">{format(value)}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step ?? 0.01}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        className="w-full h-1.5 rounded-full appearance-none bg-zinc-800 cursor-pointer accent-emerald-500"
      />
      {description && <p className="text-[10px] text-zinc-500">{description}</p>}
    </div>
  );
}

export default function ConfigPlaygroundPage() {
  const [preset, setPreset] = useState<string>('vanilla');
  const [baseSpread, setBaseSpread] = useState(0.20);
  const [volumeImpact, setVolumeImpact] = useState(0.80);
  const [playerImpact, setPlayerImpact] = useState(0.60);
  const [maxPriceChange, setMaxPriceChange] = useState(1.5);
  const [players, setPlayers] = useState(10);
  const [traders, setTraders] = useState(8);
  const [volume, setVolume] = useState(100);
  const [zScore, setZScore] = useState(0);
  const [buyRatio, setBuyRatio] = useState(0.70);
  const [yamlCopied, setYamlCopied] = useState(false);

  const fullYaml = useMemo(() => {
    return `# Auto-Tune — spread config (generated by Config Playground)
# Add to your config.yml under the spread: section

spread:
  base-spread: ${baseSpread}
  volume-impact: ${volumeImpact}
  player-impact: ${playerImpact}
  max-price-change-percent: ${maxPriceChange}
  full-effect-players: 10
  floor-percent: 0.60
`;
  }, [baseSpread, volumeImpact, playerImpact, maxPriceChange]);

  async function copyYaml() {
    try {
      await navigator.clipboard.writeText(fullYaml);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = fullYaml;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    setYamlCopied(true);
    setTimeout(() => setYamlCopied(false), 2000);
  }

  const applyPreset = useCallback((key: string) => {
    const p = PRESETS[key];
    if (!p) return;
    setPreset(key);
    setBaseSpread(p.config.baseSpread ?? 0.20);
    setMaxPriceChange(p.config.maxPriceChangePercent ?? 1.5);
    setPlayers(p.players);
    setTraders(p.traders);
    setVolume(p.volume);
    setZScore(p.zScore);
  }, []);

  const config: MarketConfig = useMemo(() => ({
    ...DEFAULT_CONFIG,
    baseSpread,
    volumeImpact,
    playerImpact,
    maxPriceChangePercent: maxPriceChange,
  }), [baseSpread, volumeImpact, playerImpact, maxPriceChange]);

  const { bpd, spd } = useMemo(
    () => calculateSpread(buyRatio, players, zScore, volume, traders, config),
    [buyRatio, players, zScore, volume, traders, config],
  );

  const chartData = useMemo(() => {
    return Array.from({ length: 21 }, (_, i) => {
      const ratio = i / 20;
      const { bpd: cBpd, spd: cSpd } = calculateSpread(ratio, players, zScore, volume, traders, config);
      const spread = ((cBpd + cSpd) * 100) / 2;
      return {
        ratio: Math.round(ratio * 100),
        bpd: parseFloat((cBpd * 100).toFixed(3)),
        spd: parseFloat((cSpd * 100).toFixed(3)),
        spread: parseFloat(spread.toFixed(3)),
        imbalance: Math.abs(ratio - 0.5) * 2,
      };
    });
  }, [players, zScore, volume, traders, config]);

  const currentPoint = chartData.find((d) => d.ratio === Math.round(buyRatio * 100)) ?? chartData[10];

  const regime = computeRegime(buyRatio, baseSpread, maxPriceChange, players, traders, volume, zScore);
  const regimeInfo = REGIME_INFO[regime];
  const riskWarnings = computeRiskWarnings(baseSpread, maxPriceChange, players, traders, volume);

  const exampleBase = 250;
  const buyPrice = exampleBase * (1 + bpd);
  const sellPrice = exampleBase * (1 - spd);

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-950">
        <div className="mx-auto max-w-7xl px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-emerald-600 flex items-center justify-center">
              <Sliders className="h-4 w-4 text-white" />
            </div>
            <div>
              <h1 className="text-sm font-bold text-white">Config Playground</h1>
              <p className="text-xs text-zinc-400">See how parameters affect your market spread</p>
            </div>
          </div>
          <div className="flex items-center gap-3 text-xs text-zinc-400">
            <a href="/simulator" className="hover:text-emerald-400 transition-colors">Simulator</a>
            <span>·</span>
            <a href="/how-it-works" className="hover:text-emerald-400 transition-colors">How It Works</a>
            <span>·</span>
            <a href="/sweep-results" className="hover:text-emerald-400 transition-colors">Sweep Results</a>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-8 space-y-8">
        {/* Config Playground vs Simulator */}
        <div className="flex items-start gap-3 p-4 rounded-xl border border-emerald-900/40 bg-emerald-950/20">
          <Info className="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
          <div className="text-sm">
            <span className="text-emerald-400 font-medium">Config Playground</span>
            <span className="text-gray-400"> — set spread parameters and see the resulting math. </span>
            <span className="text-gray-500">Want to run a simulated economy? </span>
            <a href="/simulator" className="text-emerald-400 hover:text-emerald-300 underline underline-offset-2">Try the Simulator →</a>
            <span className="text-gray-500"> It runs a full 14-day economy with archetypes, loans, and market events.</span>
          </div>
        </div>

        {/* Presets */}
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-zinc-300 flex items-center gap-2">
            <RotateCcw className="h-3.5 w-3.5 text-emerald-500" />
            Start from a Preset
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
            {Object.entries(PRESETS).map(([key, p]) => (
              <button
                key={key}
                onClick={() => applyPreset(key)}
                className={`p-2.5 rounded-lg border text-left transition-all ${
                  preset === key
                    ? 'border-emerald-600/60 bg-emerald-950/30'
                    : 'border-zinc-800 bg-zinc-900/50 hover:border-zinc-700'
                }`}
              >
                <p className="text-xs font-semibold text-zinc-200 leading-tight">{p.label}</p>
                {p.note && <p className="text-[10px] text-emerald-500/70 mt-0.5">{p.note}</p>}
                <p className="text-[10px] text-zinc-500 mt-0.5">{p.desc}</p>
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left: Parameters */}
          <div className="space-y-6">
            <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-950/60 space-y-5">
              <h3 className="text-sm font-semibold text-zinc-200">Engine Parameters</h3>

              <SliderRow
                label="Base Spread"
                description="Starting spread before market conditions (10%–40% recommended)"
                value={baseSpread}
                min={0.05}
                max={0.50}
                step={0.01}
                format={(v) => `${(v * 100).toFixed(0)}%`}
                onChange={setBaseSpread}
              />

              <SliderRow
                label="Volume Impact"
                description="How strongly buy/sell imbalance shifts the spread (0–1)"
                value={volumeImpact}
                min={0.0}
                max={1.0}
                step={0.05}
                format={(v) => v.toFixed(2)}
                onChange={setVolumeImpact}
              />

              <SliderRow
                label="Player Impact"
                description="How much player count compresses spreads (0–1)"
                value={playerImpact}
                min={0.0}
                max={1.0}
                step={0.05}
                format={(v) => v.toFixed(2)}
                onChange={setPlayerImpact}
              />

              <SliderRow
                label="Max Price Change"
                description="Max price shift per 5-minute tick (0.5%–3% recommended)"
                value={maxPriceChange}
                min={0.5}
                max={5.0}
                step={0.1}
                format={(v) => `${v.toFixed(1)}%`}
                onChange={setMaxPriceChange}
              />
            </div>

            <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-950/60 space-y-5">
              <h3 className="text-sm font-semibold text-zinc-200">Server Conditions</h3>

              <SliderRow
                label="Online Players"
                description="Current players on your server"
                value={players}
                min={1}
                max={100}
                step={1}
                format={(v) => `${v} players`}
                onChange={setPlayers}
              />

              <SliderRow
                label="Active Traders"
                description="Distinct players who traded recently"
                value={traders}
                min={1}
                max={50}
                step={1}
                format={(v) => `${v} traders`}
                onChange={setTraders}
              />

              <SliderRow
                label="Trade Volume"
                description="Recent trading activity (z-score input)"
                value={volume}
                min={1}
                max={500}
                step={10}
                format={(v) => `${v} units`}
                onChange={setVolume}
              />

              <SliderRow
                label="Activity Z-Score"
                description="-3 = very quiet, 0 = normal, +3 = very busy"
                value={zScore}
                min={-3}
                max={3}
                step={0.1}
                format={(v) => (v >= 0 ? `+${v.toFixed(1)}` : v.toFixed(1))}
                onChange={setZScore}
              />
            </div>
          </div>

          {/* Right: Results */}
          <div className="space-y-6">
            {/* Current spread card */}
            <div className="p-5 rounded-xl border border-emerald-900/40 bg-gradient-to-br from-emerald-950/60 to-zinc-950/60 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-zinc-200">Spread at {Math.round(buyRatio * 100)}% Buy Ratio</h3>
                <div className="flex items-center gap-3">
                  <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded text-xs font-semibold border ${regimeInfo.bg} ${regimeInfo.color}`}>
                    <regimeInfo.Icon className="w-3 h-3" />
                    {regimeInfo.label}
                  </span>
                  <div className={`text-sm font-mono font-bold ${spreadColor(bpd, spd)}`}>
                    ±{((bpd + spd) * 50).toFixed(2)}%
                  </div>
                </div>
              </div>

              <p className="text-xs text-zinc-400 leading-relaxed -mt-1">{regimeInfo.desc}</p>

              <SpreadBar bpd={bpd} spd={spd} />

              <div className="p-3 rounded-lg bg-zinc-900/60 space-y-1.5">
                <p className="text-xs text-zinc-400">Example prices for an item worth {exampleBase} base</p>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-[10px] text-emerald-400/70 uppercase tracking-wider">Buy Price</p>
                    <p className="text-lg font-mono font-bold text-emerald-400">${buyPrice.toFixed(2)}</p>
                  </div>
                  <div className="text-center">
                    <p className="text-xs text-zinc-500">spread</p>
                    <p className="text-xs font-mono text-zinc-400">${(buyPrice - sellPrice).toFixed(2)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-[10px] text-amber-400/70 uppercase tracking-wider">Sell Price</p>
                    <p className="text-lg font-mono font-bold text-amber-400">${sellPrice.toFixed(2)}</p>
                  </div>
                </div>
              </div>

              <SliderRow
                label="Buy Ratio"
                description="What % of trades are buys right now"
                value={buyRatio}
                min={0.05}
                max={0.95}
                step={0.01}
                format={(v) => `${Math.round(v * 100)}% buys`}
                onChange={setBuyRatio}
              />
            </div>

            {/* Spread curve chart */}
            <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-950/60">
              <h3 className="text-sm font-semibold text-zinc-200 mb-4">How Spread Changes with Buy Ratio</h3>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartData} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                    <XAxis
                      dataKey="ratio"
                      tickFormatter={(v) => `${v}%`}
                      tick={{ fontSize: 10, fill: '#6b7280' }}
                      axisLine={{ stroke: '#374151' }}
                      tickLine={false}
                    />
                    <YAxis
                      tickFormatter={(v) => `${v}%`}
                      tick={{ fontSize: 10, fill: '#6b7280' }}
                      axisLine={{ stroke: '#374151' }}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{ background: '#0f172a', border: '1px solid #1e293b', borderRadius: '8px', fontSize: 12 }}
                      labelFormatter={(v) => `${v}% buy ratio`}
                      formatter={(v: number) => [`${v.toFixed(2)}%`, '']}
                    />
                    <ReferenceLine x={50} stroke="#374151" strokeDasharray="3 3" />
                    <Line
                      type="monotone"
                      dataKey="bpd"
                      stroke="#10b981"
                      strokeWidth={2}
                      dot={false}
                      name="Buy spread"
                    />
                    <Line
                      type="monotone"
                      dataKey="spd"
                      stroke="#f59e0b"
                      strokeWidth={2}
                      dot={false}
                      name="Sell spread"
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <div className="flex items-center gap-4 mt-3">
                <div className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                  <span className="h-2 w-2 rounded-full bg-emerald-500" />
                  Buy spread (BPD)
                </div>
                <div className="flex items-center gap-1.5 text-[11px] text-zinc-400">
                  <span className="h-2 w-2 rounded-full bg-amber-500" />
                  Sell spread (SPD)
                </div>
                <div className="flex items-center gap-1.5 text-[11px] text-zinc-500 ml-auto">
                  <Info className="h-3 w-3" />
                  50% buy ratio = balanced market
                </div>
              </div>
            </div>

            {/* Spread health guide */}
            <div className="p-4 rounded-xl border border-zinc-800 bg-zinc-950/40">
              <h4 className="text-xs font-semibold text-zinc-300 mb-3">Spread Health Guide</h4>
              <div className="space-y-2">
                {[
                  { label: '±2–4%', desc: 'Tight spreads — healthy, efficient market', color: 'bg-emerald-500/20 border-emerald-900/40', text: 'text-emerald-400' },
                  { label: '±4–8%', desc: 'Moderate spreads — normal for small servers', color: 'bg-amber-500/20 border-amber-900/40', text: 'text-amber-400' },
                  { label: '±8%+', desc: 'Wide spreads — thin liquidity, consider adding players', color: 'bg-red-500/20 border-red-900/40', text: 'text-red-400' },
                ].map((tier) => (
                  <div key={tier.label} className={`flex items-center gap-3 p-2.5 rounded-lg border ${tier.color}`}>
                    <span className={`text-xs font-mono font-bold shrink-0 ${tier.text}`}>{tier.label}</span>
                    <span className="text-xs text-zinc-400">{tier.desc}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Risk warnings */}
            {riskWarnings.length > 0 && (
              <div className="p-4 rounded-xl border border-red-900/40 bg-red-950/20">
                <h4 className="text-xs font-semibold text-red-300 mb-3 flex items-center gap-1.5">
                  <AlertTriangle className="w-3.5 h-3.5" />
                  Configuration Warnings
                </h4>
                <div className="space-y-2">
                  {riskWarnings.map((w, i) => <RiskWarning key={i} warning={w} />)}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Config export */}
        <div className="p-5 rounded-xl border border-zinc-800 bg-zinc-950/40">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-zinc-200">Config Export</h3>
            <button
              onClick={copyYaml}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                yamlCopied
                  ? 'bg-emerald-600/20 text-emerald-400 border border-emerald-600/40'
                  : 'bg-zinc-800 hover:bg-zinc-700 text-zinc-300 border border-zinc-700'
              }`}
            >
              {yamlCopied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
              {yamlCopied ? 'Copied!' : 'Copy Full YAML'}
            </button>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {[
              { key: 'spread.base-spread', value: baseSpread },
              { key: 'spread.volume-impact', value: volumeImpact },
              { key: 'spread.player-impact', value: playerImpact },
              { key: 'spread.max-price-change-percent', value: maxPriceChange },
            ].map((c) => (
              <div key={c.key} className="p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800">
                <p className="text-[10px] text-zinc-500 font-mono truncate">{c.key}</p>
                <p className="text-sm font-mono font-semibold text-emerald-400 mt-0.5">{c.value}</p>
              </div>
            ))}
          </div>
          <p className="text-xs text-zinc-500 mt-3">
            Add these to your <code className="text-emerald-400/70 bg-zinc-900/60 px-1 py-0.5 rounded text-[11px]">config.yml</code> under the <code className="text-emerald-400/70 bg-zinc-900/60 px-1 py-0.5 rounded text-[11px]">spread:</code> section.
            Restart the server after editing.
          </p>
        </div>
      </main>
    </div>
  );
}
