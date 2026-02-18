'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { CompareChart } from '@/components/dashboard/compare-chart';
import { api, type ItemDto, type Stats } from '@/lib/api';

export default function ComparePage() {
  const { apiBase } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);

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
        <h2 className="text-2xl font-bold text-foreground">Compare Items</h2>
        <CompareChart items={items} apiBase={apiBase} />
      </main>
    </div>
  );
}
