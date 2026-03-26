'use client';

import { useEffect, useState, useMemo, useRef } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
  BarChart,
  Bar,
  Cell,
} from 'recharts';
import { Search, ChevronDown, TrendingUp, TrendingDown, Minus, ArrowLeftRight } from 'lucide-react';
import type { ItemDto, PriceHistoryDto } from '@/lib/api';

type Period = '1h' | '6h' | '1d';

const PERIOD_MS: Record<Period, number> = {
  '1h': 60 * 60 * 1000,
  '6h': 6 * 60 * 60 * 1000,
  '1d': 24 * 60 * 60 * 1000,
};

interface RatioEntry {
  timestamp: number;
  ratio: number;
}

function buildRatioSeries(
  historyA: PriceHistoryDto[],
  historyB: PriceHistoryDto[],
  periodMs: number,
): RatioEntry[] {
  if (historyA.length === 0 || historyB.length === 0) return [];

  const bucketAvg = (entries: PriceHistoryDto[]) => {
    const buckets = new Map<number, number[]>();
    for (const e of entries) {
      const key = Math.floor(e.timestamp / periodMs) * periodMs;
      const bucket = buckets.get(key);
      if (bucket) {
        bucket.push(e.price);
      } else {
        buckets.set(key, [e.price]);
      }
    }
    const result = new Map<number, number>();
    for (const [ts, prices] of Array.from(buckets.entries())) {
      result.set(ts, prices[prices.length - 1]);
    }
    return result;
  };

  const bucketsA = bucketAvg(historyA);
  const bucketsB = bucketAvg(historyB);

  const result: RatioEntry[] = [];
  for (const [ts, priceA] of Array.from(bucketsA.entries())) {
    const priceB = bucketsB.get(ts);
    if (priceB && priceB !== 0) {
      result.push({ timestamp: ts, ratio: priceA / priceB });
    }
  }

  return result.sort((a, b) => a.timestamp - b.timestamp);
}

function RatioTooltip({
  active,
  payload,
  label,
  nameA,
  nameB,
}: {
  active?: boolean;
  payload?: Array<{ payload: RatioEntry }>;
  label?: number;
  nameA: string;
  nameB: string;
}) {
  if (!active || !payload || payload.length === 0) return null;
  const entry = payload[0].payload;
  return (
    <div className="rounded-lg border border-border bg-card p-2.5 text-xs shadow-md">
      <p className="mb-1.5 font-medium text-foreground">
        {new Date(label ?? entry.timestamp).toLocaleString()}
      </p>
      <div className="grid grid-cols-2 gap-x-4 gap-y-0.5">
        <span className="text-muted-foreground">Ratio:</span>
        <span className="text-right font-medium">{entry.ratio.toFixed(4)}</span>
        <span className="text-muted-foreground col-span-2 mt-1 text-center">
          {nameA} / {nameB}
        </span>
      </div>
    </div>
  );
}

function ItemSelector({
  items,
  selected,
  onSelect,
  label,
}: {
  items: ItemDto[];
  selected: ItemDto | null;
  onSelect: (item: ItemDto) => void;
  label: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const filtered = useMemo(() => {
    if (!search) return items;
    const q = search.toLowerCase();
    return items.filter(
      (i) =>
        i.displayName.toLowerCase().includes(q) ||
        i.material.toLowerCase().includes(q),
    );
  }, [items, search]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between rounded-md border border-border bg-background px-3 py-2 text-sm transition-colors hover:bg-muted"
      >
        <span className="truncate">
          {selected ? selected.displayName : <span className="text-muted-foreground">{label}</span>}
        </span>
        <ChevronDown className="ml-2 h-4 w-4 shrink-0 text-muted-foreground" />
      </button>
      {open && (
        <div className="absolute left-0 right-0 top-full z-50 mt-1 rounded-md border border-border bg-card shadow-lg">
          <div className="flex items-center border-b border-border px-3 py-2">
            <Search className="mr-2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search items..."
              className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              autoFocus
            />
          </div>
          <div className="max-h-48 overflow-y-auto">
            {filtered.length === 0 ? (
              <div className="px-3 py-2 text-sm text-muted-foreground">No items found</div>
            ) : (
              filtered.map((item) => (
                <button
                  key={item.id}
                  onClick={() => {
                    onSelect(item);
                    setOpen(false);
                    setSearch('');
                  }}
                  className={`flex w-full items-center justify-between px-3 py-1.5 text-sm transition-colors hover:bg-muted ${
                    selected?.id === item.id ? 'bg-muted/50 font-medium' : ''
                  }`}
                >
                  <span className="truncate">{item.displayName}</span>
                  <span className="ml-2 shrink-0 text-xs text-muted-foreground">
                    ${item.price.toFixed(2)}
                  </span>
                </button>
              ))
            )}
          </div>
        </div>
      )}
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

function useItemHistory(apiBase: string, itemId: number | null) {
  const [history, setHistory] = useState<PriceHistoryDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (itemId === null) {
      setHistory([]);
      return;
    }
    setLoading(true);
    fetch(`${apiBase}/api/items/${itemId}/history?limit=500`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: PriceHistoryDto[]) => {
        setHistory(data.reverse());
        setLoading(false);
      })
      .catch(() => {
        setHistory([]);
        setLoading(false);
      });
  }, [apiBase, itemId]);

  return { history, loading };
}

