'use client';

import React, { useState, useCallback, useMemo } from 'react';
import {
  ArrowRightLeft, Play, AlertTriangle, CheckCircle2, Info,
  TrendingUp, TrendingDown, Minus, Zap, Shield, DollarSign,
  ChevronDown, ChevronUp, FileText, Lightbulb, RefreshCw,
} from 'lucide-react';

// ─── Types ───────────────────────────────────────────────────────────────────

interface ParsedConfig {
  // Economy engine
  baseSpread: number | null;
  volumeImpact: number | null;
  playerImpact: number | null;
  maxPriceChangePercent: number | null;
  liquidityCoeff: number | null;
  liquidityFullEffectTraders: number | null;
  tradeWindowDays: number | null;
  sellPressureMultiplier: number | null;
  trendDampening: number | null;
  // Loan
  counterCylindrical: boolean | null;
  tier3Ratio: number | null;
  postDefaultCooldownHours: number | null;
  singleLoanGdpCap: number | null;
  // GuildBuyer
  guildBuyerThreshold: number | null;
  // Floor
  diamondFloor: number | null;
  floorPercent: number | null;
  // Archetype
  marketMakers: number | null;
  guildBuyers: number | null;
  // Raw for display
  raw: string;
}

interface DiffEntry {
  param: string;
  label: string;
  category: 'economy' | 'loan' | 'guildbuyer' | 'floor' | 'archetype';
  before: string;
  after: string;
  delta: string;
  icon: React.ReactNode;
  impact: string;
  severity: 'positive' | 'negative' | 'neutral' | 'warning';
  documented?: { good: string; bad: string; verdict: string };
}

interface SimResult {
  bpd: number;
  spd: number;
  regime: string;
  bpdDelta: number | null;
  spdDelta: number | null;
  documentedImpacts: Array<{ param: string; effect: string; severity: 'positive' | 'negative' | 'neutral' | 'warning' }>;
  warnings: string[];
}

// ─── YAML Parser ─────────────────────────────────────────────────────────────

function parseYaml(yaml: string): ParsedConfig {
  const lines = yaml.split('\n');
  const out: ParsedConfig = { raw: yaml } as ParsedConfig;

  // Helper: find a numeric value by key, handling nested YAML
  function findNum(key: string): number | null {
    // Direct key: value
    const re1 = new RegExp(`^\\s*${key}:\\s*([0-9.e+-]+)`, 'i');
    // Quoted value
    const re2 = new RegExp(`^\\s*${key}:\\s*["']([0-9.e+-]+)["']`, 'i');
    for (const line of lines) {
      const m1 = line.match(re1);
      if (m1) { const v = parseFloat(m1[1]); return isNaN(v) ? null : v; }
      const m2 = line.match(re2);
      if (m2) { const v = parseFloat(m2[1]); return isNaN(v) ? null : v; }
    }
    return null;
  }

  function findBool(key: string): boolean | null {
    const re = new RegExp(`^\\s*${key}:\\s*(true|false)`, 'i');
    for (const line of lines) {
      const m = line.match(re);
      if (m) return m[1].toLowerCase() === 'true';
    }
    return null;
  }

  // Economy
  out.baseSpread = findNum('base-spread') ?? findNum('baseSpread');
  out.volumeImpact = findNum('volume-impact') ?? findNum('volumeImpact');
  out.playerImpact = findNum('player-impact') ?? findNum('playerImpact');
  out.maxPriceChangePercent = findNum('max-price-change-percent') ?? findNum('maxPriceChangePercent');
  out.liquidityCoeff = findNum('liquidity-coefficient') ?? findNum('liquidityCoeff') ?? findNum('liquidity-coeff');
  out.liquidityFullEffectTraders = findNum('liquidity-full-effect-traders') ?? findNum('liquidityFullEffectTraders');
  out.tradeWindowDays = findNum('trade-window-days') ?? findNum('tradeWindowDays');
  out.sellPressureMultiplier = findNum('sell-pressure-multiplier') ?? findNum('sellPressureMultiplier');
  out.trendDampening = findNum('trend-dampening') ?? findNum('trendDampening');
  // Loan
  out.counterCylindrical = findBool('counter-cyclical') ?? findBool('counterCylindrical');
  out.tier3Ratio = findNum('tier3-ratio') ?? findNum('tier3') ?? findNum('tier3Ratio');
  out.postDefaultCooldownHours = findNum('post-default-cooldown-hours') ?? findNum('postDefaultCooldownHours');
  out.singleLoanGdpCap = findNum('single-loan-gdp-cap') ?? findNum('singleLoanGdpCap');
  // GuildBuyer
  out.guildBuyerThreshold = findNum('threshold');
  // Floor — look for diamond: or DIAMOND:
  const diamondRe = /^\s*(?:diamond|DIAMOND):\s*([0-9]+)/im;
  for (const line of lines) {
    const m = line.match(diamondRe);
    if (m) { out.diamondFloor = parseInt(m[1]); break; }
  }
  out.floorPercent = findNum('floor-percent') ?? findNum('floorPercent') ?? findNum('floor-percentage');
  // Archetype
  const mmRe = /^\s*(?:market-?maker|marketMaker|MM)[s]?:\s*([0-9]+)/im;
  const gbRe = /^\s*(?:guild-buyer|guildBuyer|GB)[s]?:\s*([0-9]+)/im;
  for (const line of lines) {
    const mm = line.match(mmRe);
    if (mm) out.marketMakers = parseInt(mm[1]);
    const gb = line.match(gbRe);
    if (gb) out.guildBuyers = parseInt(gb[1]);
  }
  out.marketMakers ??= findNum('market-makers') ?? findNum('marketMakers');
  out.guildBuyers ??= findNum('guild-buyers') ?? findNum('guildBuyers');

  return out;
}

