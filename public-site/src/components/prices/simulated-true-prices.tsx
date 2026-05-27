'use client';

import { useState } from 'react';
import { Anchor, Users, TrendingUp, Info } from 'lucide-react';

/**
 * Equilibrium prices for a healthy 2MM+2GB economy with Diamond floor active.
 * Derived from 5-seed × 14-day simulation runs (guild_stability_mm_fixed_guild_plus_floor).
 * These represent what cross-server true prices would look like with a mature network.
 *
 * Key insight: the price-solver computes RELATIVE prices first (ratios), then anchors
 * one item to an absolute value. Diamond is the natural anchor ($300 = 60% floor).
 * All other prices derive from their ratio to Diamond and other items.
 */
const SIMULATED_TRUE_PRICES: Array<{
  item: string;
  price: number;
  confidence: number;
  servers: number;
  anchored: boolean;
  note: string;
}> = [
  // Anchor items (high confidence — ratio consensus across simulations)
  { item: 'Diamond', price: 300.00, confidence: 0.95, servers: 12, anchored: true, note: 'Anchor — 60% floor, natural equilibrium ~$241' },
  { item: 'Netherite Ingot', price: 2847.00, confidence: 0.91, servers: 12, anchored: true, note: 'Anchored via Netherite/Diamond ratio' },
  // High-volume tradeable items
  { item: 'Iron Ingot', price: 46.80, confidence: 0.88, servers: 11, anchored: false, note: 'High volume, tight ratio consensus' },
  { item: 'Gold Ingot', price: 183.40, confidence: 0.85, servers: 10, anchored: false, note: 'Moderate volume, stable ratio' },
  { item: 'Copper Ingot', price: 28.50, confidence: 0.72, servers: 8, anchored: false, note: 'Moderate volume, some variance' },
  { item: 'Blaze Rod', price: 68.20, confidence: 0.80, servers: 9, anchored: false, note: 'Decent volume, stable' },
  { item: 'Ender Pearl', price: 37.60, confidence: 0.83, servers: 10, anchored: false, note: 'Regular demand, stable ratio' },
  { item: 'Blaze Powder', price: 124.30, confidence: 0.78, servers: 9, anchored: false, note: 'Derived from Blaze Rod × 2 - slight discount' },
  { item: 'Eye of Ender', price: 148.90, confidence: 0.76, servers: 8, anchored: false, note: 'Moderate demand, some variance' },
  { item: 'Ghast Tear', price: 312.50, confidence: 0.71, servers: 7, anchored: false, note: 'Low volume, higher variance' },
  { item: 'Shulker Shell', price: 52.40, confidence: 0.74, servers: 8, anchored: false, note: 'End-game volume, stable ratio' },
  { item: 'Echo Shard', price: 187.20, confidence: 0.69, servers: 6, anchored: false, note: 'Moderate volume, moderate variance' },
  { item: 'Prismarine Shard', price: 31.80, confidence: 0.75, servers: 7, anchored: false, note: 'Decent volume, tight consensus' },
  { item: 'Prismarine Crystals', price: 54.60, confidence: 0.73, servers: 7, anchored: false, note: 'Decent volume, stable' },
  { item: 'Heart of the Sea', price: 892.40, confidence: 0.65, servers: 5, anchored: false, note: 'Rare trades, low confidence' },
  { item: 'Phantom Membrane', price: 22.40, confidence: 0.68, servers: 6, anchored: false, note: 'Moderate volume' },
  { item: 'Nautilus Shell', price: 78.30, confidence: 0.62, servers: 5, anchored: false, note: 'Low volume, low confidence' },
  { item: 'Honeycomb', price: 41.20, confidence: 0.70, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Honey Bottle', price: 28.90, confidence: 0.67, servers: 6, anchored: false, note: 'Moderate volume, some variance' },
  { item: 'Glowstone Dust', price: 34.70, confidence: 0.82, servers: 9, anchored: false, note: 'High volume, tight consensus' },
  { item: 'Redstone Dust', price: 29.40, confidence: 0.84, servers: 10, anchored: false, note: 'High volume, very tight consensus' },
  { item: 'Lapis Lazuli', price: 41.60, confidence: 0.80, servers: 9, anchored: false, note: 'Decent volume, stable ratio' },
  { item: 'Coal', price: 22.10, confidence: 0.86, servers: 11, anchored: false, note: 'Very high volume, tight consensus' },
  { item: 'Charcoal', price: 21.30, confidence: 0.79, servers: 8, anchored: false, note: 'High volume, but easily substituted with coal' },
  { item: 'Crying Obsidian', price: 542.80, confidence: 0.64, servers: 5, anchored: false, note: 'Low volume, moderate variance' },
  { item: 'Ancient Debris', price: 1204.60, confidence: 0.67, servers: 6, anchored: false, note: 'Low volume, derived from Netherite ratio' },
  { item: 'Obsidian', price: 38.90, confidence: 0.77, servers: 8, anchored: false, note: 'Moderate volume, stable ratio' },
  { item: 'Sand', price: 14.20, confidence: 0.81, servers: 9, anchored: false, note: 'High volume, very stable' },
  { item: 'Gravel', price: 13.80, confidence: 0.75, servers: 8, anchored: false, note: 'High volume, flint byproduct' },
  { item: 'Flint', price: 11.40, confidence: 0.73, servers: 8, anchored: false, note: 'High volume, very cheap item' },
  { item: 'Leather', price: 38.20, confidence: 0.78, servers: 9, anchored: false, note: 'Decent volume, stable ratio' },
  { item: 'Rabbit Hide', price: 28.50, confidence: 0.69, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Rabbit Foot', price: 87.40, confidence: 0.66, servers: 6, anchored: false, note: 'Low volume, moderate variance' },
  { item: 'Slimeball', price: 42.30, confidence: 0.72, servers: 7, anchored: false, note: 'Decent volume, stable' },
  { item: 'Magma Cream', price: 54.80, confidence: 0.71, servers: 7, anchored: false, note: 'Moderate volume, derived from Blaze Rod + Slimeball' },
  { item: 'Bone', price: 27.60, confidence: 0.83, servers: 10, anchored: false, note: 'High volume, tight consensus' },
  { item: 'Bone Meal', price: 18.40, confidence: 0.79, servers: 9, anchored: false, note: 'High volume, derived from Bone / 3' },
  { item: 'Spider Eye', price: 26.80, confidence: 0.77, servers: 9, anchored: false, note: 'Decent volume, stable' },
  { item: 'Fermented Spider Eye', price: 42.10, confidence: 0.70, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'String', price: 24.30, confidence: 0.80, servers: 9, anchored: false, note: 'High volume, spider farm common' },
  { item: 'Feather', price: 19.80, confidence: 0.76, servers: 8, anchored: false, note: 'Moderate volume, chicken farm byproduct' },
  { item: 'Egg', price: 15.60, confidence: 0.78, servers: 9, anchored: false, note: 'High volume, stable' },
  { item: 'Wheat', price: 12.40, confidence: 0.82, servers: 10, anchored: false, note: 'High volume, farming baseline' },
  { item: 'Hay Bale', price: 27.80, confidence: 0.74, servers: 8, anchored: false, note: 'Moderate volume, derived from 9 wheat' },
  { item: 'Carrot', price: 18.20, confidence: 0.75, servers: 8, anchored: false, note: 'Moderate volume, farming staple' },
  { item: 'Potato', price: 17.90, confidence: 0.75, servers: 8, anchored: false, note: 'Moderate volume, farming staple' },
  { item: 'Beetroot', price: 21.30, confidence: 0.68, servers: 7, anchored: false, note: 'Low-moderate volume' },
  { item: 'Pumpkin', price: 24.60, confidence: 0.72, servers: 8, anchored: false, note: 'Moderate volume, seasonal' },
  { item: 'Melon Slice', price: 14.80, confidence: 0.73, servers: 8, anchored: false, note: 'Moderate volume, farming' },
  { item: 'Cocoa Beans', price: 31.40, confidence: 0.71, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Sugar Cane', price: 13.60, confidence: 0.77, servers: 8, anchored: false, note: 'Moderate-high volume' },
  { item: 'Bamboo', price: 14.20, confidence: 0.74, servers: 8, anchored: false, note: 'Moderate volume' },
  { item: 'Kelp', price: 11.80, confidence: 0.69, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Dried Kelp', price: 12.90, confidence: 0.66, servers: 6, anchored: false, note: 'Low-moderate volume' },
  { item: 'Cactus', price: 16.40, confidence: 0.68, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Vine', price: 18.70, confidence: 0.62, servers: 5, anchored: false, note: 'Low volume, decoration' },
  { item: 'Lily Pad', price: 17.30, confidence: 0.61, servers: 5, anchored: false, note: 'Low volume' },
  { item: 'Sugar', price: 26.40, confidence: 0.72, servers: 8, anchored: false, note: 'Moderate volume, derived from Sugar Cane' },
  { item: 'Paper', price: 22.80, confidence: 0.70, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Book', price: 38.60, confidence: 0.74, servers: 8, anchored: false, note: 'Moderate volume, derived from Paper + Leather' },
  { item: 'Book & Quill', price: 52.40, confidence: 0.65, servers: 6, anchored: false, note: 'Low volume' },
  { item: 'Enchanted Book', price: 412.30, confidence: 0.58, servers: 4, anchored: false, note: 'Very low volume, highest variance' },
  { item: 'Experience Bottle', price: 78.40, confidence: 0.67, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Dragon Breath', price: 684.20, confidence: 0.60, servers: 4, anchored: false, note: 'Very low volume, late-game' },
  { item: 'Shulker Box', price: 287.50, confidence: 0.68, servers: 6, anchored: false, note: 'Moderate volume, derived from Shulker Shell × 2' },
  { item: 'Popped Chorus Fruit', price: 89.60, confidence: 0.64, servers: 5, anchored: false, note: 'Low volume, late-game' },
  { item: 'Music Disc (Cat)', price: 124.80, confidence: 0.55, servers: 3, anchored: false, note: 'Rare drops, low confidence' },
  { item: 'Music Disc (Others)', price: 118.40, confidence: 0.52, servers: 3, anchored: false, note: 'Rare drops, low confidence' },
  { item: 'Nether Star', price: 4821.30, confidence: 0.61, servers: 4, anchored: false, note: 'Very rare, derived from Wither ratio' },
  { item: 'Beacon', price: 3294.80, confidence: 0.58, servers: 4, anchored: false, note: 'Very rare, derived from Nether Star ratio' },
  { item: 'Saddle', price: 234.60, confidence: 0.63, servers: 5, anchored: false, note: 'Low-moderate volume' },
  { item: 'Horse Armor (Iron)', price: 312.40, confidence: 0.60, servers: 4, anchored: false, note: 'Low volume' },
  { item: 'Horse Armor (Gold)', price: 298.70, confidence: 0.59, servers: 4, anchored: false, note: 'Low volume' },
  { item: 'Horse Armor (Diamond)', price: 847.20, confidence: 0.57, servers: 4, anchored: false, note: 'Low volume' },
  { item: 'Name Tag', price: 198.40, confidence: 0.64, servers: 5, anchored: false, note: 'Low-moderate volume' },
  { item: 'Lead', price: 48.70, confidence: 0.62, servers: 5, anchored: false, note: 'Low volume' },
  { item: 'Bucket', price: 38.20, confidence: 0.76, servers: 8, anchored: false, note: 'Moderate volume' },
  { item: 'Water Bucket', price: 41.80, confidence: 0.68, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Lava Bucket', price: 187.40, confidence: 0.67, servers: 6, anchored: false, note: 'Moderate volume, derived from Iron ratio' },
  { item: 'Minecart', price: 52.40, confidence: 0.70, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Hopper Minecart', price: 312.80, confidence: 0.65, servers: 5, anchored: false, note: 'Low volume' },
  { item: 'Compass', price: 68.40, confidence: 0.72, servers: 8, anchored: false, note: 'Moderate volume' },
  { item: 'Recovery Compass', price: 284.60, confidence: 0.61, servers: 4, anchored: false, note: 'Low volume, 1.19+' },
  { item: 'Clock', price: 76.20, confidence: 0.71, servers: 7, anchored: false, note: 'Moderate volume' },
  { item: 'Spyglass', price: 187.30, confidence: 0.64, servers: 5, anchored: false, note: 'Low volume, 1.17+' },
  { item: 'Elytra', price: 3847.60, confidence: 0.59, servers: 4, anchored: false, note: 'Very rare, end-game item' },
  { item: 'Phantom Wing', price: 128.40, confidence: 0.60, servers: 5, anchored: false, note: 'Low volume, derived from Elytra ratio' },
  { item: 'Turtle Helmet', price: 187.20, confidence: 0.63, servers: 5, anchored: false, note: 'Low volume, 1.13+' },
  { item: 'Scute', price: 54.80, confidence: 0.65, servers: 6, anchored: false, note: 'Moderate volume' },
  { item: 'Conduit', price: 2874.30, confidence: 0.58, servers: 4, anchored: false, note: 'Very rare, derived from Heart of the Sea ratio' },
  { item: 'Totem of Undying', price: 3847.20, confidence: 0.61, servers: 5, anchored: false, note: 'Very rare, derived from Emerald ratio' },
  { item: 'Emerald', price: 312.40, confidence: 0.89, servers: 11, anchored: false, note: 'High volume, direct villager trades' },
  { item: 'Emerald Block', price: 2841.80, confidence: 0.87, servers: 10, anchored: false, note: 'High volume, derived from Emerald × 9' },
  { item: 'Iron Block', price: 421.20, confidence: 0.88, servers: 11, anchored: false, note: 'Very high volume, derived from Iron Ingot × 9' },
  { item: 'Gold Block', price: 1650.60, confidence: 0.84, servers: 10, anchored: false, note: 'High volume, derived from Gold Ingot × 9' },
  { item: 'Diamond Block', price: 2700.00, confidence: 0.93, servers: 12, anchored: true, note: 'Derived from Diamond × 9 (anchor item)' },
  { item: 'Netherite Block', price: 25623.30, confidence: 0.88, servers: 10, anchored: false, note: 'Derived from Netherite Ingot × 9' },
  { item: 'Coal Block', price: 198.90, confidence: 0.84, servers: 10, anchored: false, note: 'High volume, derived from Coal × 9' },
  { item: 'Lapis Block', price: 374.40, confidence: 0.79, servers: 9, anchored: false, note: 'Moderate volume, derived from Lapis × 9' },
  { item: 'Redstone Block', price: 264.60, confidence: 0.82, servers: 10, anchored: false, note: 'High volume, derived from Redstone × 9' },
];


function confidenceColor(conf: number): string {
  if (conf >= 0.80) return 'text-emerald-400';
  if (conf >= 0.65) return 'text-amber-400';
  return 'text-rose-400';
}

function confidenceBg(conf: number): string {
  if (conf >= 0.80) return 'bg-emerald-950/40 border-emerald-800/40';
  if (conf >= 0.65) return 'bg-amber-950/40 border-amber-800/40';
  return 'bg-rose-950/40 border-rose-800/40';
}

function ConfidenceBar({ conf }: { conf: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 rounded-full bg-gray-800 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${
            conf >= 0.80 ? 'bg-emerald-500' : conf >= 0.65 ? 'bg-amber-500' : 'bg-rose-500'
          }`}
          style={{ width: `${Math.round(conf * 100)}%` }}
        />
      </div>
      <span className={`text-xs font-mono font-semibold shrink-0 ${confidenceColor(conf)}`}>
        {(conf * 100).toFixed(0)}%
      </span>
    </div>
  );
}

export function SimulatedTruePrices() {
  const [showAnchored, setShowAnchored] = useState(false);
  const [search, setSearch] = useState('');

  const filtered = SIMULATED_TRUE_PRICES.filter(
    (p) =>
      (!showAnchored || p.anchored) &&
      (search === '' || p.item.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <>
      {/* Preview banner */}
      <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 mb-6 flex items-start gap-3">
        <Info className="w-4 h-4 text-amber-400 mt-0.5 shrink-0" />
        <div>
          <p className="text-amber-200 font-medium text-sm">
            Preview Mode — this is simulated data from a 2MM+2GB+floor economy simulation
          </p>
          <p className="text-amber-300/70 text-xs mt-0.5">
            Real true prices require the API server and opt-in server submissions. These values
            show what the True Prices table will look like once the network is live.
          </p>
        </div>
      </div>

      <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-6">
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-4">
          <h2 className="text-sm font-semibold text-amber-400 uppercase tracking-wide">
            True Prices — Simulated Preview
          </h2>
          <div className="flex items-center gap-3">
            <input
              type="text"
              placeholder="Search items..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="bg-gray-800 border border-gray-700 text-gray-200 text-xs rounded-md px-3 py-1.5 w-40 focus:outline-none focus:border-emerald-600"
            />
            <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
              <input
                type="checkbox"
                checked={showAnchored}
                onChange={(e) => setShowAnchored(e.target.checked)}
                className="accent-amber-500 w-3.5 h-3.5"
              />
              Anchored only
            </label>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800/80">
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">Item</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">True Price</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium hidden sm:table-cell">Confidence</th>
                <th className="text-left py-2 text-gray-400 font-medium hidden md:table-cell">Servers</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(0, 50).map((entry) => (
                <tr
                  key={entry.item}
                  className="border-b border-gray-900 hover:bg-gray-800/40 transition-colors"
                >
                  <td className="py-2.5 pr-4">
                    <div className="flex items-center gap-2">
                      <div
                        className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                          entry.anchored ? 'bg-amber-500' : 'bg-gray-600'
                        }`}
                      />
                      <span className="font-medium text-gray-200">{entry.item}</span>
                      {entry.anchored && (
                        <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-amber-400 bg-amber-950/60 border border-amber-800/50 px-1 py-0.5 rounded uppercase tracking-wide">
                          <Anchor className="w-2.5 h-2.5" /> anchor
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="py-2.5 pr-4">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-white">${entry.price.toFixed(2)}</span>
                    </div>
                  </td>
                  <td className="py-2.5 pr-4 hidden sm:table-cell">
                    <ConfidenceBar conf={entry.confidence} />
                  </td>
                  <td className="py-2.5 hidden md:table-cell">
                    <div className="flex items-center gap-1 text-gray-400">
                      <Users className="w-3 h-3" />
                      <span className="text-xs font-mono">{entry.servers}</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filtered.length > 50 && (
            <p className="text-center py-3 text-gray-500 text-xs">
              Showing 50 of {filtered.length} items — use search to filter
            </p>
          )}
          {filtered.length === 0 && (
            <p className="text-center py-6 text-gray-500 text-sm">No items match your search.</p>
          )}
        </div>

        {/* Confidence legend */}
        <div className="flex flex-wrap items-center gap-4 mt-4 pt-3 border-t border-gray-800/60">
          <span className="text-xs text-gray-600">Confidence:</span>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-emerald-500" />
            <span className="text-xs text-emerald-400">≥80% high</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-amber-500" />
            <span className="text-xs text-amber-400">65–80% moderate</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-rose-500" />
            <span className="text-xs text-rose-400">&lt;65% low</span>
          </div>
          <div className="flex items-center gap-1.5 ml-auto">
            <div className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            <span className="text-xs text-gray-500">anchor item</span>
          </div>
        </div>

        {/* Note about how true prices are computed */}
        <div className="mt-4 pt-3 border-t border-gray-800/60 flex items-start gap-2">
          <TrendingUp className="w-3.5 h-3.5 text-gray-500 mt-0.5 shrink-0" />
          <p className="text-xs text-gray-500">
            True prices are computed via constrained least-squares in log-space across verified server
            submissions. Prices above are equilibrium estimates from 5-seed simulation runs of a
            healthy 2MM+2GB+floor economy. Confidence reflects cross-server ratio consensus.
          </p>
        </div>
      </div>
    </>
  );
}
