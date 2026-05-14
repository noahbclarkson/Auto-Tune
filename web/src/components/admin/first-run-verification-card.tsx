'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { api, type AdminHealthDto, type Stats } from '@/lib/api';
import {
  CheckCircle,
  XCircle,
  Loader2,
  AlertTriangle,
} from 'lucide-react';

type CheckState = 'pending' | 'loading' | 'pass' | 'fail' | 'warn';

interface Check {
  label: string;
  state: CheckState;
  detail: string;
}

function CheckRow({ check }: { check: Check }) {
  const stateConfig = {
    pending: { icon: <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />, label: 'text-muted-foreground', detail: 'text-muted-foreground' },
    loading: { icon: <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />, label: 'text-muted-foreground', detail: 'text-muted-foreground' },
    pass:    { icon: <CheckCircle className="w-4 h-4 text-emerald-400" />,         label: 'text-emerald-400',      detail: 'text-muted-foreground' },
    fail:    { icon: <XCircle className="w-4 h-4 text-red-400" />,                  label: 'text-red-400',          detail: 'text-red-400' },
    warn:    { icon: <AlertTriangle className="w-4 h-4 text-amber-400" />,          label: 'text-amber-400',        detail: 'text-muted-foreground' },
  }[check.state];

  return (
    <div className="flex items-center gap-3 py-2 border-b border-border/40 last:border-0">
      <div className="flex-shrink-0">{stateConfig.icon}</div>
      <div className="flex-1 min-w-0">
        <div className={`text-sm font-medium ${stateConfig.label}`}>{check.label}</div>
        <div className={`text-xs ${stateConfig.detail}`}>{check.detail}</div>
      </div>
    </div>
  );
}