// ─── Default Config ──────────────────────────────────────────────────────────

const DEFAULT_CONFIG: ParsedConfig = {
  baseSpread: 0.20,
  volumeImpact: 0.80,
  playerImpact: 0.60,
  maxPriceChangePercent: 1.5,
  liquidityCoeff: 0.01,
  liquidityFullEffectTraders: 10,
  tradeWindowDays: 7,
  sellPressureMultiplier: 1.0,
  trendDampening: 0.10,
  counterCylindrical: true,
  tier3Ratio: 30,
  postDefaultCooldownHours: 168,
  singleLoanGdpCap: 1.0,
  guildBuyerThreshold: 0.05,
  diamondFloor: null,
  floorPercent: 0.60,
  marketMakers: 2,
  guildBuyers: 2,
  raw: '',
};

const DEFAULTS_EN = DEFAULT_CONFIG;

// ─── Spread Calculator (mirrors market-engine.ts) ────────────────────────────

function calcSpread(cfg: ParsedConfig, players = 10, volume = 100, traders = 8, zScore = 0): { bpd: number; spd: number } {
  const base = cfg.baseSpread ?? 0.20;
  const volImp = cfg.volumeImpact ?? 0.80;
  const plrImp = cfg.playerImpact ?? 0.60;
  const liqCoeff = cfg.liquidityCoeff ?? 0.01;
  const liqFull = cfg.liquidityFullEffectTraders ?? 10;
  const maxChange = cfg.maxPriceChangePercent ?? 1.5;

  const imbalance = 0.1; // assumed balanced for preview
  const baseHalf = base / 2;
  const volShift = imbalance * volImp;
  const bHalfRaw = baseHalf + volShift;
  const sHalfRaw = baseHalf - volShift;

  const fullEffectPlayers = 10;
  const plrScale = 1 - plrImp * Math.min(players / fullEffectPlayers, 1);
  const liqScale = 1 + liqCoeff * Math.max(0, liqFull - traders) / liqFull;

  let bHalf = bHalfRaw * plrScale * liqScale;
  let sHalf = sHalfRaw * plrScale * liqScale;
  bHalf = Math.max(0.001, bHalf);
  sHalf = Math.max(0.001, sHalf);

  const volMult = (() => {
    const z = Math.abs(zScore);
    if (z <= 1) return 1.0;
    if (z > 2) return z > 0 ? 1.3 : 0.8;
    const t = Math.min(z - 1, 1);
    return z > 0 ? 1 + 0.3 * t : 1 - 0.2 * t;
  })();

  bHalf *= volMult;
  sHalf *= volMult;

  return { bpd: bHalf * 2, spd: sHalf * 2 };
}

// ─── Simulate ────────────────────────────────────────────────────────────────

