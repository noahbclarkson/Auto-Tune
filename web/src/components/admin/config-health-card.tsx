'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { type AdminConfigDto, type ConfigEntry, api } from '@/lib/api';
import { useAppContext } from '@/context/app-context';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ApiErrorBanner } from '@/components/ui/api-error-banner';
import { Settings2, ChevronDown, ChevronUp, CheckCircle, AlertTriangle } from 'lucide-react';

interface Props {
  className?: string;
}

type Status = 'ok' | 'warn' | 'bad';

function configStatus(entry: ConfigEntry): Status {
  if (typeof entry.current === 'boolean') return 'ok';
  const current = entry.current as number;
  const { rangeMin, rangeMax } = entry;
  if (rangeMin === undefined || rangeMax === undefined) return 'ok';
  if (current < rangeMin || current > rangeMax) return 'bad';
  const def = entry.default as number | undefined;
  if (def === undefined) return 'ok';
  const rangeHalf = (rangeMax - rangeMin) / 2;
  const dist = Math.abs(current - def);
  if (dist > rangeHalf * 0.8) return 'warn';
  return 'ok';
}

function formatValue(entry: ConfigEntry): string {
  if (typeof entry.current === 'boolean') {
    return entry.current ? 'Enabled' : 'Disabled';
  }
  const val = entry.current as number;
  switch (entry.unit) {
    case 'percent':
      return `${(val * 100).toFixed(1)}%`;
    case 'decimal':
      return `${val.toFixed(2)}`;
    case 'ratio':
      return `${val.toFixed(1)}×`;
    case 'hours':
      return `${val}h`;
    case 'days':
      return `${val}d`;
    case 'items':
      return `${val}`;
    default:
      return String(val);
  }
}

function formatRange(entry: ConfigEntry): string {
  if (typeof entry.current === 'boolean') return '';
  switch (entry.unit) {
    case 'percent':
      return `${entry.rangeMin! * 100}%–${entry.rangeMax! * 100}%`;
    case 'decimal':
      return `${entry.rangeMin!.toFixed(2)}–${entry.rangeMax!.toFixed(2)}`;
    case 'ratio':
      return `${entry.rangeMin!.toFixed(0)}×–${entry.rangeMax!.toFixed(0)}×`;
    case 'hours':
      return `${entry.rangeMin}h–${entry.rangeMax}h`;
    case 'days':
      return `${entry.rangeMin}d–${entry.rangeMax}d`;
    case 'items':
      return `${entry.rangeMin}–${entry.rangeMax}`;
    default:
      return '';
  }
}

function formatDefault(entry: ConfigEntry): string {
  if (typeof entry.current === 'boolean') return '';
  switch (entry.unit) {
    case 'percent':
      return `${(entry.default! * 100).toFixed(1)}%`;
    case 'decimal':
      return `${entry.default!.toFixed(2)}`;
    case 'ratio':
      return `${entry.default!.toFixed(1)}×`;
    case 'hours':
      return `${entry.default!}h`;
    case 'days':
      return `${entry.default!}d`;
    case 'items':
      return `${entry.default!}`;
    default:
      return String(entry.default);
  }
}

function statusColor(status: Status): string {
  if (status === 'ok') return 'text-emerald-400';
  if (status === 'warn') return 'text-amber-400';
  return 'text-red-400';
}

function statusBg(status: Status): string {
  if (status === 'ok') return 'bg-emerald-500/15 border-emerald-500/30';
  if (status === 'warn') return 'bg-amber-500/15 border-amber-500/30';
  return 'bg-red-500/15 border-red-500/30';
}

function StatusDot({ status }: { status: Status }) {
  if (status === 'ok') return <CheckCircle className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />;
  if (status === 'warn') return <AlertTriangle className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />;
  return <AlertTriangle className="w-3.5 h-3.5 text-red-400 flex-shrink-0" />;
}

