'use client';

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { generateSpreadCurve, MarketConfig, simulatePrice } from '@/lib/market-engine';

interface SpreadChartProps {
  config: MarketConfig;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  basePrice: number;
  buyRatio: number;
}

export function SpreadChart({
  config,
  onlinePlayers,
  zScore,
  weightedVolume,
  basePrice,
  buyRatio,
}: SpreadChartProps) {
  // Generate spread curve data
  const spreadData = generateSpreadCurve(config, onlinePlayers, zScore, weightedVolume);

  // Generate price simulation data
  const priceSimulation = simulatePrice(50, basePrice, buyRatio, onlinePlayers, config);
  const priceData = priceSimulation.map((price, tick) => ({
    tick,
    price,
    hours: (tick * 5) / 60, // 5-min ticks to hours
  }));

  return (
    <div className="space-y-5 sm:space-y-6">
      {/* Spread vs Buy Ratio */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 sm:p-6">
        <h3 className="text-base sm:text-lg font-semibold text-white mb-3 sm:mb-4">
          Spread vs Buy Ratio
        </h3>
        <p className="text-sm text-gray-400 mb-3 sm:mb-4">
          How buy and sell spreads change as the buy/sell ratio shifts from all sells to all buys.
        </p>
        <div className="h-48 sm:h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={spreadData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis
                dataKey="buyRatio"
                tickFormatter={(v) => `${(v * 100).toFixed(0)}%`}
                stroke="#9CA3AF"
                fontSize={12}
              />
              <YAxis
                tickFormatter={(v) => `${v.toFixed(1)}%`}
                stroke="#9CA3AF"
                fontSize={12}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1F2937',
                  border: '1px solid #374151',
                  borderRadius: '8px',
                }}
                labelFormatter={(v) => `Buy Ratio: ${(v * 100).toFixed(0)}%`}
                formatter={(value: number, name: string) => [
                  `${value.toFixed(2)}%`,
                  name === 'bpd' ? 'Buy Spread' : name === 'spd' ? 'Sell Spread' : 'Total',
                ]}
              />
              <Legend
                formatter={(value) =>
                  value === 'bpd' ? 'Buy Spread (BPD)' : value === 'spd' ? 'Sell Spread (SPD)' : 'Total'
                }
              />
              <Line
                type="monotone"
                dataKey="bpd"
                stroke="#22C55E"
                strokeWidth={2}
                dot={false}
                name="bpd"
              />
              <Line
                type="monotone"
                dataKey="spd"
                stroke="#EF4444"
                strokeWidth={2}
                dot={false}
                name="spd"
              />
              <Line
                type="monotone"
                dataKey="totalSpread"
                stroke="#9CA3AF"
                strokeWidth={1}
                strokeDasharray="5 5"
                dot={false}
                name="totalSpread"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Price Simulation */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 sm:p-6">
        <h3 className="text-base sm:text-lg font-semibold text-white mb-3 sm:mb-4">
          Price Simulation Over Time
        </h3>
        <p className="text-sm text-gray-400 mb-3 sm:mb-4">
          Simulated price evolution over 50 ticks (~4 hours at 5-min intervals) with the current buy ratio.
        </p>
        <div className="h-48 sm:h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={priceData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis
                dataKey="tick"
                stroke="#9CA3AF"
                fontSize={12}
                label={{ value: 'Tick', position: 'insideBottom', offset: -5, fill: '#9CA3AF' }}
              />
              <YAxis
                tickFormatter={(v) => `$${v.toFixed(0)}`}
                stroke="#9CA3AF"
                fontSize={12}
                domain={['auto', 'auto']}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1F2937',
                  border: '1px solid #374151',
                  borderRadius: '8px',
                }}
                labelFormatter={(v) => `Tick ${v} (${((v as number) * 5 / 60).toFixed(1)}h)`}
                formatter={(value: number) => [`$${value.toFixed(2)}`, 'Price']}
              />
              <Line
                type="monotone"
                dataKey="price"
                stroke="#10B981"
                strokeWidth={2}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-4 flex items-center gap-4 text-sm text-gray-400">
          <span>
            <strong>Initial:</strong> ${basePrice.toFixed(2)}
          </span>
          <span>
            <strong>Final:</strong> ${priceSimulation[priceSimulation.length - 1].toFixed(2)}
          </span>
          <span>
            <strong>Change:</strong>{' '}
            {((priceSimulation[priceSimulation.length - 1] / basePrice - 1) * 100).toFixed(2)}%
          </span>
        </div>
      </div>
    </div>
  );
}