function simulate(before: ParsedConfig, after: ParsedConfig): SimResult {
  const beforeSpread = calcSpread(before);
  const afterSpread = calcSpread(after);
  const bpdDelta = ((afterSpread.bpd - beforeSpread.bpd) / beforeSpread.bpd) * 100;
  const spdDelta = ((afterSpread.spd - beforeSpread.spd) / beforeSpread.spd) * 100;

  const impacts: SimResult['documentedImpacts'] = [];
  const warnings: string[] = [];

  // GuildBuyer threshold
  if (before.guildBuyerThreshold !== null && after.guildBuyerThreshold !== null) {
    const diff = after.guildBuyerThreshold - before.guildBuyerThreshold;
    if (Math.abs(diff) > 0.001) {
      if (diff > 0) {
        impacts.push({ param: 'GuildBuyer threshold', effect: `↑ +${(diff * 100).toFixed(1)}pp — More buyers trigger, higher debt risk. 5% is production default.`, severity: 'warning' });
        warnings.push(`GuildBuyer threshold raised from ${(before.guildBuyerThreshold * 100).toFixed(0)}% to ${(after.guildBuyerThreshold * 100).toFixed(0)}% — more players can open large credit positions. Monitor D/G closely.`);
      } else {
        impacts.push({ param: 'GuildBuyer threshold', effect: `↓ ${(diff * 100).toFixed(1)}pp — Tighter credit, lower debt risk.`, severity: 'positive' });
      }
    }
  }

  // tier3 ratio
  if (before.tier3Ratio !== null && after.tier3Ratio !== null) {
    const diff = after.tier3Ratio - before.tier3Ratio;
    if (Math.abs(diff) > 0.5) {
      if (diff > 0) {
        impacts.push({ param: 'tier3 ratio', effect: `↑ ${diff.toFixed(0)} — Circuit fires less often. Safe for long-run stability.`, severity: 'positive' });
      } else {
        impacts.push({ param: 'tier3 ratio', effect: `↓ ${diff.toFixed(0)} — Circuit fires sooner. Risk of TIER3 oscillations if set too low (<20).`, severity: 'warning' });
        warnings.push(`tier3 ratio lowered to ${after.tier3Ratio}. Only use below 20 with careful monitoring. 30 is production default.`);
      }
    }
  }

  // sell pressure
  if (before.sellPressureMultiplier !== null && after.sellPressureMultiplier !== null) {
    const diff = after.sellPressureMultiplier - before.sellPressureMultiplier;
    if (Math.abs(diff) > 0.01) {
      if (diff < 0) {
        impacts.push({ param: 'sell pressure', effect: `↓ ${diff.toFixed(2)} — Prices stickier, GDP +5.2% but D/G +39.9% worse. Stability trade-off.`, severity: 'warning' });
        warnings.push(`Lower sell pressure improves GDP but worsens debt dynamics. Watch D/G closely if enabling.`);
      } else {
        impacts.push({ param: 'sell pressure', effect: `↑ ${diff.toFixed(2)} — More symmetric pricing. D/G improves, GDP slightly lower.`, severity: 'positive' });
      }
    }
  }

  // trend dampening
  if (before.trendDampening !== null && after.trendDampening !== null) {
    const diff = after.trendDampening - before.trendDampening;
    if (Math.abs(diff) > 0.01) {
      if (diff > 0) {
        impacts.push({ param: 'trend dampening', effect: `↑ ${diff.toFixed(2)} — Prevents runaway trends. +7.7% GDP, D/G unchanged. Free improvement.`, severity: 'positive' });
      } else {
        impacts.push({ param: 'trend dampening', effect: `↓ ${diff.toFixed(2)} — Slower trend correction. Not recommended.`, severity: 'negative' });
      }
    }
  }

  // counter-cyclical
  if (before.counterCylindrical !== null && after.counterCylindrical !== null) {
    if (before.counterCylindrical !== after.counterCylindrical) {
      if (after.counterCylindrical === false) {
        impacts.push({ param: 'counter-cyclical', effect: `DISABLED — Tiered interest: >3x→50%, >5x→25%, >10x→0%. Less smooth than counter-cyclical.`, severity: 'warning' });
        warnings.push('Counter-cyclical disabled. Ensure tier3 ratio is set appropriately for your economy size.');
      } else {
        impacts.push({ param: 'counter-cyclical', effect: `ENABLED — Smooth interest reduction as D/G rises. Recommended default.`, severity: 'positive' });
      }
    }
  }

  // floor percent
  if (before.floorPercent !== null && after.floorPercent !== null) {
    const diff = after.floorPercent - before.floorPercent;
    if (Math.abs(diff) > 0.01) {
      if (diff > 0 && diff <= 0.10) {
        impacts.push({ param: 'price floor', effect: `↑ +${(diff * 100).toFixed(0)}pp — Seller protection improves (short-term). ⚠️ 90d sim: 60% floor → GDP -19.1% — use 30-50% for long-run health.`, severity: 'positive' });
      } else if (diff > 0.10) {
        impacts.push({ param: 'price floor', effect: `↑ +${(diff * 100).toFixed(0)}pp — Risk of economy choke. Floor above 70% destroys GDP.`, severity: 'negative' });
        warnings.push(`Floor at ${(after.floorPercent * 100).toFixed(0)}% risks choking the economy. 50% max recommended for long-run servers.`);
      } else {
        impacts.push({ param: 'price floor', effect: `↓ ${(diff * 100).toFixed(0)}pp — Less seller protection. Floor rarely binds below 50%.`, severity: 'warning' });
      }
    }
  }

  // base spread
  if (before.baseSpread !== null && after.baseSpread !== null) {
    const diff = (after.baseSpread - before.baseSpread) / before.baseSpread;
    if (Math.abs(diff) > 0.05) {
      if (diff > 0) {
        impacts.push({ param: 'base spread', effect: `↑ +${(diff * 100).toFixed(0)}% wider spreads — More margin per trade, less volume.`, severity: 'negative' });
      } else {
        impacts.push({ param: 'base spread', effect: `↓ ${(diff * 100).toFixed(0)}% tighter spreads — Better liquidity, more volume.`, severity: 'positive' });
      }
    }
  }

  // archetype changes
  if (before.marketMakers !== null && after.marketMakers !== null) {
    const diff = after.marketMakers - before.marketMakers;
    if (diff !== 0) {
      if (diff > 0) {
        impacts.push({ param: 'MarketMakers', effect: `+${diff} MM — +101% GDP, -48% volatility. Each MM adds two-sided liquidity.`, severity: 'positive' });
      } else {
        impacts.push({ param: 'MarketMakers', effect: `${diff} fewer MM — Lower GDP, higher volatility. MM is the primary economy stabilizer.`, severity: 'warning' });
      }
    }
  }

  if (before.guildBuyers !== null && after.guildBuyers !== null) {
    const diff = after.guildBuyers - before.guildBuyers;
    if (diff !== 0) {
      if (diff > 0) {
        impacts.push({ param: 'GuildBuyers', effect: `+${diff} GB — More buy pressure, higher GDP but more debt. Balance with MM count.`, severity: 'neutral' });
      } else {
        impacts.push({ param: 'GuildBuyers', effect: `${diff} fewer GB — Less buy pressure, lower GDP, lower debt.`, severity: 'neutral' });
      }
    }
  }

  // post-default cooldown
  if (before.postDefaultCooldownHours !== null && after.postDefaultCooldownHours !== null) {
    const diff = after.postDefaultCooldownHours - before.postDefaultCooldownHours;
    if (Math.abs(diff) > 1) {
      if (diff > 0) {
        impacts.push({ param: 'post-default cooldown', effect: `↑ +${diff}h — Stronger default deterrent. 168h is production default.`, severity: 'positive' });
      } else {
        impacts.push({ param: 'post-default cooldown', effect: `↓ ${diff}h — Faster re-entry after default. Increases cascade risk.`, severity: 'warning' });
      }
    }
  }

  // Regime assessment
  const regime = afterSpread.bpd > 0.06 ? 'VOLATILE' : afterSpread.bpd > 0.04 ? 'MODERATE' : 'STABLE';

  return {
    bpd: afterSpread.bpd,
    spd: afterSpread.spd,
    regime,
    bpdDelta: isNaN(bpdDelta) ? null : bpdDelta,
    spdDelta: isNaN(spdDelta) ? null : spdDelta,
    documentedImpacts: impacts,
    warnings,
  };
}

