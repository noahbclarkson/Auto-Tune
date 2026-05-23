'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertTriangle, Loader2, AlertCircle, Activity, ShieldOff, Zap } from 'lucide-react';

interface AntiDumpData {
  enabled: boolean;
  spreadShockActive: boolean;
  shockRemainingTicks: number;
  spreadShockMultiplier: number;
  spreadShockTriggerBps: number;
  maxSellPerItemPerTick: number;
  highValueSellCooldownTicks: number;
  topVolumes: Array<{
    id: number;
    material: string;
    displayName: string;
    volume: number;
    cap: number;
    atCap: boolean;
  }>;
}

function VolumeRow({ item, cap }: { item: AntiDumpData['topVolumes'][0]; cap: number }) {
  const pct = cap > 0 ? Math.min((item.volume / cap) * 100, 100) : 0;
  const isWarning = item.atCap || pct >= 80;
  return (
    <div className="flex items-center gap-2 py-1.5 border-b border-border/40 last:border-0">
      <Activity className={`w-3.5 h-3.5 flex-shrink-0 ${isWarning ? 'text-amber-400' : 'text-muted-foreground'}`} />
      <span className="text-sm truncate min-w-0 flex-1">{item.displayName}</span>
      <div className="flex items-center gap-2">
        <span className={`text-xs tabular-nums font-medium ${isWarning ? 'text-amber-400' : 'text-muted-foreground'}`}>
          {item.volume.toLocaleString()}{cap > 0 ? `/${cap}` : ''}
        </span>
        {cap > 0 && (
          <div className="w-16 h-1.5 rounded-full bg-muted overflow-hidden">
            <div
              className={`h-full rounded-full ${isWarning ? 'bg-amber-400' : 'bg-emerald-500/60'}`}
              style={{ width: `${pct}%` }}
            />
          </div>
        )}
      </div>
    </div>
  );
}

export function AdminAntiDumpCard() {
  const { apiBase } = useAppContext();
  const [antiDump, setAntiDump] = useState<AntiDumpData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchHealth = useCallback(async () => {
    try {
      const res = await fetch(`${apiBase}/api/admin/health`);
      if (!res.ok) throw new Error('Non-200 response');
      const json = await res.json();
      setAntiDump(json.antiDump ?? null);
      setError(null);
    } catch {
      setError('Anti-dump state unavailable');
    } finally {
      setLoading(false);
    }
  }, [apiBase]);

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 15000);
    return () => clearInterval(interval);
  }, [fetchHealth]);

  const hasVolumes = antiDump && antiDump.topVolumes && antiDump.topVolumes.length > 0;
  const cooldownActive = antiDump && antiDump.highValueSellCooldownTicks > 0;
  const capActive = antiDump && antiDump.maxSellPerItemPerTick > 0;

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          <svg className="w-4 h-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
          Whale Anti-Dump
          {antiDump?.spreadShockActive && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30">
              <Zap className="w-3 h-3" /> Shock Active
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading && !antiDump ? (
          <div className="flex items-center justify-center py-6">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : error && !antiDump ? (
          <div className="flex items-center gap-2 py-4 text-xs text-muted-foreground">
            <AlertCircle className="w-3.5 h-3.5" />
            {error}
          </div>
        ) : antiDump ? (
          <>
            {/* Status badges */}
            <div className="flex flex-wrap gap-2">
              {antiDump.enabled ? (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                  <Activity className="w-3 h-3" /> Enabled
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-red-500/15 text-red-400 border border-red-500/30">
                  <ShieldOff className="w-3 h-3" /> Disabled
                </span>
              )}
              {antiDump.spreadShockActive && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30">
                  <Zap className="w-3 h-3" />
                  Spread ×{antiDump.spreadShockMultiplier.toFixed(1)} — {antiDump.shockRemainingTicks}s remaining
                </span>
              )}
            </div>

            {/* Config summary */}
            <div className="grid grid-cols-3 gap-2 pt-2 border-t border-border/50">
              <div className="text-center">
                <div className={`text-sm font-bold tabular-nums ${capActive ? 'text-emerald-400' : 'text-muted-foreground'}`}>
                  {antiDump.maxSellPerItemPerTick > 0 ? antiDump.maxSellPerItemPerTick : '—'}
                </div>
                <div className="text-xs text-muted-foreground">Sell cap/item</div>
              </div>
              <div className="text-center">
                <div className={`text-sm font-bold tabular-nums ${cooldownActive ? 'text-emerald-400' : 'text-muted-foreground'}`}>
                  {antiDump.highValueSellCooldownTicks > 0 ? `${antiDump.highValueSellCooldownTicks}t` : '—'}
                </div>
                <div className="text-xs text-muted-foreground">Epic cooldown</div>
              </div>
              <div className="text-center">
                <div className="text-sm font-bold tabular-nums text-sky-400">
                  {(antiDump.spreadShockTriggerBps * 100).toFixed(0)}%
                </div>
                <div className="text-xs text-muted-foreground">Shock trigger</div>
              </div>
            </div>

            {/* Top volumes */}
            {hasVolumes && (
              <div className="pt-2 border-t border-border/50">
                <p className="text-xs font-medium text-muted-foreground mb-2">Top sell volumes (this tick)</p>
                <div className="space-y-0.5 max-h-40 overflow-y-auto">
                  {antiDump.topVolumes.slice(0, 8).map((item) => (
                    <VolumeRow key={item.id} item={item} cap={antiDump.maxSellPerItemPerTick > 0 ? antiDump.maxSellPerItemPerTick : item.cap} />
                  ))}
                </div>
              </div>
            )}

            {!hasVolumes && !antiDump.spreadShockActive && (
              <p className="text-xs text-muted-foreground py-2">No high-volume sells this tick.</p>
            )}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}