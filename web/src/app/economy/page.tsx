'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { EconomyChart } from '@/components/economy/economy-chart';
import { VolumeMultiplierGauge } from '@/components/economy/volume-multiplier-gauge';
import { EconomyTemperatureGauge } from '@/components/dashboard/economy-temperature-gauge';
import { Card, CardContent } from '@/components/ui/card';
import {
  Activity,
  TrendingUp,
  TrendingDown,
  Minus,
  AlertTriangle,
  CheckCircle,
  DollarSign,
  Layers,
  Zap,
  BarChart3,
} from 'lucide-react';
import {
  api,
  type Stats,
  type EconomySnapshotDto,
  type GdpData,
  type InflationData,
  type DebtData,
  type VolumeMultiplierDto,
  type AdminHealthDto,
  type WhatMovedEntry,
} from '@/lib/api';
import { formatCurrency, formatPercent } from '@/lib/format';

function volatilityLabel(v: number): { label: string; color: string; bg: string; ring: string } {
  if (v < 0.05) return { label: 'Stable', color: 'text-emerald-500', bg: 'bg-emerald-500', ring: 'ring-emerald-500' };
  if (v < 0.15) return { label: 'Moderate', color: 'text-amber-500', bg: 'bg-amber-500', ring: 'ring-amber-500' };
  return { label: 'Unstable', color: 'text-red-500', bg: 'bg-red-500', ring: 'ring-red-500' };
}

/** Composite economy health score 0–100 derived from admin health endpoint. */
function computeHealthScore(h: AdminHealthDto): number {
  // Volatility component (40% weight) — most important
  const volScore = h.avgVolatility < 0.05 ? 100
    : h.avgVolatility < 0.10 ? 80
    : h.avgVolatility < 0.15 ? 60
    : h.avgVolatility < 0.25 ? 30
    : 10;

  // Debt/GDP component (30% weight)
  const d2gScore = h.debtGdpRatio < 0.5 ? 100
    : h.debtGdpRatio < 1.0 ? 75
    : h.debtGdpRatio < 3.0 ? 45
    : 10;

  // Buy/sell balance component (30% weight)
  const imbalance = Math.abs(h.buyPct - 50) / 50; // 0 = perfect, 1 = extreme
  const balScore = Math.round((1 - imbalance) * 100);

  return Math.round(volScore * 0.4 + d2gScore * 0.3 + balScore * 0.3);
}

