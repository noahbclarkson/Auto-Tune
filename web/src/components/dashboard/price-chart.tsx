'use client';

import { useEffect, useState, useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { Maximize2, Minimize2 } from 'lucide-react';
import type { ItemDto, PriceHistoryDto } from '@/lib/api';

type Period = '1h' | '6h' | '1d';

const PERIOD_MS: Record<Period, number> = {
  '1h': 60 * 60 * 1000,
  '6h': 6 * 60 * 60 * 1000,
  '1d': 24 * 60 * 60 * 1000,
};

export interface OhlcEntry {
  timestamp: number;
  open: number;
  close: number;
  high: number;
  low: number;
  wickRange: [number, number];
  bodyRange: [number, number];
  isUp: boolean;
  buyPrice: number;
  sellPrice: number;
  buyVolume: number;
  sellVolume: number;
}

export function groupIntoBuckets(
  history: PriceHistoryDto[],
  periodMs: number,
): OhlcEntry[] {
  if (history.length === 0) return [];

  const sorted = [...history].sort((a, b) => a.timestamp - b.timestamp);
  const buckets = new Map<number, PriceHistoryDto[]>();

  for (const entry of sorted) {
    const key = Math.floor(entry.timestamp / periodMs) * periodMs;
    const bucket = buckets.get(key);
    if (bucket) {
      bucket.push(entry);
    } else {
      buckets.set(key, [entry]);
    }
  }

  const result: OhlcEntry[] = [];
  for (const [ts, entries] of Array.from(buckets.entries())) {
    const open = entries[0].price;
    const close = entries[entries.length - 1].price;
    const high = Math.max(...entries.map((e) => e.price));
    const low = Math.min(...entries.map((e) => e.price));
    const isUp = close >= open;

    const avgBpd =
      entries.reduce((sum, e) => sum + e.bpd, 0) / entries.length;
    const avgSpd =
      entries.reduce((sum, e) => sum + e.spd, 0) / entries.length;
    const buyPrice = close * (1 + avgBpd);
    const sellPrice = close * (1 - avgSpd);

    const buyVolume = entries.reduce((sum, e) => sum + e.buyVolume, 0);
    const sellVolume = entries.reduce((sum, e) => sum + e.sellVolume, 0);

    result.push({
      timestamp: ts,
      open,
      close,
      high,
      low,
      wickRange: [low, high],
      bodyRange: [Math.min(open, close), Math.max(open, close)],
      isUp,
      buyPrice,
      sellPrice,
      buyVolume,
      sellVolume,
    });
  }

  return result;
}

export function CandlestickShape(props: Record<string, unknown>) {
  const { x, y, width, height, payload } = props as {
    x: number;
    y: number;
    width: number;
    height: number;
    payload: OhlcEntry;
  };
  if (!payload || height === 0) return null;

  const { high, low, isUp } = payload;
  const bodyTop = Math.max(payload.open, payload.close);
  const bodyBottom = Math.min(payload.open, payload.close);

  const color = isUp ? '#10b981' : '#ef4444';
  const totalRange = high - low;

  if (totalRange <= 0) {
    const midY = y + height / 2;
    return (
      <line
        x1={x}
        x2={x + width}
        y1={midY}
        y2={midY}
        stroke={color}
        strokeWidth={2}
      />
    );
  }

  const pixelBodyTop = y + ((high - bodyTop) / totalRange) * height;
  const pixelBodyBottom = y + ((high - bodyBottom) / totalRange) * height;
  const bodyHeight = Math.max(1, pixelBodyBottom - pixelBodyTop);

  const midX = x + width / 2;
  const bodyX = x + width * 0.15;
  const bodyWidth = width * 0.7;

  return (
    <g>
      <line x1={midX} x2={midX} y1={y} y2={pixelBodyTop} stroke={color} strokeWidth={1} />
      <rect
        x={bodyX}
        y={pixelBodyTop}
        width={bodyWidth}
        height={bodyHeight}
        fill={color}
        stroke={color}
        strokeWidth={1}
      />
      <line
        x1={midX}
        x2={midX}
        y1={pixelBodyTop + bodyHeight}
        y2={y + height}
        stroke={color}
        strokeWidth={1}
      />
    </g>
  );
}

function PriceTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: Array<{ payload: OhlcEntry }>;
  label?: number;
}) {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0].payload;
  return (
    <div className="rounded-lg border border-border bg-card p-2.5 text-xs shadow-md">
      <p className="mb-1.5 font-medium text-foreground">
        {new Date(label ?? entry.timestamp).toLocaleString()}
      </p>
      <div className="grid grid-cols-2 gap-x-4 gap-y-0.5">
        <span className="text-muted-foreground">Open:</span>
        <span className="text-right font-medium">${entry.open.toFixed(2)}</span>
        <span className="text-muted-foreground">Close:</span>
        <span className={`text-right font-medium ${entry.isUp ? 'text-emerald-600' : 'text-red-500'}`}>
          ${entry.close.toFixed(2)}
        </span>
        <span className="text-muted-foreground">High:</span>
        <span className="text-right font-medium">${entry.high.toFixed(2)}</span>
        <span className="text-muted-foreground">Low:</span>
        <span className="text-right font-medium">${entry.low.toFixed(2)}</span>
      </div>
      <div className="mt-1.5 border-t border-border pt-1.5 grid grid-cols-2 gap-x-4 gap-y-0.5">
        <span className="text-muted-foreground">Buy Price:</span>
        <span className="text-right font-medium text-emerald-600">${entry.buyPrice.toFixed(2)}</span>
        <span className="text-muted-foreground">Sell Price:</span>
        <span className="text-right font-medium text-amber-600">${entry.sellPrice.toFixed(2)}</span>
      </div>
      <div className="mt-1.5 border-t border-border pt-1.5 grid grid-cols-2 gap-x-4 gap-y-0.5">
        <span className="text-muted-foreground">Buy Vol:</span>
        <span className="text-right font-medium text-emerald-600">{entry.buyVolume}</span>
        <span className="text-muted-foreground">Sell Vol:</span>
        <span className="text-right font-medium text-amber-600">{entry.sellVolume}</span>
      </div>
    </div>
  );
}

