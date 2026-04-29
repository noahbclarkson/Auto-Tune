'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api, type Stats, type AuctionOrderDto, type AuctionFillDto, type AuctionMaterialDto } from '@/lib/api';
import { formatCurrency, formatTimeAgo } from '@/lib/format';
import { TrendingUp, TrendingDown, Package, ArrowUpDown, Search, User, BarChart2 } from 'lucide-react';
import { DepthChart } from '@/components/auction/depth-chart';
import { type AuctionDepthData } from '@/components/auction/depth-chart-types';
import { LineChart, Line, ResponsiveContainer, Tooltip } from 'recharts';

interface AuctionStats {
  totalOrders: number;
  totalFills: number;
  activeOrders: number;
  materialsWithOrders: number;
  bookSummary: Record<string, { bestBid: number | null; bestAsk: number | null; bidCount: number; askCount: number }>;
  recentFills: Array<{ id: string; quantity: number; price: number; filledAt: number }>;
}

function StatsBar({ stats, fillRate }: { stats: AuctionStats; fillRate: Array<{ date: string; count: number }> }) {
  const sparkData = fillRate.map((d) => ({ date: d.date.slice(5), count: d.count }));
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Active Orders</p>
          <p className="text-xl font-bold text-foreground">{stats.activeOrders}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Total Fills</p>
          <p className="text-xl font-bold text-foreground">{stats.totalFills.toLocaleString()}</p>
          {sparkData.length > 1 && (
            <div className="h-8 mt-1">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={sparkData}>
                  <Line type="monotone" dataKey="count" stroke="#6366f1" strokeWidth={1.5} dot={false} />
                  <Tooltip
                    labelFormatter={(label) => `Day ${label}`}
                    formatter={(value: number | undefined) => [value ?? 0, 'Fills']}
                    contentStyle={{ fontSize: 11, padding: '2px 6px' }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Materials Listed</p>
          <p className="text-xl font-bold text-foreground">{Object.keys(stats.bookSummary).length}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Spreadable Items</p>
          <p className="text-xl font-bold text-foreground">
            {Object.values(stats.bookSummary).filter((s) => s.bestBid !== null && s.bestAsk !== null).length}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function SideBadge({ side }: { side: 'BUY' | 'SELL' }) {
  return (
    <Badge variant={side === 'BUY' ? 'default' : 'secondary'} className="text-xs">
      {side === 'BUY' ? (
        <><TrendingUp className="w-3 h-3 mr-1" />Buy</>
      ) : (
        <><TrendingDown className="w-3 h-3 mr-1" />Sell</>
      )}
    </Badge>
  );
}

function StatusBadge({ status }: { status: string }) {
  const variants: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    ACTIVE: 'default',
    FILLED: 'secondary',
    CANCELLED: 'destructive',
    EXPIRED: 'outline',
  };
  return <Badge variant={variants[status] ?? 'outline'} className="text-xs">{status}</Badge>;
}

function OrderRow({ order }: { order: AuctionOrderDto }) {
  const fillPct = order.originalQuantity > 0
    ? Math.round((order.filledQuantity / order.originalQuantity) * 100)
    : 0;
  return (
    <tr className="border-b border-border last:border-0 hover:bg-muted/30">
      <td className="px-3 py-2.5 text-sm">
        <Link
          href={`/auction/order?id=${encodeURIComponent(order.id)}`}
          className="font-medium text-foreground hover:text-primary transition-colors"
          title={`View order ${order.id}`}
        >
          {order.material}
        </Link>
        <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">{order.id.slice(0, 8)}</p>
      </td>
      <td className="px-3 py-2.5 text-center"><SideBadge side={order.side} /></td>
      <td className="px-3 py-2.5 text-right font-medium text-foreground">
        {formatCurrency(order.price)}
      </td>
      <td className="px-3 py-2.5 text-right text-muted-foreground text-sm">
        {order.remainingQuantity.toLocaleString()} / {order.originalQuantity.toLocaleString()}
      </td>
      <td className="px-3 py-2.5 text-right">
        <div className="flex items-center justify-end gap-2">
          <div className="w-12 h-1.5 bg-muted rounded-full overflow-hidden">
            <div className="h-full bg-primary rounded-full" style={{ width: `${fillPct}%` }} />
          </div>
          <span className="text-xs text-muted-foreground w-8">{fillPct}%</span>
        </div>
      </td>
      <td className="px-3 py-2.5 text-center"><StatusBadge status={order.status} /></td>
      <td className="px-3 py-2.5 text-right text-muted-foreground text-xs">
        {formatTimeAgo(order.createdAt)}
      </td>
    </tr>
  );
}

function OrdersTable({ orders, materialFilter }: { orders: AuctionOrderDto[]; materialFilter: string }) {
  const filtered = materialFilter
    ? orders.filter((o) => o.material.toLowerCase().includes(materialFilter.toLowerCase()))
    : orders;
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">
          Active Orders
          {materialFilter && (
            <span className="ml-2 text-sm font-normal text-muted-foreground">
              — filtered: <span className="text-primary">{materialFilter}</span>
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {filtered.length === 0 ? (
          <p className="text-sm text-muted-foreground py-8 text-center">
            {materialFilter ? 'No orders match that filter' : 'No active orders'}
          </p>
        ) : (
          <div className="rounded-md border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Material</th>
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Side</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Price</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Remaining</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Filled</th>
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Status</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Age</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((o) => <OrderRow key={o.id} order={o} />)}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function RecentFills({ fills }: { fills: AuctionFillDto[] }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Recent Fills</CardTitle>
      </CardHeader>
      <CardContent>
        {fills.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No recent fills</p>
        ) : (
          <div className="space-y-3">
            {fills.map((f) => (
              <div key={f.id} className="flex items-center justify-between py-2 border-b border-border last:border-0">
                <div className="flex items-center gap-3">
                  <div className="p-1.5 rounded bg-primary/10">
                    <ArrowUpDown className="w-4 h-4 text-primary" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-foreground">
                      {f.quantity.toLocaleString()} @ {formatCurrency(f.price)}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Total: {formatCurrency(f.total)}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">{formatTimeAgo(f.filledAt)}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function MaterialsBook({ materials, onSelectMaterial }: { materials: AuctionMaterialDto[]; onSelectMaterial?: (mat: string) => void }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">
          <Package className="w-4 h-4 inline mr-1" />Material Summary
        </CardTitle>
      </CardHeader>
      <CardContent>
        {materials.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No materials listed</p>
        ) : (
          <div className="rounded-md border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Material</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Best Bid</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Best Ask</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Spread</th>
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Orders</th>
                </tr>
              </thead>
              <tbody>
                {materials.map((m) => {
                  const spread = m.bestBid !== null && m.bestAsk !== null
                    ? ((m.bestAsk - m.bestBid) / m.bestBid * 100).toFixed(1)
                    : null;
                  return (
                    <tr key={m.material} className="border-b border-border last:border-0 hover:bg-muted/30">
                      <td className="px-3 py-2.5">
                        <button
                          type="button"
                          onClick={() => onSelectMaterial?.(m.material)}
                          className="font-medium text-foreground hover:text-primary transition-colors text-left"
                        >
                          {m.material}
                        </button>
                      </td>
                      <td className="px-3 py-2.5 text-right text-green-600 dark:text-green-400">
                        {m.bestBid !== null ? formatCurrency(m.bestBid) : '—'}
                      </td>
                      <td className="px-3 py-2.5 text-right text-red-600 dark:text-red-400">
                        {m.bestAsk !== null ? formatCurrency(m.bestAsk) : '—'}
                      </td>
                      <td className="px-3 py-2.5 text-right text-muted-foreground">
                        {spread !== null ? `${spread}%` : '—'}
                      </td>
                      <td className="px-3 py-2.5 text-center text-muted-foreground">
                        {m.totalOrders}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

type Tab = 'orders' | 'fills' | 'materials' | 'depth' | 'myorders';
const TABS: { id: Tab; label: string }[] = [
  { id: 'orders', label: 'Active Orders' },
  { id: 'fills', label: 'Recent Fills' },
  { id: 'materials', label: 'Materials' },
  { id: 'depth', label: 'Depth' },
  { id: 'myorders', label: 'My Orders' },
];

export default function AuctionPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [orders, setOrders] = useState<AuctionOrderDto[]>([]);
  const [fills, setFills] = useState<AuctionFillDto[]>([]);
  const [materials, setMaterials] = useState<AuctionMaterialDto[]>([]);
  const [auctionStats, setAuctionStats] = useState<AuctionStats | null>(null);
  const [tab, setTab] = useState<Tab>('orders');
  const [materialFilter, setMaterialFilter] = useState('');
  const [depthMaterial, setDepthMaterial] = useState<string>('');
  const [depthData, setDepthData] = useState<AuctionDepthData | null>(null);
  const [depthLoading, setDepthLoading] = useState(false);
  const [myOrdersPlayer, setMyOrdersPlayer] = useState('');
  const [myOrdersResult, setMyOrdersResult] = useState<AuctionOrderDto[] | null>(null);
  const [myOrdersError, setMyOrdersError] = useState<string | null>(null);
  const [fillRateData, setFillRateData] = useState<Array<{ date: string; count: number }>>([]);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [statsData, auctionStatsData, ordersData, fillsData, materialsData, _depthResult, fillRate] = await Promise.all([
        api.stats(apiBase),
        api.auction.stats(apiBase).catch(() => null),
        api.auction.orders(apiBase).catch(() => [] as AuctionOrderDto[]),
        api.auction.fills(apiBase, 25).catch(() => [] as AuctionFillDto[]),
        api.auction.materials(apiBase).catch(() => [] as AuctionMaterialDto[]),
        depthData !== null || depthMaterial === '' ? Promise.resolve() : api.auction.depth(apiBase, depthMaterial, 8).then(setDepthData).catch(() => setDepthData(null)),
        api.auction.fillRate(apiBase, 7).catch(() => [] as { date: string; count: number }[]),
      ]);
      void _depthResult;
      setStats(statsData);
      setAuctionStats(auctionStatsData);
      setOrders(ordersData);
      setFills(fillsData);
      setMaterials(materialsData);
      setFillRateData(fillRate);
      setError(null);
    } catch {
      setError('Could not load auction data. Is the server running?');
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
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Auction House</h2>
            <p className="text-sm text-muted-foreground mt-0.5">
              Real-time order book — live data from this server
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="h-2 w-2 rounded-full bg-green-500 animate-pulse" />
            <span className="text-xs text-muted-foreground">Live</span>
          </div>
        </div>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950 p-4">
            <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
          </div>
        )}

        {auctionStats && <StatsBar stats={auctionStats} fillRate={fillRateData} />}

        <div className="flex gap-1 border-b border-border">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
                tab === t.id
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'orders' && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Search className="w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Filter by material (e.g. diamond, iron_ingot)..."
                value={materialFilter}
                onChange={(e) => setMaterialFilter(e.target.value)}
                className="flex-1 text-sm bg-transparent border-b border-border outline-none focus:border-primary transition-colors px-1 py-1"
              />
              {materialFilter && (
                <button
                  onClick={() => setMaterialFilter('')}
                  className="text-xs text-muted-foreground hover:text-foreground"
                >
                  Clear
                </button>
              )}
            </div>
            <OrdersTable orders={orders} materialFilter={materialFilter} />
          </div>
        )}
        {tab === 'fills' && <RecentFills fills={fills} />}
        {tab === 'materials' && <MaterialsBook materials={materials} onSelectMaterial={(mat) => { setDepthMaterial(mat); setTab('depth'); }} />}
        {tab === 'depth' && (
          <DepthTab
            material={depthMaterial}
            data={depthData}
            loading={depthLoading}
            onLoad={(mat) => { setDepthMaterial(mat); setDepthLoading(true); api.auction.depth(apiBase, mat, 8).then(setDepthData).catch(() => setDepthData(null)).finally(() => setDepthLoading(false)); }}
          />
        )}
        {tab === 'myorders' && (
          <MyOrdersPanel
            apiBase={apiBase}
            player={myOrdersPlayer}
            setPlayer={setMyOrdersPlayer}
            result={myOrdersResult}
            setResult={setMyOrdersResult}
            error={myOrdersError}
            setError={setMyOrdersError}
          />
        )}
      </main>
    </div>
  );
}

function MyOrdersPanel({
  apiBase,
  player,
  setPlayer,
  result,
  setResult,
  error,
  setError,
}: {
  apiBase: string;
  player: string;
  setPlayer: (v: string) => void;
  result: AuctionOrderDto[] | null;
  setResult: (v: AuctionOrderDto[] | null) => void;
  error: string | null;
  setError: (v: string | null) => void;
}) {
  const [loading, setLoading] = useState(false);

  const handleLookup = async () => {
    if (!player.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const data = await api.auction.player(apiBase, player.trim());
      setResult(data);
    } catch {
      setError('Could not load orders for that player. Check the name and try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base flex items-center gap-2">
          <User className="w-4 h-4" />
          My Orders
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Enter player name..."
            value={player}
            onChange={(e) => setPlayer(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleLookup()}
            className="flex-1 text-sm bg-transparent border border-border rounded px-3 py-2 outline-none focus:border-primary transition-colors"
          />
          <button
            onClick={handleLookup}
            disabled={loading || !player.trim()}
            className="px-4 py-2 text-sm bg-primary text-primary-foreground rounded hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loading ? 'Loading...' : 'Look Up'}
          </button>
        </div>

        {error && (
          <p className="text-sm text-red-500">{error}</p>
        )}

        {result !== null && (
          result.length === 0 ? (
            <p className="text-sm text-muted-foreground py-4 text-center">No orders found for {player}</p>
          ) : (
            <div className="rounded-md border border-border overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/50">
                    <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">Material</th>
                    <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Side</th>
                    <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Price</th>
                    <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Qty</th>
                    <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Status</th>
                    <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Age</th>
                  </tr>
                </thead>
                <tbody>
                  {result.map((o) => (
                    <tr key={o.id} className="border-b border-border last:border-0 hover:bg-muted/30">
                      <td className="px-3 py-2.5">
                        <Link
                          href={`/auction/order?id=${encodeURIComponent(o.id)}`}
                          className="font-medium text-foreground hover:text-primary transition-colors"
                          title={`View order ${o.id}`}
                        >
                          {o.material}
                        </Link>
                        <p className="mt-0.5 font-mono text-[10px] text-muted-foreground">{o.id.slice(0, 8)}</p>
                      </td>
                      <td className="px-3 py-2.5 text-center"><SideBadge side={o.side} /></td>
                      <td className="px-3 py-2.5 text-right font-medium text-foreground">{formatCurrency(o.price)}</td>
                      <td className="px-3 py-2.5 text-right text-muted-foreground text-sm">
                        {o.remainingQuantity.toLocaleString()} / {o.originalQuantity.toLocaleString()}
                      </td>
                      <td className="px-3 py-2.5 text-center"><StatusBadge status={o.status} /></td>
                      <td className="px-3 py-2.5 text-right text-muted-foreground text-xs">{formatTimeAgo(o.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        )}
      </CardContent>
    </Card>
  );
}

function DepthTab({
  material,
  data,
  loading,
  onLoad,
}: {
  material: string;
  data: AuctionDepthData | null;
  loading: boolean;
  onLoad: (mat: string) => void;
}) {
  const [input, setInput] = useState(material);

  const handleLoad = () => {
    if (input.trim()) onLoad(input.trim().toUpperCase());
  };

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base flex items-center gap-2">
          <BarChart2 className="w-4 h-4" />
          Market Depth — {material || 'Select a material'}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Material (e.g. diamond, iron_ingot)..."
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleLoad()}
            className="flex-1 text-sm bg-transparent border border-border rounded px-3 py-2 outline-none focus:border-primary transition-colors"
          />
          <button
            onClick={handleLoad}
            disabled={loading || !input.trim()}
            className="px-4 py-2 text-sm bg-primary text-primary-foreground rounded hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            {loading ? 'Loading...' : 'Load'}
          </button>
        </div>

        {material && (
          <p className="text-xs text-muted-foreground">
            Depth chart for <span className="font-medium text-foreground">{material}</span>
            {data && ` — ${data.bids.length} bid levels, ${data.asks.length} ask levels`}
          </p>
        )}

        {loading && (
          <div className="flex items-center justify-center h-48 text-sm text-muted-foreground">
            Loading depth data...
          </div>
        )}

        {!loading && material && data && (
          <DepthChart data={data} material={material} />
        )}

        {!loading && material && !data && (
          <div className="flex items-center justify-center h-48 text-sm text-muted-foreground">
            No depth data for {material}. Try placing buy/sell orders first.
          </div>
        )}

        {!material && (
          <div className="flex items-center justify-center h-48 text-sm text-muted-foreground">
            Enter a material and click Load to see the depth chart
          </div>
        )}
      </CardContent>
    </Card>
  );
}
