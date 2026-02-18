'use client';

import { useState, useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { formatLargeCurrency } from '@/lib/format';
import type { LeaderboardEntryDto } from '@/lib/api';

type FilterMode = 'all' | 'buyers' | 'sellers';

interface LeaderboardTableProps {
  entries: LeaderboardEntryDto[];
}

function RankBadge({ rank }: { rank: number }) {
  if (rank === 1) return <span className="text-lg">&#x1F947;</span>;
  if (rank === 2) return <span className="text-lg">&#x1F948;</span>;
  if (rank === 3) return <span className="text-lg">&#x1F949;</span>;
  return <span className="text-muted-foreground">{rank}</span>;
}

export function LeaderboardTable({ entries }: LeaderboardTableProps) {
  const [filter, setFilter] = useState<FilterMode>('all');

  const sorted = useMemo(() => {
    const copy = [...entries];
    if (filter === 'buyers') {
      copy.sort((a, b) => b.totalBought - a.totalBought);
    } else if (filter === 'sellers') {
      copy.sort((a, b) => b.totalSold - a.totalSold);
    }
    return copy.map((e, i) => ({ ...e, rank: i + 1 }));
  }, [entries, filter]);

  const filters: { key: FilterMode; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'buyers', label: 'Top Buyers' },
    { key: 'sellers', label: 'Top Sellers' },
  ];

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Top Traders</CardTitle>
          <div className="flex gap-1">
            {filters.map((f) => (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                  filter === f.key
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {sorted.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No trading data yet</p>
        ) : (
          <div className="rounded-md border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground w-16">Rank</th>
                  <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Player</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Total Traded</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Buy Volume</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Sell Volume</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Transactions</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((entry) => (
                  <tr key={entry.username + entry.rank} className="border-b border-border last:border-0">
                    <td className="px-3 py-2.5 text-center">
                      <RankBadge rank={entry.rank} />
                    </td>
                    <td className="px-3 py-2.5 font-medium text-foreground">{entry.username}</td>
                    <td className="px-3 py-2.5 text-right font-medium text-foreground">
                      {formatLargeCurrency(entry.totalTraded)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-emerald-600 dark:text-emerald-400">
                      {formatLargeCurrency(entry.totalBought)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-amber-600 dark:text-amber-400">
                      {formatLargeCurrency(entry.totalSold)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-muted-foreground">{entry.transactionCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
