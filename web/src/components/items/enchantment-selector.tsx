'use client';

import { useState, useMemo } from 'react';
import { Sparkles } from 'lucide-react';
import { formatCurrency } from '@/lib/format';

// Enchantment multipliers by item family and enchantment name.
// Keys match Minecraft registry names (uppercase).
const ENCHANTMENT_TABLE: Record<string, Record<string, number[]>> = {
  SWORD: {
    SHARPNESS:     [1.25, 1.60, 2.00, 2.50, 3.00],
    FIRE_ASPECT:   [1.20, 1.50],
    LOOTING:       [1.30, 1.80, 2.50],
    KNOCKBACK:     [1.10, 1.25, 1.50],
    BANE_OF_ARTHROPODS: [1.15, 1.35, 1.60, 1.90],
    SMITE:         [1.15, 1.35, 1.60, 1.90],
    SWEEPING_EDGE: [1.20, 1.50, 2.00],
  },
  PICKAXE: {
    EFFICIENCY:    [1.30, 1.70, 2.20, 3.00, 4.00],
    FORTUNE:       [1.50, 2.00, 3.00],
    UNBREAKING:    [1.10, 1.25, 1.50],
  },
  SHOVEL: {
    EFFICIENCY:    [1.30, 1.70, 2.20, 3.00, 4.00],
    UNBREAKING:    [1.10, 1.25, 1.50],
  },
  AXE: {
    SHARPNESS:     [1.25, 1.60, 2.00, 2.50, 3.00],
    BANE_OF_ARTHROPODS: [1.15, 1.35, 1.60, 1.90],
    SMITE:         [1.15, 1.35, 1.60, 1.90],
    EFFICIENCY:    [1.30, 1.70, 2.20, 3.00, 4.00],
    UNBREAKING:    [1.10, 1.25, 1.50],
  },
  HOE: {
    UNBREAKING:    [1.10, 1.25, 1.50],
    EFFICIENCY:    [1.30, 1.70, 2.20, 3.00, 4.00],
  },
  BOW: {
    POWER:         [1.30, 1.70, 2.20, 2.80, 3.50],
    FLAME:         [1.15, 1.40],
    INFINITY:      [2.00],
  },
  CROSSBOW: {
    MULTISHOT:     [1.50],
    PIERCING:      [1.15, 1.40, 1.70],
    QUICK_CHARGE:  [1.15, 1.35, 1.60],
  },
  TRIDENT: {
    LOYALTY:       [1.10, 1.30, 1.60],
    CHANNELING:    [1.10],
    RIPTIDE:       [1.20, 1.60, 2.20],
    IMPALING:      [1.20, 1.50, 1.90, 2.40],
  },
  ARMOR: {
    PROTECTION:           [1.20, 1.50, 1.90, 2.40],
    FIRE_PROTECTION:      [1.20, 1.50, 1.90, 2.40],
    BLAST_PROTECTION:     [1.20, 1.50, 1.90, 2.40],
    PROJECTILE_PROTECTION:[1.20, 1.50, 1.90, 2.40],
    THORNS:               [1.20, 1.50, 2.00],
    UNBREAKING:           [1.10, 1.25, 1.50],
    RESPIRATION:          [1.10, 1.25, 1.50],
    AQUA_AFFINITY:        [1.10, 1.25, 1.50],
    DEPTH_STRIDER:        [1.15, 1.40, 1.70],
    FROST_WALKER:         [1.20, 1.50],
    SOUL_SPEED:           [1.20, 1.50, 2.00],
    MENDING:              [1.50],
  },
  ELYTRA: {
    UNBREAKING:    [1.10, 1.25, 1.50],
    MENDING:       [1.50],
  },
  FISHING_ROD: {
    LUCK_OF_THE_SEA: [1.20, 1.50, 2.00],
    LURE:           [1.20, 1.50, 2.00],
    UNBREAKING:     [1.10, 1.25, 1.50],
  },
  SHIELD: {
    UNBREAKING: [1.10, 1.25, 1.50],
  },
  SHEARS: {
    UNBREAKING: [1.10, 1.25, 1.50],
  },
  HEAD: {
    PROTECTION:    [1.20, 1.50, 1.90, 2.40],
    FIRE_PROTECTION: [1.20, 1.50, 1.90, 2.40],
    UNBREAKING:   [1.10, 1.25, 1.50],
    MENDING:      [1.50],
  },
  // Generic fallback for any item type
  OTHER: {
    UNBREAKING: [1.10, 1.25, 1.50],
    MENDING:   [1.50],
  },
};

// Map Minecraft material names to enchantment families
function getEnchantmentFamily(material: string): string[] {
  const m = material.toUpperCase();
  if (m.includes('SWORD')) return ['SWORD'];
  if (m.includes('PICKAXE')) return ['PICKAXE'];
  if (m.includes('SHOVEL')) return ['SHOVEL'];
  if (m.includes('AXE')) return ['AXE'];
  if (m.includes('HOE')) return ['HOE'];
  if (m.includes('BOW')) return ['BOW'];
  if (m.includes('CROSSBOW')) return ['CROSSBOW'];
  if (m.includes('TRIDENT')) return ['TRIDENT'];
  if (m.includes('HELMET') || m.includes('CHESTPLATE') || m.includes('LEGGINGS') || m.includes('BOOTS')) return ['ARMOR'];
  if (m.includes('ELYTRA')) return ['ELYTRA'];
  if (m.includes('FISHING_ROD')) return ['FISHING_ROD'];
  if (m.includes('SHIELD')) return ['SHIELD'];
  if (m.includes('SHEARS')) return ['SHEARS'];
  if (m.includes('SKULL') || m.includes('HEAD') || m.includes('PLAYER_HEAD') || m.includes('CREEPER_HEAD') || m.includes('ZOMBIE_HEAD') || m.includes('SKELETON_SKULL') || m.includes('DRAGON_HEAD')) return ['HEAD'];
  return ['OTHER'];
}

