'use client';

import { Suspense, useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { ItemTable } from '@/components/dashboard/item-table';
import { ItemGrid } from '@/components/dashboard/item-grid';
import { Card, CardContent } from '@/components/ui/card';
import { ApiErrorBanner } from '@/components/ui/api-error-banner';
import { api, type ItemDto, type Stats, type TrendDto } from '@/lib/api';
import { formatPercent } from '@/lib/format';
import { LayoutGrid, List, TrendingUp, TrendingDown, ArrowUp, ArrowDown } from 'lucide-react';

function ItemsStatsBar({ items }: { items: ItemDto[] }) {
  const stats = useMemo(() => {
    if (!items.length) return null;
    const sections = new Set(items.map((i) => i.section)).size;
    const avgSpread = items.reduce((s, i) => s + (i.bpd + i.spd) * 100, 0) / items.length;
    const movers = [...items].sort((a, b) => Math.abs(b.change24h) - Math.abs(a.change24h));
    const biggestMover = movers[0];
    const avg24h = items.reduce((s, i) => s + i.change24h, 0) / items.length;
    return { sections, avgSpread, biggestMover, avg24h };
  }, [items]);

  if (!stats) return null;

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5">
      <Card>
        <CardContent className="p-3">
          <p className="text-xs text-muted-foreground mb-0.5">Items</p>
          <p className="text-lg font-bold text-foreground">{items.length.toLocaleString()}</p>
          <p className="text-xs text-muted-foreground">{stats.sections} sections</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-3">
          <p className="text-xs text-muted-foreground mb-0.5">Avg Spread</p>
          <p className={`text-lg font-bold font-mono ${stats.avgSpread < 5 ? 'text-emerald-600 dark:text-emerald-400' : stats.avgSpread < 10 ? 'text-amber-600 dark:text-amber-400' : 'text-red-500 dark:text-red-400'}`}>
            ±{stats.avgSpread.toFixed(1)}%
          </p>
          <p className="text-xs text-muted-foreground">buy–sell gap</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-3">
          <p className="text-xs text-muted-foreground mb-0.5">Market 24h</p>
          <p className={`text-lg font-bold font-mono ${stats.avg24h > 0 ? 'text-emerald-600 dark:text-emerald-400' : stats.avg24h < 0 ? 'text-red-500 dark:text-red-400' : 'text-muted-foreground'}`}>
            {stats.avg24h > 0 ? '+' : ''}{formatPercent(stats.avg24h)}
          </p>
          <p className="text-xs text-muted-foreground">avg price change</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-3">
          <p className="text-xs text-muted-foreground mb-0.5">Most Volatile</p>
          <p className="text-sm font-bold text-foreground truncate">{stats.biggestMover?.displayName ?? '—'}</p>
          <p className={`text-xs font-mono ${stats.biggestMover && stats.biggestMover.change24h > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-500 dark:text-red-400'}`}>
            {stats.biggestMover && (stats.biggestMover.change24h > 0 ? '+' : '') + formatPercent(stats.biggestMover.change24h)}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function TopMoversSection({ items }: { items: ItemDto[] }) {
  const movers = useMemo(() => {
    if (items.length === 0) return { gainers: [], losers: [] };
    const sorted = [...items].sort((a, b) => b.change24h - a.change24h);
    return {
      gainers: sorted.filter(i => i.change24h > 0).slice(0, 5),
      losers: sorted.filter(i => i.change24h < 0).slice(0, 5),
    };
  }, [items]);

  if (items.length === 0) return null;

  const maxAbs = Math.max(
    ...movers.gainers.map(i => i.change24h),
    ...movers.losers.map(i => Math.abs(i.change24h)),
    0.01,
  );

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-5">
      {/* Top Gainers */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp className="w-4 h-4 text-emerald-500" />
            <p className="text-xs font-semibold text-emerald-400 uppercase tracking-wide">Top Gainers</p>
          </div>
          <div className="space-y-2">
            {movers.gainers.length === 0 && (
              <p className="text-xs text-muted-foreground py-2">No gainers in the last 24h</p>
            )}
            {movers.gainers.map((item) => {
              const barWidth = (item.change24h / maxAbs) * 100;
              return (
                <div key={item.id} className="flex items-center gap-2">
                  <div className="w-20 text-xs text-foreground truncate font-medium" title={item.displayName}>
                    {item.displayName}
                  </div>
                  <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                      style={{ width: `${barWidth}%` }}
                    />
                  </div>
                  <div className="w-14 text-right">
                    <span className="text-xs font-mono text-emerald-500 flex items-center justify-end gap-0.5">
                      <ArrowUp className="w-2.5 h-2.5" />
                      {formatPercent(item.change24h)}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {/* Top Losers */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 mb-3">
            <TrendingDown className="w-4 h-4 text-red-500" />
            <p className="text-xs font-semibold text-red-400 uppercase tracking-wide">Top Losers</p>
          </div>
          <div className="space-y-2">
            {movers.losers.length === 0 && (
              <p className="text-xs text-muted-foreground py-2">No losers in the last 24h</p>
            )}
            {movers.losers.map((item) => {
              const barWidth = (Math.abs(item.change24h) / maxAbs) * 100;
              return (
                <div key={item.id} className="flex items-center gap-2">
                  <div className="w-20 text-xs text-foreground truncate font-medium" title={item.displayName}>
                    {item.displayName}
                  </div>
                  <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-red-500 rounded-full transition-all duration-500"
                      style={{ width: `${barWidth}%` }}
                    />
                  </div>
                  <div className="w-14 text-right">
                    <span className="text-xs font-mono text-red-400 flex items-center justify-end gap-0.5">
                      <ArrowDown className="w-2.5 h-2.5" />
                      {formatPercent(Math.abs(item.change24h))}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export default function ItemsPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-background"><div className="mx-auto max-w-7xl px-6 py-6"><p className="text-muted-foreground">Loading...</p></div></div>}>
      <ItemsPageContent />
    </Suspense>
  );
}

function ItemsPageContent() {
  const { apiBase } = useAppContext();
  const searchParams = useSearchParams();
  const sectionFilter = searchParams.get('section');
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);
  const [view, setView] = useState<'table' | 'grid'>('table');
  const [error, setError] = useState<string | null>(null);

  const displayedItems = useMemo(() => {
    if (!sectionFilter) return items;
    return items.filter((i) => i.section === sectionFilter);
  }, [items, sectionFilter]);

  const fetchData = useCallback(async () => {
    try {
      const [itemsData, statsData, trendsData] = await Promise.all([
        api.items.list(apiBase),
        api.stats(apiBase),
        api.economy.trends(apiBase).catch(() => []),
      ]);
      setItems(itemsData);
      setStats(statsData);
      setTrends(trendsData as TrendDto[]);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load items');
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6">
        {error && (
          <ApiErrorBanner
            message={`Unable to load items: ${error}`}
            apiBase={apiBase}
            onRetry={fetchData}
          />
        )}
        {!sectionFilter && <ItemsStatsBar items={items} />}
        {sectionFilter && (
          <div className="mb-4 flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Showing:</span>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 border border-primary/30 px-3 py-1 text-sm font-medium text-primary">
              {sectionFilter}
            </span>
            <a href="/items/" className="text-xs text-muted-foreground hover:text-foreground underline">Clear filter</a>
          </div>
        )}
        {!sectionFilter && <TopMoversSection items={items} />}

        {/* View toggle */}
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm text-muted-foreground">
            {displayedItems.length} {sectionFilter ? `item${displayedItems.length !== 1 ? 's' : ''} in ${sectionFilter}` : `items across ${new Set(items.map(i => i.section)).size} sections`}
          </p>
          <div className="flex items-center gap-1 border border-border rounded-lg p-0.5">
            <button
              onClick={() => setView('table')}
              className={`rounded-md p-1.5 transition-colors ${
                view === 'table' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
              title="Table view"
            >
              <List className="h-4 w-4" />
            </button>
            <button
              onClick={() => setView('grid')}
              className={`rounded-md p-1.5 transition-colors ${
                view === 'grid' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              }`}
              title="Grid view"
            >
              <LayoutGrid className="h-4 w-4" />
            </button>
          </div>
        </div>

        {view === 'table' ? (
          <ItemTable
            items={displayedItems}
            trends={trends}
            linkToDetail={true}
            pageSize={25}
          />
        ) : (
          <ItemGrid items={displayedItems} trends={trends} linkToDetail={true} />
        )}
      </main>
    </div>
  );
}
