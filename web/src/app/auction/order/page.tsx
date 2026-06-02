'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAppContext } from '@/context/app-context';
import { PlayerIdentityStrip } from '@/components/auction/player-identity-strip';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api, type Stats, type AuctionOrderDto } from '@/lib/api';
import { formatCurrency, formatTimeAgo } from '@/lib/format';
import {
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  Bell,
  BellRing,
  ArrowUpDown,
  Package,
  Clock,
  CheckCircle,
  XCircle,
  AlertCircle,
  Loader2,
} from 'lucide-react';

interface FillEntry {
  id: string;
  quantity: number;
  price: number;
  total: number;
  filledAt: number;
}

type WatchStatus = 'inactive' | 'watching' | 'filled' | 'error';

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

function StatusIcon({ status }: { status: AuctionOrderDto['status'] }) {
  if (status === 'ACTIVE') return <AlertCircle className="w-4 h-4 text-amber-500" />;
  if (status === 'FILLED') return <CheckCircle className="w-4 h-4 text-emerald-500" />;
  if (status === 'CANCELLED') return <XCircle className="w-4 h-4 text-red-500" />;
  return <Clock className="w-4 h-4 text-muted-foreground" />;
}

const STORAGE_KEY = 'autotune:watched-orders';

function getWatchedOrders(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? new Set(JSON.parse(raw) as string[]) : new Set();
  } catch {
    return new Set();
  }
}

function addWatchedOrder(id: string) {
  const set = getWatchedOrders();
  set.add(id);
  localStorage.setItem(STORAGE_KEY, JSON.stringify([...set]));
}

function removeWatchedOrder(id: string) {
  const set = getWatchedOrders();
  set.delete(id);
  localStorage.setItem(STORAGE_KEY, JSON.stringify([...set]));
}

function isWatched(id: string): boolean {
  return getWatchedOrders().has(id);
}

async function requestNotificationPermission(): Promise<boolean> {
  if (!('Notification' in window)) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;
  const result = await Notification.requestPermission();
  return result === 'granted';
}

