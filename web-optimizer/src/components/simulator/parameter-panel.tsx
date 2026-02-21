'use client';

import { MarketConfig, DEFAULT_CONFIG } from '@/lib/market-engine';

interface ParameterPanelProps {
  config: MarketConfig;
  setConfig: React.Dispatch<React.SetStateAction<MarketConfig>>;
  basePrice: number;
  setBasePrice: React.Dispatch<React.SetStateAction<number>>;
  buyRatio: number;
  setBuyRatio: React.Dispatch<React.SetStateAction<number>>;
  onlinePlayers: number;
  setOnlinePlayers: React.Dispatch<React.SetStateAction<number>>;
  zScore: number;
  setZScore: React.Dispatch<React.SetStateAction<number>>;
  weightedVolume: number;
  setWeightedVolume: React.Dispatch<React.SetStateAction<number>>;
  distinctTraders: number;
  setDistinctTraders: React.Dispatch<React.SetStateAction<number>>;
}

interface SliderConfig {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min: number;
  max: number;
  step: number;
  format: (value: number) => string;
  description?: string;
  color?: string;
}

function fillPct(value: number, min: number, max: number) {
  return `${Math.round(((value - min) / (max - min)) * 100)}%`;
}

function Slider({ label, value, onChange, min, max, step, format, description, color = '#10b981' }: SliderConfig) {
  const pct = fillPct(value, min, max);
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <label className="text-xs font-medium text-gray-400">{label}</label>
        <span className="text-xs text-emerald-400 font-mono bg-emerald-950/40 px-1.5 py-0.5 rounded border border-emerald-900/50">{format(value)}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        style={{
          background: `linear-gradient(to right, ${color} 0%, ${color} ${pct}, #1f2937 ${pct}, #1f2937 100%)`,
        }}
        className="w-full h-5 cursor-pointer"
      />
      {description && <p className="text-xs text-gray-600">{description}</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Preset Scenarios
// ---------------------------------------------------------------------------

interface PresetScenario {
  name: string;
  emoji: string;
  description: string;
  basePrice: number;
  buyRatio: number;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  distinctTraders: number;
  config: MarketConfig;
}

const PRESETS: PresetScenario[] = [
  {
    name: 'New Server',
    emoji: '🌱',
    description: 'Low player base, minimal trading history',
    basePrice: 100,
    buyRatio: 0.35,
    onlinePlayers: 3,
    zScore: -0.5,
    weightedVolume: 50,
    distinctTraders: 2,
    config: { ...DEFAULT_CONFIG },
  },
  {
    name: 'Established',
    emoji: '⚖️',
    description: 'Healthy server with balanced buy/sell activity',
    basePrice: 250,
    buyRatio: 0.52,
    onlinePlayers: 40,
    zScore: 0.2,
    weightedVolume: 1500,
    distinctTraders: 15,
    config: { ...DEFAULT_CONFIG },
  },
  {
    name: 'Crash',
    emoji: '📉',
    description: 'Panic selling — everyone is dumping',
    basePrice: 100,
    buyRatio: 0.15,
    onlinePlayers: 25,
    zScore: 2.5,
    weightedVolume: 300,
    distinctTraders: 8,
    config: { ...DEFAULT_CONFIG, baseSpread: 0.40, volumeImpact: 0.9 },
  },
  {
    name: 'Boom',
    emoji: '🚀',
    description: 'Peak activity — many players, lots of buying',
    basePrice: 500,
    buyRatio: 0.80,
    onlinePlayers: 80,
    zScore: 1.8,
    weightedVolume: 4000,
    distinctTraders: 25,
    config: { ...DEFAULT_CONFIG, playerImpact: 0.85 },
  },
];

export function ParameterPanel({
  config,
  setConfig,
  basePrice,
  setBasePrice,
  buyRatio,
  setBuyRatio,
  onlinePlayers,
  setOnlinePlayers,
  zScore,
  setZScore,
  weightedVolume,
  setWeightedVolume,
  distinctTraders,
  setDistinctTraders,
}: ParameterPanelProps) {
  const updateConfig = (key: keyof MarketConfig, value: number) =>
    setConfig((prev) => ({ ...prev, [key]: value }));

  const applyPreset = (preset: PresetScenario) => {
    setBasePrice(preset.basePrice);
    setBuyRatio(preset.buyRatio);
    setOnlinePlayers(preset.onlinePlayers);
    setZScore(preset.zScore);
    setWeightedVolume(preset.weightedVolume);
    setDistinctTraders(preset.distinctTraders);
    setConfig(preset.config);
  };

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-800 bg-gray-900">
        <h3 className="text-sm font-semibold text-white">Market Parameters</h3>
      </div>

      <div className="p-4 space-y-5">
        {/* Presets */}
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Quick Presets</p>
          <div className="grid grid-cols-2 gap-1.5">
            {PRESETS.map((preset) => (
              <button
                key={preset.name}
                onClick={() => applyPreset(preset)}
                title={preset.description}
                className="flex items-center gap-2 px-2.5 py-2 rounded-lg bg-gray-800/60 hover:bg-gray-750 border border-gray-700/60 hover:border-emerald-700/50 transition-all text-left group"
              >
                <span className="text-sm shrink-0">{preset.emoji}</span>
                <span className="text-xs font-medium text-gray-300 group-hover:text-emerald-400 leading-tight truncate">
                  {preset.name}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Trade State */}
        <div className="space-y-3.5">
          <p className="text-xs text-gray-500 uppercase tracking-wider">Trade State</p>

          <Slider label="Base Price" value={basePrice} onChange={setBasePrice}
            min={1} max={10000} step={1}
            format={(v) => `$${v.toLocaleString()}`}
            description="Item's reference price" />

          <Slider label="Buy Ratio" value={buyRatio} onChange={setBuyRatio}
            min={0} max={1} step={0.01}
            format={(v) => `${(v * 100).toFixed(0)}% buys`}
            description="Fraction of trades that are purchases"
            color={buyRatio > 0.5 ? '#10b981' : '#f43f5e'} />

          <Slider label="Online Players" value={onlinePlayers} onChange={setOnlinePlayers}
            min={0} max={100} step={1}
            format={(v) => `${v}`}
            description="Affects price velocity & spread compression" />

          <Slider label="Volume Z-Score" value={zScore} onChange={setZScore}
            min={-3} max={3} step={0.1}
            format={(v) => `${v > 0 ? '+' : ''}${v.toFixed(1)}σ`}
            description="Market activity vs. historical mean"
            color={zScore >= 0 ? '#10b981' : '#f59e0b'} />

          <Slider label="Weighted Volume" value={weightedVolume} onChange={setWeightedVolume}
            min={0} max={5000} step={10}
            format={(v) => v.toLocaleString()}
            description="Recency-weighted trade volume (liquidity)" />

          <Slider label="Distinct Traders" value={distinctTraders} onChange={setDistinctTraders}
            min={0} max={50} step={1}
            format={(v) => `${v}`}
            description="Unique players trading this item" color="#38bdf8" />
        </div>

        {/* Spread Config */}
        <div className="space-y-3.5 pt-1 border-t border-gray-800">
          <p className="text-xs text-gray-500 uppercase tracking-wider mt-3">Spread Config</p>

          <Slider label="Base Spread" value={config.baseSpread} onChange={(v) => updateConfig('baseSpread', v)}
            min={0.05} max={0.80} step={0.01}
            format={(v) => `${(v * 100).toFixed(0)}%`} />

          <Slider label="Volume Impact" value={config.volumeImpact} onChange={(v) => updateConfig('volumeImpact', v)}
            min={0} max={1} step={0.01}
            format={(v) => v.toFixed(2)} />

          <Slider label="Player Impact" value={config.playerImpact} onChange={(v) => updateConfig('playerImpact', v)}
            min={0} max={1} step={0.01}
            format={(v) => v.toFixed(2)} />

          <Slider label="Full Effect Players" value={config.fullEffectPlayers} onChange={(v) => updateConfig('fullEffectPlayers', v)}
            min={5} max={100} step={1}
            format={(v) => `${v} players`} />

          <Slider label="Liquidity Coeff" value={config.liquidityCoeff} onChange={(v) => updateConfig('liquidityCoeff', v)}
            min={0.001} max={0.1} step={0.001}
            format={(v) => v.toFixed(3)} />

          <Slider label="Max Price Change" value={config.maxPriceChangePercent} onChange={(v) => updateConfig('maxPriceChangePercent', v)}
            min={0.1} max={10} step={0.1}
            format={(v) => `${v.toFixed(1)}%`} />
        </div>

        {/* Reset */}
        <button
          onClick={() => {
            setConfig(DEFAULT_CONFIG);
            setBasePrice(100);
            setBuyRatio(0.5);
            setOnlinePlayers(10);
            setZScore(0);
            setWeightedVolume(0);
            setDistinctTraders(5);
          }}
          className="w-full py-2 text-xs text-gray-500 hover:text-gray-300 rounded-lg border border-gray-800 hover:border-gray-700 bg-gray-800/30 hover:bg-gray-800/60 transition-all"
        >
          Reset to Defaults
        </button>
      </div>
    </div>
  );
}
