'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { LeaderboardTable } from '@/components/leaderboard/leaderboard-table';
import { api, type Stats, type LeaderboardEntryDto } from '@/lib/api';

export default function LeaderboardPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [entries, setEntries] = useState<LeaderboardEntryDto[]>([]);

  const fetchData = useCallback(async () => {
    try {
      const [statsData, leaderboardData] = await Promise.all([
        api.stats(apiBase),
        api.leaderboard(apiBase, 20).catch(() => []),
      ]);
      setStats(statsData);
      setEntries(leaderboardData as LeaderboardEntryDto[]);
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
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <h2 className="text-2xl font-bold text-foreground">Leaderboard</h2>
        <LeaderboardTable entries={entries} />
      </main>
    </div>
  );
}
