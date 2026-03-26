'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { StatsCards } from '@/components/dashboard/stats-cards';
import { EconomyPanel } from '@/components/dashboard/economy-panel';
import { TransactionFeed } from '@/components/dashboard/transaction-feed';
import { MarketHealthBar } from '@/components/dashboard/market-health-bar';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api, type ItemDto, type Stats, type TrendDto, type EconomySnapshotDto } from '@/lib/api';
import { formatCurrency, formatPercent } from '@/lib/format';

export default function Home() {
  const { apiBase } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [gdp, setGdp] = useState<number | null>(null);
  const [inflation, setInflation] = useState<number | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);
  const [history, setHistory] = useState<EconomySnapshotDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [itemsData, statsData, gdpData, inflationData, trendsData, historyData] = await Promise.all([
        api.items.list(apiBase),
        api.stats(apiBase),
        api.economy.gdp(apiBase).catch(() => null),
        api.economy.inflation(apiBase).catch(() => null),
        api.economy.trends(apiBase).catch(() => []),
        api.economy.history(apiBase, 30).catch(() => []),
      ]);

      setItems(itemsData);
      setStats(statsData);
      setError(null);

      if (gdpData) setGdp(gdpData.gdp);
      if (inflationData) setInflation(inflationData.averagePriceChange);
      setTrends(trendsData as TrendDto[]);
      setHistory((historyData as EconomySnapshotDto[]).reverse());
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
          gdpHistory={history}
          inflationHistory={history}
        />

        <MarketHealthBar
          items={items}
          gdp={gdp}
          inflation={inflation}
          onlinePlayers={stats?.onlinePlayers ?? 0}
        />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <TransactionFeed apiBase={apiBase} />

            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base">Top Movers (24h)</CardTitle>
                  <span className="text-xs text-muted-foreground">{topMovers.length} items</span>
                </div>
              </CardHeader>
              <CardContent>
                {topMovers.length === 0 ? (
                  <p className="text-sm text-muted-foreground py-4 text-center">No data yet</p>
                ) : (
                  <div className="space-y-1">
                    {topMovers.map((item) => {
                      const trend = trends.find((t) => t.itemId === item.id);
                      const spreadPct = ((item.bpd + item.spd) * 100).toFixed(1);
                      const spreadWidth = Math.min(100, ((item.bpd + item.spd) / 0.5) * 100);
                      const changeDir = item.change24h > 0 ? 'up' : item.change24h < 0 ? 'down' : 'flat';
                      const changeColor = item.change24h > 0
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : item.change24h < 0
                        ? 'text-red-500 dark:text-red-400'
                        : 'text-muted-foreground';
                      return (
                        <a
                          key={item.id}
                          href={`/items/detail/?id=${item.id}`}
                          className="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-muted/60 transition-colors group"
                        >
                          {/* Change direction indicator */}
                          <div className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold ${
                            changeDir === 'up' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' :
                            changeDir === 'down' ? 'bg-red-500/10 text-red-500 dark:text-red-400' :
                            'bg-muted text-muted-foreground'
                          }`}>
                            {changeDir === 'up' ? '▲' : changeDir === 'down' ? '▼' : '—'}
                          </div>

                          {/* Item name + section */}
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <span className="font-medium text-foreground group-hover:text-primary transition-colors truncate">
                                {item.displayName}
                              </span>
                              <Badge variant="secondary" className="capitalize text-[10px] shrink-0">
                                {item.section}
                              </Badge>
                            </div>
                            <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                              <span className="text-emerald-600 dark:text-emerald-400">BUY {formatCurrency(item.buyPrice)}</span>
                              <span>·</span>
                              <span className="text-amber-600 dark:text-amber-400">SELL {formatCurrency(item.sellPrice)}</span>
                            </div>
                          </div>

                          {/* Spread bar */}
                          <div className="shrink-0 w-20 hidden sm:block">
                            <div className="flex items-center justify-between text-[10px] text-muted-foreground mb-1">
                              <span>Spread</span>
                              <span>{spreadPct}%</span>
                            </div>
                            <div className="h-1 rounded-full bg-muted overflow-hidden">
                              <div
                                className="h-full rounded-full bg-primary/60"
                                style={{ width: `${spreadWidth}%` }}
                              />
                            </div>
                          </div>

                          {/* Change + trend */}
                          <div className="shrink-0 text-right w-16">
                            <p className={`text-sm font-bold ${changeColor}`}>
                              {formatPercent(item.change24h)}
                            </p>
                            {trend && (
                              <Badge
                                variant={
                                  trend.direction === 'UP' ? 'success' :
                                  trend.direction === 'DOWN' ? 'destructive' : 'secondary'
                                }
                                className="text-[10px] mt-0.5"
                              >
                                {trend.direction}
                              </Badge>
                            )}
                          </div>
                        </a>
                      );
                    })}
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
