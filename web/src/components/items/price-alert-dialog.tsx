'use client';

import { useState, useEffect } from 'react';
import { Bell, Copy, Check, X, Info } from 'lucide-react';
import type { ItemDto } from '@/lib/api';

interface PriceAlertDialogProps {
  item: ItemDto;
  open: boolean;
  onClose: () => void;
}

const DIRECTIONS = [
  { value: 'above', label: 'Above (price rises above target)', color: 'text-emerald-600 dark:text-emerald-400' },
  { value: 'below', label: 'Below (price drops below target)', color: 'text-amber-600 dark:text-amber-400' },
] as const;

export function PriceAlertDialog({ item, open, onClose }: PriceAlertDialogProps) {
  const [direction, setDirection] = useState<'above' | 'below'>('below');
  const [price, setPrice] = useState('');
  const [copied, setCopied] = useState(false);
  const [showHelp, setShowHelp] = useState(false);

  // Reset state when dialog opens for a new item
  useEffect(() => {
    if (open) {
      setCopied(false);
      setPrice('');
      setDirection('below');
    }
  }, [open, item.id]);

  if (!open) return null;

  const command = `/autotune alert add ${item.material} ${direction} ${price || '<price>'}`;

  async function handleCopy() {
    if (!price) return;
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback for environments without clipboard API
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Dialog */}
      <div className="relative z-10 w-full max-w-sm bg-card border border-border rounded-2xl shadow-2xl shadow-black/50 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-primary" />
            <h2 className="text-sm font-semibold text-foreground">Set Price Alert</h2>
          </div>
          <button
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground transition-colors"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
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

        {/* Form */}
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

        {/* Footer */}
        <div className="px-5 py-4 border-t border-border flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-3.5 py-1.5 rounded-lg border border-border text-xs text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
          >
            Cancel
          </button>
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
            title={!price ? 'Enter a target price above' : 'Copy command to clipboard'}
          >
            {copied ? (
              <>
                <Check className="h-3.5 w-3.5" />
                Copied!
              </>
            ) : (
              <>
                <Copy className="h-3.5 w-3.5" />
                Copy Command
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
