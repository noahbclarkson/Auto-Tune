'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { BadgeShowcase } from '@/components/badges/badge-showcase';
import { api, type Stats } from '@/lib/api';
import { Award } from 'lucide-react';

export default function BadgesPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const statsData = await api.stats(apiBase);
      setStats(statsData);
    } catch {
      // silently fail — badge showcase works without stats
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

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

        <BadgeShowcase />
      </main>
    </div>
  );
}
