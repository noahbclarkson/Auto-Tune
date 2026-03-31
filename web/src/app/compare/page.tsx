'use client';

import { useEffect, useState, useCallback, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { CompareChart } from '@/components/dashboard/compare-chart';
import { api, type ItemDto, type Stats } from '@/lib/api';

function ComparePageInner() {
  const { apiBase } = useAppContext();
  const searchParams = useSearchParams();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);

  const initialItemA = searchParams.get('a');
  const initialItemB = searchParams.get('b');

  const fetchData = useCallback(async () => {
    try {
      const [itemsData, statsData] = await Promise.all([
        api.items.list(apiBase),
        api.stats(apiBase),
      ]);
      setItems(itemsData);
      setStats(statsData);
    } catch {
      // silently fail
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <CompareChart
          items={items}
          apiBase={apiBase}
          initialItemA={initialItemA}
          initialItemB={initialItemB}
        />
      </main>
    </div>
  );
}

export default function ComparePage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
          <div className="h-8 w-48 animate-pulse rounded bg-muted" />
          <div className="h-96 animate-pulse rounded border border-border" />
        </main>
      </div>
    }>
      <ComparePageInner />
    </Suspense>
  );
}
