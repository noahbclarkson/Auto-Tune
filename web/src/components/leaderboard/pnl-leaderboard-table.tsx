'use client';

import { useState, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatLargeCurrency } from '@/lib/format';
import type { LeaderboardPnlEntry } from '@/lib/api';
import { TrendingUp, TrendingDown } from 'lucide-react';

type PnlFilter = 'all' | 'buyers' | 'sellers';

interface PnlLeaderboardTableProps {
  entries: LeaderboardPnlEntry[];
  loading: boolean;
}

function RankBadge({ rank }: { rank: number }) {
  if (rank === 1) return <span className="text-lg">&#x1F947;</span>;
  if (rank === 2) return <span className="text-lg">&#x1F948;</span>;
  if (rank === 3) return <span className="text-lg">&#x1F949;</span>;
  return <span className="text-muted-foreground">{rank}</span>;
}

export function PnlLeaderboardTable({ entries, loading }: PnlLeaderboardTableProps) {
  const [filter, setFilter] = useState<PnlFilter>('all');

  const filtered = useMemo(() => {
    if (filter === 'buyers') return entries.filter(e => e.realizedPnl < 0);
    if (filter === 'sellers') return entries.filter(e => e.realizedPnl > 0);
    return entries;
  }, [entries, filter]);

  if (loading) {
    return (
      <Card>
        <CardContent className="p-6">
          <div className="space-y-3">
            {Array.from({ length: 10 }).map((_, i) => (
              <div key={i} className="h-10 rounded bg-muted animate-pulse" />
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (entries.length === 0) {
    return (
      <Card>
        <CardContent className="p-6 text-center">
          <p className="text-muted-foreground">No P&amp;L data for this period.</p>
          <p className="text-xs text-muted-foreground mt-1">
            P&amp;L is calculated from completed buy/sell round-trips using average-cost accounting.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <CardTitle className="text-sm font-semibold">Profit &amp; Loss — {filter === 'all' ? 'All Traders' : filter === 'buyers' ? 'Net Buyers (losses)' : 'Net Sellers (profits)'}</CardTitle>
          <div className="flex gap-1 bg-muted rounded-lg p-1">
            {(['all', 'sellers', 'buyers'] as PnlFilter[]).map(f => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${
                  filter === f
                    ? 'bg-emerald-600 text-white'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                {f === 'all' ? 'All' : f === 'sellers' ? 'Profits ↑' : 'Losses ↓'}
              </button>
            ))}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left pb-2 pr-4 text-xs font-medium text-muted-foreground w-8">#</th>
                <th className="text-left pb-2 pr-4 text-xs font-medium text-muted-foreground">Player</th>
                <th className="text-right pb-2 pr-4 text-xs font-medium text-muted-foreground">Realized P&amp;L</th>
                <th className="text-right pb-2 text-xs font-medium text-muted-foreground">Transactions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(0, 50).map((entry) => {
                const isProfit = entry.realizedPnl >= 0;
                return (
                  <tr
                    key={`${entry.rank}-${entry.username}`}
                    className="border-b border-border/50 last:border-0 hover:bg-muted/30 transition-colors"
                  >
                    <td className="py-2.5 pr-4">
                      <RankBadge rank={entry.rank} />
                    </td>
                    <td className="py-2.5 pr-4">
                      <span className="font-medium text-foreground">{entry.username}</span>
                    </td>
                    <td className="py-2.5 pr-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        {isProfit
                          ? <TrendingUp className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                          : <TrendingDown className="w-3.5 h-3.5 text-red-500 shrink-0" />
                        }
                        <span className={`font-semibold tabular-nums ${isProfit ? 'text-emerald-500' : 'text-red-500'}`}>
                          {isProfit ? '+' : ''}{formatLargeCurrency(entry.realizedPnl)}
                        </span>
                      </div>
                    </td>
                    <td className="py-2.5 text-right text-muted-foreground tabular-nums">
                      {entry.transactionCount.toLocaleString()}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {filtered.length > 50 && (
          <p className="text-xs text-muted-foreground mt-3 text-center">
            Showing top 50 of {filtered.length} traders
          </p>
        )}
      </CardContent>
    </Card>
  );
}
