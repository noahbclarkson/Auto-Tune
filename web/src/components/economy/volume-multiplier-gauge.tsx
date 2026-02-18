'use client';

import { Card, CardContent } from '@/components/ui/card';

interface VolumeMultiplierGaugeProps {
  multiplier: number;
}

export function VolumeMultiplierGauge({ multiplier }: VolumeMultiplierGaugeProps) {
  const label =
    multiplier > 1.05
      ? 'Low Activity (Spreads Widening)'
      : multiplier < 0.95
      ? 'High Activity (Spreads Narrowing)'
      : 'Normal Activity';

  const color =
    multiplier > 1.05
      ? 'text-amber-600 dark:text-amber-400'
      : multiplier < 0.95
      ? 'text-emerald-600 dark:text-emerald-400'
      : 'text-foreground';

  const pct = ((multiplier - 0.8) / (1.3 - 0.8)) * 100;
  const clampedPct = Math.max(0, Math.min(100, pct));

  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-sm text-muted-foreground mb-2">Global Volume Multiplier</p>
        <div className="flex items-center gap-4">
          <p className={`text-2xl font-bold ${color}`}>{multiplier.toFixed(3)}x</p>
          <div className="flex-1">
            <div className="h-2 rounded-full bg-muted overflow-hidden">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${clampedPct}%` }}
              />
            </div>
            <div className="flex justify-between text-[10px] text-muted-foreground mt-1">
              <span>0.8x</span>
              <span>1.0x</span>
              <span>1.3x</span>
            </div>
          </div>
        </div>
        <p className="text-xs text-muted-foreground mt-2">{label}</p>
      </CardContent>
    </Card>
  );
}