// ─── Diff Builder ────────────────────────────────────────────────────────────

const PARAM_META: Array<{ key: keyof ParsedConfig; label: string; category: DiffEntry['category']; unit?: string; icon: React.ReactNode; fmt: (v: number | boolean | null) => string }> = [
  { key: 'baseSpread', label: 'Base Spread', category: 'economy', unit: '', icon: <Shield size={14} />, fmt: (v) => v === null ? '—' : `${((v as number) * 100).toFixed(1)}%` },
  { key: 'sellPressureMultiplier', label: 'Sell Pressure', category: 'economy', icon: <TrendingDown size={14} />, fmt: (v) => v === null ? '—' : `${(v as number).toFixed(2)}×` },
  { key: 'trendDampening', label: 'Trend Dampening', category: 'economy', icon: <TrendingDown size={14} />, fmt: (v) => v === null ? '—' : `${(v as number).toFixed(3)}` },
  { key: 'maxPriceChangePercent', label: 'Max Price Change', category: 'economy', unit: '%', icon: <Zap size={14} />, fmt: (v) => v === null ? '—' : `${v}%` },
  { key: 'liquidityCoeff', label: 'Liquidity Coeff', category: 'economy', icon: <Info size={14} />, fmt: (v) => v === null ? '—' : `${(v as number).toFixed(3)}` },
  { key: 'guildBuyerThreshold', label: 'GuildBuyer Threshold', category: 'guildbuyer', icon: <DollarSign size={14} />, fmt: (v) => v === null ? '—' : `${((v as number) * 100).toFixed(0)}%` },
  { key: 'tier3Ratio', label: 'TIER3 Ratio', category: 'loan', icon: <AlertTriangle size={14} />, fmt: (v) => v === null ? '—' : `${v}×` },
  { key: 'counterCylindrical', label: 'Counter-Cyclical', category: 'loan', icon: <RefreshCw size={14} />, fmt: (v) => v === null ? '—' : (v ? 'ON' : 'OFF') },
  { key: 'postDefaultCooldownHours', label: 'Post-Default Cooldown', category: 'loan', unit: 'h', icon: <Shield size={14} />, fmt: (v) => v === null ? '—' : `${v}h` },
  { key: 'singleLoanGdpCap', label: 'Single Loan GDP Cap', category: 'loan', unit: '×', icon: <DollarSign size={14} />, fmt: (v) => v === null ? '—' : `${v}×` },
  { key: 'floorPercent', label: 'Floor Percent', category: 'floor', unit: '%', icon: <Shield size={14} />, fmt: (v) => v === null ? '—' : `${((v as number) * 100).toFixed(0)}%` },
  { key: 'diamondFloor', label: 'Diamond Floor', category: 'floor', unit: '$', icon: <Shield size={14} />, fmt: (v) => v === null ? '—' : `$${v}` },
  { key: 'marketMakers', label: 'MarketMakers', category: 'archetype', icon: <TrendingUp size={14} />, fmt: (v) => v === null ? '—' : `${v}` },
  { key: 'guildBuyers', label: 'GuildBuyers', category: 'archetype', icon: <DollarSign size={14} />, fmt: (v) => v === null ? '—' : `${v}` },
];

