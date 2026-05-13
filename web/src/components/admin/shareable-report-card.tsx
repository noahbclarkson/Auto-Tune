'use client';

import { useMemo } from 'react';
import { Activity, Shield, DollarSign, BarChart3, TrendingUp, Zap, ExternalLink } from 'lucide-react';
import type { AdminHealthDto } from '@/lib/api';
import { formatLargeCurrency } from '@/lib/format';

interface ReportCardProps {
  health: AdminHealthDto;
  serverName?: string;
  compact?: boolean;
}

function healthColor(score: number): string {
  if (score >= 70) return 'text-emerald-400';
  if (score >= 40) return 'text-amber-400';
  return 'text-red-400';
}

function healthBg(score: number): string {
  if (score >= 70) return 'bg-emerald-500';
  if (score >= 40) return 'bg-amber-500';
  return 'bg-red-500';
}

function computeScore(h: AdminHealthDto): number {
  const volScore = h.avgVolatility < 0.05 ? 100 : h.avgVolatility < 0.10 ? 80 : h.avgVolatility < 0.15 ? 60 : h.avgVolatility < 0.25 ? 30 : 10;
  const d2gScore = h.debtGdpRatio < 0.5 ? 100 : h.debtGdpRatio < 1.0 ? 75 : h.debtGdpRatio < 3.0 ? 45 : 10;
  const imbalance = Math.abs(h.buyPct - 50) / 50;
  const balScore = Math.round((1 - imbalance) * 100);
  const baseScore = Math.round(volScore * 0.4 + d2gScore * 0.3 + balScore * 0.3);

  if (h.circuitBreakerTier === 'ADMIN_RECOVERY' || h.circuitBreakerTier === 'TIER3') return Math.min(baseScore, 10);
  if (h.circuitBreakerTier === 'TIER2') return Math.min(baseScore, 30);
  if (h.circuitBreakerTier === 'TIER1') return Math.min(baseScore, 60);
  return baseScore;
}

function circuitLabel(tier: string): string {
  if (tier === 'NORMAL') return 'Normal';
  if (tier === 'ADMIN_RECOVERY') return 'Admin Recovery';
  if (tier === 'TIER1') return 'Tier 1';
  if (tier === 'TIER2') return 'Tier 2';
  if (tier === 'TIER3') return 'Tier 3';
  return tier;
}

function circuitColor(tier: string): string {
  if (tier === 'NORMAL') return 'text-emerald-400';
  if (tier === 'ADMIN_RECOVERY') return 'text-yellow-400';
  if (tier === 'TIER1') return 'text-amber-400';
  if (tier === 'TIER2') return 'text-orange-400';
  if (tier === 'TIER3') return 'text-red-400';
  return 'text-muted-foreground';
}

