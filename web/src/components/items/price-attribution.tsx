'use client';

import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ChevronDown, ChevronUp, TrendingUp, TrendingDown, Minus, Activity, Zap, BarChart2 } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { api, type PriceChangeDto, type ItemDto } from '@/lib/api';

const BADGE_STYLES: Record<PriceChangeDto['attributionKey'], { bg: string; text: string; label: string }> = {
  NORMAL: { bg: 'bg-emerald-100 dark:bg-emerald-900/40', text: 'text-emerald-700 dark:text-emerald-300', label: 'Normal' },
  EVENT: { bg: 'bg-purple-100 dark:bg-purple-900/40', text: 'text-purple-700 dark:text-purple-300', label: 'Event' },
  VOLUME: { bg: 'bg-orange-100 dark:bg-orange-900/40', text: 'text-orange-700 dark:text-orange-300', label: 'Volume' },
  TREND: { bg: 'bg-blue-100 dark:bg-blue-900/40', text: 'text-blue-700 dark:text-blue-300', label: 'Trend' },
  STABLE: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', label: 'Stable' },
};

function AttributionBadge({ key_, label }: { key_: PriceChangeDto['attributionKey']; label: string }) {
  const style = BADGE_STYLES[key_];
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${style.bg} ${style.text}`}>
      {key_ === 'EVENT' && <Zap className="h-3 w-3" />}
      {key_ === 'VOLUME' && <Activity className="h-3 w-3" />}
      {key_ === 'TREND' && (label.includes('+') ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />)}
      {key_ === 'STABLE' && <Minus className="h-3 w-3" />}
      {key_ === 'NORMAL' && <BarChart2 className="h-3 w-3" />}
      {label}
    </span>
  );
}

function AttributionRow({ item, first }: { item: PriceChangeDto; first: boolean }) {
  const style = BADGE_STYLES[item.attributionKey];
  const pctColor = item.percentChange > 0 ? 'text-emerald-600 dark:text-emerald-400' : item.percentChange < 0 ? 'text-red-500 dark:text-red-400' : 'text-muted-foreground';

  return (
    <div className={`flex items-start gap-3 py-2.5 px-1 ${!first ? 'border-t border-border/50' : ''}`}>
      {/* Left: badge + time */}
      <div className="flex flex-col items-center gap-1 min-w-[72px]">
        <AttributionBadge key_={item.attributionKey} label={style.label} />
        <span className="text-[10px] text-muted-foreground whitespace-nowrap">
          {formatDistanceToNow(new Date(item.timestamp), { addSuffix: true })}
        </span>
      </div>

      {/* Center: price change + attribution text */}
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2">
          <span className={`text-sm font-mono font-semibold ${pctColor}`}>
            {item.percentChange > 0 ? '+' : ''}{item.percentChange.toFixed(2)}%
          </span>
          <span className="text-xs text-muted-foreground font-mono">
            ${item.currentPrice.toFixed(2)}
          </span>
        </div>
        <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed line-clamp-2">{item.attribution}</p>

        {/* Extra context chips */}
        <div className="flex flex-wrap gap-1.5 mt-1">
          {item.volumeVsNormal > 1.5 && (
            <span className="inline-flex items-center gap-0.5 text-[10px] px-1.5 py-0.5 rounded bg-orange-100 dark:bg-orange-900/40 text-orange-700 dark:text-orange-300 font-mono">
              🔥 {item.volumeVsNormal.toFixed(1)}× vol
            </span>
          )}
          {item.hasActiveEvent && (
            <span className="inline-flex items-center gap-0.5 text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300 font-mono">
              🎯 {item.eventMultiplier !== 1.0 ? `${item.eventMultiplier > 1 ? '+' : ''}${((item.eventMultiplier - 1) * 100).toFixed(0)}%` : 'event'} mult
            </span>
          )}
          <span className="text-[10px] text-muted-foreground/60 font-mono">
            BPD {item.bpd > 0 ? '+' : ''}{(item.bpd * 100).toFixed(2)}%
          </span>
        </div>
      </div>

      {/* Right: volume */}
      <div className="text-right min-w-[40px]">
        <span className="text-xs font-mono text-muted-foreground">
          {item.totalVolume.toLocaleString()}
        </span>
        <p className="text-[10px] text-muted-foreground/60">units</p>
      </div>
    </div>
  );
}

interface PriceAttributionProps {
  item: ItemDto;
  apiBase: string;
}

export function PriceAttribution({ item, apiBase }: PriceAttributionProps) {
  const [data, setData] = useState<PriceChangeDto[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    api.items.attribution(apiBase, item.id)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false));
  }, [apiBase, item.id]);

  if (loading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base flex items-center gap-2">
            What moved this price?
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 animate-pulse">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-12 bg-muted rounded" />
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return null;
  }

  const displayed = expanded ? data : data.slice(0, 5);
  const total = data.length;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base flex items-center gap-2">
            What moved this price?
            <span className="text-xs font-normal text-muted-foreground bg-muted px-2 py-0.5 rounded-full">
              {total} periods
            </span>
          </CardTitle>
          {total > 5 && (
            <button
              onClick={() => setExpanded(e => !e)}
              className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {expanded ? (
                <><ChevronUp className="h-3.5 w-3.5" /> Show less</>
              ) : (
                <><ChevronDown className="h-3.5 w-3.5" /> Show {total - 5} more</>
              )}
            </button>
          )}
        </div>

        {/* Summary pills */}
        <SummaryPills data={data} />
      </CardHeader>
      <CardContent className="pt-1">
        {displayed.map((item, i) => (
          <AttributionRow key={item.timestamp} item={item} first={i === 0} />
        ))}
      </CardContent>
    </Card>
  );
}

function SummaryPills({ data }: { data: PriceChangeDto[] }) {
  // Count attribution types
  const counts = data.reduce<Record<string, number>>((acc, d) => {
    acc[d.attributionKey] = (acc[d.attributionKey] || 0) + 1;
    return acc;
  }, {});

  const total = data.length;
  const entries = Object.entries(counts).sort((a, b) => b[1] - a[1]);

  return (
    <div className="flex flex-wrap gap-1.5 mt-1">
      {entries.map(([key, count]) => {
        const style = BADGE_STYLES[key as PriceChangeDto['attributionKey']];
        const pct = ((count / total) * 100).toFixed(0);
        return (
          <span
            key={key}
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${style.bg} ${style.text}`}
          >
            {style.label}
            <span className="opacity-60">{pct}%</span>
          </span>
        );
      })}
    </div>
  );
}
