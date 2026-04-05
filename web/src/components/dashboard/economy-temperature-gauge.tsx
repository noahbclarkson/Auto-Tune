'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Thermometer, TrendingUp, TrendingDown, Minus, BarChart2 } from 'lucide-react';

interface EconomyTemperatureGaugeProps {
  /** 0–100 composite score */
  score: number;
  /** Composite health label */
  label: string;
  /** Debt/GDP ratio */
  debtGdpRatio: number | null;
  /** Aggregate volatility */
  avgVolatility: number | null;
  /** Buy ratio percentage */
  buyPct: number | null;
}

function scoreToTemp(score: number): { label: string; color: string; bg: string; fillPct: number } {
  if (score >= 80) return {
    label: 'Healthy',
    color: 'text-emerald-500',
    bg: 'bg-emerald-500',
    fillPct: score,
  };
  if (score >= 55) return {
    label: 'Moderate',
    color: 'text-amber-500',
    bg: 'bg-amber-500',
    fillPct: score,
  };
  if (score >= 30) return {
    label: 'Stressed',
    color: 'text-orange-500',
    bg: 'bg-orange-500',
    fillPct: score,
  };
  return {
    label: 'Critical',
    color: 'text-red-500',
    bg: 'bg-red-500',
    fillPct: score,
  };
}

export function EconomyTemperatureGauge({
  score,
  label,
  debtGdpRatio,
  avgVolatility,
  buyPct,
}: EconomyTemperatureGaugeProps) {
  const temp = scoreToTemp(score);

  const gaugeWidth = 100;
  const fillWidth = Math.round((temp.fillPct / 100) * gaugeWidth);

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center gap-4">
          {/* Thermometer icon + label */}
          <div className="flex flex-col items-center gap-1 shrink-0">
            <Thermometer className={`w-6 h-6 ${temp.color}`} />
            <span className={`text-xs font-bold ${temp.color}`}>{temp.label}</span>
          </div>

          {/* Temperature bar */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1.5">
              <span className="text-xs text-muted-foreground w-8">Cold</span>
              <div className="flex-1 h-3 rounded-full bg-muted overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-500 ${temp.bg}`}
                  style={{ width: `${fillWidth}%` }}
                />
              </div>
              <span className="text-xs text-muted-foreground w-8 text-right">Hot</span>
            </div>

            {/* Score + key metrics */}
            <div className="flex items-center gap-3 text-xs text-muted-foreground flex-wrap">
              <span className="font-bold text-foreground text-sm">{score}/100</span>
              <span>·</span>
              <span title="Volatility">
                <BarChart2 className="inline w-3 h-3 mr-0.5" />
                Vol {avgVolatility !== null ? (avgVolatility * 100).toFixed(1) + '%' : '--'}
              </span>
              <span>·</span>
              <span title="Debt/GDP">
                D/G {debtGdpRatio !== null ? debtGdpRatio.toFixed(1) + 'x' : '--'}
              </span>
              <span>·</span>
              <span title="Buy ratio">
                {buyPct !== null ? buyPct.toFixed(0) + '%' : '--'} buy
              </span>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
