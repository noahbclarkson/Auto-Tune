'use client';

// Animated SVG flow diagram showing the spread pipeline
// Shows how 6 factors cascade into buy price and sell price

const STEPS = [
  { label: 'Base\nSpread', short: 'halfSpread', color: '#6b7280' },
  { label: 'Imbalance\nMultiplier', short: '×Imb', color: '#38bdf8' },
  { label: 'Liquidity\nFactor', short: '×LiQ', color: '#a78bfa' },
  { label: 'Player\nScaling', short: '×PS', color: '#fbbf24' },
  { label: 'Global\nVolume', short: '×Vol', color: '#fb7185' },
];

const BUY_COLOR = '#10b981';
const SELL_COLOR = '#ef4444';

function Arrow({ x, y }: { x: number; y: number }) {
  return (
    <path
      d={`M ${x} ${y} L ${x + 24} ${y}`}
      stroke="#374151"
      strokeWidth="2"
      markerEnd="url(#arrowhead)"
    />
  );
}

export function SpreadFlowDiagram() {
  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 p-6 overflow-x-auto">
      <h3 className="text-sm font-semibold text-white mb-4">The Six-Factor Pipeline</h3>
      <p className="text-xs text-gray-500 mb-5 leading-relaxed">
        The spread is computed fresh every 5-minute tick by multiplying five sequential factors against the base half-spread.
        Each factor can only compress or expand the spread — it cannot flip buy above sell.
      </p>

      {/* SVG Flow Diagram */}
      <div className="flex items-start gap-0 min-w-[640px]">
        {/* Factor boxes */}
        {STEPS.map((step, i) => (
          <div key={step.short} className="flex flex-col items-center">
            <div
              className="w-24 h-16 rounded-xl border-2 flex flex-col items-center justify-center text-center p-1"
              style={{ borderColor: step.color, backgroundColor: `${step.color}10` }}
            >
              <span className="text-[9px] font-mono font-bold leading-tight" style={{ color: step.color }}>
                {step.label}
              </span>
            </div>
            <p className="text-[8px] text-gray-500 mt-1 font-mono">{step.short}</p>
            {i < STEPS.length - 1 && (
              <div className="flex items-center mt-1">
                <svg width="24" height="16">
                  <path d="M 4 8 L 20 8" stroke="#374151" strokeWidth="2" markerEnd="url(#arrowhead-gray)" />
                </svg>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Equals signs */}
      <div className="flex items-center mt-3 min-w-[640px]">
        <div className="flex-1 flex items-center justify-center">
          <span className="text-gray-600 text-xs font-mono mx-2">=</span>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <span className="text-gray-600 text-xs font-mono mx-2">=</span>
        </div>
      </div>

      {/* Result: BPD and SPD */}
      <div className="flex items-center justify-center gap-8 mt-3 min-w-[640px]">
        <div className="flex flex-col items-center gap-1">
          <div className="w-28 rounded-xl border-2 border-emerald-600/60 bg-emerald-950/30 px-4 py-3 text-center">
            <p className="text-[9px] text-gray-500 uppercase tracking-wider mb-1">Buy Premium</p>
            <p className="text-lg font-bold font-mono text-emerald-400">BPD</p>
            <p className="text-[9px] text-gray-500 font-mono mt-0.5">Deterministic</p>
          </div>
          <p className="text-[9px] text-gray-500">applied to base price →</p>
        </div>

        <div className="w-px h-12 bg-gray-800" />

        <div className="flex flex-col items-center gap-1">
          <div className="w-28 rounded-xl border-2 border-red-600/60 bg-red-950/30 px-4 py-3 text-center">
            <p className="text-[9px] text-gray-500 uppercase tracking-wider mb-1">Sell Discount</p>
            <p className="text-lg font-bold font-mono text-red-400">SPD</p>
            <p className="text-[9px] text-gray-500 font-mono mt-0.5">Deterministic</p>
          </div>
          <p className="text-[9px] text-gray-500">applied to base price →</p>
        </div>
      </div>

      {/* Final prices */}
      <div className="flex items-center justify-center gap-6 mt-4 min-w-[640px]">
        <div className="text-center">
          <p className="text-xs text-gray-500 mb-1">Buy Price</p>
          <div className="inline-flex items-center gap-1.5 bg-emerald-950/40 border border-emerald-800/40 rounded-lg px-4 py-2">
            <span className="text-emerald-400 font-mono font-bold text-sm">basePrice</span>
            <span className="text-gray-500">×</span>
            <span className="text-emerald-400 font-mono font-bold text-sm">(1 + BPD)</span>
          </div>
        </div>
        <div className="text-center">
          <p className="text-xs text-gray-500 mb-1">Sell Price</p>
          <div className="inline-flex items-center gap-1.5 bg-red-950/40 border border-red-800/40 rounded-lg px-4 py-2">
            <span className="text-red-400 font-mono font-bold text-sm">basePrice</span>
            <span className="text-gray-500">×</span>
            <span className="text-red-400 font-mono font-bold text-sm">(1 − SPD)</span>
          </div>
        </div>
      </div>

      {/* Buy > Sell guarantee note */}
      <div className="mt-4 pt-4 border-t border-gray-800 flex items-start gap-2">
        <div className="w-2 h-2 rounded-full bg-emerald-500 mt-1 shrink-0" />
        <p className="text-[11px] text-gray-500 leading-relaxed">
          <strong className="text-gray-400">Asymmetry guarantee:</strong> Because the two factors use{' '}
          <code className="text-emerald-400 bg-gray-800 px-1 rounded text-[10px]">imbalance</code> (widens buy) and{' '}
          <code className="text-red-400 bg-gray-800 px-1 rounded text-[10px]">(2 − imbalance)</code> (narrows sell), the buy price is{' '}
          <em>always</em> above the sell price — even in extreme conditions.
        </p>
      </div>

      {/* SVG defs */}
      <svg width="0" height="0" className="absolute">
        <defs>
          <marker id="arrowhead" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
            <path d="M 0 0 L 6 3 L 0 6 Z" fill="#374151" />
          </marker>
          <marker id="arrowhead-gray" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
            <path d="M 0 0 L 6 3 L 0 6 Z" fill="#374151" />
          </marker>
        </defs>
      </svg>
    </div>
  );
}
