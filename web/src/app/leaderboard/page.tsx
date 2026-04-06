'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { LeaderboardTable } from '@/components/leaderboard/leaderboard-table';
import { api } from '@/lib/api';
import type { LeaderboardEntryDto, Stats } from '@/lib/api';

type Period = 'all' | 'day' | 'week' | 'month';

const PERIODS: { key: Period; label: string }[] = [
  { key: 'all', label: 'All Time' },
  { key: 'day', label: 'Today' },
  { key: 'week', label: 'This Week' },
  { key: 'month', label: 'This Month' },
];

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

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold text-foreground">Leaderboard</h2>
          {/* Period filter — shown once data is loaded so table isn't empty */}
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
        <LeaderboardTable entries={entries} />
      </main>
    </div>
  );
}
