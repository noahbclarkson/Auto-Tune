'use client';

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { PriceHistoryPoint } from '@/lib/api-client';

interface PriceHistoryChartProps {
  item: string;
  points: PriceHistoryPoint[];
}

function formatLabel(timestamp: string): string {
  return new Date(timestamp).toLocaleString();
}

export function PriceHistoryChart({ item, points }: PriceHistoryChartProps) {
  const chartData = [...points]
    .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())
    .map((point) => ({
      ...point,
      shortTime: new Date(point.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }));

  return (
    <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">
          Price History: {item}
        </h2>
        <span className="text-xs text-gray-500">{chartData.length} snapshots</span>
      </div>

      <div className="h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 6, right: 18, left: 8, bottom: 4 }}>
            <defs>
              <linearGradient id="historyFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#34d399" stopOpacity={0.35} />
                <stop offset="95%" stopColor="#34d399" stopOpacity={0.02} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#1f2937" strokeDasharray="4 4" />
            <XAxis dataKey="shortTime" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 12 }} minTickGap={20} />
            <YAxis
              stroke="#9ca3af"
              tick={{ fill: '#9ca3af', fontSize: 12 }}
              tickFormatter={(value: number) => `$${value.toFixed(2)}`}
              width={84}
            />
            <Tooltip
              contentStyle={{
                backgroundColor: '#0f172a',
                border: '1px solid #374151',
                borderRadius: '0.5rem',
                color: '#f9fafb',
              }}
              formatter={(value: number, _name, payload) => [`$${value.toFixed(2)}`, 'Price']}
              labelFormatter={(_label, payload) => {
                const point = payload?.[0]?.payload as PriceHistoryPoint | undefined;
                return point ? formatLabel(point.timestamp) : '';
              }}
            />
            <Area type="monotone" dataKey="price" stroke="#34d399" strokeWidth={2} fill="url(#historyFill)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
