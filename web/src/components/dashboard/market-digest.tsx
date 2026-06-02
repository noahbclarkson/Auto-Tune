'use client';

import { useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { TrendingUp, TrendingDown, Activity, Zap, BarChart2 } from 'lucide-react';
import type { ItemDto, TrendDto } from '@/lib/api';
import { formatPercent } from '@/lib/format';

interface MarketDigestProps {
  items: ItemDto[];
  trends: TrendDto[];
}

function DigestRow({
  icon,
  label,
  children,
  accent = 'gray',
}: {
  icon: React.ReactNode;
  label: string;
  children: React.ReactNode;
  accent?: 'emerald' | 'rose' | 'sky' | 'amber' | 'gray';
}) {
  const accentClasses = {
    emerald: 'border-emerald-900/50 bg-emerald-950/10',
    rose: 'border-rose-900/50 bg-rose-950/10',
    sky: 'border-sky-900/50 bg-sky-950/10',
    amber: 'border-amber-900/50 bg-amber-950/10',
    gray: 'border-gray-800 bg-gray-900/30',
  };
  return (
    <div className={`rounded-lg border p-3 ${accentClasses[accent]}`}>
      <div className="flex items-center gap-1.5 mb-2">
        <span className="text-muted-foreground">{icon}</span>
        <span className="text-xs text-muted-foreground uppercase tracking-wider font-medium">{label}</span>
      </div>
      {children}
    </div>
  );
}

function ItemPill({
  item,
  change,
  accent,
}: {
  item: ItemDto;
  change: number;
  accent: 'emerald' | 'rose' | 'sky';
}) {
  const cls = accent === 'emerald' ? 'text-emerald-400' : accent === 'rose' ? 'text-rose-400' : 'text-sky-400';
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-1.5 min-w-0">
        <span className="text-xs text-gray-300 truncate">{item.displayName}</span>
        <Badge variant="secondary" className="text-[10px] shrink-0 capitalize">
          {item.section}
        </Badge>
      </div>
      <div className={`text-xs font-mono font-semibold shrink-0 ml-2 ${cls}`}>
        {change > 0 ? '+' : ''}{formatPercent(change)}
      </div>
    </div>
  );
}

