'use client';

import { useState, useEffect } from 'react';
import { ShoppingCart, TrendingDown, BarChart2, Coins, X } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

const STORAGE_KEY = 'autotune_onboarding_dismissed';

interface QuickStartProps {
  className?: string;
}

export function QuickStart({ className }: QuickStartProps) {
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'true') setDismissed(true);
  }, []);

  const handleDismiss = () => {
    localStorage.setItem(STORAGE_KEY, 'true');
    setDismissed(true);
  };

  if (dismissed) return null;

  const steps = [
    {
      icon: ShoppingCart,
      command: '/shop',
      description: 'Browse all items, search by name, sort by price or change',
      label: 'Buy items',
      color: 'text-emerald-500',
      bg: 'bg-emerald-500/10',
    },
    {
      icon: TrendingDown,
      command: '/sell',
      description: 'Sell items instantly at current market prices',
      label: 'Sell items',
      color: 'text-amber-500',
      bg: 'bg-amber-500/10',
    },
    {
      icon: BarChart2,
      command: '/compare',
      description: 'Compare any two items side-by-side with live charts',
      label: 'Compare prices',
      color: 'text-sky-500',
      bg: 'bg-sky-500/10',
    },
    {
      icon: Coins,
      command: '/loans',
      description: 'Take loans to invest in your projects, repay over time',
      label: 'Take a loan',
      color: 'text-violet-500',
      bg: 'bg-violet-500/10',
    },
  ];

  return (
    <Card className={`border-emerald-900/30 bg-gradient-to-r from-emerald-950/50 to-emerald-900/20 ${className ?? ''}`}>
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded-md bg-emerald-500/20 flex items-center justify-center shrink-0">
              <BarChart2 className="h-4 w-4 text-emerald-400" />
            </div>
            <div>
              <p className="text-sm font-semibold text-emerald-300 leading-none">Welcome to the Market</p>
              <p className="text-xs text-emerald-400/70 mt-0.5">Prices change with supply and demand — here&apos;s how to play</p>
            </div>
          </div>
          <button
            onClick={handleDismiss}
            className="shrink-0 text-emerald-500/40 hover:text-emerald-400/70 transition-colors p-1 rounded"
            aria-label="Dismiss welcome banner"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {steps.map((step) => {
            const Icon = step.icon;
            return (
              <div
                key={step.command}
                className="flex flex-col gap-1.5 p-2.5 rounded-lg bg-background/40 border border-emerald-900/20 hover:border-emerald-700/30 transition-colors"
              >
                <div className={`h-7 w-7 rounded-md ${step.bg} flex items-center justify-center`}>
                  <Icon className={`h-3.5 w-3.5 ${step.color}`} />
                </div>
                <p className="text-xs font-mono font-semibold text-foreground">{step.command}</p>
                <p className="text-[11px] text-muted-foreground leading-tight">{step.description}</p>
              </div>
            );
          })}
        </div>

        <div className="mt-3 flex items-center gap-2">
          <div className="h-px flex-1 bg-gradient-to-r from-transparent via-emerald-900/50 to-transparent" />
          <p className="text-[11px] text-emerald-500/50">
            Prices update every 5 minutes — buy low, sell high
          </p>
          <div className="h-px flex-1 bg-gradient-to-r from-transparent via-emerald-900/50 to-transparent" />
        </div>
      </CardContent>
    </Card>
  );
}
