'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { PriceChart } from '@/components/dashboard/price-chart';
import { ItemDetailHeader } from '@/components/items/item-detail-header';
import { ItemStatsRow } from '@/components/items/item-stats-row';
import { ItemTransactionsTable } from '@/components/items/item-transactions-table';
import { SimilarItems } from '@/components/items/similar-items';
import { PriceAttribution } from '@/components/items/price-attribution';
import { ArrowLeft } from 'lucide-react';
import { api, type ItemDto, type Stats, type ItemTrendDto } from '@/lib/api';

export default function ItemDetailPage() {
  const { apiBase } = useAppContext();
  const [item, setItem] = useState<ItemDto | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);
  const [trend, setTrend] = useState<ItemTrendDto | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    if (!id) {
      setLoading(false);
      return;
    }

    try {
      const [itemData, statsData, trendData] = await Promise.all([
        api.items.get(apiBase, Number(id)),
        api.stats(apiBase),
        api.items.trend(apiBase, Number(id)).catch(() => null),
      ]);
      setItem(itemData);
      setStats(statsData);
      setTrend(trendData);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-6 py-6">
          <p className="text-muted-foreground">Loading...</p>
        </main>
      </div>
    );
  }

  if (!item) {
    return (
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-6 py-6">
          <p className="text-muted-foreground">Item not found</p>
          <a href="/items/" className="text-primary hover:underline text-sm mt-2 inline-block">
            Back to items
          </a>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <a
          href="/items/"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to items
        </a>

        <ItemDetailHeader item={item} trend={trend} />
        <ItemStatsRow item={item} />
        <PriceChart
          item={item}
          apiBase={apiBase}
          expanded={true}
          onToggleExpand={() => {}}
        />
        <PriceAttribution item={item} apiBase={apiBase} />
        <SimilarItems apiBase={apiBase} currentItem={item} />
        <ItemTransactionsTable apiBase={apiBase} itemId={item.id} />
      </main>
    </div>
  );
}
