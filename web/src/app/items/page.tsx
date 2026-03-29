'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { ItemTable } from '@/components/dashboard/item-table';
import { ItemGrid } from '@/components/dashboard/item-grid';
import { Card, CardContent } from '@/components/ui/card';
import { api, type ItemDto, type Stats, type TrendDto } from '@/lib/api';
import { formatPercent } from '@/lib/format';
import { LayoutGrid, List } from 'lucide-react';

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

export default function ItemsPage() {
  const { apiBase } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);
  const [view, setView] = useState<'table' | 'grid'>('table');

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
    } catch {
      // silently fail
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
        <ItemsStatsBar items={items} />

        {/* View toggle */}
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm text-muted-foreground">
            {items.length} items across {new Set(items.map(i => i.section)).size} sections
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
            items={items}
            trends={trends}
            linkToDetail={true}
            pageSize={25}
          />
        ) : (
          <ItemGrid items={items} trends={trends} linkToDetail={true} />
        )}
      </main>
    </div>
  );
}
