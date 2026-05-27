'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { Play, Pause, RotateCcw, Zap } from 'lucide-react';

// ─── Seeded RNG (LCG) ────────────────────────────────────────────────────────

function createRng(seed: number) {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) & 0xffffffff;
    return (s >>> 0) / 0xffffffff;
  };
}

// ─── Item definitions ────────────────────────────────────────────────────────

const DEMO_ITEMS = [
  { material: 'diamond',        displayName: 'Diamond',          basePrice: 500,   section: 'ores',    color: '#60a5fa' },
  { material: 'iron_ingot',     displayName: 'Iron Ingot',       basePrice: 50,    section: 'ores',    color: '#d1d5db' },
  { material: 'gold_apple',     displayName: 'Gold. Apple',      basePrice: 500,   section: 'food',    color: '#fbbf24' },
  { material: 'redstone',       displayName: 'Redstone',         basePrice: 20,    section: 'ores',    color: '#ef4444' },
  { material: 'netherite',      displayName: 'Netherite Ingot',  basePrice: 2500,  section: 'ores',    color: '#8b5cf6' },
];

const SECTION_LABELS: Record<string, string> = {
  ores: 'Ores & Minerals',
  food: 'Food & Consumables',
  drops: 'Mob Drops',
  building: 'Building',
};

const EVENTS = [
  { label: 'Raid event ends — loot floods market', type: 'SUPPLY_GLUT', effect: -0.08 },
  { label: 'Dragon fight weekend — demand surges', type: 'DEMAND_SURGE', effect: +0.12 },
  { label: 'Server population spikes 3×', type: 'INFLATION_BOOST', effect: +0.06 },
  { label: 'Silent period — few players online', type: 'DEFLATION_DROP', effect: -0.05 },
  { label: 'Gold Rush — mining contest announced', type: 'GOLD_RUSH', effect: +0.10 },
];

const BUY_LABELS = ['buyers dominate', 'slight buy pressure', 'balanced', 'slight sell pressure', 'sellers dominate'];

interface TickEvent {
  tick: number;
  label: string;
  type: string;
  effect: number;
}

// ─── Core simulation ─────────────────────────────────────────────────────────

function runSimulation(seed: number, nTicks: number, buyBias: number) {
  const rng = createRng(seed);
  const prices: number[][] = [];
  const events: TickEvent[] = [];

  // Init prices at base
  for (const item of DEMO_ITEMS) {
    prices.push([item.basePrice]);
  }

  let activeEvent: TickEvent | null = null;
  let eventRemaining = 0;

  for (let t = 1; t <= nTicks; t++) {
    // Random event every ~60 ticks
    if (eventRemaining === 0 && rng() < 0.016) {
      const ev = EVENTS[Math.floor(rng() * EVENTS.length)];
      activeEvent = { tick: t, label: ev.label, type: ev.type, effect: ev.effect };
      eventRemaining = 10 + Math.floor(rng() * 15);
      events.push(activeEvent);
    }
    if (eventRemaining > 0) eventRemaining--;

    const buyProb = 0.5 + (rng() - 0.5) * 0.2 + buyBias;

    for (let i = 0; i < DEMO_ITEMS.length; i++) {
      const item = DEMO_ITEMS[i];
      const prev = prices[i][t - 1];

      // Price change driven by buy/sell pressure
      const tradeRatio = (buyProb - 0.5) * 2.0;
      const maxChange = 0.015; // 1.5% per tick (matches engine)
      const baseChange = tradeRatio * maxChange * 0.7;

      // Event effect (applied to all items)
      const eventMult = activeEvent ? activeEvent.effect * 0.5 : 0;

      // Random noise
      const noise = (rng() - 0.5) * 0.008;

      const totalChange = baseChange + eventMult + noise;
      const nextPrice = Math.max(item.basePrice * 0.1, prev * (1 + totalChange));
      prices[i].push(nextPrice);
    }
  }

  return { prices, events };
}

// ─── Sparkline ───────────────────────────────────────────────────────────────

