'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { EconomyChart } from '@/components/economy/economy-chart';
import { VolumeMultiplierGauge } from '@/components/economy/volume-multiplier-gauge';
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
} from 'lucide-react';
import {
  api,
  type Stats,
  type EconomySnapshotDto,
  type GdpData,
  type InflationData,
  type DebtData,
  type VolumeMultiplierDto,
} from '@/lib/api';
import { formatCurrency, formatPercent } from '@/lib/format';

export default function EconomyPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [gdp, setGdp] = useState<GdpData | null>(null);
  const [inflation, setInflation] = useState<InflationData | null>(null);
  const [debt, setDebt] = useState<DebtData | null>(null);
  const [history, setHistory] = useState<EconomySnapshotDto[]>([]);
  const [volumeMultiplier, setVolumeMultiplier] = useState<VolumeMultiplierDto | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [statsData, gdpData, inflationData, debtData, historyData, vmData] = await Promise.all([
        api.stats(apiBase),
        api.economy.gdp(apiBase).catch(() => null),
        api.economy.inflation(apiBase).catch(() => null),
        api.economy.debt(apiBase).catch(() => null),
        api.economy.history(apiBase, 500).catch(() => []),
        api.economy.volumeMultiplier(apiBase).catch(() => null),
      ]);
      setStats(statsData);
      setGdp(gdpData);
      setInflation(inflationData as InflationData | null);
      setDebt(debtData as DebtData | null);
      setHistory((historyData as EconomySnapshotDto[]).reverse());
      setVolumeMultiplier(vmData as VolumeMultiplierDto | null);
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
  const gdpHealth = gdp?.gdp !== null && gdp?.gdp !== undefined && gdp.gdp > 0;
  const debtHealthy = debtToGdp !== null && debtToGdp < 1.0;
  const inflationVal = inflation?.averagePriceChange ?? null;
  const inflationHealthy = inflationVal !== null && Math.abs(inflationVal) < 2.0;
  const overallHealthy = gdpHealth && debtHealthy && inflationHealthy && (stats?.onlinePlayers ?? 0) > 0;
  const onlinePlayers = stats?.onlinePlayers ?? 0;

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

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={onlinePlayers} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold text-foreground">Economy Overview</h2>
          {/* Overall health badge */}
          <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium border ${
            overallHealthy
              ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-400'
              : gdpHealth && onlinePlayers > 0
              ? 'bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-400'
              : 'bg-red-500/10 border-red-500/30 text-red-500'
          }`}>
            {overallHealthy
              ? <><CheckCircle className="w-3.5 h-3.5" /> Economy Healthy</>
              : !gdpHealth && onlinePlayers > 0
              ? <><AlertTriangle className="w-3.5 h-3.5" /> Economy Flat</>
              : onlinePlayers === 0
              ? <><Activity className="w-3.5 h-3.5" /> No Players</>
              : <><AlertTriangle className="w-3.5 h-3.5" /> Needs Attention</>
            }
          </div>
        </div>

        {/* Summary stat cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <DollarSign className="w-4 h-4 text-amber-500" />
                <p className="text-sm text-muted-foreground">GDP (24h)</p>
              </div>
              <p className="text-xl font-bold text-foreground">
                {gdp ? formatCurrency(gdp.gdp) : '--'}
              </p>
              {gdp && history.length >= 2 && (() => {
                const old = history[0]?.gdp ?? 0;
                const pct = old > 0 ? ((gdp.gdp - old) / old) * 100 : 0;
                return (
                  <p className={`text-xs font-medium mt-1 ${pct >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                    {pct >= 0 ? '↑' : '↓'} {Math.abs(pct).toFixed(1)}% vs start of window
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
                  <span className="text-xs opacity-70">({inflation?.label ?? '--'})</span>
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
        </div>

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
                  ? '⚠ Debt exceeds GDP. The circuit breaker pauses loan interest accumulation when debt exceeds 10× GDP.'
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
    </div>
  );
}
