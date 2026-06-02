'use client';

import { useState, useMemo } from 'react';
import { MarketConfig, DEFAULT_CONFIG, calculatePrices, MarketEvent } from '@/lib/market-engine';
import { ParameterPanel } from '@/components/simulator/parameter-panel';
import { PricePreview } from '@/components/simulator/price-preview';
import { SpreadChart } from '@/components/simulator/spread-chart';
import { StabilityForecast } from '@/components/simulator/stability-forecast';
import { ConfigImpactPreview } from '@/components/simulator/config-impact-preview';
import { MarketEventsPanel } from '@/components/simulator/market-events-panel';
import { ProjectionChart } from '@/components/simulator/projection-chart';

export default function SimulatorPage() {
  const [config, setConfig] = useState<MarketConfig>(DEFAULT_CONFIG);
  const [basePrice, setBasePrice] = useState(100);
  const [buyRatio, setBuyRatio] = useState(0.5);
  const [onlinePlayers, setOnlinePlayers] = useState(10);
  const [zScore, setZScore] = useState(0);
  const [weightedVolume, setWeightedVolume] = useState(0);
  const [distinctTraders, setDistinctTraders] = useState(5);
  const [activeEvents, setActiveEvents] = useState<MarketEvent[]>([]);
  const [projectionMaterial, setProjectionMaterial] = useState('DIAMOND');

  const prices = useMemo(() => {
    return calculatePrices(basePrice, buyRatio, onlinePlayers, zScore, weightedVolume, distinctTraders, config);
  }, [basePrice, buyRatio, onlinePlayers, zScore, weightedVolume, distinctTraders, config]);

  const applyPreset = (preset: { basePrice: number; buyRatio: number; onlinePlayers: number; zScore: number; weightedVolume: number; distinctTraders: number; config: MarketConfig }) => {
    setConfig(preset.config);
    setBasePrice(preset.basePrice);
    setBuyRatio(preset.buyRatio);
    setOnlinePlayers(preset.onlinePlayers);
    setZScore(preset.zScore);
    setWeightedVolume(preset.weightedVolume);
    setDistinctTraders(preset.distinctTraders);
    document.getElementById('simulator-charts')?.scrollIntoView({ behavior: 'smooth' });
  };

  const ARCHETYPE_PRESETS = [
    {
      name: 'Survival SMP',
      emoji: '🏕️',
      description: 'Balanced economy driven by MarketMakers and GuildBuyers. Ideal for vanilla-style servers.',
      basePrice: 300, buyRatio: 0.72, onlinePlayers: 12, zScore: 0.1, weightedVolume: 200, distinctTraders: 9,
      config: { ...DEFAULT_CONFIG, baseSpread: 0.10, maxPriceChangePercent: 1.5 },
    },
    {
      name: 'Skyblock',
      emoji: '☁️',
      description: 'Sell-heavy island economies. Wide spreads compensate for limited buyers.',
      basePrice: 200, buyRatio: 0.35, onlinePlayers: 8, zScore: -0.2, weightedVolume: 80, distinctTraders: 5,
      config: { ...DEFAULT_CONFIG, baseSpread: 0.15, maxPriceChangePercent: 1.5, volumeImpact: 0.9 },
    },
    {
      name: 'High-Volume',
      emoji: '⚡',
      description: 'Busy servers with tight spreads and high liquidity. Many concurrent traders.',
      basePrice: 500, buyRatio: 0.75, onlinePlayers: 25, zScore: 0.5, weightedVolume: 600, distinctTraders: 18,
      config: { ...DEFAULT_CONFIG, baseSpread: 0.05, maxPriceChangePercent: 1.0, playerImpact: 0.75 },
    },
    {
      name: 'Casual-Heavy',
      emoji: '🎮',
      description: 'Servers where most players are casual buyers, not farmers. Sim-proven: +114% GDP, −22% Debt/GDP vs default mix.',
      basePrice: 300, buyRatio: 0.80, onlinePlayers: 10, zScore: 0.05, weightedVolume: 120, distinctTraders: 7,
      config: { ...DEFAULT_CONFIG, baseSpread: 0.12, maxPriceChangePercent: 1.5, volumeImpact: 0.85 },
    },
  ] as const;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      {/* Hero CTA — above the parameter panel */}
      <div className="mb-8 p-5 rounded-xl border border-emerald-900/40 bg-gradient-to-br from-emerald-950/60 to-gray-900/80">
        <p className="text-xs text-emerald-500 uppercase tracking-wider mb-2 font-medium">Start with a scenario</p>
        <p className="text-sm text-gray-400 mb-4">Choose your server type. Prices and charts update instantly.</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {ARCHETYPE_PRESETS.map((preset) => (
            <button
              key={preset.name}
              onClick={() => applyPreset(preset)}
              className="flex items-start gap-3 p-4 rounded-lg bg-gray-900/70 hover:bg-gray-800/70 border border-gray-700/60 hover:border-emerald-600/60 transition-all text-left group cursor-pointer"
            >
              <span className="text-2xl mt-0.5 shrink-0">{preset.emoji}</span>
              <div>
                <p className="text-sm font-semibold text-gray-200 group-hover:text-emerald-400 transition-colors mb-0.5">{preset.name}</p>
                <p className="text-xs text-gray-500 leading-relaxed">{preset.description}</p>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white mb-1">Price Simulator</h1>
        <p className="text-sm text-gray-500">
          Adjust parameters and watch prices update in real-time. All math matches the live Java plugin.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left column */}
        <div className="lg:col-span-1 space-y-4">
          <ParameterPanel
            config={config}
            setConfig={setConfig}
            basePrice={basePrice}
            setBasePrice={setBasePrice}
            buyRatio={buyRatio}
            setBuyRatio={setBuyRatio}
            onlinePlayers={onlinePlayers}
            setOnlinePlayers={setOnlinePlayers}
            zScore={zScore}
            setZScore={setZScore}
            weightedVolume={weightedVolume}
            setWeightedVolume={setWeightedVolume}
            distinctTraders={distinctTraders}
            setDistinctTraders={setDistinctTraders}
          />

          {/* Market Events */}
          <MarketEventsPanel events={activeEvents} onEventsChange={setActiveEvents} />
        </div>

        {/* Right column */}
        <div id="simulator-charts" className="lg:col-span-2 space-y-4">
          <ConfigImpactPreview />

          <StabilityForecast
            buyRatio={buyRatio}
            onlinePlayers={onlinePlayers}
            zScore={zScore}
            weightedVolume={weightedVolume}
            distinctTraders={distinctTraders}
            config={config}
          />

          {/* Price Projection with events */}
          <ProjectionChart
            config={config}
            basePrice={basePrice}
            buyRatio={buyRatio}
            onlinePlayers={onlinePlayers}
            activeEvents={activeEvents}
            projectionMaterial={projectionMaterial}
            onMaterialChange={setProjectionMaterial}
          />

          <PricePreview
            buyPrice={prices.buyPrice}
            sellPrice={prices.sellPrice}
            basePrice={basePrice}
            bpd={prices.bpd}
            spd={prices.spd}
            buyRatio={buyRatio}
            onlinePlayers={onlinePlayers}
            zScore={zScore}
            weightedVolume={weightedVolume}
            distinctTraders={distinctTraders}
            config={config}
          />

          <SpreadChart
            config={config}
            onlinePlayers={onlinePlayers}
            zScore={zScore}
            weightedVolume={weightedVolume}
            distinctTraders={distinctTraders}
            basePrice={basePrice}
            buyRatio={buyRatio}
          />
        </div>
      </div>
    </div>
  );
}