function Sparkline({ data, color, width = 120, height = 32 }: { data: number[]; color: string; width?: number; height?: number }) {
  if (data.length < 2) return <div style={{ width, height }} />;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pad = 2;
  const w = width - pad * 2;
  const h = height - pad * 2;

  const pts = data.map((v, i) => {
    const x = pad + (i / (data.length - 1)) * w;
    const y = pad + h - ((v - min) / range) * h;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });

  const area = `M${pts.join(' L')} L${(width - pad).toFixed(1)},${(height - pad).toFixed(1)} L${pad},${(height - pad).toFixed(1)} Z`;

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className="overflow-visible">
      <defs>
        <linearGradient id={`sg-${color.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#sg-${color.replace('#', '')})`} />
      <polyline
        points={pts.join(' ')}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

const TICK_MS = 700;
const HISTORY = 80;

export function LiveDemo() {
  const [running, setRunning] = useState(true);
  const [tick, setTick] = useState(0);
  const [resetKey, setResetKey] = useState(0);
  const [lastEvent, setLastEvent] = useState<TickEvent | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { prices, events } = runSimulation(
    42 + resetKey,
    tick,
    0.05 // slight buy bias
  );

  const currentPrices = prices.map((p) => p[p.length - 1]);
  const historyPrices = prices.map((p) => p.slice(-HISTORY));

  const avgChange = prices.length > 0
    ? prices.reduce((sum, p, i) => {
        if (p.length < 2) return sum;
        return sum + (p[p.length - 1] - p[p.length - 2]) / p[p.length - 2];
      }, 0) / prices.length
    : 0;

  const buyLabelIdx = Math.round((0.5 + avgChange / 0.015) * 2);
  const buyLabel = BUY_LABELS[Math.max(0, Math.min(4, buyLabelIdx))];

  // Set recent event when tick changes
  useEffect(() => {
    const recent = [...events].reverse().find((e) => e.tick <= tick);
    if (recent) setLastEvent(recent);
  }, [tick, events]);

  // Tick
  useEffect(() => {
    if (!running) return;
    intervalRef.current = setInterval(() => {
      setTick((t) => t + 1);
    }, TICK_MS);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [running, resetKey]);

  const handleReset = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    setTick(0);
    setLastEvent(null);
    setResetKey((k) => k + 1);
    setRunning(true);
  }, []);

  const formatPrice = (v: number) => {
    if (v >= 10000) return `$${(v / 1000).toFixed(1)}k`;
    if (v >= 1000) return `$${v.toFixed(0)}`;
    return `$${v.toFixed(2)}`;
  };

  const pctChange = (itemIdx: number) => {
    const p = prices[itemIdx];
    if (!p || p.length < 2) return 0;
    return (p[p.length - 1] - p[0]) / p[0];
  };

  return (
    <div className="rounded-2xl border border-gray-800 bg-gray-900/80 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
        <div className="flex items-center gap-2">
          <span className="relative flex h-2.5 w-2.5">
            {running ? (
              <>
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500" />
              </>
            ) : (
              <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500" />
            )}
          </span>
          <span className="text-xs font-mono text-gray-300 uppercase tracking-wider">
            Live Economy Sim
          </span>
          <span className="text-xs text-gray-600">·</span>
          <span className="text-xs text-gray-500 font-mono">tick {tick}</span>
        </div>

        <div className="flex items-center gap-2">
          {lastEvent && (
            <div className="flex items-center gap-1 px-2 py-1 rounded-md bg-amber-950/40 border border-amber-900/50">
              <Zap className="w-3 h-3 text-amber-400" />
              <span className="text-[10px] text-amber-400 font-medium">{lastEvent.label}</span>
            </div>
          )}
          <button
            onClick={() => setRunning((r) => !r)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border border-gray-700 text-xs text-gray-300 hover:border-emerald-700 hover:text-emerald-400 transition-colors"
          >
            {running ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            {running ? 'Pause' : 'Resume'}
          </button>
          <button
            onClick={handleReset}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border border-gray-700 text-xs text-gray-300 hover:border-gray-600 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Reset
          </button>
        </div>
      </div>

      {/* Price rows */}
      <div className="divide-y divide-gray-800/60">
        {DEMO_ITEMS.map((item, idx) => {
          const pct = pctChange(idx);
          const current = currentPrices[idx];
          const history = historyPrices[idx];
          const halfSpread = 0.10; // fixed 10% spread for demo
          const buyPrice = current * (1 + halfSpread);
          const sellPrice = current * (1 - halfSpread);
          const pctStr = (pct * 100).toFixed(1);
          const pctColor = pct > 0 ? 'text-emerald-400' : pct < 0 ? 'text-rose-400' : 'text-gray-500';

          return (
            <div key={item.material} className="flex items-center gap-3 px-4 py-2.5 hover:bg-gray-800/30 transition-colors">
              {/* Sparkline */}
              <div className="shrink-0">
                <Sparkline data={history} color={item.color} width={120} height={32} />
              </div>

              {/* Name + section */}
              <div className="w-28 shrink-0">
                <p className="text-xs font-medium text-gray-200 leading-none">{item.displayName}</p>
                <p className="text-[10px] text-gray-600 mt-0.5">{SECTION_LABELS[item.section] ?? item.section}</p>
              </div>

              {/* Current sell (bid) price */}
              <div className="w-20 shrink-0">
                <p className="text-xs text-gray-500">Sell</p>
                <p className="text-sm font-mono font-semibold text-rose-400">{formatPrice(sellPrice)}</p>
              </div>

              {/* Current buy (ask) price */}
              <div className="w-20 shrink-0">
                <p className="text-xs text-gray-500">Buy</p>
                <p className="text-sm font-mono font-semibold text-emerald-400">{formatPrice(buyPrice)}</p>
              </div>

              {/* Spread bar */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <div className="flex-1 h-1.5 rounded-full bg-gray-800 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-gray-600"
                      style={{ width: '10%', marginLeft: '45%' }}
                    />
                  </div>
                </div>
              </div>

              {/* vs base */}
              <div className="w-16 shrink-0 text-right">
                <p className={`text-xs font-mono font-semibold ${pctColor}`}>
                  {pct >= 0 ? '+' : ''}{pctStr}%
                </p>
                <p className="text-[10px] text-gray-600">vs base</p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Footer */}
      <div className="px-4 py-2.5 border-t border-gray-800 bg-gray-900/50 flex items-center justify-between">
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <span className="font-mono">avg:</span>
          <span className={avgChange >= 0 ? 'text-emerald-500' : 'text-rose-500'}>
            {avgChange >= 0 ? '▲' : '▼'} {Math.abs(avgChange * 100).toFixed(3)}%/tick
          </span>
          <span className="text-gray-700">|</span>
          <span>{buyLabel}</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-gray-600">
          <span>10 ticks ≈ 1 market tick</span>
          <span className="text-gray-700">|</span>
          <span>Powered by Auto-Tune engine</span>
        </div>
      </div>
    </div>
  );
}
