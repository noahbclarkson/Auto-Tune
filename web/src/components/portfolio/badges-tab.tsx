'use client';

import { useEffect, useState } from 'react';
import { PlayerBadgeDto } from '@/lib/api';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

interface BadgesTabProps {
  playerName: string;
  apiBase: string;
}

interface PlayerBadgesResponse {
  playerName: string;
  earnedCount: number;
  totalPossible: number;
  badges: PlayerBadgeDto[];
}

export function BadgesTab({ playerName, apiBase }: BadgesTabProps) {
  const [data, setData] = useState<PlayerBadgesResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const res = await fetch(`${apiBase}/api/badges/player/${encodeURIComponent(playerName)}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        const json = await res.json();
        
        if (mounted) {
          setData(json);
        }
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Failed to load badges');
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }
    
    load();
    return () => { mounted = false; };
  }, [playerName, apiBase]);

  if (loading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        {Array.from({ length: 10 }).map((_, i) => (
          <Skeleton key={i} className="h-32 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <Card className="border-destructive/50 bg-destructive/5">
        <CardContent className="p-6 text-center text-sm text-destructive">
          {error}
        </CardContent>
      </Card>
    );
  }

  if (!data) return null;

  const earnedCount = data.earnedCount;
  const total = data.totalPossible;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Achievements</h2>
        <div className="text-sm text-muted-foreground">
          <span className="text-foreground font-medium">{earnedCount}</span> / {total} Earned
        </div>
      </div>

      {earnedCount === 0 ? (
        <Card className="bg-muted/50 border-dashed">
          <CardContent className="p-8 text-center">
            <div className="text-4xl mb-3 opacity-50">🏆</div>
            <h3 className="font-medium text-muted-foreground mb-1">No achievements yet</h3>
            <p className="text-sm text-muted-foreground max-w-sm mx-auto">
              Participate in the market by buying, selling, and completing trades to earn badges.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
          {data.badges.map((badge) => (
            <Card key={badge.badgeType} className="border-border/50 bg-card/50 overflow-hidden transition-colors hover:border-border hover:bg-card">
              <CardContent className="p-4 flex flex-col items-center text-center h-full">
                <div className="text-3xl mb-2 drop-shadow-sm" title={badge.badgeType}>
                  {getBadgeEmoji(badge.badgeType)}
                </div>
                <h3 className="font-medium text-sm leading-tight mb-1">{badge.displayName}</h3>
                <p className="text-[11px] text-muted-foreground leading-tight mb-3 flex-grow">
                  {badge.description}
                </p>
                <div className="text-[10px] uppercase font-semibold tracking-wider text-primary/80 bg-primary/10 px-2 py-0.5 rounded-full mt-auto">
                  {badge.earnedAt ? new Date(badge.earnedAt).toLocaleDateString() : 'Earned'}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function getBadgeEmoji(type: string): string {
  const map: Record<string, string> = {
    FIRST_SALE: '📜',
    LOAN_SHARK: '🦈',
    MARKET_MAKER: '💹',
    HOARDER: '📦',
    TREND_SPOTTER: '🔭',
    STABLE_HAND: '🤝',
    BIG_SPENDER: '💰',
    DIVERSIFIED: '🎨',
    CENTURION: '⭐',
    FIRST_BUYER: '🛒',
    LOAN_TAKER: '📄'
  };
  return map[type] || '🏆';
}
