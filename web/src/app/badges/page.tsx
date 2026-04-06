'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { BadgeShowcase } from '@/components/badges/badge-showcase';
import { AchievementTimeline } from '@/components/badges/achievement-timeline';
import { api, type Stats, type PlayerBadgesResponse } from '@/lib/api';
import { Award, Search, Loader2 } from 'lucide-react';

export default function BadgesPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [searchName, setSearchName] = useState('');
  const [playerName, setPlayerName] = useState<string | null>(null);
  const [playerBadges, setPlayerBadges] = useState<PlayerBadgesResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = useCallback(async () => {
    try {
      const data = await api.stats(apiBase);
      setStats(data);
    } catch {
      // silently fail — badge showcase works without stats
    }
  }, [apiBase]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const handleSearch = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    const name = searchName.trim();
    if (!name) return;
    setLoading(true);
    setError(null);
    setPlayerBadges(null);
    try {
      const data = await api.badges.player(apiBase, name);
      setPlayerBadges(data);
      setPlayerName(name);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load badges');
    } finally {
      setLoading(false);
    }
  }, [searchName, apiBase]);

  const handleClear = useCallback(() => {
    setSearchName('');
    setPlayerName(null);
    setPlayerBadges(null);
    setError(null);
  }, []);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-8 space-y-6">

        {/* Page header */}
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-primary/10 border border-primary/20">
            <Award className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-foreground">Badge Showcase</h2>
            <p className="text-sm text-muted-foreground">Achievements earned through market activity</p>
          </div>
        </div>

        {/* Player search */}
        <form onSubmit={handleSearch} className="flex items-center gap-2">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={searchName}
              onChange={e => setSearchName(e.target.value)}
              placeholder="Search a player..."
              className="w-full pl-9 pr-4 py-2 rounded-lg border bg-background text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
            />
          </div>
          <button
            type="submit"
            disabled={loading || !searchName.trim()}
            className="px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            View Badges
          </button>
          {playerName && (
            <button
              type="button"
              onClick={handleClear}
              className="px-3 py-2 rounded-lg border text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
            >
              Clear
            </button>
          )}
        </form>

        {/* Error state */}
        {error && (
          <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            {error}
          </div>
        )}

        {/* Player achievement timeline */}
        {playerBadges && (
          <div className="rounded-xl border bg-card p-5">
            <AchievementTimeline response={playerBadges} />
          </div>
        )}

        {/* Badge showcase */}
        <BadgeShowcase />

      </main>
    </div>
  );
}