export function FirstRunVerificationCard() {
  const { apiBase } = useAppContext();

  const [stats, setStats] = useState<Stats | null>(null);
  const [health, setHealth] = useState<AdminHealthDto | null>(null);
  const [statsState, setStatsState] = useState<CheckState>('loading');
  const [itemsState, setItemsState] = useState<CheckState>('loading');
  const [tradeState, setTradeState] = useState<CheckState>('loading');
  const [circuitState, setCircuitState] = useState<CheckState>('loading');

  const fetchAll = useCallback(async () => {
    try {
      const [statsData, healthData] = await Promise.all([
        api.stats(apiBase).catch(() => null),
        api.admin.health(apiBase).catch(() => null),
      ]);

      setStats(statsData);
      setHealth(healthData);
      setStatsState(statsData ? 'pass' : 'fail');

      if (healthData) {
        // Items tracked
        if (statsData && statsData.totalItems > 0) {
          setItemsState('pass');
        } else if (statsData && statsData.totalItems === 0) {
          setItemsState('warn');
        } else {
          setItemsState(healthData.gdp > 0 ? 'pass' : 'warn');
        }

        // Trading active
        if (healthData.gdp > 0) {
          setTradeState('pass');
        } else {
          setTradeState('warn');
        }

        // Trade/circuit health
        if (healthData.circuitBreakerTier === 'TIER2' || healthData.circuitBreakerTier === 'TIER3') {
          setCircuitState('fail');
        } else if (
          healthData.circuitBreakerTier === 'TIER1' ||
          healthData.buyPct < 20 ||
          healthData.buyPct > 80
        ) {
          setCircuitState('warn');
        } else {
          setCircuitState('pass');
        }
      } else {
        setItemsState('loading');
        setTradeState('loading');
        setCircuitState('loading');
      }
    } catch {
      setStatsState('fail');
      setItemsState('fail');
      setTradeState('fail');
      setCircuitState('fail');
    }
  }, [apiBase]);

  useEffect(() => {
    fetchAll();
    const interval = setInterval(fetchAll, 20000);
    return () => clearInterval(interval);
  }, [fetchAll]);

  const checks: Check[] = [
    {
      label: 'Plugin active',
      state: statsState,
      detail: stats
        ? `"${stats.serverName}" online — ${stats.onlinePlayers} player${stats.onlinePlayers !== 1 ? 's' : ''} connected`
        : 'Could not reach plugin API',
    },
    {
      label: 'Items tracked',
      state: itemsState,
      detail: stats
        ? stats.totalItems > 0
          ? `${stats.totalItems.toLocaleString()} items with prices`
          : 'No items with prices yet — add base prices in config or /at admin item baseprice'
        : health && health.gdp > 0
        ? 'Items tracked via economy activity'
        : 'Waiting for items to be configured',
    },
    {
      label: 'Prices updating',
      state: tradeState,
      detail: health
        ? health.gdp > 0
          ? `GDP $${health.gdp.toLocaleString()} recorded — market tick is running`
          : 'No economy activity yet — prices will update after first trades'
        : 'Waiting for health data…',
    },
    {
      label: 'Trade mix healthy',
      state: circuitState,
      detail: health
        ? health.circuitBreakerTier !== 'NORMAL'
          ? `Circuit ${health.circuitBreakerTier} active — see Circuit Breaker Tiers section below`
          : health.buyPct >= 20 && health.buyPct <= 80
          ? `Buy/Sell ${health.buyPct.toFixed(0)}%/${health.sellPct.toFixed(0)}% — enough two-sided activity for price discovery`
          : `Buy/Sell ${health.buyPct.toFixed(0)}%/${health.sellPct.toFixed(0)}% — watch for one-sided activity`
        : 'Checking…',
    },
  ];

  const allPass = checks.every((c) => c.state === 'pass');
  const anyFail = checks.some((c) => c.state === 'fail');
  const anyWarn = checks.some((c) => c.state === 'warn');

  return (
    <Card className={`border-border ${allPass ? 'border-emerald-900/40' : anyFail ? 'border-red-900/40' : 'border-amber-900/40'}`}>
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          {allPass ? (
            <CheckCircle className="w-4 h-4 text-emerald-400" />
          ) : anyFail ? (
            <XCircle className="w-4 h-4 text-red-400" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-amber-400" />
          )}
          {allPass ? 'Economy Verified' : anyFail ? 'Economy Setup Issues' : 'Economy Starting Up'}
        </CardTitle>
        <p className="text-xs text-muted-foreground mt-1">
          {allPass
            ? 'Your economy is live and healthy. Prices update every 5 minutes based on player trades.'
            : anyFail
            ? 'One or more checks failed. Review the details below and fix issues before your economy goes live.'
            : 'Checks in progress — these will resolve once players start trading.'}
        </p>
      </CardHeader>
      <CardContent className="space-y-1">
        {checks.map((check) => (
          <CheckRow key={check.label} check={check} />
        ))}

        {anyWarn && !anyFail && (
          <div className="mt-3 px-3 py-2.5 rounded-md bg-amber-500/10 border border-amber-500/20">
            <div className="flex items-start gap-2">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-400 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-amber-300 leading-relaxed">
                <strong>No trades yet?</strong> Auto-Tune needs player activity to set prices.
                Make sure <code className="bg-amber-950/50 px-1 py-0.5 rounded text-amber-200">buy: true</code> and{' '}
                <code className="bg-amber-950/50 px-1 py-0.5 rounded text-amber-200">sell: true</code> are set on your
                key items in <code className="bg-amber-950/50 px-1 py-0.5 rounded text-amber-200">shops.yml</code>.
                Players can trade with <code className="bg-amber-950/50 px-1 py-0.5 rounded text-amber-200">/shop</code> and{' '}
                <code className="bg-amber-950/50 px-1 py-0.5 rounded text-amber-200">/sell</code>.
              </div>
            </div>
          </div>
        )}

        {allPass && (
          <div className="mt-3 px-3 py-2.5 rounded-md bg-emerald-500/10 border border-emerald-500/20">
            <div className="flex items-start gap-2">
              <CheckCircle className="w-3.5 h-3.5 text-emerald-400 mt-0.5 flex-shrink-0" />
              <div className="text-xs text-emerald-300 leading-relaxed">
                <strong>Your economy is live.</strong> Prices update every 5 minutes.
                The bundled dashboard at port 8989 shows live prices, charts, and loan status in real time.
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}