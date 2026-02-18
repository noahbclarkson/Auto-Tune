'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { ItemTable } from '@/components/dashboard/item-table';
import { api, type ItemDto, type Stats, type TrendDto } from '@/lib/api';

export default function ItemsPage() {
  const { apiBase } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);

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
        <ItemTable
          items={items}
          trends={trends}
          linkToDetail={true}
          pageSize={25}
        />
      </main>
    </div>
  );
}
