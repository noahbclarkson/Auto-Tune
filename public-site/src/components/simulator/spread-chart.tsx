'use client';

import {
  AreaChart,
  Area,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import { generateSpreadCurve, MarketConfig, simulatePrice } from '@/lib/market-engine';

interface SpreadChartProps {
  config: MarketConfig;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  distinctTraders: number;
  basePrice: number;
  buyRatio: number;
}

const TOOLTIP_STYLE = {
  backgroundColor: '#0f172a',
  border: '1px solid #1e293b',
  borderRadius: '8px',
  fontSize: '12px',
  boxShadow: '0 4px 20px rgba(0,0,0,0.4)',
};

const GRID_COLOR = '#1e293b';
const AXIS_COLOR = '#475569';

export function SpreadChart({
  config,
  onlinePlayers,
  zScore,
  weightedVolume,
  distinctTraders,
  basePrice,
  buyRatio,
}: SpreadChartProps) {
  const spreadData = generateSpreadCurve(config, onlinePlayers, zScore, weightedVolume, distinctTraders);

  const priceSimulation = simulatePrice(72, basePrice, buyRatio, onlinePlayers, config);
  const priceData = priceSimulation.map((price, tick) => ({ tick, price }));
  const initialPrice = priceData[0].price;
  const finalPrice = priceData[priceData.length - 1].price;
  const changePct = ((finalPrice / initialPrice) - 1) * 100;
  const priceUp = finalPrice >= initialPrice;

  return (
    <div className="space-y-4">
      {/* Spread vs Buy Ratio */}
      <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-800 bg-gray-900">
          <h3 className="text-sm font-semibold text-white">Spread vs Buy Ratio</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            How BPD/SPD shift as buy pressure changes — current ratio marked with dashed line
          </p>
        </div>
        <div className="p-4 h-52">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={spreadData}>
              <defs>
                <linearGradient id="bpdGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10b981" stopOpacity={0.25} />
                  <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="spdGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#f43f5e" stopOpacity={0.2} />
                  <stop offset="95%" stopColor="#f43f5e" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={GRID_COLOR} />
              <XAxis
                dataKey="buyRatio"
                tickFormatter={(v) => `${(v * 100).toFixed(0)}%`}
                stroke={AXIS_COLOR}
                tick={{ fontSize: 11 }}
                tickLine={false}
              />
              <YAxis
                tickFormatter={(v) => `${v.toFixed(1)}%`}
                stroke={AXIS_COLOR}
                tick={{ fontSize: 11 }}
                tickLine={false}
                width={40}
              />
              <Tooltip
                contentStyle={TOOLTIP_STYLE}
                labelFormatter={(v) => `Buy ratio: ${(v * 100).toFixed(0)}%`}
                formatter={(value: number, name: string) => [
                  `${value.toFixed(3)}%`,
                  name === 'bpd' ? 'BPD (ask spread)' : name === 'spd' ? 'SPD (bid spread)' : 'Total',
                ]}
              />
              <Legend
                formatter={(v) =>
                  v === 'bpd' ? 'BPD' : v === 'spd' ? 'SPD' : 'Total'
                }
                iconType="circle"
                iconSize={8}
                wrapperStyle={{ fontSize: 11 }}
              />
              <ReferenceLine
                x={buyRatio}
                stroke="#64748b"
                strokeDasharray="4 3"
                strokeWidth={1.5}
              />
              <Area type="monotone" dataKey="bpd" stroke="#10b981" strokeWidth={2} fill="url(#bpdGrad)" dot={false} name="bpd" />
              <Area type="monotone" dataKey="spd" stroke="#f43f5e" strokeWidth={2} fill="url(#spdGrad)" dot={false} name="spd" />
              <Line type="monotone" dataKey="totalSpread" stroke="#475569" strokeWidth={1} strokeDasharray="4 3" dot={false} name="total" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Price simulation over time */}
      <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-800 bg-gray-900">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="text-sm font-semibold text-white">Price Simulation</h3>
              <p className="text-xs text-gray-500 mt-0.5">
                72 ticks (6 hours) at current buy ratio · constant conditions
              </p>
            </div>
            <div className="text-right">
              <span className={`text-sm font-bold font-mono ${priceUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                {priceUp ? '▲' : '▼'} {Math.abs(changePct).toFixed(2)}%
              </span>
              <p className="text-xs text-gray-600 font-mono">${finalPrice.toFixed(2)}</p>
            </div>
          </div>
        </div>
        <div className="p-4 h-52">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={priceData}>
              <defs>
                <linearGradient id="priceGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={priceUp ? '#10b981' : '#f43f5e'} stopOpacity={0.3} />
                  <stop offset="95%" stopColor={priceUp ? '#10b981' : '#f43f5e'} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={GRID_COLOR} />
              <XAxis
                dataKey="tick"
                stroke={AXIS_COLOR}
                tick={{ fontSize: 11 }}
                tickLine={false}
                tickFormatter={(v) => `${v}`}
              />
              <YAxis
                tickFormatter={(v) => `$${v >= 1000 ? (v / 1000).toFixed(1) + 'k' : v.toFixed(0)}`}
                stroke={AXIS_COLOR}
                tick={{ fontSize: 11 }}
                tickLine={false}
                width={48}
                domain={['auto', 'auto']}
              />
              <Tooltip
                contentStyle={TOOLTIP_STYLE}
                labelFormatter={(v) => `Tick ${v} (${((v as number) * 5 / 60).toFixed(1)}h)`}
                formatter={(value: number) => [`$${value.toFixed(2)}`, 'Price']}
              />
              <ReferenceLine y={initialPrice} stroke="#334155" strokeDasharray="3 3" strokeWidth={1} />
              <Area
                type="monotone"
                dataKey="price"
                stroke={priceUp ? '#10b981' : '#f43f5e'}
                strokeWidth={2}
                fill="url(#priceGrad)"
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
        <div className="px-4 pb-3 flex items-center gap-5 text-xs font-mono text-gray-500">
          <span>Start: <span className="text-gray-300">${initialPrice.toFixed(2)}</span></span>
          <span>End: <span className={priceUp ? 'text-emerald-400' : 'text-rose-400'}>${finalPrice.toFixed(2)}</span></span>
          <span>Δ: <span className={priceUp ? 'text-emerald-400' : 'text-rose-400'}>{priceUp ? '+' : ''}{changePct.toFixed(2)}%</span></span>
        </div>
      </div>
    </div>
  );
}
