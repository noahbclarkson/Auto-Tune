'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { api, type Stats, type PortfolioDto, type HoldingDto, type ActiveLoanDto } from '@/lib/api';
import { formatCurrency, formatLargeCurrency, formatPercent } from '@/lib/format';
import { Badge } from '@/components/ui/badge';

export default function PortfolioPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [searchName, setSearchName] = useState('');
  const [playerName, setPlayerName] = useState<string | null>(null);
  const [portfolio, setPortfolio] = useState<PortfolioDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const statsData = await api.stats(apiBase);
      setStats(statsData);
    } catch {
      // non-critical
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSearch = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    const name = searchName.trim();
    if (!name) return;
    setPlayerName(name);
    setPortfolio(null);
    setError(null);
    setLoading(true);

    api.portfolio.get(apiBase, name)
      .then(setPortfolio)
      .catch((err: Error) => {
        if (err.message.includes('404')) {
          setError(`Player "${name}" not found. Make sure they have traded on this server.`);
        } else {
          setError(`Failed to load portfolio: ${err.message}`);
        }
      })
      .finally(() => setLoading(false));
  }, [searchName, apiBase]);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">

        {/* Title + Search */}
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Player Portfolio</h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              See any player&apos;s holdings, P&amp;L, and net worth
            </p>
          </div>
          <form onSubmit={handleSearch} className="flex gap-2">
            <input
              type="text"
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              placeholder="Player name..."
              className="rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 w-48"
            />
            <button
              type="submit"
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
            >
              Search
            </button>
          </form>
        </div>

        {/* Empty state */}
        {!playerName && !loading && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="text-5xl mb-4">📊</div>
            <h3 className="text-lg font-semibold text-foreground mb-1">Enter a player name</h3>
            <p className="text-sm text-muted-foreground max-w-xs">
              Search for any player who has traded on this server to see their portfolio, holdings, and P&amp;L.
            </p>
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            {error}
          </div>
        )}

        {/* Loading skeleton */}
        {loading && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-24 rounded-xl bg-muted animate-pulse" />
              ))}
            </div>
            <div className="h-64 rounded-xl bg-muted animate-pulse" />
          </div>
        )}

        {/* Portfolio data */}
        {portfolio && !loading && (
          <div className="space-y-6">
            {/* Summary cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <StatCard
                label="Net Worth"
                value={formatLargeCurrency(portfolio.netWorth)}
                sub={formatCurrency(portfolio.vaultBalance) + ' cash'}
                highlight
              />
              <StatCard
                label="Vault Balance"
                value={formatLargeCurrency(portfolio.vaultBalance)}
                sub={`${portfolio.transactionCount.toLocaleString()} txns`}
              />
              <StatCard
                label="Holdings Value"
                value={formatLargeCurrency(portfolio.holdingsValue)}
                sub={`${portfolio.holdings.length} item${portfolio.holdings.length !== 1 ? 's' : ''}`}
              />
              <StatCard
                label="Total Debt"
                value={portfolio.totalDebt > 0 ? formatCurrency(portfolio.totalDebt) : '$0.00'}
                sub={
                  portfolio.activeLoans.length > 0
                    ? `${portfolio.activeLoans.length} active loan${portfolio.activeLoans.length !== 1 ? 's' : ''}`
                    : 'No active loans'
                }
                danger={portfolio.totalDebt > 0}
              />
            </div>

            {/* Credit score + player info bar */}
            <div className="flex items-center gap-3 flex-wrap">
              <Badge variant={portfolio.creditScore >= 700 ? 'success' : portfolio.creditScore >= 400 ? 'secondary' : 'destructive'}>
                Credit Score: {portfolio.creditScore}
              </Badge>
              {portfolio.uuid && (
                <span className="text-xs text-muted-foreground font-mono">
                  {portfolio.uuid.substring(0, 8)}…
                </span>
              )}
            </div>

            {/* Holdings table */}
            {portfolio.holdings.length > 0 ? (
              <HoldingsTable holdings={portfolio.holdings} />
            ) : (
              <Card>
                <CardContent className="py-8 text-center text-sm text-muted-foreground">
                  No holdings found in the last 90 days of transaction history.
                </CardContent>
              </Card>
            )}

            {/* Active loans */}
            {portfolio.activeLoans.length > 0 && (
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-base">Active Loans</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="rounded-md border border-border overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-border bg-muted/50">
                          <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Principal</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Balance</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Rate</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Due</th>
                        </tr>
                      </thead>
                      <tbody>
                        {portfolio.activeLoans.map((loan) => (
                          <tr key={loan.loanId} className="border-b border-border last:border-0">
                            <td className="px-3 py-2.5">{formatCurrency(loan.principal)}</td>
                            <td className="px-3 py-2.5 text-right text-destructive">{formatCurrency(loan.currentBalance)}</td>
                            <td className="px-3 py-2.5 text-right">{loan.interestRate.toFixed(1)}%</td>
                            <td className="px-3 py-2.5 text-right text-muted-foreground">
                              {new Date(loan.dueDate * 1000).toLocaleDateString()}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        )}
      </main>
    </div>
  );
}

function StatCard({ label, value, sub, highlight, danger }: {
  label: string;
  value: string;
  sub: string;
  highlight?: boolean;
  danger?: boolean;
}) {
  return (
    <Card className={highlight ? 'border-primary/40 bg-primary/[0.03]' : danger ? 'border-destructive/30' : ''}>
      <CardContent className="p-4">
        <p className="text-xs text-muted-foreground font-medium uppercase tracking-wide">{label}</p>
        <p className={`text-2xl font-bold mt-1 font-mono ${danger ? 'text-destructive' : 'text-foreground'}`}>
          {value}
        </p>
        <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>
      </CardContent>
    </Card>
  );
}

function HoldingsTable({ holdings }: { holdings: HoldingDto[] }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Holdings</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="rounded-md border border-border overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Item</th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Qty</th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Avg Buy</th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Current</th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Value</th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Unreal. P&amp;L</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => {
                const pnlClass = h.unrealizedPnl > 0
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : h.unrealizedPnl < 0
                  ? 'text-destructive'
                  : 'text-muted-foreground';
                return (
                  <tr key={h.itemId} className="border-b border-border last:border-0 hover:bg-muted/40 transition-colors">
                    <td className="px-3 py-2.5">
                      <div className="flex flex-col">
                        <span className="font-medium text-foreground">{h.displayName}</span>
                        <span className="text-xs text-muted-foreground font-mono">{h.material}</span>
                      </div>
                    </td>
                    <td className="px-3 py-2.5 text-right font-mono">{h.netQuantity.toLocaleString()}</td>
                    <td className="px-3 py-2.5 text-right font-mono text-muted-foreground">{formatCurrency(h.avgBuyPrice)}</td>
                    <td className="px-3 py-2.5 text-right font-mono">{formatCurrency(h.currentPrice)}</td>
                    <td className="px-3 py-2.5 text-right font-mono font-medium">{formatCurrency(h.currentValue)}</td>
                    <td className={`px-3 py-2.5 text-right font-mono ${pnlClass}`}>
                      <div className="flex flex-col items-end">
                        <span>{h.unrealizedPnl >= 0 ? '+' : ''}{formatCurrency(h.unrealizedPnl)}</span>
                        <span className="text-xs opacity-75">{formatPercent(h.pnlPct)}</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
