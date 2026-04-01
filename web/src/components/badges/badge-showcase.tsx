'use client';

import { Award, Star } from 'lucide-react';

interface BadgeDefinition {
  name: string;
  description: string;
  material: string;
  color: string;
  threshold?: number;
  rarity: 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary';
  emoji: string;
  hint?: string;
}

const BADGES: BadgeDefinition[] = [
  {
    name: 'First Sale',
    description: 'Sold your first item on the market',
    material: 'PAPER',
    color: '#22c55e',
    rarity: 'common',
    emoji: '📄',
  },
  {
    name: 'First Buyer',
    description: 'Bought your first item from the market',
    material: 'SHOP_USE',
    color: '#3b82f6',
    rarity: 'common',
    emoji: '🛒',
  },
  {
    name: 'Loan Taker',
    description: 'Took out your first loan',
    material: 'WRITABLE_BOOK',
    color: '#6366f1',
    rarity: 'common',
    emoji: '📘',
  },
  {
    name: 'Centurion',
    description: 'Completed 100 or more transactions',
    material: 'NETHER_STAR',
    color: '#e5e7eb',
    rarity: 'uncommon',
    emoji: '⭐',
    threshold: 100,
    hint: '100 transactions',
  },
  {
    name: 'Big Spender',
    description: 'Completed a single transaction worth 1,000,000 or more',
    material: 'GOLD_BLOCK',
    color: '#f97316',
    rarity: 'rare',
    emoji: '🏆',
    threshold: 1_000_000,
    hint: '1M+ in one transaction',
  },
  {
    name: 'Market Maker',
    description: 'Traded across 10 or more different items',
    material: 'EMERALD',
    color: '#15803d',
    rarity: 'uncommon',
    emoji: '💎',
    threshold: 10,
    hint: '10+ different items',
  },
  {
    name: 'Hoarder',
    description: 'Held 50 or more items in your autosell inventory at once',
    material: 'CHEST',
    color: '#eab308',
    rarity: 'uncommon',
    emoji: '📦',
    threshold: 50,
    hint: '50+ autosell items',
  },
  {
    name: 'Loan Shark',
    description: 'Fully repaid a loan of 100,000 or more',
    material: 'GOLD_INGOT',
    color: '#eab308',
    rarity: 'rare',
    emoji: '🥇',
    threshold: 100_000,
    hint: 'Repay 100K+ loan',
  },
  {
    name: 'Trend Spotter',
    description: 'Had a price alert fire with the price moving as predicted',
    material: 'CLOCK',
    color: '#06b6d4',
    rarity: 'rare',
    emoji: '⏰',
    hint: 'Alert fires in your favor',
  },
  {
    name: 'Diversified',
    description: 'Held positions in 5 or more different item sections simultaneously',
    material: 'RAINBOW_BANNER_PATTERN',
    color: '#0ea5e9',
    rarity: 'rare',
    emoji: '🌈',
    threshold: 5,
    hint: '5+ item sections held',
  },
  {
    name: 'Stable Hand',
    description: 'Remained active for 7 days without ever defaulting on a loan',
    material: 'FLOWER_BANNER_PATTERN',
    color: '#c084fc',
    rarity: 'epic',
    emoji: '🌸',
    threshold: 7,
    hint: '7+ days, no defaults',
  },
];

const RARITY_CONFIG = {
  common: { label: 'Common', color: 'text-muted-foreground', bg: 'bg-muted', border: 'border-border', icon: null },
  uncommon: { label: 'Uncommon', color: 'text-green-600 dark:text-green-400', bg: 'bg-green-500/10', border: 'border-green-500/30', icon: null },
  rare: { label: 'Rare', color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-500/10', border: 'border-blue-500/30', icon: null },
  epic: { label: 'Epic', color: 'text-purple-600 dark:text-purple-400', bg: 'bg-purple-500/10', border: 'border-purple-500/30', icon: null },
  legendary: { label: 'Legendary', color: 'text-orange-500 dark:text-orange-400', bg: 'bg-orange-500/10', border: 'border-orange-500/30', icon: null },
};

function BadgeCard({ badge }: { badge: BadgeDefinition }) {
  const rarity = RARITY_CONFIG[badge.rarity];

  return (
    <div className={`flex flex-col rounded-xl border ${rarity.border} ${rarity.bg} p-5 gap-3 transition-all hover:scale-[1.02] hover:shadow-md`}>
      {/* Header: emoji + rarity */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-3xl">{badge.emoji}</span>
          <div>
            <p className="text-sm font-semibold" style={{ color: badge.color }}>{badge.name}</p>
            <span className={`text-[10px] font-medium uppercase tracking-wider ${rarity.color}`}>
              {rarity.label}
            </span>
          </div>
        </div>
      </div>

      {/* Description */}
      <p className="text-xs text-muted-foreground leading-relaxed">
        {badge.description}
      </p>

      {/* Threshold hint */}
      {badge.hint && (
        <div className="mt-auto pt-2 border-t border-border/50 flex items-center gap-1.5">
          <Award className="h-3 w-3 text-muted-foreground/60" />
          <span className="text-[10px] text-muted-foreground">{badge.hint}</span>
        </div>
      )}
    </div>
  );
}

export function BadgeShowcase() {
  return (
    <div className="space-y-6">
      {/* Section header */}
      <div>
        <p className="text-sm text-muted-foreground mt-1">
          Earn badges by actively participating in the server economy. Badges are awarded automatically — keep trading!
        </p>
      </div>

      {/* Rarity legend */}
      <div className="flex flex-wrap gap-3">
        {(Object.entries(RARITY_CONFIG) as [BadgeDefinition['rarity'], typeof RARITY_CONFIG['common']][]).map(([key, config]) => (
          <div key={key} className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${config.bg} ${config.color}`}>
            <span>{config.label}</span>
          </div>
        ))}
      </div>

      {/* Badge grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {BADGES.map((badge) => (
          <BadgeCard key={badge.name} badge={badge} />
        ))}
      </div>

      {/* In-game note */}
      <div className="rounded-xl border border-border bg-muted/30 p-4 flex items-start gap-3">
        <Star className="h-4 w-4 text-primary mt-0.5 shrink-0" />
        <div className="text-sm text-muted-foreground">
          <span className="font-medium text-foreground">Earn badges in-game. </span>
          Use <code className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded">/badges</code> to view your earned badges, or browse this page to see what&apos;s possible.
          Badges also appear in the <code className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded">/shop</code> GUI next to your name.
        </div>
      </div>
    </div>
  );
}
