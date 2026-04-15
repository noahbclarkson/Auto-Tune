'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { TradingTimeline } from '@/components/portfolio/trading-timeline';
import { PnLHistoryChart } from '@/components/portfolio/pnl-history-chart';
import { api, type Stats, type PortfolioDto, type HoldingDto, type TransactionFeedDto, type PnLHistoryDto } from '@/lib/api';
import { formatCurrency, formatLargeCurrency, formatPercent } from '@/lib/format';
import { Badge } from '@/components/ui/badge';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { DiscoveryOverlay } from '@/components/onboarding/discovery-overlay';
import { BadgesTab } from '@/components/portfolio/badges-tab';
import { MarketImpactTab } from '@/components/portfolio/market-impact-tab';

type Tab = 'holdings' | 'trades' | 'badges' | 'impact';

export default function PortfolioPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [searchName, setSearchName] = useState('');
  const [playerName, setPlayerName] = useState<string | null>(null);
  const [portfolio, setPortfolio] = useState<PortfolioDto | null>(null);
  const [transactions, setTransactions] = useState<TransactionFeedDto[]>([]);
  const [pnlHistory, setPnlHistory] = useState<PnLHistoryDto[]>([]);
  const [activeTab, setActiveTab] = useState<Tab>('holdings');
  const [loading, setLoading] = useState(false);
  const [loadingTrades, setLoadingTrades] = useState(false);
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

  const loadPortfolio = useCallback((name: string) => {
    setPlayerName(name);
    setPortfolio(null);
    setTransactions([]);
    setPnlHistory([]);
    setError(null);
    setActiveTab('holdings');
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

    setLoadingTrades(true);
    api.portfolio.transactions(apiBase, name, 50)
      .then(setTransactions)
      .catch(() => setTransactions([]))
      .finally(() => setLoadingTrades(false));

    api.portfolio.pnlHistory(apiBase, name)
      .then(setPnlHistory)
      .catch(() => setPnlHistory([]));
  }, [apiBase]);

  const handleSearch = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    const name = searchName.trim();
    if (!name) return;
    loadPortfolio(name);
  }, [searchName, loadPortfolio]);

  return (
    <div className="min-h-screen bg-background">
      <DiscoveryOverlay page="portfolio" />
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">

        {/* Title + Search */}
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Player Portfolio</h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Holdings, P&amp;L, and trading history
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
          {/* CSV Export button — only shown after a player is searched */}
          {playerName && (
            <a
              href={`${apiBase}/api/portfolio/${encodeURIComponent(playerName)}/transactions.csv`}
              download={`autotune-trades-${playerName}.csv`}
              className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:bg-muted transition-colors flex items-center gap-2"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></svg>
              Export History (CSV)
            </a>
          )}
        </div>

        {/* Empty state */}
        {!playerName && !loading && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div className="text-5xl mb-4">📊</div>
            <h3 className="text-lg font-semibold text-foreground mb-1">Enter a player name</h3>
            <p className="text-sm text-muted-foreground max-w-xs">
              Search for any player who has traded on this server to see their portfolio, holdings, P&amp;L, and trade history.
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
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
              {[...Array(5)].map((_, i) => (
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
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
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
                label="Realized P&amp;L"
                value={portfolio.totalRealizedPnl > 0
                  ? '+' + formatCurrency(portfolio.totalRealizedPnl)
                  : formatCurrency(portfolio.totalRealizedPnl)}
                sub="from completed trades"
                highlight={portfolio.totalRealizedPnl > 0}
                danger={portfolio.totalRealizedPnl < 0}
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

            {/* P&L chart */}
            {portfolio.holdings.length > 1 && (
              <PnLChart holdings={portfolio.holdings} />
            )}

            {/* P&L history over time */}
            {pnlHistory.length > 1 && (
              <PnLHistoryChart history={pnlHistory} />
            )}

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

            {/* Tabs */}
            <div className="flex gap-1 border-b border-border">
              <button
                onClick={() => setActiveTab('holdings')}
                className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                  activeTab === 'holdings'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Holdings
              </button>
              <button
                onClick={() => setActiveTab('trades')}
                className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                  activeTab === 'trades'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Recent Trades
                {transactions.length > 0 && (
                  <span className="ml-1.5 text-xs text-muted-foreground">({transactions.length})</span>
                )}
              </button>
              <button
                onClick={() => setActiveTab('badges')}
                className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                  activeTab === 'badges'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Achievements
              </button>
              <button
                onClick={() => setActiveTab('impact')}
                className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                  activeTab === 'impact'
                    ? 'border-primary text-primary'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
              >
                Market Impact
              </button>
            </div>

            {/* Holdings tab */}
            {activeTab === 'holdings' && (
              portfolio.holdings.length > 0 ? (
                <HoldingsTable holdings={portfolio.holdings} />
              ) : (
                <Card>
                  <CardContent className="py-8 text-center text-sm text-muted-foreground">
                    No holdings found in the last 90 days of transaction history.
                  </CardContent>
                </Card>
              )
            )}

            {/* Trades tab */}
            {activeTab === 'trades' && (
              loadingTrades ? (
                <div className="h-48 rounded-xl bg-muted animate-pulse" />
              ) : transactions.length > 0 ? (
                <TradingTimeline transactions={transactions} />
              ) : (
                <Card>
                  <CardContent className="py-8 text-center text-sm text-muted-foreground">
                    No trades found for this player.
                  </CardContent>
                </Card>
              )
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

            {/* Badges tab */}
            {activeTab === 'badges' && playerName && (
              <BadgesTab playerName={playerName} apiBase={apiBase} />
            )}

            {/* Market Impact tab */}
            {activeTab === 'impact' && playerName && (
              <MarketImpactTab playerName={playerName} apiBase={apiBase} />
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
        <p className={`text-2xl font-bold mt-1 font-mono ${danger ? 'text-destructive' : highlight ? 'text-emerald-600 dark:text-emerald-400' : 'text-foreground'}`}>
          {value}
        </p>
        <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>
      </CardContent>
    </Card>
  );
}

function PnLChart({ holdings }: { holdings: HoldingDto[] }) {
  // Sort by absolute P&L descending, take top 10
  const data = [...holdings]
    .sort((a, b) => Math.abs(b.unrealizedPnl) - Math.abs(a.unrealizedPnl))
    .slice(0, 10)
    .map((h) => ({
      name: h.displayName.length > 18 ? h.displayName.slice(0, 17) + '…' : h.displayName,
      fullName: h.displayName,
      pnl: h.unrealizedPnl,
      pct: h.pnlPct,
    }));

  if (data.length === 0) return null;

  const maxAbs = Math.max(...data.map((d) => Math.abs(d.pnl)));

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Unrealized P&amp;L by Item</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-48">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ left: 0, right: 16 }}>
              <XAxis
                type="number"
                domain={[-maxAbs * 1.1, maxAbs * 1.1]}
                tickFormatter={(v) => `$${Math.abs(v) >= 1000 ? (v / 1000).toFixed(1) + 'K' : v.toFixed(0)}`}
                tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={130}
                tick={{ fontSize: 11, fill: 'hsl(var(--foreground))' }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                formatter={(value) => [formatCurrency(value as number), 'Unrealized P&L']}
                labelFormatter={(label) => data.find((d) => d.name === label)?.fullName ?? (label as string)}
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: '0.5rem',
                  fontSize: '12px',
                }}
              />
              <Bar dataKey="pnl" radius={[0, 4, 4, 0]}>
                {data.map((entry, index) => (
                  <Cell
                    key={`cell-${index}`}
                    fill={entry.pnl >= 0
                      ? 'hsl(160, 84%, 39%)' // emerald-500
                      : 'hsl(0, 84%, 60%)'}  // red-500
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-2 flex items-center gap-4 text-[10px] text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="inline-block w-3 h-1.5 rounded-sm bg-emerald-500" />
            Profit
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block w-3 h-1.5 rounded-sm bg-red-500" />
            Loss
          </span>
        </div>
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
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Realized P&amp;L</th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => {
                const unrealClass = h.unrealizedPnl > 0
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : h.unrealizedPnl < 0
                  ? 'text-destructive'
                  : 'text-muted-foreground';
                const realizedClass = h.realizedPnl > 0
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : h.realizedPnl < 0
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
                    <td className={`px-3 py-2.5 text-right font-mono ${unrealClass}`}>
                      <div className="flex flex-col items-end">
                        <span>{h.unrealizedPnl >= 0 ? '+' : ''}{formatCurrency(h.unrealizedPnl)}</span>
                        <span className="text-xs opacity-75">{formatPercent(h.pnlPct)}</span>
                      </div>
                    </td>
                    <td className={`px-3 py-2.5 text-right font-mono ${realizedClass}`}>
                      {h.realizedPnl !== 0
                        ? <span>{h.realizedPnl >= 0 ? '+' : ''}{formatCurrency(h.realizedPnl)}</span>
                        : <span className="text-muted-foreground">—</span>
                      }
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