export function MarketDigest({ items, trends }: MarketDigestProps) {
  const digest = useMemo(() => {
    if (!items.length) return null;

    // Top gainers — highest positive 24h change
    const gainers = [...items]
      .filter((i) => i.change24h > 0)
      .sort((a, b) => b.change24h - a.change24h)
      .slice(0, 4);

    // Top losers — lowest negative 24h change
    const losers = [...items]
      .filter((i) => i.change24h < 0)
      .sort((a, b) => a.change24h - b.change24h)
      .slice(0, 4);

    // Most volatile — largest absolute 24h change
    const volatile = [...items]
      .sort((a, b) => Math.abs(b.change24h) - Math.abs(a.change24h))
      .slice(0, 3);

    // Volume proxy: use spread health as proxy (tight spread = high activity)
    // Items with low BPD/SPD are actively traded
    const activeItems = [...items]
      .filter((i) => i.bpd < 0.15 && i.spd < 0.15)
      .sort((a, b) => (a.bpd + a.spd) - (b.bpd + b.spd))
      .slice(0, 4);

    // Spread analysis: wide vs tight
    const avgBpd = items.reduce((s, i) => s + i.bpd, 0) / items.length;
    const spreadHealth =
      avgBpd < 0.03 ? 'very tight' :
      avgBpd < 0.06 ? 'healthy' :
      avgBpd < 0.10 ? 'wide' : 'very wide';

    // Overall momentum
    const upCount = items.filter((i) => i.change24h > 0).length;
    const downCount = items.filter((i) => i.change24h < 0).length;
    const flatCount = items.filter((i) => i.change24h === 0).length;
    const momentum =
      upCount > downCount * 1.5 ? 'bullish' :
      downCount > upCount * 1.5 ? 'bearish' :
      'neutral';

    // Hottest trend from trends API
    const hottestTrend = trends.length
      ? trends.reduce((best, t) =>
          !best || Math.abs(t.percentChange) > Math.abs(best.percentChange) ? t : best,
        null as TrendDto | null
      )
      : null;

    // Compute a simple "volume score" from spread tightness
    const volumeScore = Math.max(0, 1 - avgBpd * 10);

    return {
      gainers,
      losers,
      volatile,
      activeItems,
      avgBpd,
      spreadHealth,
      momentum,
      upCount,
      downCount,
      flatCount,
      hottestTrend,
      volumeScore,
    };
  }, [items, trends]);

  if (!digest) return null;

  const momentumColor =
    digest.momentum === 'bullish' ? 'emerald' :
    digest.momentum === 'bearish' ? 'rose' : 'sky';

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <BarChart2 className="w-4 h-4 text-emerald-400" />
            <CardTitle className="text-base">Market Digest</CardTitle>
          </div>
          <Badge
            variant={momentumColor === 'emerald' ? 'default' : momentumColor === 'rose' ? 'destructive' : 'secondary'}
            className={`text-[10px] gap-1 ${
              momentumColor === 'emerald' ? 'bg-emerald-900/50 text-emerald-400 border-emerald-800' :
              momentumColor === 'rose' ? 'bg-rose-900/50 text-rose-400 border-rose-800' :
              'bg-sky-900/50 text-sky-400 border-sky-800'
            }`}
          >
            {digest.momentum === 'bullish' ? <TrendingUp className="w-3 h-3" /> :
             digest.momentum === 'bearish' ? <TrendingDown className="w-3 h-3" /> :
             <Activity className="w-3 h-3" />}
            {digest.upCount}↑ {digest.flatCount}→ {digest.downCount}↓
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
          {/* Bullish movers */}
          {digest.gainers.length > 0 && (
            <DigestRow icon={<TrendingUp className="w-3 h-3" />} label="Top Gainers" accent="emerald">
              <div className="space-y-1.5">
                {digest.gainers.map((item) => (
                  <ItemPill key={item.id} item={item} change={item.change24h} accent="emerald" />
                ))}
              </div>
            </DigestRow>
          )}

          {/* Bearish movers */}
          {digest.losers.length > 0 && (
            <DigestRow icon={<TrendingDown className="w-3 h-3" />} label="Top Losers" accent="rose">
              <div className="space-y-1.5">
                {digest.losers.map((item) => (
                  <ItemPill key={item.id} item={item} change={item.change24h} accent="rose" />
                ))}
              </div>
            </DigestRow>
          )}

          {/* Most volatile */}
          {digest.volatile.length > 0 && (
            <DigestRow icon={<Zap className="w-3 h-3" />} label="Most Volatile" accent="amber">
              <div className="space-y-1.5">
                {digest.volatile.map((item) => (
                  <ItemPill
                    key={item.id}
                    item={item}
                    change={item.change24h}
                    accent={item.change24h > 0 ? 'emerald' : 'rose'}
                  />
                ))}
              </div>
            </DigestRow>
          )}

          {/* Spread health + volume */}
          <DigestRow icon={<Activity className="w-3 h-3" />} label="Market Health" accent="sky">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-gray-500">Avg spread</span>
                <span className={`text-xs font-mono font-semibold ${
                  digest.avgBpd < 0.03 ? 'text-emerald-400' :
                  digest.avgBpd < 0.06 ? 'text-sky-400' :
                  'text-amber-400'
                }`}>
                  ±{(digest.avgBpd * 100).toFixed(1)}%
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-gray-500">Spread health</span>
                <span className={`text-xs font-semibold capitalize ${
                  digest.spreadHealth === 'very tight' || digest.spreadHealth === 'healthy'
                    ? 'text-emerald-400'
                    : digest.spreadHealth === 'wide'
                    ? 'text-amber-400'
                    : 'text-rose-400'
                }`}>
                  {digest.spreadHealth}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-xs text-gray-500">Activity level</span>
                <span className={`text-xs font-semibold capitalize ${
                  digest.volumeScore > 0.7 ? 'text-emerald-400' :
                  digest.volumeScore > 0.4 ? 'text-amber-400' : 'text-gray-500'
                }`}>
                  {digest.volumeScore > 0.7 ? 'high' : digest.volumeScore > 0.4 ? 'moderate' : 'low'}
                </span>
              </div>
              <div className="mt-1">
                <div className="h-1.5 rounded-full bg-gray-800 overflow-hidden">
                  <div
                    className="h-full rounded-full bg-sky-500/60"
                    style={{ width: `${(1 - digest.avgBpd * 5) * 100}%` }}
                  />
                </div>
              </div>
            </div>
          </DigestRow>
        </div>

        {/* Narrative summary */}
        <div className="rounded-lg border border-gray-800 bg-gray-900/30 p-3">
          {digest.momentum === 'bullish' && (
            <p className="text-xs text-gray-400 leading-relaxed">
              Most items are up today —{' '}
              {digest.gainers[0] && (
                <span className="text-emerald-400 font-medium">{digest.gainers[0].displayName}</span>
              )}
              {digest.gainers[1] && digest.gainers[2] && (
                <span> leading with <span className="text-emerald-400 font-medium">{formatPercent(digest.gainers[0].change24h)}</span></span>
              )}.
              {digest.spreadHealth === 'healthy' ? ' Spreads are healthy — the market is clearing well.' :
               digest.spreadHealth === 'wide' ? ' Wide spreads suggest caution in large trades.' :
               ' Very tight spreads indicate an efficient, high-activity market.'}
            </p>
          )}
          {digest.momentum === 'bearish' && (
            <p className="text-xs text-gray-400 leading-relaxed">
              Most items are down today —{' '}
              {digest.losers[0] && (
                <span className="text-rose-400 font-medium">{digest.losers[0].displayName}</span>
              )}
              {digest.losers[1] && (
                <span> dropping <span className="text-rose-400 font-medium">{formatPercent(digest.losers[0].change24h)}</span></span>
              )}.
              {digest.spreadHealth === 'wide' ? ' Wide spreads reflect uncertainty in the market.' :
               ' Spreads are reasonable despite the downturn.'}
            </p>
          )}
          {digest.momentum === 'neutral' && (
            <p className="text-xs text-gray-400 leading-relaxed">
              Market is flat today —{' '}
              {digest.upCount > digest.downCount
                ? 'slightly bullish with more gainers than losers.'
                : digest.downCount > digest.upCount
                ? 'slightly bearish with more decliners than advancers.'
                : 'evenly balanced between gainers and decliners.'}
              {' '}Spread health is{' '}
              <span className={digest.avgBpd < 0.05 ? 'text-emerald-400' : 'text-amber-400'}>
                {digest.spreadHealth}
              </span>.
            </p>
          )}
          {digest.hottestTrend && Math.abs(digest.hottestTrend.percentChange) > 3 && (
            <p className="text-xs text-gray-500 mt-1.5">
              Notable: <span className="text-amber-400">{digest.hottestTrend.displayName}</span> has moved{' '}
              <span className={digest.hottestTrend.direction === 'UP' ? 'text-emerald-400' : 'text-rose-400'}>
                {formatPercent(digest.hottestTrend.percentChange)} {digest.hottestTrend.direction.toLowerCase()}
              </span>
            </p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
