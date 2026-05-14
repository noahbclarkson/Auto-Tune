'use client';

import { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EconomyRecoveryAdvisor } from '@/components/admin/economy-recovery-advisor';
import { ConfigHealthCard } from '@/components/admin/config-health-card';
import { AdminAuditCard } from '@/components/admin/admin-audit-card';
import { AdminAuctionCard } from '@/components/admin/admin-auction-card';
import { ApiErrorBanner } from '@/components/ui/api-error-banner';
import { api, type AdminHealthDto, type Stats } from '@/lib/api';
import { formatLargeCurrency, formatPercent } from '@/lib/format';
import {
  Activity,
  Shield,
  TrendingUp,
  TrendingDown,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Snowflake,
  DollarSign,
  BarChart3,
  Zap,
  Layers,
  Link2,
} from 'lucide-react';
import { ShareableReportCard, decodeHealthReport, encodeHealthReport } from '@/components/admin/shareable-report-card';
import { FirstRunVerificationCard } from '@/components/admin/first-run-verification-card';

function HealthBadge({ tier, className = '' }: { tier: string; className?: string }) {
  if (tier === 'NORMAL') {
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 ${className}`}>
        <CheckCircle className="w-3 h-3" /> Normal
      </span>
    );
  }
  if (tier === 'TIER1') {
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30 ${className}`}>
        <AlertTriangle className="w-3 h-3" /> Tier 1 — Warning
      </span>
    );
  }
  if (tier === 'TIER2') {
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-orange-500/15 text-orange-400 border border-orange-500/30 ${className}`}>
        <XCircle className="w-3 h-3" /> Tier 2 — Danger
      </span>
    );
  }
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-red-500/15 text-red-400 border border-red-500/30 ${className}`}>
      <XCircle className="w-3 h-3" /> {tier}
    </span>
  );
}

