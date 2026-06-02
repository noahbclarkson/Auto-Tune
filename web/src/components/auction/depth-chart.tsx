'use client';

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { AuctionDepthData } from './depth-chart-types';

interface DepthChartProps {
  data: AuctionDepthData;
  material: string;
}

interface ChartEntry {
  price: number;
  bid: number;
  ask: number;
}

function buildDepthData(data: AuctionDepthData): { bids: ChartEntry[]; asks: ChartEntry[]; spread: number | null; spreadPct: number | null } {
  // Cumulative bids: sort prices DESC, running total qty
  const sortedBids = [...data.bids].sort((a, b) => b.price - a.price);
  const bidByPrice = new Map<number, number>();
  let bidCum = 0;
  for (const b of sortedBids) {
    bidCum += b.remainingQuantity;
    bidByPrice.set(b.price, bidCum);
  }
  const bids: ChartEntry[] = [...bidByPrice.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([price, qty]) => ({ price, bid: qty, ask: 0 }));

  // Cumulative asks: sort prices ASC, running total qty
  const sortedAsks = [...data.asks].sort((a, b) => a.price - b.price);
  const askByPrice = new Map<number, number>();
  let askCum = 0;
  for (const a of sortedAsks) {
    askCum += a.remainingQuantity;
    askByPrice.set(a.price, askCum);
  }
  const asks: ChartEntry[] = [...askByPrice.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([price, qty]) => ({ price, bid: 0, ask: qty }));

  const spread = data.bids.length && data.asks.length
    ? data.asks[0].price - data.bids[0].price
    : null;
  const spreadPct = spread !== null && data.bids[0].price > 0
    ? (spread / data.bids[0].price) * 100
    : null;

  return { bids, asks, spread, spreadPct };
}

export function DepthChart({ data, material }: DepthChartProps) {
  if (!data.bids.length && !data.asks.length) {
    return (
      <div className="flex items-center justify-center h-48 text-sm text-muted-foreground">
        No depth data available for {material}
      </div>
    );
  }

  const { bids, asks, spread, spreadPct } = buildDepthData(data);

  const allPrices = [
    ...data.bids.map((b) => b.price),
    ...data.asks.map((a) => a.price),
  ].sort((a, b) => a - b);
  const minPrice = allPrices[0] ?? 0;
  const maxPrice = allPrices[allPrices.length - 1] ?? 1;

  const totalBidQty = bids[bids.length - 1]?.bid ?? 0;
  const totalAskQty = asks[asks.length - 1]?.ask ?? 0;

  const chartData = [
    ...bids.map((d) => ({ price: d.price, bid: d.bid, ask: 0 })),
    ...asks.map((d) => ({ price: d.price, bid: 0, ask: d.ask })),
  ];

  return (
    <div className="space-y-3">
      {/* Stats row */}
      <div className="flex items-center gap-4 text-sm">
        {spread !== null && (
          <div className="flex gap-3 flex-wrap">
            <span className="text-muted-foreground">
              Spread:{' '}
              <span className="font-medium text-foreground">
                {spreadPct !== null ? `${spreadPct.toFixed(1)}%` : `$${spread.toFixed(2)}`}
              </span>
            </span>
            <span className="text-muted-foreground">
              Bid depth: <span className="font-medium text-green-600 dark:text-green-400">{totalBidQty}</span>
            </span>
            <span className="text-muted-foreground">
              Ask depth: <span className="font-medium text-red-600 dark:text-red-400">{totalAskQty}</span>
            </span>
          </div>
        )}
      </div>

      {/* Chart */}
      <div className="h-48">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={chartData}
            margin={{ top: 4, right: 8, left: -8, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
            <XAxis
              dataKey="price"
              type="number"
              domain={[minPrice * 0.98, maxPrice * 1.02]}
              tickFormatter={(v) => `$${v}`}
              tick={{ fontSize: 10 }}
              stroke="var(--muted-foreground)"
            />
            <YAxis
              tick={{ fontSize: 10 }}
              stroke="var(--muted-foreground)"
              label={{ value: 'Qty', angle: -90, position: 'insideLeft', fontSize: 10 }}
            />
            <Tooltip
              formatter={(value, name) => [value, name === 'bid' ? 'Bid Depth' : 'Ask Depth']}
              labelFormatter={(label) => `$${Number(label).toFixed(2)}`}
              contentStyle={{
                background: 'var(--card)',
                border: '1px solid var(--border)',
                borderRadius: '0.375rem',
                fontSize: '0.75rem',
              }}
            />
            <Bar dataKey="bid" fill="#16a34a" opacity={0.8} name="Bid Depth" />
            <Bar dataKey="ask" fill="#dc2626" opacity={0.8} name="Ask Depth" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* Legend */}
      <div className="flex gap-6 text-xs">
        <div className="flex items-center gap-1">
          <div className="w-3 h-3 rounded-sm bg-green-600 opacity-80" />
          <span className="text-muted-foreground">Bid depth (cumulative qty at price)</span>
        </div>
        <div className="flex items-center gap-1">
          <div className="w-3 h-3 rounded-sm bg-red-600 opacity-80" />
          <span className="text-muted-foreground">Ask depth (cumulative qty at price)</span>
        </div>
      </div>
    </div>
  );
}