export default function EconomyPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [gdp, setGdp] = useState<GdpData | null>(null);
  const [inflation, setInflation] = useState<InflationData | null>(null);
  const [debt, setDebt] = useState<DebtData | null>(null);
  const [history, setHistory] = useState<EconomySnapshotDto[]>([]);
  const [volumeMultiplier, setVolumeMultiplier] = useState<VolumeMultiplierDto | null>(null);
  const [health, setHealth] = useState<AdminHealthDto | null>(null);
  const [whatMoved, setWhatMoved] = useState<WhatMovedEntry[]>([]);

  const fetchData = useCallback(async () => {
    try {
      const [statsData, gdpData, inflationData, debtData, historyData, vmData, healthData, whatMovedData] =
        await Promise.all([
          api.stats(apiBase),
          api.economy.gdp(apiBase).catch(() => null),
          api.economy.inflation(apiBase).catch(() => null),
          api.economy.debt(apiBase).catch(() => null),
          api.economy.history(apiBase, 500).catch(() => []),
          api.economy.volumeMultiplier(apiBase).catch(() => null),
          api.admin.health(apiBase).catch(() => null),
          api.economy.whatMoved(apiBase).catch(() => [] as WhatMovedEntry[]),
        ]);
      setStats(statsData);
      setGdp(gdpData);
      setInflation(inflationData as InflationData | null);
      setDebt(debtData as DebtData | null);
      setHistory((historyData as EconomySnapshotDto[]).reverse());
      setVolumeMultiplier(vmData as VolumeMultiplierDto | null);
      setHealth(healthData as AdminHealthDto | null);
      setWhatMoved(whatMovedData);
    } catch {
      // silently fail
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const debtToGdp = gdp && debt && gdp.gdp > 0 ? debt.totalDebt / gdp.gdp : null;
  const inflationVal = inflation?.averagePriceChange ?? null;
  const onlinePlayers = stats?.onlinePlayers ?? 0;

  // Composite health score from admin health endpoint
  const healthScore = health ? computeHealthScore(health) : null;

  const inflationColor = inflationVal === null ? 'text-muted-foreground'
    : inflationVal > 3 ? 'text-red-500'
    : inflationVal > 1.5 ? 'text-amber-500'
    : inflationVal > 0.5 ? 'text-emerald-500'
    : inflationVal > -0.5 ? 'text-sky-400'
    : 'text-blue-500';

  const inflationTrend = inflationVal === null ? null
    : inflationVal > 0.5 ? { icon: TrendingUp, label: 'Rising', color: 'text-amber-500', bg: 'bg-amber-500/10' }
    : inflationVal > 0 ? { icon: TrendingUp, label: 'Slight rise', color: 'text-emerald-500', bg: 'bg-emerald-500/10' }
    : inflationVal > -0.5 ? { icon: Minus, label: 'Stable', color: 'text-sky-400', bg: 'bg-sky-500/10' }
    : { icon: TrendingDown, label: 'Deflating', color: 'text-blue-500', bg: 'bg-blue-500/10' };

  const debtColor = debtToGdp === null ? 'text-muted-foreground'
    : debtToGdp > 1.5 ? 'text-red-500'
    : debtToGdp > 1.0 ? 'text-amber-500'
    : debtToGdp > 0.5 ? 'text-emerald-500'
    : 'text-emerald-400';

  const volInfo = health ? volatilityLabel(health.avgVolatility) : null;

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={onlinePlayers} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6 flex-1">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <h2 className="text-2xl font-bold text-foreground">Economy Overview</h2>

          {/* Composite health score + badge */}
          <div className="flex items-center gap-3">
            {health && (
              <EconomyTemperatureGauge
                score={healthScore ?? 0}
                label={healthScore !== null && healthScore >= 75 ? 'Healthy' : healthScore !== null && healthScore >= 45 ? 'Moderate' : 'Critical'}
                debtGdpRatio={health.debtGdpRatio}
                avgVolatility={health.avgVolatility}
                buyPct={health.buyPct}
              />
            )}
            <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium border ${
              healthScore !== null && healthScore >= 75
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-400'
                : healthScore !== null && healthScore >= 45
                ? 'bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-400'
                : healthScore !== null
                ? 'bg-red-500/10 border-red-500/30 text-red-500'
                : onlinePlayers === 0
                ? 'bg-muted border-muted text-muted-foreground'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-400'
            }`}>
              {healthScore !== null && healthScore >= 75
                ? <><CheckCircle className="w-3.5 h-3.5" /> Economy Healthy</>
                : healthScore !== null && healthScore >= 45
                ? <><AlertTriangle className="w-3.5 h-3.5" /> Economy Moderate</>
                : healthScore !== null
                ? <><AlertTriangle className="w-3.5 h-3.5" /> Economy Unstable</>
                : onlinePlayers === 0
                ? <><Activity className="w-3.5 h-3.5" /> No Players</>
                : <><Activity className="w-3.5 h-3.5" /> Loading…</>
              }
            </div>
          </div>
        </div>

        {/* Summary stat cards — 5 columns on large screens */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          <Card>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <DollarSign className="w-4 h-4 text-amber-500" />
                <p className="text-sm text-muted-foreground">GDP</p>
              </div>
              <p className="text-xl font-bold text-foreground">
                {gdp ? formatCurrency(gdp.gdp) : '--'}
              </p>
              {gdp && history.length >= 2 && (() => {
                const old = history[0]?.gdp ?? 0;
                const pct = old > 0 ? ((gdp.gdp - old) / old) * 100 : 0;
                return (
                  <p className={`text-xs font-medium mt-1 ${pct >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                    {pct >= 0 ? '↑' : '↓'} {Math.abs(pct).toFixed(1)}% vs window start
                  </p>
                );
              })()}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <Layers className="w-4 h-4 text-purple-500" />
                <p className="text-sm text-muted-foreground">Inflation</p>
              </div>
              <p className={`text-xl font-bold ${inflationColor}`}>
                {inflation ? formatPercent(inflation.averagePriceChange) : '--'}
              </p>
              {inflationTrend && (
                <div className={`flex items-center gap-1 mt-1 ${inflationTrend.color}`}>
                  <inflationTrend.icon className="w-3 h-3" />
                  <span className="text-xs font-medium">{inflationTrend.label}</span>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <AlertTriangle className="w-4 h-4 text-red-500" />
                <p className="text-sm text-muted-foreground">Total Debt</p>
              </div>
              <p className={`text-xl font-bold ${debtColor}`}>
                {debt ? formatCurrency(debt.totalDebt) : '--'}
              </p>
              {debtToGdp !== null && (
                <p className={`text-xs font-medium mt-1 ${debtColor}`}>
                  {debtToGdp > 1 ? '⚠' : '✓'} {debtToGdp.toFixed(1)}× GDP
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <Activity className="w-4 h-4 text-emerald-500" />
                <p className="text-sm text-muted-foreground">Active Loans</p>
              </div>
              <p className="text-xl font-bold text-foreground">{debt ? debt.activeLoans : '--'}</p>
              {debt && debt.activeLoans > 0 && (
                <p className="text-xs text-muted-foreground mt-1">
                  Avg: {formatCurrency(debt.totalDebt / Math.max(1, debt.activeLoans))} / loan
                </p>
              )}
            </CardContent>
          </Card>

          {/* Volatility card — from admin health endpoint */}
          <Card className={volInfo ? `border-l-2 border-l-2 ${volInfo.ring}` : ''}>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <Zap className={`w-4 h-4 ${volInfo ? volInfo.color : 'text-muted-foreground'}`} />
                <p className="text-sm text-muted-foreground">Volatility</p>
              </div>
              <p className={`text-xl font-bold ${volInfo ? volInfo.color : 'text-muted-foreground'}`}>
                {health ? health.avgVolatility.toFixed(4) : '--'}
              </p>
              {volInfo && (
                <div className={`flex items-center gap-1 mt-1 ${volInfo.color}`}>
                  <BarChart3 className="w-3 h-3" />
                  <span className="text-xs font-medium">{volInfo.label}</span>
                </div>
              )}
              {!health && <p className="text-xs text-muted-foreground mt-1">Loading…</p>}
            </CardContent>
          </Card>
        </div>

        {/* Health score breakdown — visible when health data available */}
        {health && (
          <Card>
            <CardContent className="p-4">
              <p className="text-sm font-medium text-foreground mb-3">Health Score Breakdown</p>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* Volatility score */}
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-muted-foreground">Volatility</span>
                    <span className={`font-medium ${volInfo?.color ?? 'text-muted-foreground'}`}>
                      {health.avgVolatility < 0.05 ? '100' : health.avgVolatility < 0.10 ? '80' : health.avgVolatility < 0.15 ? '60' : health.avgVolatility < 0.25 ? '30' : '10'} / 40
                    </span>
                  </div>
                  <div className="h-2 rounded-full bg-muted overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all ${volInfo?.bg ?? 'bg-muted-foreground'}`}
                      style={{ width: `${health.avgVolatility < 0.05 ? 100 : health.avgVolatility < 0.10 ? 80 : health.avgVolatility < 0.15 ? 60 : health.avgVolatility < 0.25 ? 30 : 10}%` }}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {health.avgVolatility < 0.05 ? 'Prices are steady' : health.avgVolatility < 0.15 ? 'Normal oscillation' : 'Wild swings — check top movers'}
                  </p>
                </div>

                {/* Debt/GDP score */}
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-muted-foreground">Debt / GDP</span>
                    <span className={`font-medium ${health.debtGdpRatio < 0.5 ? 'text-emerald-500' : health.debtGdpRatio < 1.0 ? 'text-emerald-500' : health.debtGdpRatio < 3.0 ? 'text-amber-500' : 'text-red-500'}`}>
                      {health.debtGdpRatio < 0.5 ? '100' : health.debtGdpRatio < 1.0 ? '75' : health.debtGdpRatio < 3.0 ? '45' : '10'} / 30
                    </span>
                  </div>
                  <div className="h-2 rounded-full bg-muted overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all ${health.debtGdpRatio < 0.5 ? 'bg-emerald-500' : health.debtGdpRatio < 1.0 ? 'bg-emerald-500' : health.debtGdpRatio < 3.0 ? 'bg-amber-500' : 'bg-red-500'}`}
                      style={{ width: `${health.debtGdpRatio < 0.5 ? 100 : health.debtGdpRatio < 1.0 ? 75 : health.debtGdpRatio < 3.0 ? 45 : 10}%` }}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {health.debtGdpRatio < 0.5 ? 'Minimal debt — economy growing' : health.debtGdpRatio < 1.0 ? 'Healthy debt level' : health.debtGdpRatio < 3.0 ? 'Elevated — monitor closely' : 'Dangerous — circuit breaker active'}
                  </p>
                </div>

                {/* Buy/sell balance score */}
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-muted-foreground">Buy / Sell Mix</span>
                    <span className="font-medium text-sky-400">
                      {Math.round((1 - Math.abs(health.buyPct - 50) / 50) * 100)} / 30
                    </span>
                  </div>
                  <div className="h-2 rounded-full bg-muted overflow-hidden">
                    <div
                      className="h-full rounded-full bg-sky-500 transition-all"
                      style={{ width: `${Math.round((1 - Math.abs(health.buyPct - 50) / 50) * 100)}%` }}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    Buy {health.buyPct.toFixed(0)}% · Sell {health.sellPct.toFixed(0)}%
                    {Math.abs(health.buyPct - 50) < 10 ? ' — balanced' : Math.abs(health.buyPct - 50) < 20 ? ' — slight bias' : ' — skewed market'}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Top volatile + undersold items */}
        {health && (health.topVolatile.length > 0 || health.topUndersold.length > 0) && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {health.topVolatile.length > 0 && (
              <Card>
                <CardContent className="p-4">
                  <p className="text-sm font-medium text-foreground mb-3 flex items-center gap-2">
                    <Zap className="w-4 h-4 text-amber-500" />
                    Most Volatile Items
                  </p>
                  <div className="space-y-2">
                    {health.topVolatile.slice(0, 5).map((item) => (
                      <div key={item.id} className="flex items-center justify-between">
                        <span className="text-sm text-foreground">{item.displayName}</span>
                        <span className={`text-sm font-medium ${item.pctChange > 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                          {item.pctChange > 0 ? '↑' : '↓'} {Math.abs(item.pctChange).toFixed(1)}%
                        </span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}

            {health.topUndersold.length > 0 && (
              <Card>
                <CardContent className="p-4">
                  <p className="text-sm font-medium text-foreground mb-3 flex items-center gap-2">
                    <TrendingDown className="w-4 h-4 text-orange-500" />
                    Most Undersold Items
                  </p>
                  <div className="space-y-2">
                    {health.topUndersold.slice(0, 5).map((item) => (
                      <div key={item.id} className="flex items-center justify-between">
                        <span className="text-sm text-foreground">{item.displayName}</span>
                        <span className="text-sm font-medium text-orange-500">
                          {item.pctChange > 0 ? '↑' : '↓'} {Math.abs(item.pctChange).toFixed(1)}%
                        </span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        )}

        {/* What moved — natural language price explanations */}
        {whatMoved.length > 0 && (
          <Card>
            <CardContent className="p-4">
              <p className="text-sm font-medium text-foreground mb-3 flex items-center gap-2">
                <Activity className="w-4 h-4 text-amber-500" />
                What Moved Today
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {whatMoved.slice(0, 6).map((item) => (
                  <div
                    key={item.itemId}
                    className="flex items-start gap-3 p-3 rounded-lg bg-muted/50 border border-muted"
                  >
                    <span className="text-xl flex-shrink-0">{item.emoji}</span>
                    <div className="min-w-0">
                      <div className="flex items-baseline gap-2">
                        <span className="text-sm font-medium text-foreground truncate">{item.displayName}</span>
                        <span className={`text-sm font-bold flex-shrink-0 ${
                          item.direction === 'up' ? 'text-emerald-500' : 'text-red-500'
                        }`}>
                          {item.percentChange > 0 ? '+' : ''}{item.percentChange.toFixed(1)}%
                        </span>
                      </div>
                      <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                        {item.explanation}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Debt-to-GDP ratio bar */}
        {debtToGdp !== null && (
          <Card>
            <CardContent className="p-4">
              <div className="flex items-center justify-between mb-2">
                <p className="text-sm font-medium text-foreground">Debt / GDP Ratio</p>
                <p className={`text-sm font-bold ${debtColor}`}>
                  {(debtToGdp * 100).toFixed(1)}%
                </p>
              </div>
              <div className="h-3 rounded-full bg-muted overflow-hidden mb-2">
                <div
                  className="h-full rounded-full transition-all"
                  style={{
                    width: `${Math.min(100, debtToGdp * 100)}%`,
                    backgroundColor: debtToGdp > 1 ? '#ef4444' : debtToGdp > 0.5 ? '#f59e0b' : '#10b981',
                  }}
                />
              </div>
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>Healthy (&lt;50%)</span>
                <span>Warning (&gt;100%)</span>
                <span>Circuit breaker at 1000%</span>
              </div>
              <p className="text-xs text-muted-foreground mt-2">
                {debtToGdp > 1
                  ? '⚠ Debt exceeds GDP. The circuit breaker pauses loan interest when debt exceeds 10× GDP.'
                  : debtToGdp > 0.5
                  ? 'Debt is elevated but manageable. Monitor for trends.'
                  : 'Debt-to-GDP is healthy. Economy is balanced.'}
              </p>
            </CardContent>
          </Card>
        )}

        {volumeMultiplier && (
          <VolumeMultiplierGauge multiplier={volumeMultiplier.multiplier} />
        )}

        <EconomyChart history={history} />
      </main>
      <Footer />
    </div>
  );
}
