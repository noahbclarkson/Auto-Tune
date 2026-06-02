'use client';

// Simple SVG market depth chart — no external library needed.
// Shows cumulative bid/ask depth by price level.

interface DepthLevel {
  price: number;
  bidQty: number;  // cumulative bid quantity at this price
  askQty: number;  // cumulative ask quantity at this price
}

interface DepthChartData {
  levels: DepthLevel[];
  midPrice: number;
  maxQty: number;
}

interface DepthChartProps {
  bids?: { price: number; qty: number }[];
  asks?: { price: number; qty: number }[];
  currency?: string;
}

function formatPrice(p: number, currency = '$'): string {
  if (p >= 10000) return `${currency}${(p / 1000).toFixed(0)}K`;
  if (p >= 1000) return `${currency}${(p / 1000).toFixed(1)}K`;
  return `${currency}${p.toFixed(2)}`;
}

// Build cumulative depth from raw bid/ask orders
function buildDepthData(
  bids: { price: number; qty: number }[],
  asks: { price: number; qty: number }[]
): DepthChartData | null {
  if (!bids.length && !asks.length) return null;

  // Sort bids descending by price, asks ascending by price
  const sortedBids = [...bids].sort((a, b) => b.price - a.price);
  const sortedAsks = [...asks].sort((a, b) => a.price - b.price);

  // Cumulative bid depth (highest bid first = leftmost on chart)
  const bidByPrice = new Map<number, number>();
  let bidCum = 0;
  for (const b of sortedBids) {
    bidCum += b.qty;
    bidByPrice.set(b.price, bidCum);
  }
  const bidEntries: [number, number][] = Array.from(bidByPrice.entries()).sort((a, b) => b[0] - a[0]);

  // Cumulative ask depth (lowest ask first = rightmost on chart)
  const askByPrice = new Map<number, number>();
  let askCum = 0;
  for (const a of sortedAsks) {
    askCum += a.qty;
    askByPrice.set(a.price, askCum);
  }
  const askEntries: [number, number][] = Array.from(askByPrice.entries()).sort((a, b) => a[0] - b[0]);

  const allPrices = Array.from(bidByPrice.keys()).concat(Array.from(askByPrice.keys())).sort((a, b) => a - b);

  if (!allPrices.length) return null;

  const minPrice = allPrices[0];
  const maxPrice = allPrices[allPrices.length - 1];
  const midPrice = bids.length && asks.length
    ? (sortedBids[0].price + sortedAsks[0].price) / 2
    : allPrices[Math.floor(allPrices.length / 2)];

  const maxQty = Math.max(
    bidEntries.length ? bidEntries[bidEntries.length - 1][1] : 0,
    askEntries.length ? askEntries[askEntries.length - 1][1] : 0
  );

  return { levels: bidEntries.map(([price, bidQty]) => ({
    price,
    bidQty,
    askQty: 0,
  })).concat(askEntries.map(([price, askQty]) => ({
    price,
    bidQty: 0,
    askQty,
  }))), midPrice, maxQty };
}

