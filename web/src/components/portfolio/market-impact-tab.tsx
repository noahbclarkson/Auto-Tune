'use client';

import { useEffect, useState } from 'react';
import { api, type PlayerMarketImpactDto, type MarketImpactItemDto } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatPercent } from '@/lib/format';
import { TrendingUp, TrendingDown, Minus, BarChart2, Loader2 } from 'lucide-react';

interface MarketImpactTabProps {
  playerName: string;
  apiBase: string;
}

function impactLabel(pct: number): { text: string; cls: string; Icon: typeof TrendingUp } {
  if (pct >= 10) return { text: 'Market Mover', cls: 'text-violet-400', Icon: TrendingUp };
  if (pct >= 5) return { text: 'Active Trader', cls: 'text-emerald-400', Icon: TrendingUp };
  if (pct >= 1) return { text: 'Regular', cls: 'text-sky-400', Icon: Minus };
  return { text: 'Newcomer', cls: 'text-muted-foreground', Icon: Minus };
}

function WeeklyImpactCard({ data }: { data: PlayerMarketImpactDto }) {
  const weekly = impactLabel(data.weeklyImpactPct);
  const Monthly = impactLabel(data.monthlyImpactPct);
  const WeeklyIcon = weekly.Icon;
  const MonthlyIcon = Monthly.Icon;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      {/* Weekly Impact Score */}
      <Card className="relative overflow-hidden">
        <CardContent className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs text-muted-foreground mb-1">Weekly Impact</p>
              <p className={`text-2xl font-bold font-mono ${weekly.cls}`}>
                {data.weeklyImpactPct >= 0.01
                  ? (data.weeklyImpactPct >= 0 ? '+' : '') + formatPercent(data.weeklyImpactPct)
                  : '—'}
              </p>
              <p className={`text-xs mt-0.5 ${weekly.cls}`}>{weekly.text}</p>
            </div>
            <div className={`p-2 rounded-lg bg-primary/10`}>
              <WeeklyIcon className={`h-5 w-5 ${weekly.cls}`} />
            </div>
          </div>
          {data.weeklyRank > 0 && (
            <p className="text-xs text-muted-foreground mt-2">
              Rank #{data.weeklyRank} this week
            </p>
          )}
        </CardContent>
      </Card>

      {/* Monthly Impact Score */}
      <Card className="relative overflow-hidden">
        <CardContent className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs text-muted-foreground mb-1">Monthly Impact</p>
              <p className={`text-2xl font-bold font-mono ${Monthly.cls}`}>
                {data.monthlyImpactPct >= 0.01
                  ? (data.monthlyImpactPct >= 0 ? '+' : '') + formatPercent(data.monthlyImpactPct)
                  : '—'}
              </p>
              <p className={`text-xs mt-0.5 ${Monthly.cls}`}>{Monthly.text}</p>
            </div>
            <div className={`p-2 rounded-lg bg-primary/10`}>
              <MonthlyIcon className={`h-5 w-5 ${Monthly.cls}`} />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Impact explanation */}
      <Card className="relative overflow-hidden">
        <CardContent className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs text-muted-foreground mb-1">What is this?</p>
              <p className="text-sm text-foreground leading-relaxed">
                Your share of total market trading volume, weighted by how much you moved prices.
                Top 5% of traders qualify as Market Movers.
              </p>
            </div>
            <div className="p-2 rounded-lg bg-primary/10">
              <BarChart2 className="h-5 w-5 text-primary" />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function TopImpactItemsTable({ items }: { items: MarketImpactItemDto[] }) {
  if (!items.length) return null;

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">Your Most Impactful Items</CardTitle>
        <p className="text-xs text-muted-foreground">
          Items where your trading had the biggest effect on market prices
        </p>
      </CardHeader>
      <CardContent className="px-4 pb-4">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left py-2 pr-4 font-medium text-muted-foreground">Item</th>
                <th className="text-right py-2 pr-4 font-medium text-muted-foreground">Your Volume</th>
                <th className="text-right py-2 pr-4 font-medium text-muted-foreground">Market Share</th>
                <th className="text-right py-2 pr-4 font-medium text-muted-foreground">Price Δ</th>
                <th className="text-right py-2 font-medium text-muted-foreground">Your Impact</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {items.map((item) => {
                const changeDir = item.priceChangePct > 0 ? 'up' : item.priceChangePct < 0 ? 'down' : 'flat';
                const changeColor = changeDir === 'up'
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : changeDir === 'down'
                  ? 'text-red-500 dark:text-red-400'
                  : 'text-muted-foreground';
                const ChangeIcon = changeDir === 'up' ? TrendingUp : changeDir === 'down' ? TrendingDown : Minus;

                return (
                  <tr key={item.material} className="hover:bg-muted/30 transition-colors">
                    <td className="py-2.5 pr-4">
                      <div className="font-medium text-foreground">{item.itemName}</div>
                      <div className="text-xs text-muted-foreground font-mono">{item.material}</div>
                    </td>
                    <td className="text-right py-2.5 pr-4 text-foreground font-mono">
                      {item.playerVolume.toLocaleString()}
                    </td>
                    <td className="text-right py-2.5 pr-4">
                      <div className="flex items-center justify-end gap-2">
                        <div className="w-16 h-1.5 rounded-full bg-muted overflow-hidden">
                          <div
                            className="h-full rounded-full bg-primary"
                            style={{ width: `${Math.min(100, item.playerSharePct)}%` }}
                          />
                        </div>
                        <span className="text-xs text-muted-foreground font-mono w-10 text-right">
                          {item.playerSharePct.toFixed(1)}%
                        </span>
                      </div>
                    </td>
                    <td className={`text-right py-2.5 pr-4 font-mono font-medium ${changeColor}`}>
                      <span className="inline-flex items-center gap-1">
                        <ChangeIcon className="h-3 w-3" />
                        {item.priceChangePct >= 0 ? '+' : ''}{formatPercent(item.priceChangePct)}
                      </span>
                    </td>
                    <td className="text-right py-2.5 font-mono font-bold text-foreground">
                      {item.playerImpactPct >= 0.01
                        ? (item.playerImpactPct >= 0 ? '+' : '') + formatPercent(item.playerImpactPct)
                        : '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

export function MarketImpactTab({ playerName, apiBase }: MarketImpactTabProps) {
  const [data, setData] = useState<PlayerMarketImpactDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const result = await api.portfolio.marketImpact(apiBase, playerName);
        if (!cancelled) setData(result);
      } catch (err) {
        if (!cancelled) {
          const msg = err instanceof Error ? err.message : 'Failed to load market impact';
          // 404 = endpoint not yet implemented — show coming-soon state
          if (msg.includes('404')) {
            setError(null);
            setData(null);
          } else {
            setError(msg);
          }
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [apiBase, playerName]);

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-3 gap-4">
          {[0, 1, 2].map(i => (
            <Card key={i}>
              <CardContent className="p-4">
                <div className="h-16 bg-muted animate-pulse rounded" />
              </CardContent>
            </Card>
          ))}
        </div>
        <div className="h-48 bg-muted animate-pulse rounded-xl" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
        {error}
      </div>
    );
  }

  if (!data) {
    // Coming soon — endpoint not yet implemented in Java backend
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { label: 'Weekly Impact', placeholder: '—' },
            { label: 'Monthly Impact', placeholder: '—' },
            { label: 'What is this?', placeholder: 'Your market footprint' },
          ].map((card) => (
            <Card key={card.label}>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground mb-1">{card.label}</p>
                <p className="text-2xl font-bold font-mono text-muted-foreground">{card.placeholder}</p>
              </CardContent>
            </Card>
          ))}
        </div>
        <Card>
          <CardContent className="py-8 text-center">
            <div className="flex flex-col items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center">
                <BarChart2 className="h-6 w-6 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-medium text-foreground mb-1">Market Impact Score — Coming Soon</p>
                <p className="text-xs text-muted-foreground max-w-xs">
                  See how much your trading moves prices. Based on your volume share per item × item price change.
                  Updated weekly.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <WeeklyImpactCard data={data} />
      <TopImpactItemsTable items={data.topItems} />
    </div>
  );
}