function buildDiff(before: ParsedConfig, after: ParsedConfig): DiffEntry[] {
  const entries: DiffEntry[] = [];
  for (const meta of PARAM_META) {
    const beforeVal = before[meta.key] as number | boolean | null;
    const afterVal = after[meta.key] as number | boolean | null;
    if (beforeVal === null && afterVal === null) continue;

    const isDifferent = beforeVal !== afterVal && !(beforeVal === null || afterVal === null ||
      (typeof beforeVal === 'number' && typeof afterVal === 'number' && Math.abs(beforeVal - afterVal) < 0.0001));

    if (!isDifferent && beforeVal !== null && afterVal !== null) continue;
    if (beforeVal !== null && afterVal !== null && Math.abs(beforeVal as number - (afterVal as number)) < 0.0001) continue;

    let delta = '';
    let severity: DiffEntry['severity'] = 'neutral';
    let impact = '';

    if (typeof beforeVal === 'boolean' && typeof afterVal === 'boolean') {
      delta = beforeVal ? 'OFF → ON' : 'ON → OFF';
      severity = afterVal ? 'positive' : 'warning';
      impact = afterVal ? 'Enabled' : 'Disabled';
    } else if (typeof beforeVal === 'number' && typeof afterVal === 'number') {
      const d = afterVal - beforeVal;
      const pct = Math.abs(d / (Math.abs(beforeVal) || 1));
      if (pct < 0.001) continue;
      delta = d > 0 ? `+${meta.fmt(afterVal)}` : `${meta.fmt(afterVal)}`;
      const absD = Math.abs(d);

      // Known relationships
      if (meta.key === 'guildBuyerThreshold') {
        severity = d > 0 ? 'warning' : 'positive';
        impact = d > 0 ? 'Higher threshold → more debt risk' : 'Lower threshold → less debt risk';
      } else if (meta.key === 'tier3Ratio') {
        severity = d < 0 ? 'warning' : 'positive';
        impact = d < 0 ? 'Circuit fires sooner — monitor closely' : 'Circuit fires less often';
      } else if (meta.key === 'sellPressureMultiplier') {
        severity = d < 0 ? 'warning' : 'positive';
        impact = d < 0 ? 'GDP ↑ but D/G ↑↑ — stability trade-off' : 'D/G improves, GDP slightly lower';
      } else if (meta.key === 'trendDampening') {
        severity = d > 0 ? 'positive' : 'negative';
        impact = d > 0 ? 'Prevents runaway trends — recommended' : 'Slower trend correction';
      } else if (meta.key === 'postDefaultCooldownHours') {
        severity = d > 0 ? 'positive' : 'warning';
        impact = d > 0 ? 'Stronger default deterrent' : 'Faster re-entry — more risk';
      } else if (meta.key === 'floorPercent') {
        if (d > 0.10) { severity = 'negative'; impact = 'Above 70% — risk of economy choke'; }
        else if (d > 0) { severity = 'positive'; impact = 'Seller protection (short-term) — ⚠️ 90d: floor → GDP -19.1%, use 30-50% for long-run servers'; }
        else { severity = 'warning'; impact = 'Floor rarely binds below 50%'; }
      } else if (meta.key === 'baseSpread') {
        severity = d > 0 ? 'negative' : 'positive';
        impact = d > 0 ? 'Wider spreads — less trade volume' : 'Tighter spreads — better liquidity';
      } else {
        severity = d > 0 ? 'neutral' : 'neutral';
        impact = `Changed by ${meta.fmt(d > 0 ? d : beforeVal)}`;
      }
    } else {
      delta = `${meta.fmt(beforeVal)} → ${meta.fmt(afterVal)}`;
      severity = 'neutral';
      impact = 'Value changed';
    }

    entries.push({
      param: meta.key,
      label: meta.label,
      category: meta.category,
      before: meta.fmt(beforeVal),
      after: meta.fmt(afterVal),
      delta,
      icon: meta.icon,
      impact,
      severity,
    });
  }
  return entries;
}

