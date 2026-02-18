'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { StatsCards } from '@/components/dashboard/stats-cards';
import { EconomyChart } from '@/components/economy/economy-chart';
import { VolumeMultiplierGauge } from '@/components/economy/volume-multiplier-gauge';
import { Card, CardContent } from '@/components/ui/card';
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

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <h2 className="text-2xl font-bold text-foreground">Economy Overview</h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-sm text-muted-foreground">GDP (24h)</p>
              <p className="text-xl font-bold text-foreground">
                {gdp ? formatCurrency(gdp.gdp) : '--'}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-sm text-muted-foreground">Inflation</p>
              <p className="text-xl font-bold text-foreground">
                {inflation ? `${formatPercent(inflation.averagePriceChange)} (${inflation.label})` : '--'}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-sm text-muted-foreground">Total Debt</p>
              <p className="text-xl font-bold text-foreground">
                {debt ? formatCurrency(debt.totalDebt) : '--'}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-sm text-muted-foreground">Active Loans</p>
              <p className="text-xl font-bold text-foreground">{debt ? debt.activeLoans : '--'}</p>
            </CardContent>
          </Card>
        </div>

        {volumeMultiplier && (
          <VolumeMultiplierGauge multiplier={volumeMultiplier.multiplier} />
        )}

        <EconomyChart history={history} />
      </main>
    </div>
  );
}