export function ShareableReportCard({ health, serverName, compact = false }: ReportCardProps) {
  const score = useMemo(() => computeScore(health), [health]);
  const scoreColor = healthColor(score);
  const scoreBg = healthBg(score);
  const tierColor = circuitColor(health.circuitBreakerTier);
  const date = new Date(health.timestamp).toLocaleString();

  return (
    <div
      className="rounded-2xl border border-gray-800 bg-gray-950 p-6 font-sans"
      style={{ minWidth: compact ? 320 : 480, maxWidth: 560 }}
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-5">
        <div>
          <div className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-1">Economy Report</div>
          <div className="text-white font-bold text-lg leading-tight">{serverName ?? 'Auto-Tune Server'}</div>
          <div className="text-xs text-gray-500 mt-0.5">{date}</div>
        </div>
        {/* Score circle */}
        <div className="relative w-14 h-14 flex-shrink-0">
          <svg className="w-14 h-14 -rotate-90" viewBox="0 0 56 56">
            <circle cx="28" cy="28" r="22" fill="none" stroke="#1f2937" strokeWidth="5" />
            <circle
              cx="28" cy="28" r="22"
              fill="none"
              stroke={scoreBg}
              strokeWidth="5"
              strokeDasharray={`${2 * Math.PI * 22 * score / 100} ${2 * Math.PI * 22}`}
              strokeLinecap="round"
            />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center">
            <span className={`text-sm font-bold ${scoreColor}`}>{score}</span>
          </div>
        </div>
      </div>

      {/* Health label */}
      <div className={`text-center text-xs font-semibold mb-4 pb-3 border-b border-gray-800 ${scoreColor}`}>
        {score >= 70 ? 'Healthy Economy' : score >= 40 ? 'Moderate — Review Recommended' : 'Unstable — Action Needed'}
      </div>

      {/* Metrics grid */}
      <div className={`grid ${compact ? 'grid-cols-2 gap-3' : 'grid-cols-3 gap-4'} mb-4`}>
        <Metric
          label="GDP"
          value={formatLargeCurrency(health.gdp)}
          icon={<DollarSign className="w-3.5 h-3.5 text-sky-400" />}
          color="text-sky-400"
        />
        <Metric
          label="Debt / GDP"
          value={health.debtGdpRatio < 0 ? 'N/A' : `${health.debtGdpRatio.toFixed(2)}x`}
          icon={<Shield className="w-3.5 h-3.5" />}
          color={health.debtGdpRatio < 0 ? 'text-gray-500' : health.debtGdpRatio < 3 ? 'text-emerald-400' : health.debtGdpRatio < 10 ? 'text-amber-400' : 'text-red-400'}
        />
        <Metric
          label="Volatility"
          value={health.avgVolatility < 0.05 ? 'Stable' : health.avgVolatility < 0.15 ? 'Moderate' : 'Unstable'}
          icon={health.avgVolatility < 0.05
            ? <Activity className="w-3.5 h-3.5 text-emerald-400" />
            : health.avgVolatility < 0.15
            ? <Activity className="w-3.5 h-3.5 text-amber-400" />
            : <Activity className="w-3.5 h-3.5 text-red-400" />}
          color={health.avgVolatility < 0.05 ? 'text-emerald-400' : health.avgVolatility < 0.15 ? 'text-amber-400' : 'text-red-400'}
        />
        {!compact && (
          <>
            <Metric
              label="Buy / Sell"
              value={`${health.buyPct.toFixed(0)}% / ${health.sellPct.toFixed(0)}%`}
              icon={health.buyPct >= 45 && health.buyPct <= 55
                ? <TrendingUp className="w-3.5 h-3.5 text-emerald-400" />
                : <TrendingUp className="w-3.5 h-3.5 text-amber-400" />}
              color={health.buyPct >= 45 && health.buyPct <= 55 ? 'text-emerald-400' : 'text-amber-400'}
            />
            <Metric
              label="Avg Spread"
              value={`${health.avgBpd.toFixed(1)}%`}
              icon={<Zap className="w-3.5 h-3.5 text-purple-400" />}
              color={health.avgBpd < 5 ? 'text-emerald-400' : health.avgBpd < 10 ? 'text-amber-400' : 'text-red-400'}
            />
            <Metric
              label="Circuit"
              value={circuitLabel(health.circuitBreakerTier)}
              icon={<BarChart3 className="w-3.5 h-3.5" />}
              color={tierColor}
            />
          </>
        )}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-gray-800">
        <span className="text-xs text-gray-600">Generated by Auto-Tune</span>
        <a
          href="/admin"
          className="text-xs text-emerald-400 hover:text-emerald-300 flex items-center gap-1 transition-colors"
        >
          View Live <ExternalLink className="w-3 h-3" />
        </a>
      </div>
    </div>
  );
}

function Metric({ label, value, icon, color }: { label: string; value: string; icon: React.ReactNode; color: string }) {
  return (
    <div className="flex items-start gap-2">
      <div className="mt-0.5 flex-shrink-0">{icon}</div>
      <div>
        <div className="text-xs text-gray-500 leading-tight">{label}</div>
        <div className={`text-sm font-bold tabular-nums leading-tight ${color}`}>{value}</div>
      </div>
    </div>
  );
}

/** Encode health data for shareable URL */
export function encodeHealthReport(health: AdminHealthDto, serverName?: string): string {
  const payload = { health, serverName, v: 1 };
  return btoa(JSON.stringify(payload));
}

/** Decode health data from shareable URL */
export function decodeHealthReport(encoded: string): { health: AdminHealthDto; serverName?: string } | null {
  try {
    const raw = JSON.parse(atob(encoded));
    if (!raw.health || typeof raw.health.gdp !== 'number') return null;
    return { health: raw.health as AdminHealthDto, serverName: raw.serverName };
  } catch {
    return null;
  }
}