function ConfigRow({ entry }: { entry: ConfigEntry }) {
  const status = configStatus(entry);
  return (
    <div className="flex items-center gap-3 py-2 border-b border-border/40 last:border-0">
      <StatusDot status={status} />
      <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">{entry.label}</span>
      <span className={`text-sm font-bold tabular-nums ${statusColor(status)}`}>
        {formatValue(entry)}
      </span>
      <span className="text-xs text-muted-foreground w-16 text-right">default {formatDefault(entry)}</span>
      <span className="text-xs text-muted-foreground w-32 text-right">
        range {formatRange(entry)}
      </span>
      <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${statusBg(status)} ${statusColor(status)}`}>
        {status === 'ok' ? 'optimal' : status === 'warn' ? 'outside default' : 'out of range'}
      </span>
    </div>
  );
}

function BooleanRow({ label, value, description }: { label: string; value: boolean; description: string }) {
  return (
    <div className="flex items-center gap-3 py-2 border-b border-border/40 last:border-0">
      <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${value ? 'text-emerald-400' : 'text-muted-foreground'}`} />
      <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">{label}</span>
      <span className={`text-sm font-bold ${value ? 'text-emerald-400' : 'text-muted-foreground'}`}>
        {value ? 'Enabled' : 'Disabled'}
      </span>
      <span className="text-xs text-muted-foreground w-16 text-right" />
      <span className="text-xs text-muted-foreground w-32 text-right" />
      <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${value ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
        {description}
      </span>
    </div>
  );
}

function Section({
  title,
  children,
  defaultOpen = true,
}: {
  title: string;
  children: ReactNode;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="rounded-lg border border-border overflow-hidden">
      <button
        className="w-full flex items-center gap-2 px-4 py-3 text-left hover:bg-muted/30 transition-colors"
        onClick={() => setOpen((o) => !o)}
      >
        <span className={`flex-shrink-0 transition-transform ${open ? 'rotate-0' : '-rotate-90'}`}>
          {open ? <ChevronUp className="w-4 h-4 text-muted-foreground" /> : <ChevronDown className="w-4 h-4 text-muted-foreground" />}
        </span>
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </button>
      {open && <div className="px-4 pb-3">{children}</div>}
    </div>
  );
}

