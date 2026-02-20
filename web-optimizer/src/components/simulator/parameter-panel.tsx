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
}

function Slider({ label, value, onChange, min, max, step, format, description }: SliderConfig) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <label className="text-sm font-medium text-gray-300">{label}</label>
        <span className="text-sm text-emerald-400 font-mono">{format(value)}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-emerald-500"
      />
      {description && (
        <p className="text-xs text-gray-500">{description}</p>
      )}
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
    config: { ...DEFAULT_CONFIG },
  },
  {
    name: 'Established Market',
    emoji: '⚖️',
    description: 'Healthy server with balanced buy/sell activity',
    basePrice: 250,
    buyRatio: 0.52,
    onlinePlayers: 40,
    zScore: 0.2,
    weightedVolume: 1500,
    config: { ...DEFAULT_CONFIG },
  },
  {
    name: 'Economy Crash',
    emoji: '📉',
    description: 'Panic selling — everyone is selling, high volatility',
    basePrice: 100,
    buyRatio: 0.85,
    onlinePlayers: 25,
    zScore: 2.5,
    weightedVolume: 300,
    config: { ...DEFAULT_CONFIG, baseSpread: 0.45, volumeImpact: 0.7 },
  },
  {
    name: 'Economy Boom',
    emoji: '🚀',
    description: 'Peak activity — many players, lots of buying',
    basePrice: 500,
    buyRatio: 0.2,
    onlinePlayers: 80,
    zScore: 1.8,
    weightedVolume: 4000,
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
}: ParameterPanelProps) {
  const updateConfig = (key: keyof MarketConfig, value: number) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  };

  const applyPreset = (preset: PresetScenario) => {
    setBasePrice(preset.basePrice);
    setBuyRatio(preset.buyRatio);
    setOnlinePlayers(preset.onlinePlayers);
    setZScore(preset.zScore);
    setWeightedVolume(preset.weightedVolume);
    setConfig(preset.config);
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 sm:p-6 space-y-6">
      <h3 className="text-lg font-semibold text-white border-b border-gray-800 pb-3">
        Market Parameters
      </h3>

      {/* ------------------------------------------------------------------ */}
      {/* Preset Scenarios                                                     */}
      {/* ------------------------------------------------------------------ */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium text-emerald-400 uppercase tracking-wider">
          Quick Presets
        </h4>
        <div className="grid grid-cols-2 gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.name}
              onClick={() => applyPreset(preset)}
              title={preset.description}
              className="flex flex-col items-start p-2.5 rounded-lg bg-gray-800 hover:bg-gray-700 border border-gray-700 hover:border-emerald-700 transition-all text-left group"
            >
              <span className="text-base leading-none mb-1">{preset.emoji}</span>
              <span className="text-xs font-medium text-gray-200 group-hover:text-emerald-400 leading-tight">
                {preset.name}
              </span>
            </button>
          ))}
        </div>
        <p className="text-xs text-gray-500">
          Click a preset to instantly load that scenario, then fine-tune the sliders below.
        </p>
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Trade State Sliders                                                 */}
      {/* ------------------------------------------------------------------ */}
      <div className="space-y-4">
        <h4 className="text-sm font-medium text-emerald-400 uppercase tracking-wider">
          Trade State
        </h4>
        
        <Slider
          label="Base Price"
          value={basePrice}
          onChange={setBasePrice}
          min={1}
          max={10000}
          step={1}
          format={(v) => `$${v.toLocaleString()}`}
          description="Item's reference price"
        />
        
        <Slider
          label="Buy Ratio"
          value={buyRatio}
          onChange={setBuyRatio}
          min={0}
          max={1}
          step={0.01}
          format={(v) => `${(v * 100).toFixed(0)}%`}
          description="What % of trades are buys?"
        />
        
        <Slider
          label="Online Players"
          value={onlinePlayers}
          onChange={setOnlinePlayers}
          min={0}
          max={100}
          step={1}
          format={(v) => `${v} players`}
          description="Current server population"
        />
        
        <Slider
          label="Volume Z-Score"
          value={zScore}
          onChange={setZScore}
          min={-3}
          max={3}
          step={0.1}
          format={(v) => `${v > 0 ? '+' : ''}${v.toFixed(1)}σ`}
          description="How active is the market?"
        />
        
        <Slider
          label="Weighted Volume"
          value={weightedVolume}
          onChange={setWeightedVolume}
          min={0}
          max={5000}
          step={10}
          format={(v) => v.toLocaleString()}
          description="For liquidity calculation"
        />
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Spread Configuration                                                */}
      {/* ------------------------------------------------------------------ */}
      <div className="space-y-4">
        <h4 className="text-sm font-medium text-emerald-400 uppercase tracking-wider">
          Spread Config
        </h4>
        
        <Slider
          label="Base Spread"
          value={config.baseSpread}
          onChange={(v) => updateConfig('baseSpread', v)}
          min={0.05}
          max={1.0}
          step={0.01}
          format={(v) => `${(v * 100).toFixed(0)}%`}
        />
        
        <Slider
          label="Volume Impact"
          value={config.volumeImpact}
          onChange={(v) => updateConfig('volumeImpact', v)}
          min={0}
          max={1}
          step={0.01}
          format={(v) => v.toFixed(2)}
        />
        
        <Slider
          label="Player Impact"
          value={config.playerImpact}
          onChange={(v) => updateConfig('playerImpact', v)}
          min={0}
          max={1}
          step={0.01}
          format={(v) => v.toFixed(2)}
        />
        
        <Slider
          label="Full Effect Players"
          value={config.fullEffectPlayers}
          onChange={(v) => updateConfig('fullEffectPlayers', v)}
          min={5}
          max={100}
          step={1}
          format={(v) => `${v} players`}
        />
        
        <Slider
          label="Liquidity Coeff"
          value={config.liquidityCoeff}
          onChange={(v) => updateConfig('liquidityCoeff', v)}
          min={0.001}
          max={0.2}
          step={0.001}
          format={(v) => v.toFixed(3)}
        />
      </div>

      {/* Reset Button */}
      <button
        onClick={() => {
          setConfig(DEFAULT_CONFIG);
          setBasePrice(100);
          setBuyRatio(0.5);
          setOnlinePlayers(10);
          setZScore(0);
          setWeightedVolume(0);
        }}
        className="w-full py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-lg transition-colors text-sm"
      >
        Reset to Defaults
      </button>
    </div>
  );
}
