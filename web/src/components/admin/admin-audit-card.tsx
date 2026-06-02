'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { api, type AdminAuditEntryDto } from '@/lib/api';
import { Shield, Loader2, AlertCircle } from 'lucide-react';

const ACTION_LABELS: Record<string, { label: string; color: string }> = {
  MARKET_FREEZE:     { label: 'froze market',       color: 'text-sky-400' },
  MARKET_UNFREEZE:   { label: 'unfroze market',     color: 'text-emerald-400' },
  PRICE_SET:         { label: 'set price',          color: 'text-amber-400' },
  PRICE_REMOVE:      { label: 'removed price',      color: 'text-gray-400' },
  ITEM_FLOOR:        { label: 'set price floor',     color: 'text-orange-400' },
  ITEM_CEILING:      { label: 'set price ceiling',  color: 'text-purple-400' },
  CONFIG_RELOAD:     { label: 'reloaded config',    color: 'text-blue-400' },
  MARKET_EVENT:       { label: 'triggered event',     color: 'text-pink-400' },
  LOAN_APPROVED:     { label: 'approved loan',       color: 'text-emerald-400' },
  LOAN_DEFAULT:      { label: 'loan defaulted',      color: 'text-red-400' },
};

function formatRelativeTime(timestampStr: string): string {
  try {
    const ts = new Date(timestampStr).getTime();
    const now = Date.now();
    const diffMs = now - ts;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'just now';
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h ago`;
    const diffDay = Math.floor(diffHr / 24);
    return `${diffDay}d ago`;
  } catch {
    return timestampStr.substring(0, 10);
  }
}

function AuditRow({ entry }: { entry: AdminAuditEntryDto }) {
  const action = ACTION_LABELS[entry.actionType] ?? { label: entry.actionType.toLowerCase().replace(/_/g, ' '), color: 'text-gray-400' };

  return (
    <div className="flex items-start gap-3 py-2.5 border-b border-border/50 last:border-0">
      <div className="flex-1 min-w-0">
        <div className="flex items-baseline gap-2 flex-wrap">
          <span className="text-sm font-medium text-foreground">{entry.adminName}</span>
          <span className={`text-sm ${action.color}`}>{action.label}</span>
          {entry.target && (
            <span className="text-sm text-muted-foreground truncate">{entry.target}</span>
          )}
        </div>
        {entry.oldValue && entry.newValue && (
          <p className="text-xs text-muted-foreground mt-0.5">
            {entry.oldValue} → {entry.newValue}
          </p>
        )}
      </div>
      <span className="text-xs text-muted-foreground whitespace-nowrap shrink-0">
        {formatRelativeTime(entry.timestamp)}
      </span>
    </div>
  );
}

export function AdminAuditCard() {
  const { apiBase } = useAppContext();
  const [entries, setEntries] = useState<AdminAuditEntryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAudit = useCallback(async () => {
    try {
      const data = await api.admin.audit(apiBase, 15);
      setEntries(data.entries);
      setError(null);
    } catch {
      setError('Could not load audit log');
    } finally {
      setLoading(false);
    }
  }, [apiBase]);

  useEffect(() => {
    fetchAudit();
    const interval = setInterval(fetchAudit, 30000);
    return () => clearInterval(interval);
  }, [fetchAudit]);

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          <Shield className="w-4 h-4 text-gray-400" />
          Recent Admin Actions
        </CardTitle>
      </CardHeader>
      <CardContent>
        {loading && entries.length === 0 ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        ) : error && entries.length === 0 ? (
          <div className="flex items-center gap-2 py-4 text-xs text-muted-foreground">
            <AlertCircle className="w-3.5 h-3.5" />
            {error}
          </div>
        ) : entries.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">
            No admin actions recorded yet.
          </p>
        ) : (
          <div>
            {entries.map((entry) => (
              <AuditRow key={entry.id} entry={entry} />
            ))}
          </div>
        )}
        {!loading && !error && (
          <p className="text-xs text-muted-foreground mt-3 text-center">
            Run <code className="text-xs bg-muted px-1 py-0.5 rounded">/at admin auditlog</code> in-game for full history
          </p>
        )}
      </CardContent>
    </Card>
  );
}