export function DepthChart({
  bids = MOCK_BIDS,
  asks = MOCK_ASKS,
  currency = '$',
}: DepthChartProps) {
  const data = buildDepthData(bids, asks);

  if (!data || data.maxQty === 0) {
    return (
      <div className="flex items-center justify-center h-40 rounded-xl border border-gray-700 bg-gray-900 text-gray-500 text-sm">
        No depth data available
      </div>
    );
  }

  const { levels, midPrice, maxQty } = data;
  const W = 500;
  const H = 160;
  const PAD = { top: 10, right: 10, bottom: 30, left: 10 };
  const chartW = W - PAD.left - PAD.right;
  const chartH = H - PAD.top - PAD.bottom;

  // Price range
  const prices = levels.map(l => l.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const priceRange = maxPrice - minPrice || 1;

  const priceToX = (price: number) =>
    PAD.left + ((price - minPrice) / priceRange) * chartW;
  const qtyToY = (qty: number) =>
    PAD.top + chartH - (qty / maxQty) * chartH;

  const midX = priceToX(midPrice);

  // Build bid path (green, left of mid) — step function
  const bidLevels = levels.filter(l => l.bidQty > 0).sort((a, b) => b.price - a.price);
  const askLevels = levels.filter(l => l.askQty > 0).sort((a, b) => a.price - b.price);

  function buildStepPath(pts: { x: number; y: number }[]): string {
    if (!pts.length) return '';
    let d = `M ${pts[0].x} ${pts[0].y}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const cur = pts[i];
      const next = pts[i + 1];
      d += ` H ${next.x} V ${next.y}`;
    }
    return d;
  }

  const bidPts = bidLevels.map(l => ({ x: priceToX(l.price), y: qtyToY(l.bidQty) }));
  // Extend to baseline at bottom
  const bidPtsFull = bidPts.length
    ? [
        { x: PAD.left, y: qtyToY(0) },
        { x: PAD.left, y: bidPts[0].y },
        ...bidPts,
        { x: midX, y: bidPts[bidPts.length - 1]?.y ?? PAD.top + chartH },
      ]
    : [];

  const askPts = askLevels.map(l => ({ x: priceToX(l.price), y: qtyToY(l.askQty) }));
  const askPtsFull = askPts.length
    ? [
        { x: W - PAD.right, y: qtyToY(0) },
        { x: W - PAD.right, y: askPts[0].y },
        ...askPts,
        { x: midX, y: askPts[askPts.length - 1]?.y ?? PAD.top + chartH },
      ]
    : [];

  const bidPath = buildStepPath(bidPtsFull);
  const askPath = buildStepPath(askPtsFull);

  // X-axis tick prices
  const tickCount = 5;
  const ticks = Array.from({ length: tickCount + 1 }, (_, i) => {
    const p = minPrice + (priceRange * i) / tickCount;
    return { price: p, x: priceToX(p) };
  });

  return (
    <div className="rounded-xl border border-gray-700 bg-gray-900 p-4">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs text-gray-400 font-medium">Market Depth Ladder</p>
        <div className="flex items-center gap-3 text-xs">
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-sm bg-emerald-400/80" />
            <span className="text-gray-500">Bids</span>
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-sm bg-rose-400/80" />
            <span className="text-gray-500">Asks</span>
          </span>
        </div>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ display: 'block' }}>
        {/* Grid lines */}
        {[0.25, 0.5, 0.75].map(t => {
          const y = PAD.top + chartH * (1 - t);
          return (
            <line
              key={t}
              x1={PAD.left}
              y1={y}
              x2={W - PAD.right}
              y2={y}
              stroke="#1f2937"
              strokeWidth={0.5}
              strokeDasharray="3,3"
            />
          );
        })}

        {/* Mid price vertical */}
        <line
          x1={midX}
          y1={PAD.top}
          x2={midX}
          y2={PAD.top + chartH}
          stroke="#374151"
          strokeWidth={0.75}
          strokeDasharray="4,3"
        />

        {/* Bid area */}
        {bidPtsFull.length > 0 && (
          <path
            d={bidPath}
            fill="rgba(52,211,153,0.15)"
            stroke="rgba(52,211,153,0.6)"
            strokeWidth={1.5}
            strokeLinejoin="round"
          />
        )}

        {/* Ask area */}
        {askPtsFull.length > 0 && (
          <path
            d={askPath}
            fill="rgba(244,63,94,0.15)"
            stroke="rgba(244,63,94,0.6)"
            strokeWidth={1.5}
            strokeLinejoin="round"
          />
        )}

        {/* X-axis ticks */}
        {ticks.map(({ price, x }) => (
          <g key={price}>
            <line x1={x} y1={PAD.top + chartH} x2={x} y2={PAD.top + chartH + 4} stroke="#4b5563" strokeWidth={0.5} />
            <text
              x={x}
              y={PAD.top + chartH + 14}
              textAnchor="middle"
              fill="#6b7280"
              fontSize={9}
              fontFamily="monospace"
            >
              {formatPrice(price, currency)}
            </text>
          </g>
        ))}

        {/* Mid price label */}
        <text
          x={midX}
          y={PAD.top - 1}
          textAnchor="middle"
          fill="#9ca3af"
          fontSize={9}
          fontFamily="monospace"
        >
          mid {formatPrice(midPrice, currency)}
        </text>
      </svg>
    </div>
  );
}

// ─── Mock data ────────────────────────────────────────────────────────────────

const MOCK_BIDS = [
  { price: 305, qty: 12 },
  { price: 298, qty: 34 },
  { price: 290, qty: 52 },
  { price: 285, qty: 68 },
  { price: 280, qty: 79 },
  { price: 270, qty: 88 },
];

const MOCK_ASKS = [
  { price: 318, qty: 8 },
  { price: 325, qty: 18 },
  { price: 330, qty: 30 },
  { price: 340, qty: 46 },
  { price: 355, qty: 60 },
  { price: 370, qty: 75 },
];