function metricCard(
  label: string,
  value: string,
  subtext: string,
  icon: React.ReactNode,
  accent: 'green' | 'amber' | 'red' | 'blue' | 'muted'
) {
  const colors = {
    green: 'text-emerald-400 border-emerald-500/20 bg-emerald-500/5',
    amber: 'text-amber-400 border-amber-500/20 bg-amber-500/5',
    red: 'text-red-400 border-red-500/20 bg-red-500/5',
    blue: 'text-sky-400 border-sky-500/20 bg-sky-500/5',
    muted: 'text-muted-foreground border-border',
  };
  return (
    <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border ${colors[accent]}`}>
      <div className="flex-shrink-0">{icon}</div>
      <div className="min-w-0">
        <div className="text-xs font-medium text-muted-foreground">{label}</div>
        <div className="text-lg font-bold tabular-nums truncate">{value}</div>
        <div className="text-xs text-muted-foreground">{subtext}</div>
      </div>
    </div>
  );
}

export function AdminContent() {
  const { apiBase } = useAppContext();
  const searchParams = useSearchParams();
  const [health, setHealth] = useState<AdminHealthDto | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Snapshot mode: show a read-only report card from URL params
  const snapshotParam = searchParams.get('report');
  const snapshotData = snapshotParam ? decodeHealthReport(snapshotParam) : null;

  const fetchData = useCallback(async () => {
    try {
      const [healthData, statsData] = await Promise.all([
        api.admin.health(apiBase).catch((e: Error) => { throw e; }),
        api.stats(apiBase).catch(() => null),
      ]);
      setHealth(healthData);
      setStats(statsData);
      setError(null);
    } catch {
      setError('Could not load economy health data. Is the server running?');
    } finally {
      setLoading(false);
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 15000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const handleShareSnapshot = useCallback(() => {
    if (!health) return;
    const encoded = encodeHealthReport(health, stats?.serverName);
    const url = `${window.location.origin}/admin?report=${encoded}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    });
  }, [health, stats]);

  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-4 sm:px-6 py-8">
          <div className="text-muted-foreground">Loading economy health...</div>
        </main>
      </div>
    );
  }

  // Shareable snapshot mode — rendered without live data
  if (snapshotData) {
    return (
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-4 sm:px-6 py-8">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-foreground">Economy Report</h1>
            <p className="text-sm text-muted-foreground mt-0.5">
              Snapshot taken {new Date(snapshotData.health.timestamp).toLocaleString()} — this is a static share
            </p>
          </div>
          <div className="flex flex-col items-center gap-6">
            <ShareableReportCard
              health={snapshotData.health}
              serverName={snapshotData.serverName}
              compact={false}
            />
            <a
              href="/admin"
              className="text-sm text-emerald-400 hover:text-emerald-300 flex items-center gap-1.5 transition-colors"
            >
              ← View Live Dashboard
            </a>
          </div>
        </main>
      </div>
    );
  }

  if (error || !health) {
    return (
      <div className="min-h-screen bg-background">
        <Header totalItems={0} onlinePlayers={0} />
        <main className="mx-auto max-w-7xl px-4 sm:px-6 py-8">
          <div className="mb-4">
            <h1 className="text-2xl font-bold text-foreground">Economy Health</h1>
            <p className="text-sm text-muted-foreground mt-0.5">Admin diagnostic</p>
          </div>
          <ApiErrorBanner
            message={error ?? 'Could not load economy health data'}
            apiBase={apiBase}
            onRetry={fetchData}
          />
        </main>
      </div>
    );
  }

  // Derived health score
  let healthScore = 100;
  let healthLabel = 'Healthy';
  let healthAccent: 'green' | 'amber' | 'red' = 'green';

  if (health.circuitBreakerTier !== 'NORMAL') {
    healthScore = health.circuitBreakerTier === 'TIER1' ? 60 : health.circuitBreakerTier === 'TIER2' ? 30 : 10;
    healthLabel = health.circuitBreakerTier === 'TIER1' ? 'At Risk'
      : health.circuitBreakerTier === 'TIER2' ? 'Danger'
      : 'Emergency';
    healthAccent = 'red';
  } else if (health.debtGdpRatio >= 3) {
    healthScore = 70;
    healthLabel = 'Elevated Debt';
    healthAccent = 'amber';
  } else if (health.buyPct < 35 || health.buyPct > 65) {
    healthScore = 75;
    healthLabel = 'Imbalanced';
    healthAccent = 'amber';
  }

  const debtGdpColor = health.debtGdpRatio < 0 ? 'text-muted-foreground'
    : health.debtGdpRatio < 3 ? 'text-emerald-400'
    : health.debtGdpRatio < 10 ? 'text-amber-400'
    : 'text-red-400';

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />

      <main className="mx-auto max-w-7xl px-4 sm:px-6 py-8 space-y-6">
        {/* Title row */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Economy Health</h1>
            <p className="text-sm text-muted-foreground mt-0.5">
              Admin diagnostic — {stats?.serverName ?? 'Local Server'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            {health.frozen && (
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-sky-500/15 text-sky-400 border border-sky-500/30 text-sm font-medium">
                <Snowflake className="w-4 h-4" /> Market Frozen
              </span>
            )}
            <HealthBadge tier={health.circuitBreakerTier} />
            <button
              onClick={handleShareSnapshot}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-gray-800 hover:bg-gray-700 text-gray-300 hover:text-white border border-gray-700 text-sm font-medium transition-all"
              title="Copy shareable economy report link"
            >
              {copied ? (
                <>
                  <CheckCircle className="w-4 h-4 text-emerald-400" />
                  <span className="text-emerald-400">Copied!</span>
                </>
              ) : (
                <>
                  <Link2 className="w-4 h-4" />
                  Share Snapshot
                </>
              )}
            </button>
          </div>
        </div>

        {/* Health score */}
        <Card className="border-border">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                {healthAccent === 'green' && <CheckCircle className="w-8 h-8 text-emerald-400" />}
                {healthAccent === 'amber' && <AlertTriangle className="w-8 h-8 text-amber-400" />}
                {healthAccent === 'red' && <XCircle className="w-8 h-8 text-red-400" />}
                <div>
                  <div className="text-3xl font-bold">{healthScore}<span className="text-lg text-muted-foreground">/100</span></div>
                  <div className="text-sm font-medium" style={{ color: healthAccent === 'green' ? '#4ade80' : healthAccent === 'amber' ? '#fbbf24' : '#f87171' }}>{healthLabel}</div>
                </div>
              </div>
              {health.circuitBreakerTier !== 'NORMAL' && (
                <div className="text-right">
                  <div className="text-xs text-muted-foreground">Interest Rate</div>
                  <div className="text-lg font-bold text-amber-400">{Math.round(health.interestMultiplier * 100)}% of normal</div>
                  <div className="text-xs text-muted-foreground mt-0.5">Debt/GDP: <span className={debtGdpColor}>{health.debtGdpRatio < 0 ? 'N/A' : `${health.debtGdpRatio.toFixed(2)}x`}</span></div>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Key metrics grid */}
        <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
          {metricCard(
            'GDP',
            formatLargeCurrency(health.gdp),
            `${stats?.onlinePlayers ?? 0} players online`,
            <DollarSign className="w-5 h-5" />,
            'blue'
          )}
          {metricCard(
            'Total Debt',
            formatLargeCurrency(health.totalDebt),
            `${health.activeLoans} active loans`,
            <BarChart3 className="w-5 h-5" />,
            health.totalDebt > 0 ? (health.debtGdpRatio >= 3 ? 'red' : 'amber') : 'muted'
          )}
          {metricCard(
            'Debt / GDP',
            health.debtGdpRatio < 0 ? 'N/A' : `${health.debtGdpRatio.toFixed(2)}x`,
            health.circuitBreakerTier !== 'NORMAL' ? health.circuitBreakerTier : 'Normal',
            <Shield className="w-5 h-5" />,
            health.debtGdpRatio < 0 ? 'muted' : health.debtGdpRatio < 3 ? 'green' : health.debtGdpRatio < 10 ? 'amber' : 'red'
          )}
          {metricCard(
            'Trade Mix',
            `${health.buyPct.toFixed(1)}%`,
            `buy / ${health.sellPct.toFixed(1)}% sell`,
            health.buyPct >= 45 && health.buyPct <= 55
              ? <Activity className="w-5 h-5 text-emerald-400" />
              : <Activity className="w-5 h-5 text-amber-400" />,
            health.buyPct >= 45 && health.buyPct <= 55 ? 'green' : 'amber'
          )}
          {metricCard(
            'Avg Spread',
            `${health.avgBpd.toFixed(2)}%`,
            `sell ${health.avgSpd.toFixed(2)}%`,
            <Layers className="w-5 h-5" />,
            health.avgBpd < 5 ? 'green' : health.avgBpd < 10 ? 'amber' : 'red'
          )}
          {metricCard(
            'Volume',
            `${health.globalVolumeMultiplier.toFixed(2)}x`,
            health.inflationLabel,
            <Zap className="w-5 h-5" />,
            health.globalVolumeMultiplier > 0.5 && health.globalVolumeMultiplier < 1.5 ? 'green'
              : health.globalVolumeMultiplier >= 0.5 ? 'amber' : 'red'
          )}
        </div>

        {/* Two-column: volatile + undersold */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Most volatile */}
          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-amber-400" />
                Most Volatile Items (24h)
              </CardTitle>
            </CardHeader>
            <CardContent>
              {health.topVolatile.length === 0 ? (
                <p className="text-sm text-muted-foreground">No price history yet.</p>
              ) : (
                <div className="space-y-2">
                  {health.topVolatile.map((item) => {
                    const absChange = Math.abs(item.pctChange);
                    const color = absChange >= 20 ? 'text-red-400' : absChange >= 5 ? 'text-amber-400' : 'text-muted-foreground';
                    const arrow = item.pctChange > 0
                      ? <TrendingUp className="w-3 h-3 inline" />
                      : <TrendingDown className="w-3 h-3 inline" />;
                    return (
                      <div key={item.id} className="flex items-center justify-between py-1 border-b border-border/50 last:border-0">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className={`flex-shrink-0 ${color}`}>{arrow}</span>
                          <span className="text-sm truncate">{item.displayName}</span>
                        </div>
                        <span className={`text-sm font-medium tabular-nums flex-shrink-0 ${color}`}>
                          {formatPercent(item.pctChange)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Most undersold */}
          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <TrendingDown className="w-4 h-4 text-red-400" />
                Most Undersold Items (below fair value)
              </CardTitle>
            </CardHeader>
            <CardContent>
              {health.topUndersold.length === 0 ? (
                <p className="text-sm text-muted-foreground">No price history yet.</p>
              ) : (
                <div className="space-y-2">
                  {health.topUndersold.map((item) => {
                    const change = item.pctChange;
                    const color = change <= -20 ? 'text-red-400' : change <= -5 ? 'text-amber-400' : 'text-muted-foreground';
                    const arrow = change <= -20 ? '▼▼' : change <= -5 ? '▼' : '—';
                    return (
                      <div key={item.id} className="flex items-center justify-between py-1 border-b border-border/50 last:border-0">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className={`text-xs flex-shrink-0 ${color}`}>{arrow}</span>
                          <span className="text-sm truncate">{item.displayName}</span>
                        </div>
                        <span className={`text-sm font-medium tabular-nums flex-shrink-0 ${color}`}>
                          {formatPercent(change)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Circuit breaker legend */}
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold">Circuit Breaker Tiers</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 text-sm">
              <div className="flex items-start gap-2">
                <div className="w-3 h-3 rounded-full bg-emerald-400 mt-0.5 flex-shrink-0" />
                <div>
                  <div className="font-medium text-emerald-400">Normal</div>
                  <div className="text-muted-foreground text-xs">D/G &lt;3x. No intervention.</div>
                </div>
              </div>
              <div className="flex items-start gap-2">
                <div className="w-3 h-3 rounded-full bg-amber-400 mt-0.5 flex-shrink-0" />
                <div>
                  <div className="font-medium text-amber-400">Tier 1 — Warning</div>
                  <div className="text-muted-foreground text-xs">D/G 3–5x. Interest proportionally reduced (50% at 5x).</div>
                </div>
              </div>
              <div className="flex items-start gap-2">
                <div className="w-3 h-3 rounded-full bg-orange-500 mt-0.5 flex-shrink-0" />
                <div>
                  <div className="font-medium text-orange-400">Tier 2 — Danger</div>
                  <div className="text-muted-foreground text-xs">D/G 5–30x. Interest proportionally reduced (25% at 10x).</div>
                </div>
              </div>
              <div className="flex items-start gap-2">
                <div className="w-3 h-3 rounded-full bg-red-500 mt-0.5 flex-shrink-0" />
                <div>
                  <div className="font-medium text-red-400">Tier 3 — Emergency</div>
                  <div className="text-muted-foreground text-xs">D/G &gt;30x. Interest paused entirely.</div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Config Health Dashboard */}
        <FirstRunVerificationCard />
        <ConfigHealthCard />

        {/* Auction Integrity */}
        <AdminAuctionCard />

        {/* Admin Audit Log */}
        <AdminAuditCard />

        {/* Economy Recovery Advisor */}
        <EconomyRecoveryAdvisor health={health} />

        {/* Footer timestamp */}
        <p className="text-xs text-muted-foreground text-center">
          Last updated {new Date(health.timestamp).toLocaleTimeString()} — refreshes every 15s
        </p>
      </main>
    </div>
  );
}