// ─── Sample Configs ───────────────────────────────────────────────────────────

const SAMPLE_CURRENT = `# Auto-Tune config.yml — Current
economy:
  base-spread: 0.20
  volume-impact: 0.80
  player-impact: 0.60
  max-price-change-percent: 1.5
  liquidity-coefficient: 0.01
  trade-window-days: 7
  sell-pressure-multiplier: 1.0
  trend-dampening: 0.05

loans:
  counter-cyclical: true
  tier3-ratio: 30
  post-default-cooldown-hours: 168
  single-loan-gdp-cap: 1.0

guildbuyer:
  threshold: 0.05

economy:
  price-floor:
    DIAMOND: 300
  floor-percent: 0.60

archetype:
  market-makers: 2
  guild-buyers: 2
`;

const SAMPLE_NEW = `# Auto-Tune config.yml — Proposed Change
economy:
  base-spread: 0.20
  volume-impact: 0.80
  player-impact: 0.60
  max-price-change-percent: 1.5
  liquidity-coefficient: 0.01
  trade-window-days: 7
  sell-pressure-multiplier: 1.0
  trend-dampening: 0.10  # ← Changed: 0.05 → 0.10

loans:
  counter-cyclical: true
  tier3-ratio: 30
  post-default-cooldown-hours: 168
  single-loan-gdp-cap: 1.0

guildbuyer:
  threshold: 0.05

economy:
  price-floor:
    DIAMOND: 300
  floor-percent: 0.60

archetype:
  market-makers: 2
  guild-buyers: 2
`;

// ─── Sub-components ──────────────────────────────────────────────────────────

