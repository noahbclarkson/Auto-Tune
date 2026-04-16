'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { LeaderboardTable } from '@/components/leaderboard/leaderboard-table';

import { api } from '@/lib/api';
import { formatLargeCurrency } from '@/lib/format';
import type { LeaderboardEntryDto, Stats } from '@/lib/api';
import { TrendingUp, TrendingDown, Users, BarChart2, Award, ArrowUpDown } from 'lucide-react';

type Period = 'all' | 'day' | 'week' | 'month';

const PERIODS: { key: Period; label: string }[] = [
  { key: 'all', label: 'All Time' },
  { key: 'day', label: 'Today' },
  { key: 'week', label: 'This Week' },
  { key: 'month', label: 'This Month' },
];

type PeriodInfo = {
  topBuyer: LeaderboardEntryDto | null;
  topSeller: LeaderboardEntryDto | null;
  totalVolume: number;
  totalTransactions: number;
  traderCount: number;
  avgTradeSize: number;
};

function StatCard({
  icon: Icon,
  label,
  value,
  sub,
  accent,
}: {
  icon: typeof TrendingUp;
  label: string;
  value: string;
  sub?: string;
  accent?: 'default' | 'up' | 'down';
}) {
  const accentColor = accent === 'up'
    ? 'text-emerald-600 dark:text-emerald-400'
    : accent === 'down'
    ? 'text-sky-600 dark:text-sky-400'
    : 'text-foreground';
  return (
    <div className="flex items-start gap-3 rounded-lg border bg-card px-4 py-3">
      <div className="mt-0.5 rounded-md bg-primary/10 border border-primary/20 p-1.5">
        <Icon className={`h-3.5 w-3.5 ${accentColor}`} />
      </div>
      <div className="min-w-0">
        <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">{label}</p>
        <p className={`text-sm font-bold font-mono mt-0.5 truncate ${accentColor}`}>{value}</p>
        {sub && <p className="text-[10px] text-muted-foreground mt-0.5 truncate">{sub}</p>}
      </div>
    </div>
  );
}

export default function LeaderboardPage() {
  const { apiBase } = useAppContext();
  const [entries, setEntries] = useState<LeaderboardEntryDto[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState<Period>(() => {
    if (typeof window !== 'undefined') {
      return (localStorage.getItem('autotune_leaderboard_period') as Period) ?? 'all';
    }
    return 'all';
  });

  const fetchData = useCallback(async () => {
    if (!apiBase) return;
    setLoading(true);
    try {
      const [statsData, leaderboardData] = await Promise.all([
        api.stats(apiBase),
        api.leaderboard(apiBase, 100, period),
      ]);
      setStats(statsData);
      setEntries(leaderboardData);
    } catch {
      // silently fail — LeaderboardTable renders empty state
    } finally {
      setLoading(false);
    }
  }, [apiBase, period]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  function handlePeriodChange(newPeriod: Period) {
    setPeriod(newPeriod);
    if (typeof window !== 'undefined') {
      localStorage.setItem('autotune_leaderboard_period', newPeriod);
    }
  }

  const periodInfo = useMemo<PeriodInfo>(() => {
    if (entries.length === 0) {
      return { topBuyer: null, topSeller: null, totalVolume: 0, totalTransactions: 0, traderCount: 0, avgTradeSize: 0 };
    }
    const topBuyer = [...entries].sort((a, b) => b.totalBought - a.totalBought)[0];
    const topSeller = [...entries].sort((a, b) => b.totalSold - a.totalSold)[0];
    const totalVolume = entries.reduce((s, e) => s + e.totalTraded, 0);
    const totalTransactions = entries.reduce((s, e) => s + e.transactionCount, 0);
    const avgTradeSize = totalTransactions > 0 ? totalVolume / totalTransactions : 0;
    return { topBuyer, topSeller, totalVolume, totalTransactions, traderCount: entries.length, avgTradeSize };
  }, [entries]);

  const periodLabel = PERIODS.find(p => p.key === period)?.label ?? 'All Time';

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h2 className="text-2xl font-bold text-foreground">Leaderboard</h2>
            {!loading && entries.length > 0 && (
              <span className="rounded-full bg-primary/10 border border-primary/20 px-2 py-0.5 text-xs text-primary font-medium">
                {periodLabel}
              </span>
            )}
          </div>
          {!loading && entries.length > 0 && (
            <div className="flex gap-1 bg-muted rounded-lg p-1">
              {PERIODS.map(p => (
                <button
                  key={p.key}
                  onClick={() => handlePeriodChange(p.key)}
                  className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
                    period === p.key
                      ? 'bg-emerald-600 text-white'
                      : 'text-muted-foreground hover:text-foreground hover:bg-emerald-950/50'
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Stats summary — only show when data is loaded */}
        {!loading && entries.length > 0 && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <StatCard
              icon={BarChart2}
              label="Total Volume"
              value={formatLargeCurrency(periodInfo.totalVolume)}
              sub={`${entries.length} traders`}
            />
            <StatCard
              icon={TrendingUp}
              label="Top Buyer"
              value={periodInfo.topBuyer?.username ?? '—'}
              sub={periodInfo.topBuyer ? `Bought ${formatLargeCurrency(periodInfo.topBuyer.totalBought)}` : undefined}
              accent="up"
            />
            <StatCard
              icon={TrendingDown}
              label="Top Seller"
              value={periodInfo.topSeller?.username ?? '—'}
              sub={periodInfo.topSeller ? `Sold ${formatLargeCurrency(periodInfo.topSeller.totalSold)}` : undefined}
              accent="down"
            />
            <StatCard
              icon={ArrowUpDown}
              label="Transactions"
              value={periodInfo.totalTransactions.toLocaleString()}
              sub={`Avg ${formatLargeCurrency(periodInfo.avgTradeSize)}/tx`}
            />
            <StatCard
              icon={Users}
              label="Active Traders"
              value={String(periodInfo.traderCount)}
              sub="ranked by volume"
            />
            <StatCard
              icon={Award}
              label="#1 Trader"
              value={entries[0]?.username ?? '—'}
              sub={entries[0] ? `${formatLargeCurrency(entries[0].totalTraded)} total` : undefined}
            />
          </div>
        )}

        {/* Loading skeleton for stats */}
        {loading && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-[68px] rounded-lg border bg-muted animate-pulse" />
            ))}
          </div>
        )}

        <LeaderboardTable entries={entries} />
      </main>
    </div>
  );
}