function formatEnchantLabel(key: string): string {
  return key.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

function toRoman(n: number): string {
  if (n <= 0) return String(n);
  if (n > 10) return String(n);
  const tens = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
  return tens[n] ?? String(n);
}

interface EnchantmentSelectorProps {
  material: string;
  baseSellPrice: number;
}

export function EnchantmentSelector({ material, baseSellPrice }: EnchantmentSelectorProps) {
  const families = getEnchantmentFamily(material);

  const availableEnchantments = useMemo(() => {
    const result: { key: string; label: string; maxLevel: number; multipliers: number[] }[] = [];
    for (const family of families) {
      const table = ENCHANTMENT_TABLE[family] ?? ENCHANTMENT_TABLE['OTHER'];
      for (const [key, multipliers] of Object.entries(table)) {
        if (!result.find(e => e.key === key)) {
          result.push({
            key,
            label: formatEnchantLabel(key),
            maxLevel: multipliers.length,
            multipliers,
          });
        }
      }
    }
    return result;
  }, [families]);

  const [selected, setSelected] = useState<Record<string, number>>({});

  const totalMultiplier = useMemo(() => {
    let mult = 1.0;
    for (const [key, level] of Object.entries(selected)) {
      if (level > 0) {
        const entry = availableEnchantments.find(e => e.key === key);
        if (entry && level <= entry.multipliers.length) {
          mult *= entry.multipliers[level - 1];
        }
      }
    }
    return mult;
  }, [selected, availableEnchantments]);

  const enchantedPrice = baseSellPrice * totalMultiplier;

  function toggleEnchant(key: string) {
    setSelected(prev => {
      const next = { ...prev };
      if (next[key]) {
        delete next[key];
      } else {
        const entry = availableEnchantments.find(e => e.key === key);
        next[key] = entry ? entry.maxLevel : 1;
      }
      return next;
    });
  }

  function setLevel(key: string, level: number) {
    setSelected(prev => ({ ...prev, [key]: level }));
  }

  if (availableEnchantments.length === 0 || (availableEnchantments.length === 1 && availableEnchantments[0].key === 'UNBREAKING')) {
    return null;
  }

  const hasEnchantments = Object.keys(selected).length > 0;

  return (
    <div className="rounded-lg border border-primary/20 bg-primary/5 p-4 space-y-3">
      {/* Header */}
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-primary" />
        <span className="text-sm font-semibold text-foreground">Enchantment Value</span>
        {hasEnchantments && (
          <span className="ml-auto text-xs text-muted-foreground">
            ×{totalMultiplier.toFixed(2)} multiplier
          </span>
        )}
      </div>

      {/* Enchantment pills */}
      <div className="flex flex-wrap gap-2">
        {availableEnchantments
          .filter(e => e.key !== 'UNBREAKING' || Object.keys(selected).includes('UNBREAKING'))
          .map(entry => (
            <div key={entry.key} className="relative">
              {selected[entry.key] ? (
                // Active: show level selector
                <div className="flex items-center gap-1 px-2 py-1 rounded-full bg-primary/20 border border-primary/40 text-xs">
                  <button
                    onClick={() => toggleEnchant(entry.key)}
                    className="text-muted-foreground hover:text-foreground transition-colors font-medium"
                    title="Remove"
                  >
                    ×
                  </button>
                  <span className="text-foreground font-medium">{entry.label}</span>
                  <div className="flex items-center gap-0.5 ml-1">
                    {Array.from({ length: entry.maxLevel }, (_, i) => i + 1).map(level => (
                      <button
                        key={level}
                        onClick={() => setLevel(entry.key, level)}
                        className={`w-5 h-5 rounded text-[10px] font-mono transition-colors ${
                          selected[entry.key] === level
                            ? 'bg-primary text-primary-foreground'
                            : 'bg-muted text-muted-foreground hover:bg-primary/30'
                        }`}
                      >
                        {toRoman(level)}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => toggleEnchant(entry.key)}
                  className="px-2.5 py-1 rounded-full border border-border bg-background text-xs text-muted-foreground hover:border-primary/50 hover:text-primary transition-all"
                >
                  + {entry.label}
                </button>
              )}
            </div>
          ))}
      </div>

      {/* Price display */}
      {hasEnchantments && (
        <div className="flex items-center gap-3 pt-1 border-t border-border/50">
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-muted-foreground">Base sell</span>
            <span className="text-xs font-mono text-muted-foreground line-through">{formatCurrency(baseSellPrice)}</span>
          </div>
          <span className="text-muted-foreground">→</span>
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-muted-foreground">With enchantments</span>
            <span className="text-sm font-bold font-mono text-amber-600 dark:text-amber-400">
              {formatCurrency(enchantedPrice)}
            </span>
          </div>
          <div className="ml-auto flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/30">
            <span className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">
              +{formatCurrency(enchantedPrice - baseSellPrice)}
            </span>
          </div>
        </div>
      )}

      {/* Helper text */}
      {!hasEnchantments && (
        <p className="text-xs text-muted-foreground">
          Select enchantments to see the adjusted sell price. Enchanted items are worth more to the market.
        </p>
      )}
    </div>
  );
}