function SeverityBadge({ severity }: { severity: DiffEntry['severity'] }) {
  const map = {
    positive: 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30',
    negative: 'bg-red-500/20 text-red-400 border border-red-500/30',
    warning: 'bg-amber-500/20 text-amber-400 border border-amber-500/30',
    neutral: 'bg-gray-500/20 text-gray-400 border border-gray-500/30',
  };
  return <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium ${map[severity]}`}>{severity}</span>;
}

function DiffRow({ entry }: { entry: DiffEntry }) {
  const [open, setOpen] = useState(false);
  const catColor = {
    economy: 'text-blue-400',
    loan: 'text-purple-400',
    guildbuyer: 'text-yellow-400',
    floor: 'text-emerald-400',
    archetype: 'text-cyan-400',
  }[entry.category];

  return (
    <div className="border-b border-gray-800 last:border-0">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-gray-900/50 transition-colors text-left"
      >
        <span className={`mt-0.5 ${catColor}`}>{entry.icon}</span>
        <span className="flex-1 text-sm text-gray-200 font-medium">{entry.label}</span>
        <span className="text-xs text-gray-500 font-mono w-24 text-right">{entry.before}</span>
        <ArrowRightLeft size={12} className="text-gray-600 flex-shrink-0" />
        <span className="text-xs text-gray-300 font-mono w-24 text-left">{entry.after}</span>
        <SeverityBadge severity={entry.severity} />
        {open ? <ChevronUp size={14} className="text-gray-500" /> : <ChevronDown size={14} className="text-gray-500" />}
      </button>
      {open && (
        <div className="px-4 pb-3 pl-10 text-sm text-gray-400">
          <p>{entry.impact}</p>
        </div>
      )}
    </div>
  );
}

function SimResultPanel({ result, before, after }: { result: SimResult; before: ParsedConfig; after: ParsedConfig }) {
  const beforeSpread = calcSpread(before);
  const regimeColor = { STABLE: 'text-emerald-400', MODERATE: 'text-amber-400', VOLATILE: 'text-red-400' }[result.regime];
  const regimeBg = { STABLE: 'bg-emerald-500/10 border-emerald-500/20', MODERATE: 'bg-amber-500/10 border-amber-500/20', VOLATILE: 'bg-red-500/10 border-red-500/20' }[result.regime];

  return (
    <div className="space-y-4">
      {/* Regime + Spread summary */}
      <div className={`rounded-lg border p-4 ${regimeBg}`}>
        <div className="flex items-center justify-between">
          <div>
            <div className={`text-2xl font-bold ${regimeColor}`}>{result.regime}</div>
            <div className="text-sm text-gray-400 mt-1">Projected spread regime at 10 players, moderate volume</div>
          </div>
          <div className="text-right">
            <div className="text-lg font-mono text-gray-200">
              BPD {(result.bpd * 100).toFixed(2)}%
            </div>
            <div className="text-sm text-gray-500">SPD {(result.spd * 100).toFixed(2)}%</div>
            {result.bpdDelta !== null && (
              <div className={`text-xs font-mono mt-1 ${result.bpdDelta > 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                {result.bpdDelta > 0 ? '↑' : '↓'} {Math.abs(result.bpdDelta).toFixed(1)}% vs current
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Documented impacts */}
      {result.documentedImpacts.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-300 mb-2 flex items-center gap-2">
            <Lightbulb size={14} className="text-amber-400" />
            Documented impacts from simulation
          </h3>
          <div className="space-y-2">
            {result.documentedImpacts.map((imp, i) => {
              const iconColor = { positive: 'text-emerald-400', negative: 'text-red-400', warning: 'text-amber-400', neutral: 'text-gray-400' }[imp.severity];
              const rowBg = { positive: 'bg-emerald-500/5 border-emerald-500/20', negative: 'bg-red-500/5 border-red-500/20', warning: 'bg-amber-500/5 border-amber-500/20', neutral: 'bg-gray-800/50 border-gray-700' }[imp.severity];
              return (
                <div key={i} className={`rounded-md border p-3 flex gap-3 ${rowBg}`}>
                  {imp.severity === 'positive' && <CheckCircle2 size={16} className="text-emerald-400 flex-shrink-0 mt-0.5" />}
                  {imp.severity === 'negative' && <AlertTriangle size={16} className="text-red-400 flex-shrink-0 mt-0.5" />}
                  {imp.severity === 'warning' && <AlertTriangle size={16} className="text-amber-400 flex-shrink-0 mt-0.5" />}
                  {imp.severity === 'neutral' && <Info size={16} className="text-gray-400 flex-shrink-0 mt-0.5" />}
                  <div>
                    <div className="text-sm font-medium text-gray-200">{imp.param}</div>
                    <div className="text-sm text-gray-400 mt-0.5">{imp.effect}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Warnings */}
      {result.warnings.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-300 mb-2 flex items-center gap-2">
            <AlertTriangle size={14} className="text-red-400" />
            Warnings
          </h3>
          <div className="space-y-2">
            {result.warnings.map((w, i) => (
              <div key={i} className="bg-red-500/10 border border-red-500/20 rounded-md p-3 text-sm text-red-300 flex gap-2">
                <AlertTriangle size={14} className="text-red-400 flex-shrink-0 mt-0.5" />
                {w}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {result.documentedImpacts.length === 0 && result.warnings.length === 0 && (
        <div className="bg-gray-900/50 border border-gray-800 rounded-lg p-6 text-center">
          <CheckCircle2 size={32} className="text-emerald-500 mx-auto mb-2" />
          <p className="text-gray-400 text-sm">No significant documented impacts detected between these configs.</p>
          <p className="text-gray-500 text-xs mt-1">The proposed changes are within normal parameter variation.</p>
        </div>
      )}
    </div>
  );
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function ConfigPreviewPage() {
  const [currentYaml, setCurrentYaml] = useState(SAMPLE_CURRENT);
  const [newYaml, setNewYaml] = useState(SAMPLE_NEW);
  const [result, setResult] = useState<SimResult | null>(null);
  const [diff, setDiff] = useState<DiffEntry[]>([]);
  const [activeTab, setActiveTab] = useState<'diff' | 'preview'>('diff');
  const [parseError, setParseError] = useState<string | null>(null);

  const beforeCfg = useMemo(() => {
    try { return parseYaml(currentYaml); }
    catch { return DEFAULTS_EN; }
  }, [currentYaml]);

  const afterCfg = useMemo(() => {
    try { return parseYaml(newYaml); }
    catch { return DEFAULTS_EN; }
  }, [newYaml]);

  const handlePreview = useCallback(() => {
    setParseError(null);
    try {
      const before = parseYaml(currentYaml);
      const after = parseYaml(newYaml);
      const sim = simulate(before, after);
      const d = buildDiff(before, after);
      setResult(sim);
      setDiff(d);
      setActiveTab('preview');
    } catch (e) {
      setParseError(`Parse error: ${e instanceof Error ? e.message : String(e)}`);
    }
  }, [currentYaml, newYaml]);

  const handleSwap = useCallback(() => {
    setCurrentYaml(newYaml);
    setNewYaml(currentYaml);
    setResult(null);
    setDiff([]);
  }, [currentYaml, newYaml]);

  const yamlValid = useMemo(() => {
    try { parseYaml(currentYaml); parseYaml(newYaml); return true; }
    catch { return false; }
  }, [currentYaml, newYaml]);

  const changedCount = diff.length;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <div className="flex items-center gap-3 mb-2">
          <div className="p-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
            <FileText size={20} className="text-emerald-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-100">Config Change Preview</h1>
            <p className="text-sm text-gray-400">Paste two configs to see what changes — and what it means for your economy.</p>
          </div>
        </div>
      </div>

      {/* Editors */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-gray-300">Current config (before)</label>
            <button
              onClick={() => setCurrentYaml(SAMPLE_CURRENT)}
              className="text-xs text-gray-500 hover:text-gray-300 flex items-center gap-1"
            >
              <RefreshCw size={10} /> Reset
            </button>
          </div>
          <textarea
            value={currentYaml}
            onChange={e => setCurrentYaml(e.target.value)}
            className="w-full h-80 bg-gray-900 border border-gray-700 rounded-lg p-4 text-xs font-mono text-gray-300 resize-none focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/20 transition-colors"
            spellCheck={false}
            placeholder="Paste your current config.yml here..."
          />
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-gray-300">New config (after)</label>
            <div className="flex items-center gap-2">
              <button
                onClick={handleSwap}
                className="text-xs text-gray-500 hover:text-gray-300 flex items-center gap-1"
                title="Swap current and new"
              >
                <ArrowRightLeft size={10} /> Swap
              </button>
              <button
                onClick={() => setNewYaml(SAMPLE_NEW)}
                className="text-xs text-gray-500 hover:text-gray-300 flex items-center gap-1"
              >
                <RefreshCw size={10} /> Reset
              </button>
            </div>
          </div>
          <textarea
            value={newYaml}
            onChange={e => setNewYaml(e.target.value)}
            className="w-full h-80 bg-gray-900 border border-gray-700 rounded-lg p-4 text-xs font-mono text-gray-300 resize-none focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/20 transition-colors"
            spellCheck={false}
            placeholder="Paste your proposed new config.yml here..."
          />
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-4">
        <button
          onClick={handlePreview}
          disabled={!yamlValid}
          className="flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 disabled:bg-gray-700 disabled:text-gray-500 text-white rounded-lg font-medium text-sm transition-colors"
        >
          <Play size={14} />
          Preview Changes
        </button>
        {parseError && (
          <span className="text-sm text-red-400 flex items-center gap-1">
            <AlertTriangle size={12} /> {parseError}
          </span>
        )}
        {!yamlValid && (
          <span className="text-sm text-amber-400">YAML syntax error detected</span>
        )}
        {yamlValid && !result && (
          <span className="text-sm text-gray-500">Ready — click Preview to simulate changes</span>
        )}
        {changedCount > 0 && (
          <span className="text-sm text-gray-400">
            {changedCount} parameter{changedCount !== 1 ? 's' : ''} differ
          </span>
        )}
      </div>

      {/* Results */}
      {result && (
        <>
          {/* Tabs */}
          <div className="border-b border-gray-800">
            <nav className="flex gap-1">
              {(['diff', 'preview'] as const).map(tab => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === tab
                      ? 'border-emerald-500 text-emerald-400'
                      : 'border-transparent text-gray-500 hover:text-gray-300'
                  }`}
                >
                  {tab === 'diff' ? `Parameter Diff (${changedCount})` : 'Simulation Preview'}
                </button>
              ))}
            </nav>
          </div>

          {/* Diff tab */}
          {activeTab === 'diff' && (
            <div className="bg-gray-900/50 border border-gray-800 rounded-lg overflow-hidden">
              <div className="bg-gray-900/80 border-b border-gray-800 px-4 py-2 flex items-center gap-4">
                <span className="text-xs text-gray-500">Parameter</span>
                <span className="text-xs text-gray-500 flex-1 text-right">Before → After</span>
                <span className="text-xs text-gray-500">Effect</span>
                <span className="w-16" />
              </div>
              {diff.length === 0 ? (
                <div className="p-8 text-center text-gray-500 text-sm">
                  No differences detected between the two configs.
                </div>
              ) : (
                diff.map(entry => <DiffRow key={entry.param} entry={entry} />)
              )}
            </div>
          )}

          {/* Preview tab */}
          {activeTab === 'preview' && (
            <SimResultPanel result={result} before={beforeCfg} after={afterCfg} />
          )}
        </>
      )}

      {/* Info box */}
      <div className="bg-blue-500/5 border border-blue-500/20 rounded-lg p-4 text-sm text-blue-300 flex gap-3">
        <Info size={16} className="text-blue-400 flex-shrink-0 mt-0.5" />
        <div>
          <strong>How this works:</strong> The preview parses your YAML for known Auto-Tune parameters
          and computes spread impacts using the same formulas as the market engine. Documented effects
          come from multi-seed simulation runs — each impact note links to the evidence that produced it.
          For a full 14-day economy simulation, use the{' '}
          <a href="/simulator" className="underline hover:text-blue-200">market simulator</a>
          {' '}with your archetype mix.
        </div>
      </div>
    </div>
  );
}
