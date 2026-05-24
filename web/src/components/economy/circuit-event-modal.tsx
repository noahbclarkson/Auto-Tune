'use client';

import { useEffect } from 'react';
import { X } from 'lucide-react';
import type { CircuitEventDto } from '@/lib/api';
import { formatLargeCurrency } from '@/lib/format';

const TIER_COLORS: Record<string, string> = {
  NORMAL: '#10b981',
  TIER1: '#f59e0b',
  TIER2: '#f97316',
  TIER3: '#ef4444',
  ADMIN_RECOVERY: '#a855f7',
};

function tierBadge(tier: string) {
  const color = TIER_COLORS[tier] ?? '#64748b';
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold"
      style={{ backgroundColor: `${color}20`, color, border: `1px solid ${color}50` }}
    >
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      {tier === 'ADMIN_RECOVERY' ? 'Recovery' : tier}
    </span>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-border last:border-0">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-semibold text-foreground font-mono">{value}</span>
    </div>
  );
}

interface CircuitEventModalProps {
  event: CircuitEventDto;
  onClose: () => void;
}

export function CircuitEventModal({ event, onClose }: CircuitEventModalProps) {
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  const tierColor = TIER_COLORS[event.newTier] ?? '#64748b';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      {/* Modal */}
      <div className="relative w-full max-w-md rounded-xl border border-border bg-card shadow-2xl">
        {/* Header */}
        <div
          className="flex items-center justify-between rounded-t-xl border-b border-border px-5 py-4"
          style={{ borderTopColor: tierColor, borderTopWidth: '3px' }}
        >
          <div className="flex items-center gap-2">
            <span className="text-base font-semibold text-foreground">Circuit Event</span>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Content */}
        <div className="px-5 py-4 space-y-4">
          {/* Tier transition */}
          <div className="flex items-center justify-center gap-3 py-2">
            {tierBadge(event.previousTier ?? 'START')}
            <span className="text-muted-foreground text-lg">→</span>
            {tierBadge(event.newTier)}
          </div>

          {/* Stats grid */}
          <div className="rounded-lg bg-muted/40 border border-border p-4 space-y-0">
            <DetailRow
              label="Debt / GDP"
              value={`${event.debtGdpRatio >= 10 ? event.debtGdpRatio.toFixed(1) : event.debtGdpRatio.toFixed(2)}×`}
            />
            <DetailRow label="Total GDP" value={formatLargeCurrency(event.gdp)} />
            <DetailRow label="Total Debt" value={formatLargeCurrency(event.totalDebt)} />
            <DetailRow
              label="Interest Rate"
              value={`${event.interestMultiplier.toFixed(event.interestMultiplier < 0.1 ? 2 : 1)}× base`}
            />
            <DetailRow
              label="Triggered"
              value={new Date(event.timestamp).toLocaleString()}
            />
            {event.adminInitiated && (
              <div className="pt-2">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-purple-500/10 border border-purple-500/30 px-3 py-1 text-xs font-medium text-purple-600 dark:text-purple-300">
                  Manual — admin initiated
                </span>
              </div>
            )}
          </div>

          {/* Guidance */}
          {event.details && (
            <div className="rounded-lg border border-border bg-muted/30 p-4">
              <p className="text-xs font-medium text-muted-foreground mb-1.5">Admin notes</p>
              <p className="text-sm text-foreground leading-relaxed">{event.details}</p>
            </div>
          )}

          {/* Recommended actions */}
          <div className="rounded-lg border border-border bg-muted/30 p-4">
            <p className="text-xs font-medium text-muted-foreground mb-1.5">Recommended action</p>
            <p className="text-sm leading-relaxed">
              {event.newTier === 'ADMIN_RECOVERY'
                ? event.adminInitiated
                  ? 'Manual recovery is active. Monitor D/G daily and watch for unlock below 15× before reopening loans.'
                  : 'Recovery mode active. Track D/G closely — early unlock (below 15×) depends on sustained debt reduction.'
                : event.newTier === 'TIER3'
                ? event.debtGdpRatio >= 30
                  ? 'Interest paused. Begin recovery once D/G stays consistently below 15×.'
                  : 'Circuit breaker engaged. Watch for natural rebound or prepare manual intervention.'
                : event.newTier === 'TIER2'
                ? 'High debt pressure detected. Review loan defaults, consider reducing borrowing limits, and prepare early recovery steps.'
                : event.newTier === 'TIER1'
                ? 'Early warning — monitor debt trends and loan default rates. No immediate action needed but stay alert.'
                : 'Economy is stable. No action required — keep monitoring for tier changes.'}
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end rounded-b-xl border-t border-border px-5 py-3">
          <button
            onClick={onClose}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
