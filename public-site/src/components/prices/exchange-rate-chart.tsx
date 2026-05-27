'use client';

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { ExchangeRate } from '@/lib/api-client';

interface ExchangeRateChartProps {
  rates: ExchangeRate[];
}

function rateColor(rate: number): string {
  if (rate < 0.95) return '#34d399'; // below true price — green
  if (rate <= 1.05) return '#6ee7b7'; // near true price — light green
  if (rate <= 1.25) return '#fbbf24'; // moderately above — amber
  return '#f87171'; // significantly above — red
}

interface ChartDatum {
  name: string;
  rate: number;
}

export function ExchangeRateChart({ rates }: ExchangeRateChartProps) {
  const data: ChartDatum[] = rates.map((r) => ({
    name: r.name.length > 18 ? `${r.name.slice(0, 15)}…` : r.name,
    rate: Math.round(r.rate * 1000) / 1000,
  }));

  const minRate = Math.min(...rates.map((r) => r.rate), 0.8);
  const maxRate = Math.max(...rates.map((r) => r.rate), 1.5);
  const yDomain: [number, number] = [Math.floor(minRate * 10) / 10, Math.ceil(maxRate * 10) / 10];

  return (
    <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
      <div className="flex flex-wrap items-center justify-between gap-y-1 mb-4">
        <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">
          Rate vs True Prices
        </h2>
        <span className="text-xs text-gray-500">{rates.length} server{rates.length !== 1 ? 's' : ''}</span>
      </div>

      <div className="h-52 sm:h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 18, left: 8, bottom: 40 }}>
            <CartesianGrid stroke="#1f2937" strokeDasharray="4 4" vertical={false} />
            <XAxis
              dataKey="name"
              stroke="#9ca3af"
              tick={{ fill: '#9ca3af', fontSize: 11 }}
              angle={-35}
              textAnchor="end"
              interval={0}
              height={60}
            />
            <YAxis
              stroke="#9ca3af"
              tick={{ fill: '#9ca3af', fontSize: 12 }}
              tickFormatter={(v: number) => `${v.toFixed(2)}×`}
              domain={yDomain}
              width={60}
            />
            <ReferenceLine y={1} stroke="#6ee7b7" strokeDasharray="6 3" strokeWidth={1.5} label={{ value: '1.00×', fill: '#6ee7b7', fontSize: 11, position: 'insideTopRight' }} />
            <Tooltip
              contentStyle={{
                backgroundColor: '#0f172a',
                border: '1px solid #374151',
                borderRadius: '0.5rem',
                color: '#f9fafb',
              }}
              formatter={(value: number) => [`${value.toFixed(3)}×`, 'Rate']}
            />
            <Bar dataKey="rate" radius={[4, 4, 0, 0]}>
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={rateColor(entry.rate)} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="flex items-center gap-4 mt-3 text-xs text-gray-500 flex-wrap">
        <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-sm bg-emerald-400 inline-block" /> Below true price</span>
        <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-sm bg-amber-400 inline-block" /> Moderately above</span>
        <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-sm bg-red-400 inline-block" /> Significantly above</span>
      </div>
    </div>
  );
}
