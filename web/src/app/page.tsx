'use client';

import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
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
  const { apiBase, livePrices, isWsConnected } = useAppContext();
  const [items, setItems] = useState<ItemDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [gdp, setGdp] = useState<number | null>(null);
  const [inflation, setInflation] = useState<number | null>(null);
  const [trends, setTrends] = useState<TrendDto[]>([]);
  const [history, setHistory] = useState<EconomySnapshotDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  // Tracks which item IDs recently received a live WebSocket price update
  const [liveFlash, setLiveFlash] = useState<Set<number>>(new Set());
  const liveFlashTimer = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  // Apply live WebSocket price updates on top of polled data
  useEffect(() => {
    if (livePrices.size === 0) return;
    setItems((prev) => {
      if (!prev.length) return prev;
      let changed = false;
      const next = prev.map((item) => {
        const livePrice = livePrices.get(item.id);
        if (livePrice !== undefined && livePrice !== item.price) {
          changed = true;
          // Compute updated buyPrice/sellPrice using current spread
          const mid = livePrice;
          const halfSpread = (item.bpd + item.spd) / 2;
          const newBuyPrice = halfSpread > 0 ? mid / (1 - item.spd) : mid;
          const newSellPrice = halfSpread > 0 ? mid * (1 - item.spd) : mid;
          return { ...item, price: livePrice, buyPrice: newBuyPrice, sellPrice: newSellPrice };
        }
        return item;
      });
      return changed ? next : prev;
    });
    // Flash recently-updated items
    livePrices.forEach((_, id) => {
      setLiveFlash((prev) => new Set(prev).add(id));
      if (liveFlashTimer.current[id]) clearTimeout(liveFlashTimer.current[id]);
      liveFlashTimer.current[id] = setTimeout(() => {
        setLiveFlash((prev) => {
          const next = new Set(prev);
          next.delete(id);
          return next;
        });
      }, 1500);
    });
  }, [livePrices]);

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
                  <div>
                    <CardTitle className="text-base">Top Movers</CardTitle>
                    <p className="text-xs text-muted-foreground mt-0.5">Items with the largest 24h price change</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {isWsConnected && (
                      <Badge variant="outline" className="gap-1 text-[10px] border-emerald-600/40 text-emerald-500">
                        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                        Live
                      </Badge>
                    )}
                    <span className="text-xs text-muted-foreground">{topMovers.length} items</span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                {topMovers.length === 0 ? (
                  <p className="text-sm text-muted-foreground py-4 text-center">No data yet</p>
                ) : (
                  <div className="space-y-0.5">
                    {topMovers.map((item) => {
                      const trend = trends.find((t) => t.itemId === item.id);
                      const spreadPct = ((item.bpd + item.spd) * 100).toFixed(1);
                      const changeDir = item.change24h > 0 ? 'up' : item.change24h < 0 ? 'down' : 'flat';
                      const changeMag = Math.abs(item.change24h);
                      // Visual bar: log scale for the change magnitude
                      const magWidth = Math.min(100, Math.log1p(changeMag * 100) * 40);
                      const barColor = changeDir === 'up'
                        ? 'bg-emerald-500'
                        : changeDir === 'down'
                        ? 'bg-red-400'
                        : 'bg-muted';

                      return (
                        <a
                          key={item.id}
                          href={`/items/detail/?id=${item.id}`}
                          className={`flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors group ${liveFlash.has(item.id) ? 'bg-emerald-950/30 ring-1 ring-emerald-800/40' : 'hover:bg-muted/60'}`}
                        >
                          {/* Change magnitude bar */}
                          <div className="shrink-0 w-1.5 h-10 rounded-full bg-muted overflow-hidden self-center">
                            <div
                              className={`w-full rounded-full transition-all ${barColor}`}
                              style={{ height: `${magWidth}%`, marginTop: `${Math.max(0, 50 - magWidth / 2)}%` }}
                            />
                          </div>

                          {/* Change % */}
                          <div className={`shrink-0 w-14 text-right ${changeDir === 'up' ? 'text-emerald-600 dark:text-emerald-400' : changeDir === 'down' ? 'text-red-500 dark:text-red-400' : 'text-muted-foreground'}`}>
                            <p className="text-sm font-bold leading-none">
                              {item.change24h > 0 ? '+' : ''}{formatPercent(item.change24h)}
                            </p>
                            <p className="text-[10px] mt-0.5 opacity-60">24h</p>
                          </div>

                          {/* Item name + section + prices */}
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <span className="font-medium text-foreground group-hover:text-primary transition-colors truncate max-w-[10rem]">
                                {item.displayName}
                              </span>
                              <Badge variant="secondary" className="capitalize text-[10px] shrink-0">
                                {item.section}
                              </Badge>
                              {trend && (
                                <Badge
                                  variant={
                                    trend.direction === 'UP' ? 'success' :
                                    trend.direction === 'DOWN' ? 'destructive' : 'secondary'
                                  }
                                  className="text-[10px] shrink-0"
                                >
                                  {trend.direction}
                                </Badge>
                              )}
                            </div>
                            <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                              <span className="text-emerald-600 dark:text-emerald-400">BUY {formatCurrency(item.buyPrice)}</span>
                              <span>→</span>
                              <span className="text-amber-600 dark:text-amber-400">SELL {formatCurrency(item.sellPrice)}</span>
                            </div>
                          </div>

                          {/* Spread chip */}
                          <div className="shrink-0">
                            <div className={`inline-flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-medium ${
                              parseFloat(spreadPct) > 12
                                ? 'bg-red-500/10 text-red-500 dark:text-red-400'
                                : parseFloat(spreadPct) > 6
                                ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                                : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                            }`}>
                              <span>±{spreadPct}%</span>
                              <span className="opacity-50">spread</span>
                            </div>
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