export function ConfigHealthCard({ className = '' }: Props) {
  const { apiBase } = useAppContext();
  const [config, setConfig] = useState<AdminConfigDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.admin
      .config(apiBase)
      .then(setConfig)
      .catch(() => setError('Could not load config health data'))
      .finally(() => setLoading(false));
  }, [apiBase]);

  if (loading) {
    return (
      <Card className={`border-border ${className}`}>
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">Loading config health…</p>
        </CardContent>
      </Card>
    );
  }

  if (error || !config) {
    return (
      <Card className={`border-border ${className}`}>
        <CardContent className="pt-6">
          <ApiErrorBanner message={error ?? 'Could not load config'} apiBase={apiBase} onRetry={() => window.location.reload()} />
        </CardContent>
      </Card>
    );
  }

  const { spread, loans, economy, marketDigest } = config;

  return (
    <Card className={`border-border ${className}`}>
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          <Settings2 className="w-4 h-4 text-sky-400" />
          Config Health
        </CardTitle>
        <p className="text-xs text-muted-foreground mt-1">
          Current config values vs production defaults and recommended ranges.
          Green = optimal, amber = outside default, red = out of range.
        </p>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* Header row */}
        <div className="flex items-center gap-3 px-4 text-xs text-muted-foreground border-b border-border/50 pb-1">
          <span className="w-3.5" />
          <span className="w-44 flex-shrink-0 font-medium">Parameter</span>
          <span className="text-sm font-bold">Current</span>
          <span className="w-16 text-right">Default</span>
          <span className="w-32 text-right">Recommended</span>
          <span className="w-24 text-right">Status</span>
        </div>

        <Section title="Spread Engine">
          <ConfigRow entry={spread.baseSpread} />
          <ConfigRow entry={spread.volumeImpact} />
          <ConfigRow entry={spread.playerImpact} />
        </Section>

        <Section title="Loan System">
          <ConfigRow entry={loans.baseInterestRate} />
          <ConfigRow entry={loans.debtGdpTier3Ratio} />
          <ConfigRow entry={loans.postDefaultCooldownHours} />
          <BooleanRow
            label="Counter-Cyclical Interest"
            value={loans.counterCyclical}
            description={loans.counterCyclical ? 'smooth debt control' : 'tiered only'}
          />
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className="w-3.5 h-3.5 text-muted-foreground flex-shrink-0" />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Single Loan Cap</span>
            <span className="text-sm font-bold text-foreground">{loans.singleLoanGdpCap.toFixed(1)}× GDP</span>
            <span className="w-16 text-right" />
            <span className="w-32 text-right" />
            <span className="text-xs px-1.5 py-0.5 rounded border bg-emerald-500/15 border-emerald-500/30 text-emerald-400 flex-shrink-0">
              optimal
            </span>
          </div>
        </Section>

        <Section title="Economy Engine">
          <ConfigRow entry={economy.tradeWindowDays} />
          <ConfigRow entry={economy.maxPriceChangePercent} />
          <ConfigRow entry={economy.minBuyQuantity} />
          <ConfigRow entry={economy.minSellQuantity} />
        </Section>

        <Section title="Market Digest" defaultOpen={false}>
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${marketDigest.enabled ? 'text-emerald-400' : 'text-muted-foreground'}`} />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Scheduled Reports</span>
            <span className={`text-sm font-bold ${marketDigest.enabled ? 'text-emerald-400' : 'text-muted-foreground'}`}>
              {marketDigest.enabled ? 'Enabled' : 'Disabled'}
            </span>
            <span className="w-16 text-right text-xs text-muted-foreground">default: off</span>
            <span className="w-32 text-right" />
            <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${marketDigest.enabled ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
              {marketDigest.enabled ? 'active' : 'off'}
            </span>
          </div>
          {marketDigest.enabled && (
            <div className="flex items-center gap-3 py-2">
              <CheckCircle className="w-3.5 h-3.5 text-muted-foreground flex-shrink-0" />
              <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Report Schedule</span>
              <span className="text-sm font-bold text-foreground">{marketDigest.interval}</span>
              <span className="w-16 text-right text-xs text-muted-foreground">default: daily</span>
              <span className="w-32 text-right" />
              <span className="text-xs px-1.5 py-0.5 rounded border bg-emerald-500/15 border-emerald-500/30 text-emerald-400 flex-shrink-0">
                optimal
              </span>
            </div>
          )}
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${marketDigest.includeTopMovers ? 'text-emerald-400' : 'text-muted-foreground'}`} />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Top Movers</span>
            <span className={`text-sm font-bold ${marketDigest.includeTopMovers ? 'text-emerald-400' : 'text-muted-foreground'}`}>
              {marketDigest.includeTopMovers ? 'Included' : 'Excluded'}
            </span>
            <span className="w-16 text-right" />
            <span className="w-32 text-right" />
            <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${marketDigest.includeTopMovers ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
              {marketDigest.includeTopMovers ? 'included' : 'excluded'}
            </span>
          </div>
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${marketDigest.includeHealthStats ? 'text-emerald-400' : 'text-muted-foreground'}`} />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Health Stats</span>
            <span className={`text-sm font-bold ${marketDigest.includeHealthStats ? 'text-emerald-400' : 'text-muted-foreground'}`}>
              {marketDigest.includeHealthStats ? 'Included' : 'Excluded'}
            </span>
            <span className="w-16 text-right" />
            <span className="w-32 text-right" />
            <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${marketDigest.includeHealthStats ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
              {marketDigest.includeHealthStats ? 'included' : 'excluded'}
            </span>
          </div>
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${marketDigest.includeActiveEvents ? 'text-emerald-400' : 'text-muted-foreground'}`} />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Active Events</span>
            <span className={`text-sm font-bold ${marketDigest.includeActiveEvents ? 'text-emerald-400' : 'text-muted-foreground'}`}>
              {marketDigest.includeActiveEvents ? 'Included' : 'Excluded'}
            </span>
            <span className="w-16 text-right" />
            <span className="w-32 text-right" />
            <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${marketDigest.includeActiveEvents ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
              {marketDigest.includeActiveEvents ? 'included' : 'excluded'}
            </span>
          </div>
          <div className="flex items-center gap-3 py-2">
            <CheckCircle className={`w-3.5 h-3.5 flex-shrink-0 ${marketDigest.includeLoanStats ? 'text-emerald-400' : 'text-muted-foreground'}`} />
            <span className="text-sm font-medium text-foreground w-44 flex-shrink-0">Loan Stats</span>
            <span className={`text-sm font-bold ${marketDigest.includeLoanStats ? 'text-emerald-400' : 'text-muted-foreground'}`}>
              {marketDigest.includeLoanStats ? 'Included' : 'Excluded'}
            </span>
            <span className="w-16 text-right" />
            <span className="w-32 text-right" />
            <span className={`text-xs px-1.5 py-0.5 rounded border flex-shrink-0 ${marketDigest.includeLoanStats ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400' : 'bg-muted border-border text-muted-foreground'}`}>
              {marketDigest.includeLoanStats ? 'included' : 'excluded'}
            </span>
          </div>
        </Section>
      </CardContent>
    </Card>
  );
}
