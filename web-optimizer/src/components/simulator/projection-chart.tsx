'use client';

import { useMemo } from 'react';
import {
  MarketConfig,
  DEFAULT_CONFIG,
  simulatePriceWithEvents,
  MarketEvent,
} from '@/lib/market-engine';
import { formatPrice } from '@/lib/utils';

interface ProjectionChartProps {
  config: MarketConfig;
  basePrice: number;
  buyRatio: number;
  onlinePlayers: number;
  activeEvents: MarketEvent[];
  projectionMaterial: string;
  onMaterialChange: (m: string) => void;
}

const PROJECTION_MATERIALS = [
  'DIAMOND', 'EMERALD', 'GOLD_INGOT', 'IRON_INGOT', 'NETHERITE_INGOT',
  'COAL', 'LAPIS_LAZULI', 'REDSTONE', 'COPPER_INGOT', 'ANCIENT_DEBRIS',
  'OAK_LOG', 'STONE', 'COBBLESTONE', 'DIRT',
  'DIAMOND_SWORD', 'NETHERITE_SWORD', 'DIAMOND_PICKAXE',
];

export function ProjectionChart({
  config,
  basePrice,
  buyRatio,
  onlinePlayers,
  activeEvents,
  projectionMaterial,
  onMaterialChange,
}: ProjectionChartProps) {
  const TICKS = 60; // 60 ticks = 5-minute ticks × 60 = 5h simulated horizon

  const { withEvents, withoutEvents } = useMemo(() => {
    const without = simulatePriceWithEvents(
      TICKS, basePrice, buyRatio, onlinePlayers, config, [], projectionMaterial,
    );
    const withEvts = activeEvents.length > 0
      ? simulatePriceWithEvents(TICKS, basePrice, buyRatio, onlinePlayers, config, activeEvents, projectionMaterial)
      : without;
    return { withEvents: withEvts, withoutEvents: without };
  }, [basePrice, buyRatio, onlinePlayers, config, activeEvents, projectionMaterial]);

  const hasEvents = activeEvents.length > 0;

  // SVG chart dimensions
  const W = 600;
  const H = 180;
  const PAD_L = 52;
  const PAD_R = 12;
  const PAD_T = 12;
  const PAD_B = 28;
  const chartW = W - PAD_L - PAD_R;
  const chartH = H - PAD_T - PAD_B;

  // Compute y range
  const allVals = [...withoutEvents, ...withEvents];
  const minV = Math.min(...allVals) * 0.98;
  const maxV = Math.max(...allVals) * 1.02;
  const range = maxV - minV || 1;

  const xOf = (i: number) => PAD_L + (i / TICKS) * chartW;
  const yOf = (v: number) => PAD_T + chartH - ((v - minV) / range) * chartH;

  // Path builder
  const buildPath = (vals: number[]) =>
    vals.map((v, i) => `${i === 0 ? 'M' : 'L'} ${xOf(i).toFixed(1)},${yOf(v).toFixed(1)}`).join(' ');

  // Grid lines
  const gridLines: number[] = [];
  for (let i = 0; i <= 4; i++) gridLines.push(minV + (range * i) / 4);

  // X axis ticks (every 15 ticks = 1.25h)
  const xTicks = [0, 15, 30, 45, 60];

  // Final prices
  const finalNoEvent = withoutEvents[TICKS];
  const finalWithEvent = withEvents[TICKS];
  const pctDiff = finalNoEvent > 0 ? ((finalWithEvent - finalNoEvent) / finalNoEvent) * 100 : 0;

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-white">Price Projection</h3>
          <span className="text-xs text-gray-500 font-mono">
            {projectionMaterial} · {onlinePlayers} players · {(buyRatio * 100).toFixed(0)}% buys
          </span>
        </div>
        <div className="flex items-center gap-3 text-xs">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-0.5 rounded bg-gray-500" />
            <span className="text-gray-500">No events</span>
          </span>
          {hasEvents && (
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-3 h-0.5 rounded bg-emerald-500" />
              <span className="text-emerald-400">With events</span>
            </span>
          )}

          {/* Material selector */}
          <select
            value={projectionMaterial}
            onChange={(e) => onMaterialChange(e.target.value)}
            className="ml-auto rounded bg-gray-800 border border-gray-700 text-white text-xs px-2 py-1 focus:outline-none focus:border-emerald-600"
          >
            {PROJECTION_MATERIALS.map((m) => (
              <option key={m} value={m}>{m.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="p-4">
        <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ maxHeight: '200px' }}>
          {/* Grid lines */}
          {gridLines.map((v, i) => (
            <g key={i}>
              <line
                x1={PAD_L} y1={yOf(v)} x2={PAD_L + chartW} y2={yOf(v)}
                stroke="#1f2937" strokeWidth="1"
              />
              <text x={PAD_L - 4} y={yOf(v) + 4} textAnchor="end" fontSize="10" fill="#6b7280">
                ${v.toFixed(0)}
              </text>
            </g>
          ))}

          {/* X axis */}
          {xTicks.map((t) => (
            <text
              key={t}
              x={xOf(t)} y={H - 4}
              textAnchor="middle" fontSize="10" fill="#6b7280"
            >
              {t === 0 ? 'now' : `${t * 5}min`}
            </text>
          ))}

          {/* Base price dashed line */}
          <line
            x1={PAD_L} y1={yOf(basePrice)} x2={PAD_L + chartW} y2={yOf(basePrice)}
            stroke="#374151" strokeWidth="1" strokeDasharray="4 3"
          />

          {/* Without events line */}
          <path
            d={buildPath(withoutEvents)}
            fill="none" stroke="#6b7280" strokeWidth="2" strokeLinejoin="round"
          />

          {/* With events line (only if events present) */}
          {hasEvents && (
            <path
              d={buildPath(withEvents)}
              fill="none" stroke="#10b981" strokeWidth="2" strokeLinejoin="round"
            />
          )}

          {/* Endpoint dots */}
          <circle cx={xOf(TICKS)} cy={yOf(finalNoEvent)} r="3" fill="#6b7280" />
          {hasEvents && (
            <circle cx={xOf(TICKS)} cy={yOf(finalWithEvent)} r="3" fill="#10b981" />
          )}
        </svg>

        {/* Summary row */}
        <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
          <div className="rounded border border-gray-800 bg-gray-800/20 px-3 py-2">
            <p className="text-gray-500">Final (no events)</p>
            <p className="font-mono font-semibold text-white">${finalNoEvent.toFixed(2)}</p>
            <p className={`font-mono text-xs ${finalNoEvent >= basePrice ? 'text-emerald-500' : 'text-rose-500'}`}>
              {((finalNoEvent / basePrice - 1) * 100).toFixed(1)}% vs base
            </p>
          </div>
          {hasEvents ? (
            <div className="rounded border border-emerald-900/40 bg-emerald-950/20 px-3 py-2">
              <p className="text-emerald-500">Final (with events)</p>
              <p className="font-mono font-semibold text-emerald-400">${finalWithEvent.toFixed(2)}</p>
              <p className={`font-mono text-xs ${pctDiff > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                {pctDiff >= 0 ? '+' : ''}{pctDiff.toFixed(1)}% vs no events
              </p>
            </div>
          ) : (
            <div className="rounded border border-gray-800 bg-gray-800/20 px-3 py-2 flex items-center justify-center">
              <p className="text-gray-600 italic text-xs">
                Add market events above to see the effect
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
