'use client';

import { PlayerBadgesResponse, PlayerBadgeDto } from '@/lib/api';
import { Award, Clock, ChevronRight } from 'lucide-react';

function rarityColor(rarity: string): string {
  switch (rarity) {
    case 'common':    return 'text-green-400';
    case 'uncommon':  return 'text-blue-400';
    case 'rare':      return 'text-purple-400';
    case 'epic':      return 'text-pink-400';
    case 'legendary': return 'text-yellow-400';
    default:          return 'text-muted-foreground';
  }
}

function rarityBg(rarity: string): string {
  switch (rarity) {
    case 'common':    return 'bg-green-400/10 border-green-400/20';
    case 'uncommon':  return 'bg-blue-400/10 border-blue-400/20';
    case 'rare':      return 'bg-purple-400/10 border-purple-400/20';
    case 'epic':      return 'bg-pink-400/10 border-pink-400/20';
    case 'legendary': return 'bg-yellow-400/10 border-yellow-400/20';
    default:          return 'bg-muted border-muted';
  }
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const days = Math.floor(diff / 86_400_000);
  if (days === 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 30) return `${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo ago`;
  return `${Math.floor(months / 12)}y ago`;
}

interface AchievementTimelineProps {
  response: PlayerBadgesResponse;
}

function TimelineCard({ badge }: { badge: PlayerBadgeDto }) {
  return (
    <div className="flex items-start gap-4 p-4 rounded-lg border bg-card hover:bg-card/80 transition-colors">
      {/* Icon */}
      <div className={`flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center text-2xl border ${rarityBg(badge.rarity)}`}>
        <span>{badge.material === 'SHOP_USE' ? '🛒' : badge.material === 'NETHER_STAR' ? '⭐' : badge.material === 'GOLD_BLOCK' ? '🏆' : badge.material === 'EMERALD' ? '💎' : badge.material === 'PAPER' ? '📄' : '🎖'}</span>
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`font-semibold ${rarityColor(badge.rarity)}`}>
            {badge.displayName}
          </span>
          <span className="text-xs px-1.5 py-0.5 rounded border bg-muted/50 text-muted-foreground capitalize">
            {badge.rarity}
          </span>
        </div>
        <p className="text-sm text-muted-foreground mt-0.5">{badge.description}</p>
        <div className="flex items-center gap-1 mt-2 text-xs text-muted-foreground">
          <Clock className="h-3 w-3" />
          <span>Earned {formatDate(badge.earnedAt)}</span>
          <span className="text-muted-foreground/50">·</span>
          <span>{timeAgo(badge.earnedAt)}</span>
        </div>
      </div>

      <ChevronRight className="h-4 w-4 text-muted-foreground flex-shrink-0 mt-4" />
    </div>
  );
}

export function AchievementTimeline({ response }: AchievementTimelineProps) {
  const { badges, earnedCount, totalPossible, playerName } = response;
  const sorted = [...badges].sort(
    (a, b) => new Date(b.earnedAt).getTime() - new Date(a.earnedAt).getTime()
  );

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Award className="h-5 w-5 text-primary" />
        <div>
          <h3 className="font-semibold text-foreground">
            {playerName}&apos;s Achievements
          </h3>
          <p className="text-sm text-muted-foreground">
            {earnedCount} of {totalPossible} badges earned
          </p>
        </div>
      </div>

      {/* Progress bar */}
      <div className="h-2 bg-muted rounded-full overflow-hidden">
        <div
          className="h-full bg-primary rounded-full transition-all"
          style={{ width: `${(earnedCount / totalPossible) * 100}%` }}
        />
      </div>

      {/* Timeline */}
      {sorted.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground">
          <Award className="h-8 w-8 mx-auto mb-2 opacity-50" />
          <p className="text-sm">No badges earned yet. Start trading!</p>
        </div>
      ) : (
        <div className="space-y-3">
          {sorted.map((badge) => (
            <TimelineCard key={badge.badgeType} badge={badge} />
          ))}
        </div>
      )}
    </div>
  );
}
