'use client';

import { useState, useEffect, useCallback } from 'react';
import { Bell, Copy, Check, X, Info, Plus, Trash2, ToggleLeft, ToggleRight, Loader2 } from 'lucide-react';
import { useAppContext } from '@/context/app-context';
import type { ItemDto, AlertDto } from '@/lib/api';
import { api } from '@/lib/api';

interface PriceAlertDialogProps {
  item: ItemDto;
  open: boolean;
  onClose: () => void;
  /** Player name for API calls. If not provided, falls back to clipboard copy. */
  playerName?: string;
}

const DIRECTIONS = [
  { value: 'above', label: 'Above (price rises above target)', color: 'text-emerald-600 dark:text-emerald-400' },
  { value: 'below', label: 'Below (price drops below target)', color: 'text-amber-600 dark:text-amber-400' },
] as const;

export function PriceAlertDialog({ item, open, onClose, playerName }: PriceAlertDialogProps) {
  const { apiBase } = useAppContext();
  const [direction, setDirection] = useState<'above' | 'below'>('below');
  const [price, setPrice] = useState('');
  const [copied, setCopied] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [mode, setMode] = useState<'form' | 'list'>('form');

  // Alert list state (only used when playerName is provided)
  const [alerts, setAlerts] = useState<AlertDto[]>([]);
  const [alertsLoading, setAlertsLoading] = useState(false);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);

  const useApi = Boolean(playerName);

  // Load alerts when dialog opens with playerName
  const loadAlerts = useCallback(async () => {
    if (!useApi || !playerName) return;
    setAlertsLoading(true);
    setAlertsError(null);
    try {
      const list = await api.alerts.list(apiBase, playerName);
      // Filter to this item's alerts only
      setAlerts(list.filter(a => a.itemId === item.id));
    } catch (e) {
      setAlertsError('Failed to load alerts');
    } finally {
      setAlertsLoading(false);
    }
  }, [useApi, playerName, item.id, apiBase]);

  useEffect(() => {
    if (open) {
      setCopied(false);
      setPrice('');
      setDirection('below');
      setSubmitError(null);
      setSubmitSuccess(false);
      setMode('form');
      if (useApi) {
        loadAlerts();
      }
    }
  }, [open, item.id, useApi, loadAlerts]);

  if (!open) return null;

  const command = `/autotune alert add ${item.material} ${direction} ${price || '<price>'}`;

  async function handleCopy() {
    if (!price) return;
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = command;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  async function handleApiCreate() {
    if (!price || !playerName) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await api.alerts.create(apiBase, playerName, item.id, direction.toUpperCase() as 'ABOVE' | 'BELOW', parseFloat(price));
      setSubmitSuccess(true);
      setPrice('');
      await loadAlerts();
      setTimeout(() => setSubmitSuccess(false), 2000);
    } catch (e: unknown) {
      setSubmitError(e instanceof Error ? e.message : 'Failed to create alert');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove(alertId: string) {
    if (!playerName) return;
    try {
      await api.alerts.remove(apiBase, alertId, playerName);
      setAlerts(prev => prev.filter(a => a.id !== alertId));
    } catch {
      // Silently fail — alert may have already been removed server-side
    }
  }

  async function handleToggle(alertId: string) {
    if (!playerName) return;
    try {
      const updated = await api.alerts.toggle(apiBase, alertId, playerName);
      setAlerts(prev => prev.map(a => a.id === alertId ? updated : a));
    } catch {
      // Silently fail
    }
  }

  async function handleRearm(alertId: string) {
    if (!playerName) return;
    try {
      const updated = await api.alerts.rearm(apiBase, alertId, playerName);
      setAlerts(prev => prev.map(a => a.id === alertId ? updated : a));
    } catch {
      // Silently fail
    }
  }

  // Filter to this item's alerts for display
  const itemAlerts = alerts;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />

      {/* Dialog */}
      <div className="relative z-10 w-full max-w-sm bg-card border border-border rounded-2xl shadow-2xl shadow-black/50 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-primary" />
            <h2 className="text-sm font-semibold text-foreground">Price Alerts</h2>
          </div>
          <div className="flex items-center gap-1">
            {useApi && (
              <button
                onClick={() => setMode(m => m === 'form' ? 'list' : 'form')}
                className="px-2 py-1 text-xs rounded border border-border hover:bg-muted transition-colors text-muted-foreground"
              >
                {mode === 'form' ? `My alerts (${itemAlerts.length})` : 'New alert'}
              </button>
            )}
            <button
              onClick={onClose}
              className="text-muted-foreground hover:text-foreground transition-colors ml-1"
              aria-label="Close"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Item context */}
        <div className="px-5 py-3 bg-muted/50 border-b border-border flex items-center gap-3">
          <div>
            <p className="text-sm font-medium text-foreground">{item.displayName}</p>
            <p className="text-xs text-muted-foreground font-mono">{item.material}</p>
          </div>
          <div className="ml-auto text-right">
            <p className="text-xs text-muted-foreground">Current buy</p>
            <p className="text-sm font-mono font-semibold text-emerald-600 dark:text-emerald-400">
              ${item.buyPrice.toFixed(2)}
            </p>
          </div>
        </div>

        {/* Mode: list */}
        {mode === 'list' && useApi && (
          <div className="px-5 py-4 space-y-3 max-h-64 overflow-y-auto">
            {alertsLoading && (
              <div className="flex items-center justify-center py-6 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                <span className="text-sm">Loading alerts…</span>
              </div>
            )}
            {alertsError && (
              <p className="text-xs text-destructive text-center py-4">{alertsError}</p>
            )}
            {!alertsLoading && !alertsError && itemAlerts.length === 0 && (
              <p className="text-sm text-muted-foreground text-center py-4">
                No alerts for {item.displayName}. Switch to New Alert to create one.
              </p>
            )}
            {!alertsLoading && itemAlerts.map(alert => (
              <div key={alert.id} className="rounded-lg border border-border p-3 space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className={`text-xs font-medium ${alert.alertType === 'ABOVE' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'}`}>
                    {alert.alertType === 'ABOVE' ? '↑ Above' : '↓ Below'} ${alert.targetPrice.toFixed(2)}
                  </span>
                  <div className="flex items-center gap-1">
                    {alert.triggered && (
                      <button
                        onClick={() => handleRearm(alert.id)}
                        className="text-xs px-1.5 py-0.5 rounded border border-border hover:bg-muted text-muted-foreground transition-colors"
                        title="Rearm triggered alert"
                      >
                        Rearm
                      </button>
                    )}
                    {!alert.triggered && (
                      <button
                        onClick={() => handleToggle(alert.id)}
                        className="text-muted-foreground hover:text-foreground transition-colors"
                        title={alert.enabled ? 'Disable alert' : 'Enable alert'}
                      >
                        {alert.enabled
                          ? <ToggleRight className="h-4 w-4 text-primary" />
                          : <ToggleLeft className="h-4 w-4" />
                        }
                      </button>
                    )}
                    <button
                      onClick={() => handleRemove(alert.id)}
                      className="text-muted-foreground hover:text-destructive transition-colors"
                      title="Remove alert"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                  {alert.triggered
                    ? <span className="text-yellow-600 dark:text-yellow-400">⚠ Triggered at ${alert.triggeredAt ? new Date(alert.triggeredAt).toLocaleString() : 'unknown'}</span>
                    : <span>{alert.enabled ? 'Active' : 'Disabled'}</span>
                  }
                  {alert.currentPrice > 0 && (
                    <span>· Current: ${alert.currentPrice.toFixed(2)}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Mode: create form */}
        {mode === 'form' && (
          <div className="px-5 py-4 space-y-4">
            {/* Direction */}
            <div>
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2 block">
                Alert when price goes…
              </label>
              <div className="grid grid-cols-2 gap-2">
                {DIRECTIONS.map((d) => (
                  <button
                    key={d.value}
                    onClick={() => setDirection(d.value)}
                    className={`px-3 py-2 rounded-lg border text-xs font-medium transition-all ${
                      direction === d.value
                        ? 'border-primary bg-primary/10 text-foreground'
                        : 'border-border bg-background text-muted-foreground hover:border-muted-foreground/50'
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Price input */}
            <div>
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-2 block">
                Target price ($)
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm font-mono">$</span>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  placeholder={item.buyPrice.toFixed(2)}
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  className="w-full pl-7 pr-4 py-2 rounded-lg border border-border bg-background text-sm font-mono text-foreground placeholder:text-muted-foreground/40 focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                />
              </div>
              {price && (
                <p className="text-xs text-muted-foreground mt-1.5">
                  Alert fires when {item.displayName} buy price {direction === 'above' ? 'exceeds' : 'drops below'} <span className="font-mono text-foreground">${parseFloat(price).toFixed(2)}</span>
                </p>
              )}
            </div>

            {/* Generated command */}
            <div className="rounded-lg border border-dashed border-border bg-muted/30 p-3">
              <p className="text-[10px] text-muted-foreground uppercase tracking-wider mb-1.5 font-medium">
                Command
              </p>
              <code className="text-xs font-mono text-primary break-all block">
                {command.replace('<price>', price || `$${item.buyPrice.toFixed(2)}`)}
              </code>
            </div>

            {/* Submit feedback */}
            {submitError && (
              <p className="text-xs text-destructive">{submitError}</p>
            )}
            {submitSuccess && (
              <p className="text-xs text-emerald-600 dark:text-emerald-400">Alert created!</p>
            )}

            {/* Help toggle */}
            <button
              onClick={() => setShowHelp((v) => !v)}
              className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              <Info className="h-3 w-3" />
              How do alerts work?
              {showHelp ? ' ▲' : ' ▼'}
            </button>
            {showHelp && (
              <div className="text-xs text-muted-foreground bg-muted/50 rounded-lg p-3 space-y-1.5 border border-border">
                <p>Alerts notify you in-game when an item&apos;s buy price crosses your target.</p>
                <p>Use <code className="text-[10px] font-mono bg-muted px-1 py-0.5 rounded">/autotune alert list</code> to see active alerts and <code className="text-[10px] font-mono bg-muted px-1 py-0.5 rounded">/autotune alert remove</code> to delete them.</p>
                <p>Alerts check every minute. Notifications appear as in-game messages.</p>
              </div>
            )}
          </div>
        )}

        {/* Footer */}
        <div className="px-5 py-4 border-t border-border flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-3.5 py-1.5 rounded-lg border border-border text-xs text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
          >
            Cancel
          </button>
          {useApi ? (
            <button
              onClick={handleApiCreate}
              disabled={!price || submitting}
              className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                !price || submitting
                  ? 'opacity-40 cursor-not-allowed bg-primary/10 text-primary'
                  : submitSuccess
                  ? 'bg-emerald-600/20 border border-emerald-600/40 text-emerald-400'
                  : 'bg-primary/10 border border-primary/30 text-primary hover:bg-primary/20'
              }`}
            >
              {submitting ? (
                <><Loader2 className="h-3.5 w-3.5 animate-spin" />Creating…</>
              ) : submitSuccess ? (
                <><Check className="h-3.5 w-3.5" />Created!</>
              ) : (
                <><Plus className="h-3.5 w-3.5" />Create Alert</>
              )}
            </button>
          ) : (
            <button
              onClick={handleCopy}
              disabled={!price}
              className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                !price
                  ? 'opacity-40 cursor-not-allowed bg-primary/10 text-primary'
                  : copied
                  ? 'bg-emerald-600/20 border border-emerald-600/40 text-emerald-400'
                  : 'bg-primary/10 border border-primary/30 text-primary hover:bg-primary/20'
              }`}
            >
              {copied ? (
                <><Check className="h-3.5 w-3.5" />Copied!</>
              ) : (
                <><Copy className="h-3.5 w-3.5" />Copy Command</>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