export default function OrderDetailPage() {
  const router = useRouter();
  const { apiBase, playerName, setPlayerName } = useAppContext();

  const [orderId, setOrderId] = useState<string>('');
  const [stats, setStats] = useState<Stats | null>(null);
  const [order, setOrder] = useState<AuctionOrderDto | null>(null);
  const [fills, setFills] = useState<FillEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [orderError, setOrderError] = useState<string | null>(null);
  const [watchStatus, setWatchStatus] = useState<WatchStatus>('inactive');
  const [watchLoading, setWatchLoading] = useState(false);
  const [notifPermission, setNotifPermission] = useState<NotificationPermission>('default');
  const notifiedRef = useRef(false);

  // Init order ID from the static-export-safe query string and check notification permission.
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('id') ?? '';
    if (!id) {
      setOrderError('Missing order ID. Open an order from the Auction House table.');
      setLoading(false);
    }
    setOrderId(id);
    if ('Notification' in window) {
      setNotifPermission(Notification.permission);
    }
  }, []);

  // Determine initial watch state
  // Determine initial watch state
  useEffect(() => {
    if (!orderId) return;
    if (order) {
      if (order.status === 'FILLED') {
        setWatchStatus('filled');
      } else if (isWatched(orderId)) {
        setWatchStatus('watching');
      } else {
        // Check native watch status if player name is known
        if (playerName) {
          api.auction.watchStatus(apiBase, orderId, playerName)
            .then((res) => {
              if (res.watching) setWatchStatus('watching');
            })
            .catch(() => null);
        }
      }
    }
  }, [orderId, order, playerName, apiBase]);

  const fetchOrder = useCallback(async () => {
    if (!orderId) return;
    try {
      const [orderData, fillsData] = await Promise.all([
        api.auction.order(apiBase, orderId).catch(() => null),
        api.auction.fillsForOrder(apiBase, orderId).catch(() => [] as FillEntry[]),
      ]);
      if (!orderData) {
        setOrderError('Order not found. It may have been cancelled or the ID is invalid.');
        setLoading(false);
        return;
      }
      setOrder(orderData);
      setFills(fillsData);

      // Browser notification when a watched order fills
      if (
        watchStatus === 'watching' &&
        orderData.status === 'FILLED' &&
        !notifiedRef.current
      ) {
        notifiedRef.current = true;
        setWatchStatus('filled');
        removeWatchedOrder(orderId);
        if (Notification.permission === 'granted') {
          new Notification('Order Filled!', {
            body: `${orderData.side === 'BUY' ? 'Bought' : 'Sold'} ${orderData.material} @ ${formatCurrency(orderData.price)}`,
            icon: '/favicon.ico',
          });
        }
      } else if (orderData.status === 'FILLED' && watchStatus === 'inactive') {
        setWatchStatus('filled');
      }
    } catch {
      setOrderError('Failed to load order. Is the server running?');
    } finally {
      setLoading(false);
    }
  }, [apiBase, orderId, watchStatus]);

  // Initial fetch + stats
  useEffect(() => {
    if (!apiBase || !orderId) return;
    setLoading(true);
    Promise.all([
      api.stats(apiBase).catch(() => null),
      fetchOrder(),
    ]).then(([statsData]) => {
      setStats(statsData);
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiBase, orderId]);

  // 30-second polling while order is active and we're watching
  useEffect(() => {
    if (!orderId || !apiBase) return;
    if (watchStatus === 'filled') return;
    const interval = setInterval(fetchOrder, 30_000);
    return () => clearInterval(interval);
  }, [orderId, apiBase, watchStatus, fetchOrder]);

  const handleWatchToggle = async () => {
    if (watchStatus === 'watching') {
      if (playerName) {
        setWatchLoading(true);
        await api.auction.unwatch(apiBase, orderId, playerName).catch(() => null);
        setWatchLoading(false);
      }
      removeWatchedOrder(orderId);
      setWatchStatus('inactive');
      return;
    }
    // When player name is known, use native watch (persists in-game/offline)
    if (playerName) {
      setWatchLoading(true);
      const result = await api.auction.watch(apiBase, orderId, playerName).catch(() => null);
      setWatchLoading(false);
      if (result) {
        setWatchStatus('watching');
        return;
      }
    }
    // Fall back to browser notifications
    const granted = await requestNotificationPermission();
    setNotifPermission(Notification.permission);
    if (!granted) {
      addWatchedOrder(orderId);
      setWatchStatus('watching');
      return;
    }
    addWatchedOrder(orderId);
    setWatchStatus('watching');
  };

  const fillPct = order && order.originalQuantity > 0
    ? Math.round((order.filledQuantity / order.originalQuantity) * 100)
    : 0;

  const totalFilled = fills.reduce((sum, f) => sum + f.total, 0);
  const avgFillPrice = fills.length > 0
    ? fills.reduce((sum, f) => sum + f.price * f.quantity, 0) / fills.reduce((sum, f) => sum + f.quantity, 0)
    : null;

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-3xl w-full px-6 py-6 space-y-6 flex-1">
        {/* Back nav */}
        <button
          onClick={() => router.back()}
          className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Auction House
        </button>

        {/* Player identity strip for native watch notifications */}
        <div className="bg-muted/30 rounded-lg px-4 py-3">
          <p className="text-xs text-muted-foreground mb-2 leading-relaxed">
            Enter your Minecraft name to get <strong>in-game notifications</strong> when your watched orders fill — even while offline.
            Without it, only browser notifications work (and only while this page is open).
          </p>
          <PlayerIdentityStrip playerName={playerName} onPlayerNameChange={setPlayerName} />
        </div>

        {loading && (
          <div className="flex items-center justify-center py-16 gap-2 text-muted-foreground">
            <Loader2 className="w-5 h-5 animate-spin" />
            <span className="text-sm">Loading order...</span>
          </div>
        )}

        {orderError && !loading && (
          <div className="rounded-lg border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950 p-6 text-center">
            <AlertCircle className="w-8 h-8 mx-auto mb-2 text-red-500" />
            <p className="text-sm text-red-700 dark:text-red-300">{orderError}</p>
          </div>
        )}

        {!loading && order && (
          <>
            {/* Order header */}
            <div className="flex items-start justify-between gap-4 flex-wrap">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-lg bg-primary/10">
                  <Package className="w-6 h-6 text-primary" />
                </div>
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h2 className="text-xl font-bold text-foreground">{order.material}</h2>
                    <SideBadge side={order.side} />
                    <StatusIcon status={order.status} />
                    <Badge variant={order.status === 'ACTIVE' ? 'default' : order.status === 'FILLED' ? 'secondary' : 'outline'} className="text-xs">
                      {order.status}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Order ID: <span className="font-mono text-xs">{order.id}</span>
                  </p>
                </div>
              </div>

              {/* Watch toggle */}
              {order.status === 'ACTIVE' && (
                <div className="flex flex-col items-end gap-1.5">
                  <button
                    type="button"
                    onClick={handleWatchToggle}
                    disabled={watchLoading}
                    className={`inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium rounded-md transition-colors ${
                      watchStatus === 'watching'
                        ? 'bg-primary text-primary-foreground hover:opacity-90'
                        : 'border border-border bg-background hover:bg-muted text-foreground'
                    }`}
                  >
                    {watchLoading ? (
                      <><Loader2 className="w-4 h-4 animate-spin" />Please wait…</>
                    ) : watchStatus === 'watching' ? (
                      <><BellRing className="w-4 h-4" />Watching</>
                    ) : (
                      <><Bell className="w-4 h-4" />Watch Order</>
                    )}
                  </button>
                  {watchStatus === 'watching' && notifPermission === 'denied' && (
                    <p className="text-xs text-amber-500">Notifications blocked — order is still being watched</p>
                  )}
                  {watchStatus === 'watching' && notifPermission === 'default' && (
                    <p className="text-xs text-muted-foreground">Browser notifications will alert you on fill</p>
                  )}
                  {watchStatus === 'watching' && notifPermission === 'granted' && (
                    <p className="text-xs text-emerald-500/70">{playerName ? "In-game + browser notifications will alert you on fill" : "You'll be notified when this order fills"}</p>
                  )}
                  {watchStatus === 'watching' && playerName && (
                    <p className="text-xs text-primary/70">Watching via /auction watch — alerts even while offline</p>
                  )}
                </div>
              )}
            </div>

            {/* Order details grid */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              <Card>
                <CardContent className="p-4 text-center">
                  <p className="text-sm text-muted-foreground mb-1">Price</p>
                  <p className="text-xl font-bold text-foreground">{formatCurrency(order.price)}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-4 text-center">
                  <p className="text-sm text-muted-foreground mb-1">Quantity</p>
                  <p className="text-xl font-bold text-foreground">{order.originalQuantity.toLocaleString()}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-4 text-center">
                  <p className="text-sm text-muted-foreground mb-1">Remaining</p>
                  <p className="text-xl font-bold text-foreground">{order.remainingQuantity.toLocaleString()}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="p-4 text-center">
                  <p className="text-sm text-muted-foreground mb-1">Filled</p>
                  <p className="text-xl font-bold text-emerald-500">{fillPct}%</p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {order.filledQuantity.toLocaleString()} units
                  </p>
                </CardContent>
              </Card>
            </div>

            {/* Fill progress bar */}
            <Card>
              <CardContent className="p-4">
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-medium text-foreground">Fill Progress</p>
                  <p className="text-xs text-muted-foreground">
                    {order.remainingQuantity > 0 ? (
                      watchStatus === 'watching' ? (
                        <span className="text-primary">Checking every 30s…</span>
                      ) : (
                        <span>Auto-refresh off</span>
                      )
                    ) : (
                      <span className="text-emerald-500">Fully filled</span>
                    )}
                  </p>
                </div>
                <div className="h-3 rounded-full bg-muted overflow-hidden">
                  <div
                    className="h-full bg-emerald-500 rounded-full transition-all"
                    style={{ width: `${fillPct}%` }}
                  />
                </div>
                <div className="flex justify-between mt-1.5 text-xs text-muted-foreground">
                  <span>0</span>
                  <span>{order.originalQuantity.toLocaleString()} total</span>
                </div>
              </CardContent>
            </Card>

            {/* Summary stats */}
            {(fills.length > 0 || totalFilled > 0) && (
              <div className="grid grid-cols-2 gap-4">
                <Card>
                  <CardContent className="p-4 text-center">
                    <p className="text-sm text-muted-foreground mb-1">Total Filled Value</p>
                    <p className="text-xl font-bold text-foreground">{formatCurrency(totalFilled)}</p>
                  </CardContent>
                </Card>
                {avgFillPrice !== null && (
                  <Card>
                    <CardContent className="p-4 text-center">
                      <p className="text-sm text-muted-foreground mb-1">Avg Fill Price</p>
                      <p className="text-xl font-bold text-foreground">{formatCurrency(avgFillPrice)}</p>
                    </CardContent>
                  </Card>
                )}
              </div>
            )}

            {/* Fill history */}
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-base flex items-center gap-2">
                  <ArrowUpDown className="w-4 h-4" />
                  Fill History
                  {order.status === 'ACTIVE' && watchStatus === 'watching' && (
                    <span className="ml-auto text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">
                      Live
                    </span>
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent>
                {fills.length === 0 ? (
                  <div className="py-8 text-center">
                    <p className="text-sm text-muted-foreground">
                      {order.status === 'ACTIVE'
                        ? 'No partial fills yet — waiting for matching orders'
                        : 'No fills recorded for this order'}
                    </p>
                  </div>
                ) : (
                  <div className="rounded-md border border-border overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-border bg-muted/50">
                          <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">#</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Quantity</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Price</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Total</th>
                          <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Time</th>
                        </tr>
                      </thead>
                      <tbody>
                        {[...fills].reverse().map((f, i) => (
                          <tr key={f.id} className="border-b border-border last:border-0 hover:bg-muted/30">
                            <td className="px-3 py-2.5 text-muted-foreground text-xs">{fills.length - i}</td>
                            <td className="px-3 py-2.5 text-right font-medium text-foreground">
                              {f.quantity.toLocaleString()}
                            </td>
                            <td className="px-3 py-2.5 text-right text-foreground">
                              {formatCurrency(f.price)}
                            </td>
                            <td className="px-3 py-2.5 text-right text-foreground">
                              {formatCurrency(f.total)}
                            </td>
                            <td className="px-3 py-2.5 text-right text-muted-foreground text-xs">
                              {formatTimeAgo(f.filledAt)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Timestamps */}
            <Card>
              <CardContent className="p-4">
                <p className="text-sm font-medium text-foreground mb-2">Timestamps</p>
                <div className="space-y-1.5 text-xs text-muted-foreground">
                  <div className="flex items-center gap-2">
                    <Clock className="w-3.5 h-3.5" />
                    <span>Created: {new Date(order.createdAt).toLocaleString()}</span>
                  </div>
                  {order.expiresAt > 0 && (
                    <div className="flex items-center gap-2">
                      <AlertCircle className="w-3.5 h-3.5" />
                      <span>Expires: {new Date(order.expiresAt).toLocaleString()}</span>
                    </div>
                  )}
                  {order.status === 'FILLED' && fills.length > 0 && (
                    <div className="flex items-center gap-2">
                      <CheckCircle className="w-3.5 h-3.5 text-emerald-500" />
                      <span>Fully filled: {new Date(fills[fills.length - 1].filledAt).toLocaleString()}</span>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
