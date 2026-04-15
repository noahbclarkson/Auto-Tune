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

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
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
        <div className="lg:col-span-2 space-y-4">
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
