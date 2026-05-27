'use client';

import {
  Line,
  LineChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  ReferenceLine,
} from 'recharts';
import type { ExchangeRateHistoryPoint } from '@/lib/api-client';

interface ExchangeRateHistoryChartProps {
  history: ExchangeRateHistoryPoint[];
  serverName: string;
  onClose: () => void;
}

function formatTimestamp(ts: string): string {
  return new Date(ts).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
}

export function ExchangeRateHistoryChart({ history, serverName, onClose }: ExchangeRateHistoryChartProps) {
  const data = history.map((pt) => ({
    t: formatTimestamp(pt.timestamp),
    rate: Math.round(pt.rate * 1000) / 1000,
    servers: pt.server_count,
  }));

  const minRate = Math.min(...data.map((d) => d.rate), 0.8);
  const maxRate = Math.max(...data.map((d) => d.rate), 1.5);
  const yDomain: [number, number] = [
    Math.floor(minRate * 10) / 10,
    Math.ceil(maxRate * 10) / 10,
  ];

  return (
    <div className="bg-gray-900/60 border border-gray-700/50 rounded-xl overflow-hidden mb-6">
      <div className="px-5 py-4 border-b border-gray-800/50 flex items-center justify-between">
        <div>
          <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">
            {serverName} — Rate History
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">{data.length} data points</p>
        </div>
        <button
          onClick={onClose}
          className="text-xs text-gray-400 hover:text-gray-200 px-2 py-1 rounded border border-gray-700 hover:border-gray-600 transition-colors"
        >
          Close
        </button>
      </div>
      <div className="p-5">
        <div className="h-48 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="#1f2937" strokeDasharray="4 4" vertical={false} />
              <XAxis
                dataKey="t"
                stroke="#6b7280"
                tick={{ fill: '#6b7280', fontSize: 11 }}
                interval="preserveStartEnd"
              />
              <YAxis
                stroke="#6b7280"
                tick={{ fill: '#6b7280', fontSize: 11 }}
                tickFormatter={(v: number) => `${v.toFixed(2)}×`}
                domain={yDomain}
                width={52}
              />
              <ReferenceLine y={1} stroke="#6ee7b7" strokeDasharray="6 3" strokeWidth={1.5} label={{ value: '1.00×', fill: '#6ee7b7', fontSize: 11, position: 'insideTopRight' }} />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#0f172a',
                  border: '1px solid #374151',
                  borderRadius: '0.5rem',
                  color: '#f9fafb',
                  fontSize: 12,
                }}
                formatter={(value: number) => [`${value.toFixed(3)}×`, 'Rate']}
                labelFormatter={(label) => `Date: ${label}`}
              />
              <Line
                type="monotone"
                dataKey="rate"
                stroke="#34d399"
                strokeWidth={2}
                dot={{ fill: '#34d399', r: 2.5 }}
                activeDot={{ r: 4, fill: '#34d399' }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}