function PeriodToggle({
  period,
  onChange,
}: {
  period: Period;
  onChange: (p: Period) => void;
}) {
  const periods: Period[] = ['1h', '6h', '1d'];
  return (
    <div className="flex gap-1">
      {periods.map((p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`rounded px-2 py-0.5 text-xs font-medium transition-colors ${
            period === p
              ? 'bg-primary text-primary-foreground'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground'
          }`}
        >
          {p}
        </button>
      ))}
    </div>
  );
}

interface PriceChartProps {
  item: ItemDto;
  apiBase: string;
  expanded: boolean;
  onToggleExpand: () => void;
}

export function PriceChart({ item, apiBase, expanded, onToggleExpand }: PriceChartProps) {
  const [history, setHistory] = useState<PriceHistoryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<Period>('1h');

  const fetchLimit = expanded ? 500 : 200;

  useEffect(() => {
    setLoading(true);
    fetch(`${apiBase}/api/items/${item.id}/history?limit=${fetchLimit}`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: PriceHistoryDto[]) => {
        setHistory(data.reverse());
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, [item.id, apiBase, fetchLimit]);

  const ohlcData = useMemo(
    () => groupIntoBuckets(history, PERIOD_MS[period]),
    [history, period],
  );

  const chartHeight = expanded ? 'h-96' : 'h-48';

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">{item.displayName}</CardTitle>
          <div className="flex items-center gap-2">
            <PeriodToggle period={period} onChange={setPeriod} />
            <button
              onClick={onToggleExpand}
              className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              title={expanded ? 'Collapse chart' : 'Expand chart'}
            >
              {expanded ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
            </button>
          </div>
        </div>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>
            Buy: <span className="font-medium text-emerald-600">${item.buyPrice.toFixed(2)}</span>
          </span>
          <span>
            Sell: <span className="font-medium text-amber-600">${item.sellPrice.toFixed(2)}</span>
          </span>
          <span className={`font-medium ${item.change24h > 0 ? 'text-emerald-600' : item.change24h < 0 ? 'text-red-500' : ''}`}>
            {item.change24h > 0 ? '+' : ''}{item.change24h.toFixed(2)}%
          </span>
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className={`flex ${chartHeight} items-center justify-center text-sm text-muted-foreground`}>
            Loading history...
          </div>
        ) : ohlcData.length === 0 ? (
          <div className={`flex ${chartHeight} items-center justify-center text-sm text-muted-foreground`}>
            No price history yet
          </div>
        ) : (
          <div className={chartHeight}>
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={ohlcData}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={(ts) =>
                    period === '1d'
                      ? new Date(ts).toLocaleDateString([], { month: 'short', day: 'numeric' })
                      : new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                  }
                  stroke="hsl(var(--muted-foreground))"
                  fontSize={11}
                />
                <YAxis
                  stroke="hsl(var(--muted-foreground))"
                  fontSize={11}
                  domain={['auto', 'auto']}
                  tickFormatter={(v) => `$${Number(v).toFixed(2)}`}
                />
                <Tooltip content={<PriceTooltip />} />
                <Legend
                  verticalAlign="top"
                  height={24}
                  iconType="line"
                  wrapperStyle={{ fontSize: 11 }}
                />
                <Bar
                  dataKey="wickRange"
                  name="Base Price"
                  shape={<CandlestickShape />}
                  isAnimationActive={false}
                  legendType="none"
                />
                <Line
                  type="monotone"
                  dataKey="buyPrice"
                  name="Buy"
                  stroke="#10b981"
                  strokeDasharray="5 3"
                  dot={false}
                  strokeWidth={1.5}
                  isAnimationActive={false}
                />
                <Line
                  type="monotone"
                  dataKey="sellPrice"
                  name="Sell"
                  stroke="#f59e0b"
                  strokeDasharray="5 3"
                  dot={false}
                  strokeWidth={1.5}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        )}
        {ohlcData.length > 0 && (
          <div className="mt-3 grid grid-cols-2 gap-2 text-xs text-muted-foreground">
            <div>
              Buy Volume:{' '}
              <span className="font-medium text-foreground">
                {ohlcData.reduce((s, e) => s + e.buyVolume, 0)}
              </span>
            </div>
            <div>
              Sell Volume:{' '}
              <span className="font-medium text-foreground">
                {ohlcData.reduce((s, e) => s + e.sellVolume, 0)}
              </span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
