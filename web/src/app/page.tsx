'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { StatsCards } from '@/components/dashboard/stats-cards';
import { EconomyPanel } from '@/components/dashboard/economy-panel';
import { TransactionFeed } from '@/components/dashboard/transaction-feed';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api, type ItemDto, type Stats, type TrendDto } from '@/lib/api';
import { formatCurrency, formatPercent } from '@/lib/format';

export default function Home() {
  const { apiBase } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [gdp, setGdp] = useState<number | null>(null);
  const [inflation, setInflation] = useState<number | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [itemsData, statsData, gdpData, inflationData, trendsData] = await Promise.all([
        api.items.list(apiBase),
        api.stats(apiBase),
        api.economy.gdp(apiBase).catch(() => null),
        api.economy.inflation(apiBase).catch(() => null),
        api.economy.trends(apiBase).catch(() => []),
      ]);

      setItems(itemsData);
      setStats(statsData);
      setError(null);

      if (gdpData) setGdp(gdpData.gdp);
      if (inflationData) setInflation(inflationData.averagePriceChange);
      setTrends(trendsData as TrendDto[]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to connect to server');
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const topMovers = useMemo(() => {
    return [...items]
      .sort((a, b) => Math.abs(b.change24h) - Math.abs(a.change24h))
      .slice(0, 5);
  }, [items]);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />

      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        {error && (
          <div className="rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
            Unable to load market data: {error}
          </div>
        )}

        <StatsCards
          totalItems={stats?.totalItems ?? 0}
          onlinePlayers={stats?.onlinePlayers ?? 0}
          gdp={gdp}
          inflation={inflation}
        />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <TransactionFeed apiBase={apiBase} />

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-base">Top Movers (24h)</CardTitle>
              </CardHeader>
              <CardContent>
                {topMovers.length === 0 ? (
                  <p className="text-sm text-muted-foreground py-4 text-center">No data yet</p>
                ) : (
                  <div className="rounded-md border border-border">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-border bg-muted/50">
                          <th className="px-3 py-2 text-left font-medium text-muted-foreground">Item</th>
                          <th className="px-3 py-2 text-right font-medium text-muted-foreground">Price</th>
                          <th className="px-3 py-2 text-right font-medium text-muted-foreground">Change</th>
                          <th className="px-3 py-2 text-center font-medium text-muted-foreground">Trend</th>
                        </tr>
                      </thead>
                      <tbody>
                        {topMovers.map((item) => {
                          const trend = trends.find((t) => t.itemId === item.id);
                          return (
                            <tr key={item.id} className="border-b border-border last:border-0">
                              <td className="px-3 py-2">
                                <a
                                  href={`/items/detail/?id=${item.id}`}
                                  className="font-medium text-foreground hover:text-primary transition-colors"
                                >
                                  {item.displayName}
                                </a>
                              </td>
                              <td className="px-3 py-2 text-right text-muted-foreground">
                                {formatCurrency(item.price)}
                              </td>
                              <td
                                className={`px-3 py-2 text-right font-medium ${
                                  item.change24h > 0
                                    ? 'text-emerald-600 dark:text-emerald-400'
                                    : item.change24h < 0
                                    ? 'text-red-500 dark:text-red-400'
                                    : 'text-muted-foreground'
                                }`}
                              >
                                {formatPercent(item.change24h)}
                              </td>
                              <td className="px-3 py-2 text-center">
                                {trend && (
                                  <Badge
                                    variant={
                                      trend.direction === 'UP'
                                        ? 'success'
                                        : trend.direction === 'DOWN'
                                        ? 'destructive'
                                        : 'secondary'
                                    }
                                    className="text-[10px]"
                                  >
                                    {trend.direction}
                                  </Badge>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            <EconomyPanel apiBase={apiBase} />
          </div>
        </div>
      </main>
    </div>
  );
}