function TrendIcon({ change }: { change: number }) {
  if (Math.abs(change) < 0.5) return <Minus className="w-3 h-3 text-muted-foreground" />;
  if (change > 0) return <TrendingUp className="w-3 h-3 text-emerald-500" />;
  return <TrendingDown className="w-3 h-3 text-red-500" />;
}

interface ItemStatsRowProps {
  item: ItemDto;
  label: string;
  accent: string;
}

function ItemStatsRow({ item, label, accent }: ItemStatsRowProps) {
  const buyPrice = item.price * (1 + (item.bpd ?? 0));
  const sellPrice = item.price * (1 - (item.spd ?? 0));
  const totalSpread = ((item.bpd ?? 0) + (item.spd ?? 0)) * 100;
  const change = item.change24h ?? 0;

  return (
    <div className={`rounded-lg border p-3 space-y-2 ${accent}`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{label}</span>
        <span className="text-sm font-bold truncate max-w-[140px]">{item.displayName}</span>
      </div>
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div>
          <p className="text-muted-foreground">Base</p>
          <p className="font-semibold">${item.price.toFixed(2)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Buy</p>
          <p className="font-semibold text-emerald-600 dark:text-emerald-400">${buyPrice.toFixed(2)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Sell</p>
          <p className="font-semibold text-red-500">${sellPrice.toFixed(2)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">24h</p>
          <div className="flex items-center gap-1">
            <TrendIcon change={change} />
            <span className={`font-semibold ${change >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
              {change >= 0 ? '+' : ''}{change.toFixed(2)}%
            </span>
          </div>
        </div>
      </div>
      {/* Spread bar */}
      <div>
        <div className="flex justify-between text-[10px] text-muted-foreground mb-1">
          <span>Spread: {totalSpread.toFixed(1)}%</span>
          <span>BPD {((item.bpd ?? 0) * 100).toFixed(1)}% / SPD {((item.spd ?? 0) * 100).toFixed(1)}%</span>
        </div>
        <div className="h-1.5 rounded-full bg-muted overflow-hidden flex">
          <div
            className="bg-emerald-500 h-full"
            style={{ width: `${Math.min(50, ((item.bpd ?? 0) / ((item.bpd ?? 0) + (item.spd ?? 0) || 1)) * 50)}%` }}
          />
          <div className="bg-red-500 h-full" style={{ width: `${Math.min(50, ((item.spd ?? 0) / ((item.bpd ?? 0) + (item.spd ?? 0) || 1)) * 50)}%` }} />
        </div>
      </div>
    </div>
  );
}

interface SpreadComparisonData {
  item: string;
  bpd: number;
  spd: number;
}

interface CompareChartProps {
  items: ItemDto[];
  apiBase: string;
}

export function CompareChart({ items, apiBase }: CompareChartProps) {
  const [itemA, setItemA] = useState<ItemDto | null>(null);
  const [itemB, setItemB] = useState<ItemDto | null>(null);
  const [period, setPeriod] = useState<Period>('1h');
  const [view, setView] = useState<'ratio' | 'spread'>('ratio');

  const { history: historyA, loading: loadingA } = useItemHistory(apiBase, itemA?.id ?? null);
  const { history: historyB, loading: loadingB } = useItemHistory(apiBase, itemB?.id ?? null);

  const ratioData = useMemo(
    () => buildRatioSeries(historyA, historyB, PERIOD_MS[period]),
    [historyA, historyB, period],
  );

  const spreadData: SpreadComparisonData[] = useMemo(() => {
    if (!itemA && !itemB) return [];
    return [
      { item: itemA?.displayName ?? 'Item A', bpd: (itemA?.bpd ?? 0) * 100, spd: (itemA?.spd ?? 0) * 100 },
      { item: itemB?.displayName ?? 'Item B', bpd: (itemB?.bpd ?? 0) * 100, spd: (itemB?.spd ?? 0) * 100 },
    ];
  }, [itemA, itemB]);

  const loading = loadingA || loadingB;
  const bothSelected = itemA !== null && itemB !== null;
  const currentRatio = itemB && itemB.price !== 0 && itemA ? itemA.price / itemB.price : null;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <CardTitle className="text-base">Compare Items</CardTitle>
          <div className="flex items-center gap-2">
            <div className="flex gap-1 bg-muted rounded p-0.5">
              <button
                onClick={() => setView('ratio')}
                className={`flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium transition-colors ${
                  view === 'ratio' ? 'bg-background shadow text-foreground' : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <ArrowLeftRight className="w-3 h-3" /> Ratio
              </button>
              <button
                onClick={() => setView('spread')}
                className={`flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium transition-colors ${
                  view === 'spread' ? 'bg-background shadow text-foreground' : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                Spread
              </button>
            </div>
            <PeriodToggle period={period} onChange={setPeriod} />
          </div>
        </div>
        <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <ItemSelector
            items={items}
            selected={itemA}
            onSelect={setItemA}
            label="Select Item A"
          />
          <ItemSelector
            items={items}
            selected={itemB}
            onSelect={setItemB}
            label="Select Item B"
          />
        </div>
        {bothSelected && currentRatio !== null && (
          <div className="mt-2 flex items-center gap-2 text-sm">
            <span className="text-muted-foreground">Ratio:</span>
            <span className="font-bold text-foreground">{currentRatio.toFixed(4)}</span>
            <span className="text-muted-foreground">
              ({itemA.displayName} / {itemB.displayName})
            </span>
          </div>
        )}
      </CardHeader>

      <CardContent className="space-y-4">
        {/* Side-by-side item stats */}
        {bothSelected && (
          <div className="grid grid-cols-2 gap-3">
            <ItemStatsRow item={itemA!} label="Item A" accent="border-border bg-emerald-500/5 dark:bg-emerald-500/10" />
            <ItemStatsRow item={itemB!} label="Item B" accent="border-border bg-sky-500/5 dark:bg-sky-500/10" />
          </div>
        )}

        {/* Chart area */}
        {!bothSelected ? (
          <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">
            Select two items to compare their price ratio or spread
          </div>
        ) : loading ? (
          <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">
            Loading history...
          </div>
        ) : view === 'ratio' ? (
          ratioData.length === 0 ? (
            <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">
              No overlapping price history
            </div>
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={ratioData}>
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
                    tickFormatter={(v) => Number(v).toFixed(2)}
                  />
                  <Tooltip
                    content={
                      <RatioTooltip
                        nameA={itemA?.displayName ?? 'A'}
                        nameB={itemB?.displayName ?? 'B'}
                      />
                    }
                  />
                  <ReferenceLine y={1} stroke="hsl(var(--muted-foreground))" strokeDasharray="3 3" strokeOpacity={0.5} />
                  <Line
                    type="monotone"
                    dataKey="ratio"
                    stroke="#8b5cf6"
                    strokeWidth={2}
                    dot={false}
                    isAnimationActive={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={spreadData} layout="vertical" margin={{ left: 80, right: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis type="number" stroke="hsl(var(--muted-foreground))" fontSize={11} tickFormatter={(v) => `${v.toFixed(1)}%`} />
                <YAxis type="category" dataKey="item" stroke="hsl(var(--muted-foreground))" fontSize={12} width={75} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '12px',
                  }}
                  formatter={(value, name) => [`${Number(value).toFixed(2)}%`, name === 'bpd' ? 'Buy Premium (BPD)' : 'Sell Discount (SPD)']}
                />
                <Bar dataKey="bpd" stackId="a" fill="#10b981" radius={[0, 0, 0, 0]} />
                <Bar dataKey="spd" stackId="a" fill="#ef4444" radius={[0, 2, 2, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
