'use client';

import { useState, useMemo } from 'react';
import { MarketConfig, DEFAULT_CONFIG, calculatePrices } from '@/lib/market-engine';
import { ParameterPanel } from '@/components/simulator/parameter-panel';
import { PricePreview } from '@/components/simulator/price-preview';
import { SpreadChart } from '@/components/simulator/spread-chart';

export default function SimulatorPage() {
  // Market state parameters
  const [config, setConfig] = useState<MarketConfig>(DEFAULT_CONFIG);
  const [basePrice, setBasePrice] = useState(100);
  const [buyRatio, setBuyRatio] = useState(0.5);
  const [onlinePlayers, setOnlinePlayers] = useState(10);
  const [zScore, setZScore] = useState(0);
  const [weightedVolume, setWeightedVolume] = useState(0);

  // Calculate prices reactively
  const prices = useMemo(() => {
    return calculatePrices(basePrice, buyRatio, onlinePlayers, zScore, weightedVolume, config);
  }, [basePrice, buyRatio, onlinePlayers, zScore, weightedVolume, config]);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white mb-2">
          Price Simulator
        </h1>
        <p className="text-gray-400">
          Experiment with market parameters to see how Auto-Tune calculates prices.
          Adjust the sliders and watch the prices update in real-time.
        </p>
      </div>

      {/* Main Grid */}
      <div className="grid lg:grid-cols-3 gap-6">
        {/* Left: Parameters */}
        <div className="lg:col-span-1">
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
          />
        </div>

        {/* Right: Results */}
        <div className="lg:col-span-2 space-y-6">
          {/* Price Preview */}
          <PricePreview
            buyPrice={prices.buyPrice}
            sellPrice={prices.sellPrice}
            basePrice={basePrice}
            bpd={prices.bpd}
            spd={prices.spd}
          />

          {/* Charts */}
          <SpreadChart
            config={config}
            onlinePlayers={onlinePlayers}
            zScore={zScore}
            weightedVolume={weightedVolume}
            basePrice={basePrice}
            buyRatio={buyRatio}
          />
        </div>
      </div>

      {/* Explanation */}
      <div className="mt-8 bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-white mb-3">
          Understanding the Results
        </h3>
        <div className="grid md:grid-cols-2 gap-4 text-sm text-gray-400">
          <div>
            <h4 className="font-medium text-gray-300 mb-2">Price Calculation</h4>
            <ul className="space-y-1">
              <li>• <strong>Buy Price</strong>: What players pay to purchase items</li>
              <li>• <strong>Sell Price</strong>: What players receive when selling</li>
              <li>• <strong>Spread</strong>: The gap between buy and sell prices</li>
              <li>• <strong>Server Margin</strong>: The server&apos;s profit on each transaction</li>
            </ul>
          </div>
          <div>
            <h4 className="font-medium text-gray-300 mb-2">Key Factors</h4>
            <ul className="space-y-1">
              <li>• More players → tighter spreads (better prices)</li>
              <li>• High buy ratio → higher buy price, lower sell price</li>
              <li>• High activity (z &gt; 1) → tighter spreads</li>
              <li>• High liquidity → much tighter spreads</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
