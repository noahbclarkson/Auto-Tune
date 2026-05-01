'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertTriangle, Loader2, AlertCircle, TrendingUp, TrendingDown } from 'lucide-react';

interface AuctionAuditDto {
  days: number;
  statusCounts: Record<string, number>;
  churn: {
    totalOrders: number;
    activeOrders: number;
    filledOrders: number;
    cancelledOrders: number;
    expiredOrders: number;
    reclaimedOrders: number;
    cancellationRate: number;
    fillRate: number;
    expirationRate: number;
  };
  selfTradeFills: number;
  thinBooks: Array<{
    material: string;
    bidCount: number;
    askCount: number;
    bidQuantity: number;
    askQuantity: number;
    bestBid: number | null;
    bestAsk: number | null;
    largestSellQuantity: number;
    thinBook: boolean;
    largeSellWall: boolean;
  }>;
  largeSellWalls: Array<{
    material: string;
    bidCount: number;
    askCount: number;
    bidQuantity: number;
    askQuantity: number;
    bestBid: number | null;
    bestAsk: number | null;
    largestSellQuantity: number;
    thinBook: boolean;
    largeSellWall: boolean;
  }>;
  warnings: string[];
}

function formatRate(rate: number, per: string = 'orders'): string {
  return `${(rate * 100).toFixed(1)}% ${per}`;
}

function StatusRow({ label, count, accent }: { label: string; count: number; accent: 'green' | 'amber' | 'red' | 'muted' }) {
  const colors = { green: 'text-emerald-400', amber: 'text-amber-400', red: 'text-red-400', muted: 'text-muted-foreground' };
  return (
    <div className="flex items-center justify-between">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className={`text-sm font-medium tabular-nums ${count > 0 ? colors[accent] : 'text-muted-foreground'}`}>{count}</span>
    </div>
  );
}

function ThinBookRow({ book }: { book: AuctionAuditDto['thinBooks'][0] }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-border/40 last:border-0">
      <div className="flex items-center gap-2 min-w-0">
        <TrendingDown className="w-3.5 h-3.5 text-red-400 flex-shrink-0" />
        <span className="text-sm truncate">{book.material}</span>
      </div>
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span>B:{book.bidCount} · A:{book.askCount}</span>
      </div>
    </div>
  );
}

function LargeWallRow({ book }: { book: AuctionAuditDto['largeSellWalls'][0] }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-border/40 last:border-0">
      <div className="flex items-center gap-2 min-w-0">
        <TrendingUp className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
        <span className="text-sm truncate">{book.material}</span>
      </div>
      <span className="text-xs text-amber-400 tabular-nums">×{book.largestSellQuantity.toLocaleString()}</span>
    </div>
  );
}

export function AdminAuctionCard() {
  const { apiBase } = useAppContext();
  const [data, setData] = useState<AuctionAuditDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAuctionAudit = useCallback(async () => {
    try {
      const res = await fetch(`${apiBase}/api/admin/auction-audit?days=7`);
      if (!res.ok) throw new Error('Non-200 response');
      const json: AuctionAuditDto = await res.json();
      setData(json);
      setError(null);
    } catch {
      setError('Auction audit unavailable');
    } finally {
      setLoading(false);
    }
  }, [apiBase]);

  useEffect(() => {
    fetchAuctionAudit();
    const interval = setInterval(fetchAuctionAudit, 30000);
    return () => clearInterval(interval);
  }, [fetchAuctionAudit]);

  const hasWarnings = data && data.warnings.length > 0;
  const churn = data?.churn;

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          <svg className="w-4 h-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 3h18v18H3z" />
            <path d="M3 9h18M9 21V9" />
          </svg>
          Auction Integrity
          {hasWarnings && <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading && !data ? (
          <div className="flex items-center justify-center py-6">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : error && !data ? (
          <div className="flex items-center gap-2 py-4 text-xs text-muted-foreground">
            <AlertCircle className="w-3.5 h-3.5" />
            {error}
          </div>
        ) : data ? (
          <>
            {/* Warnings */}
            {hasWarnings && (
              <div className="space-y-1.5">
                {data.warnings.map((w, i) => (
                  <div key={i} className="flex items-start gap-2 px-3 py-2 rounded-md bg-amber-500/10 border border-amber-500/20">
                    <AlertTriangle className="w-3.5 h-3.5 text-amber-400 mt-0.5 flex-shrink-0" />
                    <span className="text-xs text-amber-300 leading-relaxed">{w}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Status counts */}
            {data.statusCounts && (
              <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                <StatusRow label="Active" count={data.statusCounts['active'] ?? 0} accent="green" />
                <StatusRow label="Filled" count={data.statusCounts['filled'] ?? 0} accent="muted" />
                <StatusRow label="Cancelled" count={data.statusCounts['cancelled'] ?? 0} accent="amber" />
                <StatusRow label="Expired" count={data.statusCounts['expired'] ?? 0} accent="muted" />
                <StatusRow label="Reclaimed" count={data.statusCounts['reclaimed'] ?? 0} accent="muted" />
                <StatusRow label="Self-trades" count={data.selfTradeFills} accent={data.selfTradeFills > 0 ? 'red' : 'muted'} />
              </div>
            )}

            {/* Churn rates */}
            {churn && (
              <div className="grid grid-cols-3 gap-2 pt-2 border-t border-border/50">
                <div className="text-center">
                  <div className={`text-sm font-bold tabular-nums ${churn.cancellationRate >= 0.35 ? 'text-amber-400' : 'text-emerald-400'}`}>
                    {(churn.cancellationRate * 100).toFixed(0)}%
                  </div>
                  <div className="text-xs text-muted-foreground">Cancel</div>
                </div>
                <div className="text-center">
                  <div className="text-sm font-bold tabular-nums text-emerald-400">
                    {(churn.fillRate * 100).toFixed(0)}%
                  </div>
                  <div className="text-xs text-muted-foreground">Fill</div>
                </div>
                <div className="text-center">
                  <div className={`text-sm font-bold tabular-nums ${churn.expirationRate >= 0.20 ? 'text-amber-400' : 'text-muted-foreground'}`}>
                    {(churn.expirationRate * 100).toFixed(0)}%
                  </div>
                  <div className="text-xs text-muted-foreground">Expire</div>
                </div>
              </div>
            )}

            {/* Thin books */}
            {data.thinBooks && data.thinBooks.length > 0 && (
              <div className="pt-2 border-t border-border/50">
                <p className="text-xs font-medium text-muted-foreground mb-2">Thin books — easy to manipulate</p>
                <div className="space-y-0.5">
                  {data.thinBooks.slice(0, 5).map((book) => (
                    <ThinBookRow key={book.material} book={book} />
                  ))}
                </div>
              </div>
            )}

            {/* Large sell walls */}
            {data.largeSellWalls && data.largeSellWalls.length > 0 && (
              <div className="pt-2 border-t border-border/50">
                <p className="text-xs font-medium text-muted-foreground mb-2">Large sell walls</p>
                <div className="space-y-0.5">
                  {data.largeSellWalls.slice(0, 5).map((book) => (
                    <LargeWallRow key={book.material} book={book} />
                  ))}
                </div>
              </div>
            )}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
