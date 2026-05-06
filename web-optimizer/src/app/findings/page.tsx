'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { ChevronDown, ArrowRight, FlaskConical, TrendingUp, TrendingDown, Minus, AlertTriangle, CheckCircle, XCircle, Info, Users, Sliders, BarChart2, Shield } from 'lucide-react';
import { cn } from '@/lib/utils';

/* ─────────────────────────────────────────────────────────────
   Findings content — organized by category
   Each finding: question, verdict badge, answer paragraphs,
   key metrics, related links
───────────────────────────────────────────────────────────── */

type Verdict = '✅ Production Default' | '⚠️ Use with Caution' | '❌ Never' | '🔄 Context-Dependent' | '💡 Key Insight' | '🔄 Contained' | '✅ Contained by circuit' | '✅ Protected' | '✅ Yes';

type Finding = {
  q: string;
  verdict: Verdict;
  verdictClass: string;
  answer: string[];
  metrics?: { label: string; value: string; note?: string }[];
  relatedLinks?: { href: string; label: string }[];
};

type Category = {
  id: string;
  label: string;
  icon: React.ElementType;
  findings: Finding[];
};

/* ─── EXPLOIT RESISTANCE ─────────────────────────────────── */

const EXPLOIT_RESISTANCE_FINDINGS: Finding[] = [
  {
    q: 'Can a single player manipulate prices to exploit the economy?',
    verdict: '🔄 Contained',
    verdictClass: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    answer: [
      'Single-player price manipulation is structurally discouraged but not impossible. '
        + 'The market engine applies player-scaled price influence (tanh curve at 99% effect with 10+ traders) '
        + '— one player cannot move prices alone unless the server has very few active participants.',
      'The more realistic exploit scenario is the "Whale" archetype: a player who accumulates '
        + 'massive inventory over 3–5 days, then dumps it at 50% of perceived value. This creates '
        + 'temporary sell-pressure cascades that the circuit breaker must absorb.',
      'Whale stress test (5 seeds × 14 days, whale = 3-day accumulation → 50% dump → 1.5-day dormant): '
        + 'D/G worsens +37% on average. But the circuit breaker activates in 3/5 seeds — containing '
        + 'the worst cases. In 2/5 seeds, D/G stays flat or improves. The circuit is a governor, '
        + 'not a cure — but it prevents total collapse.',
    ],
    metrics: [
      { label: 'Whale D/G impact', value: '+37%', note: 'average across 5 seeds' },
      { label: 'Circuit activates', value: '3/5 seeds', note: 'contains catastrophic cases' },
      { label: 'GDP inflation', value: '+167%', note: 'transaction volume amplification (not real GDP)' },
      { label: 'Recovery', value: 'dormant 1.5d', note: 'whale waits before next cycle' },
    ],
    relatedLinks: [
      { href: '/simulator', label: 'Run whale stress test' },
      { href: '/docs', label: 'Circuit breaker docs' },
    ],
  },
  {
    q: 'What happens if many players exploit the same item at once?',
    verdict: '✅ Contained by circuit',
    verdictClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    answer: [
      'Simultaneous multi-player exploitation is the design scenario for the loan circuit breaker. '
        + 'If an exploit becomes known (e.g., a dupe glitch that gives players free items), the economy '
        + 'could see massive sell pressure on one item. The circuit breaker responds: '
        + 'sell pressure → price cascades down → GuildBuyers buy aggressively on credit → debt spikes. '
        + 'When D/G crosses tier3_ratio (30×), TIER3 fires and interest pauses at 0%. '
        + 'The hysteresis lock keeps the circuit closed until D/G drops below 27× (90% of tier3). '
        + 'The economy oscillates in the 15–22× D/G band until the exploit is patched.',
      'The circuit cannot prevent price manipulation, but it prevents the debt spiral that would '
        + 'otherwise make the economy unusable for weeks. Admins should monitor /at admin stats and '
        + 'patch exploits quickly — the circuit is a governor, not an excuse to leave exploits unpatched.',
    ],
    metrics: [
      { label: 'TIER3 fires at', value: 'D/G > 30×', note: 'tier3_ratio = 30 default' },
      { label: 'Interest pause', value: '0%', note: 'when TIER3 active' },
      { label: 'Hysteresis unlock', value: 'D/G < 27×', note: '10% band prevents oscillation' },
      { label: 'D/G containment', value: '< 30×', note: 'circuit prevents unbounded escalation' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Admin monitoring guide' },
      { href: '/admin', label: 'Admin command reference' },
    ],
  },
  {
    q: 'Can players borrow more than the economy can handle?',
    verdict: '✅ Protected',
    verdictClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    answer: [
      'Yes — the loan system has multiple layers of protection: single loan GDP cap (max 1× GDP per loan), '
        + 'post-default cooldown (168h / 7 days prevents cascade re-borrowing after default), '
        + 'credit scoring (new players start at 500, max loan = credit_score × 0.001 × GDP), '
        + 'and counter-cyclical interest (at D/G = 30×, interest is 0% — circuit breaker pauses all interest).',
      'Post-default cooldown alone reduces Debt/GDP by 65% in cascade test scenarios — it is the single '
        + 'most impactful loan safety mechanism in the system.',
    ],
    metrics: [
      { label: 'Post-default cooldown', value: '168h', note: '7-day lockout after default' },
      { label: 'D/G reduction', value: '−65%', note: 'from cooldown alone in cascade test' },
      { label: 'Single loan cap', value: '1× GDP', note: 'maximum loan size' },
      { label: 'TIER3 interest', value: '0%', note: 'circuit fires at D/G > 30×' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Loan system docs' },
    ],
  },
  {
    q: 'Are there admin controls to freeze or override item prices?',
    verdict: '✅ Yes',
    verdictClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    answer: [
      'Yes — admins can freeze or override prices on a per-item basis:',
      '`/at admin item floor <item> <value>` — sets a minimum displayed price. The internal engine '
        + 'continues price discovery, but displayed buy/sell prices never fall below this floor.',
      '`/at admin item ceiling <item> <value>` — sets a maximum displayed price. Same pattern as floor.',
      '`/at admin item freeze <item>` — pauses price discovery entirely for one item. Spreads still '
        + 'compute normally so players can still trade. Useful for: new item discovery, testing, '
        + 'or items where price manipulation is a known issue.',
      '⚠️ Warning: frozen items develop spread blowout (3–4× wider spreads) because price discovery '
        + 'is paused. The spread compensates for incorrect pricing. Use freeze sparingly.',
    ],
    metrics: [
      { label: 'Per-item controls', value: 'floor / ceiling / freeze', note: 'all per-item, all instant' },
      { label: 'Spread blowout', value: '3–4× wider', note: 'when frozen (price cant correct)' },
    ],
    relatedLinks: [
      { href: '/admin', label: 'Admin command reference' },
    ],
  },
];

/* ─── ARCHETYPE DECISIONS ─────────────────────────────────── */

const ARCHETYPE_FINDINGS: Finding[] = [
  {
    q: 'Should I add MarketMakers alongside GuildBuyers?',
    verdict: '✅ Production Default',
    verdictClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    answer: [
      'Yes — 2 MarketMakers with 2 GuildBuyers is the recommended production configuration. '
        + 'MarketMakers post two-sided limit orders around perceived fair value (2–5% spread), while '
        + 'GuildBuyers provide asymmetric buy pressure when prices dip. Together they create a near-balanced economy.',
      'Without MarketMakers, prices naturally settle 50–70% below base. With 2MM+2GB, '
        + 'prices stay within 10–20% of base and the economy grows long-term.',
    ],
    metrics: [
      { label: 'GDP improvement', value: '+101%', note: '1MM+GB → 2MM+2GB+floor' },
      { label: 'Volatility reduction', value: '−48%', note: 'Coefficient of variation' },
      { label: 'Spread compression', value: '−22%', note: 'Buy price delta' },
    ],
    relatedLinks: [
      { href: '/simulator', label: 'Run the simulator' },
      { href: '/how-it-works', label: 'Engine mechanics' },
      { href: '/docs', label: 'Full admin guide' },
    ],
  },
  {
    q: 'Can Newbies replace GuildBuyers?',
    verdict: '❌ Never',
    verdictClass: 'text-red-400 bg-red-950/60 border-red-800/50',
    answer: [
      'GuildBuyers are non-negotiable. Replacing both GuildBuyers with 2 Newbies in a 2MM+2GB economy causes '
        + 'GDP to collapse by 68% and debt-to-GDP to explode 37×. Newbies provide passive buy pressure but they '
        + 'do not actively buy price dips — which is what makes GuildBuyers essential.',
      'GuildBuyers buy proactively when price falls below perceived value, creating a natural price floor. '
        + 'Newbies buy opportunistically but lack the structured dip-buying behavior that stabilizes prices.',
      'Newbies CAN supplement the economy — they reduce volatility by 68% in healthy economies and '
        + 'improve D/G by 42% in stressed economies. But they cannot replace GuildBuyers.',
    ],
    metrics: [
      { label: 'GDP without GBs', value: '−68%', note: 'replaced with 2 Newbies' },
      { label: 'D/G explosion', value: '37× worse', note: '5.0x → 41.7x' },
      { label: 'Volatility increase', value: '+25%', note: 'without GB dip-buying' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'GuildBuyer config docs' },
    ],
  },
  {
    q: 'Should I add InsiderTraders to my economy?',
    verdict: '🔄 Context-Dependent',
    verdictClass: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    answer: [
      'InsiderTraders buy when price falls below the rolling mean and sell when it rises above — a mean-reversion strategy. '
        + 'Their effect depends entirely on whether your economy is healthy or stressed.',
      'In healthy economies, ITs add +30% GDP but worsen D/G by +2.4×. The added buy pressure '
        + 'causes GuildBuyers to trigger more frequently, amplifying debt. Only add ITs if D/G is '
        + 'below 5× and you are monitoring it weekly.',
      'In stressed economies, ITs are counter-cyclical: they absorb sell pressure from Farmers '
        + 'and Hoarders during price dips, improving D/G by −1.3×. But the GDP cost is −12%. '
        + 'Do NOT add ITs to stressed economies unless D/G debt is your primary concern.',
    ],
    metrics: [
      { label: 'IT in healthy GDP', value: '+30.1%', note: 'but D/G +2.4×' },
      { label: 'IT in stressed D/G', value: '−1.3×', note: 'counter-cyclical benefit' },
      { label: 'IT in stressed GDP', value: '−12.2%', note: 'significant cost' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Archetype config docs' },
    ],
  },
  {
    q: 'Should I add VolumeTraders to stabilize spreads?',
    verdict: '❌ Never',
    verdictClass: 'text-red-400 bg-red-950/60 border-red-800/50',
    answer: [
      'VolumeTraders fire on spread widening and price dislocations. In ANY economy — healthy or stressed — '
        + 'VTs are net negative. They compress spreads but at the cost of lower GDP and higher volatility.',
      'In healthy economies: −9.2% GDP, +8.7% volatility worse. In stressed economies: −12.7% GDP. '
        + 'The mechanism: VT amplifies the dominant directional pressure. In healthy economies it '
        + 'accelerates GuildBuyer debt accumulation. In stressed economies it worsens sell cascades.',
      'Never add VolumeTraders to any configuration. Spread compression is better achieved by '
        + 'adding more MarketMakers or ensuring a healthy player count (10+ active traders).',
    ],
    metrics: [
      { label: 'VT in healthy GDP', value: '−9.2%', note: 'vs no VT baseline' },
      { label: 'VT in stressed GDP', value: '−12.7%', note: 'amplifies sell cascades' },
      { label: 'VT volatility', value: '+8.7% worse', note: 'opposite of intended effect' },
    ],
  },
  {
    q: 'What if I have AFK Farmers on my server?',
    verdict: '❌ Never',
    verdictClass: 'text-red-400 bg-red-950/60 border-red-800/50',
    answer: [
      'AFKFarmers are the most destructive archetype tested: −49.5% GDP and +65.9% volatility. '
        + 'They accumulate resources offline (high gather rate, low online presence) and dump them '
        + 'periodically at near-zero margin — creating sudden supply spikes that crash prices.',
      'The mechanism: offline accumulation → periodic dump at 0–3% margin → price spike crash → '
        + 'circuit breaker fires constantly. This is more destructive than IT+VT combined.',
      'If your server has AFK farmers, reduce the gather_rate in config or add a cooldown '
        + 'on large-volume sells. A sell volume cap per player per hour is the most effective mitigation.',
    ],
    metrics: [
      { label: 'AFK Farmer GDP', value: '−49.5%', note: 'most destructive tested' },
      { label: 'AFK Farmer volatility', value: '+65.9%', note: 'vs baseline' },
      { label: 'AFK Farmer D/G', value: '+21.4×', note: 'debt amplification' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Archetype avoidance guide' },
    ],
  },
  {
    q: 'Should I add Hoarders to my economy?',
    verdict: '⚠️ Use with Caution',
    verdictClass: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    answer: [
      'Hoarders accumulate inventory and rarely sell, providing a passive price floor. '
        + 'Replacing 2 Farmers with 2 Hoarders: GDP +1.5% (essentially flat) and D/G −6.9% '
        + '(slight improvement). The effect is marginal.',
      'Hoarders reduce supply → prices stay slightly higher → GuildBuyers trigger less → less debt. '
        + 'But the effect is too small to be a primary strategy. They are neutral enough to not '
        + 'be worth worrying about — but not beneficial enough to engineer for.',
    ],
    metrics: [
      { label: 'Hoarder GDP', value: '+1.5%', note: 'essentially flat' },
      { label: 'Hoarder D/G', value: '−6.9%', note: 'slight improvement' },
    ],
  },
];

/* ─── CONFIG DECISIONS ─────────────────────────────────────── */

const CONFIG_FINDINGS: Finding[] = [
  {
    q: 'What is the optimal GuildBuyer threshold?',
    verdict: '✅ Production Default',
    verdictClass: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    answer: [
      '5% is the recommended threshold — updated 2026-04-20 based on 30-day simulation data. '
        + 'A 5-seed × 5-threshold test (25 runs at 14d) shows 7% wins on GDP (+7%) with lowest volatility. '
        + 'But at 30 days: 7% GDP advantage DISAPPEARS (+0.7% vs 5%) while D/G is +2.89× WORSE (17.8× vs 14.9×). '
        + 'The 7% GDP advantage is a 14d artifact — it does not persist.',
      'For servers under 14 days, 7% remains defensible for the GDP boost. For servers running 30d+, '
        + '5% is strictly better on D/G with essentially no GDP cost. The old default of 15–30% is '
        + 'catastrophically bad. Monitor D/G monthly — if it climbs above 15×, lower to 5%.',
    ],
    metrics: [
      { label: '7% GDP (14d)', value: '1,261K', note: 'best across 5 seeds' },
      { label: '7% vol (14d)', value: '0.110', note: 'lowest of all thresholds' },
      { label: '7% vs 5% D/G (30d)', value: '17.8× vs 14.9×', note: '7% is +2.89× WORSE at 30d' },
      { label: '7% vs 5% GDP (30d)', value: '+0.7%', note: '7% advantage GONE at 30d' },
      { label: 'Production default', value: '5%', note: 'updated from 7% — use 7% only for <14d servers' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'GuildBuyer config docs' },
    ],
  },
  {
    q: 'What does the Diamond floor actually do?',
    verdict: '🔄 Context-Dependent',
    verdictClass: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    answer: [
      'The Diamond floor (default 60%, ~$300) keeps displayed prices from falling below it. '
        + 'This protects sellers from catastrophic price collapses — but it has a paradoxical '
        + 'effect on internal prices.',
      'When the floor binds, players gather Diamond MORE because the sell price is artificially '
        + 'protected. This increases supply → internal prices drop below what they would have '
        + 'been without the floor. Diamond internal price averages $285 (floor) while natural '
        + 'equilibrium is $241. The floor paradox: displayed prices are protected but internal '
        + 'prices are lower than they should be.',
      '90-day data (5 seeds): floor makes the economy significantly WORSE. 60% floor → GDP -19.1% '
        + 'and D/G +1.6x higher vs no floor. Floor protects displayed prices but suppresses natural '
        + 'correction, causing inventory glut and GDP contraction over time. Short-run (14d) shows '
        + 'benefit (+29% GDP), but this reverses after ~60 days. Seller protection benefit is real '
        + 'but comes at a heavy long-run cost. Consider 30-50% for light protection or disabling for '
        + 'servers that run >60 days. Above 70% destroys the economy regardless of horizon.',
    ],
    metrics: [
      { label: '60% floor 14d GDP', value: '+29%', note: 'short-run benefit (14d)' },
      { label: '60% floor 90d GDP', value: '-19.1%', note: 'long-run reversal (90d)' },
      { label: '60% floor D/G', value: '+1.6x worse', note: '90d, vs no floor' },
      { label: 'Floor danger zone', value: '>70%', note: 'destroys economy immediately' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Floor config docs' },
    ],
  },
  {
    q: 'Should I enable market events in my economy?',
    verdict: '❌ Never',
    verdictClass: 'text-red-400 bg-red-950/60 border-red-800/50',
    answer: [
      'Do NOT enable frequent or strong events in a healthy 2MM+2GB+floor economy. '
        + 'A 10-day event burst (DEMAND_SURGE 2× on Diamond, SUPPLY_GLUT 2× on Iron, '
        + 'INFLATION_BOOST 1.5× across all items, GOLD_RUSH 1.8×) produced: GDP −2.0% '
        + 'and D/G +0.63× WORSE. One seed (98765) went catastrophic.',
      'Events introduce instability without compensating GDP benefits. In a healthy economy '
        + 'prices are already discovering value efficiently — events distort that process.',
      'Events CAN help stagnant economies (low trade volume, no price movement) as a '
        + 'stimulus mechanism. Keep them rare (once per season) and weak (multiplier ≤ 1.2×).',
    ],
    metrics: [
      { label: 'Events GDP', value: '−2.0%', note: 'in healthy economy' },
      { label: 'Events D/G', value: '+0.63× worse', note: 'even in healthy' },
      { label: 'Seed 98765 D/G', value: '7.46× vs 4.87×', note: 'catastrophic on one seed' },
    ],
  },
  {
    q: 'Is the 2MM+2GB+floor config stable at 60+ days?',
    verdict: '⚠️ Use with Caution',
    verdictClass: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    answer: [
      'The 30-day test was misleading — the economy appeared to deleverage (D/G 8.3× → 7.5×). '
        + 'A 60-day test reveals the truth: D/G explodes from 7.5× (30d) to 20.1× (60d). '
        + 'The 30-day "improvement" was a temporary pause before catastrophic debt accumulation. '
        + 'However — a 90-day test (3 seeds, 2026-04-20) shows D/G partially RECOVERS to ~16× by day 90.',
      'The root cause is two-part: (1) At D/G=27, counter-cyclical multiplier = 10% interest. '
        + 'GDP grows ~2.4%/day while debt grows ~2%/day at this rate — D/G slowly accumulates. '
        + '(2) At D/G=30, TIER3 fires (0% interest), but the 10% hysteresis band (unlocks at 27) '
        + 'is too narrow — it unlocks before deleveraging completes, and debt immediately '
        + 'compounds faster than GDP can grow. MM/GB opening loans ACCUMULATE during TIER3 lock, '
        + 'overwhelming any deleveraging that would otherwise occur.',
      'The counter-cyclical circuit is a GOVERNOR, not a cure: it contains D/G within the 15–30× band. '
        + 'A 90-day test (3 seeds, 2026-04-20) confirmed the economy oscillates in the 15–22× range after day 60 '
        + 'and D/G partially recovers from ~20× (day 60) to ~16× (day 90). The circuit successfully prevents '
        + 'unbounded escalation — D/G never exceeds 30×. The oscillation is uncomfortable but the economy is stable and functional.',
      '⚠️ 180-DAY ESCALATION (2026-04-21): The 90-day "recovery" is temporary. '
        + 'Both tested seeds show CATASTROPHIC RELAPSE at day 150–180: D/G escalates from 16× (day 90) to 42× (seed 42) and from 9.8× (day 90) to 26× (seed 12345). '
        + 'TIER2↔TIER3 oscillation fires 4–8 more times after day 90. The circuit cannot stop long-run debt accumulation — '
        + 'debt compounds ~10%/day while GDP grows ~1%/day. Admins of servers running past day 120 should monitor D/G weekly. '
        + 'If D/G exceeds 25×, consider triggering `/at admin recovery` — earlier activation is exponentially more effective.',
      'All 7 proposed fixes FAILED at 5-seed × 60d: (1) wider TIER3 hysteresis band (50% vs 10% — '
        + 'D/G essentially flat, not a solution), (2) cap GB total debt at 3× GDP — 0.000× improvement '
        + '(cap is at loan creation, not the lock accumulation problem), (3) tier3=50+min_int=0.20 — D/G +1.05× worse, '
        + '(4) tier3=50 alone — TIER3 still fires, D/G worse, (5) tier3=100 alone — D/G +2.3× WORSE, 0 TIER3 events '
        + '(eliminating circuit events makes it WORSE because the 0% pause is the only thing slowing debt), '
        + '(6) loan-lock alone — neutral, (7) tier3=100+loan-lock combo — D/G +2.3× WORSE, 0 TIER3 events. '
        + 'No config workaround resolves the architectural imbalance between debt (~10%/day) and GDP (~1%/day). '
        + 'An architectural fix (e.g., forced deleveraging on TIER3 exit, or bypassing TIER2 on recovery) is needed for long-run stability.',
      '✅ WHAT ADMINS CAN DO NOW: (1) Monitor D/G weekly via `/at admin stats`. '
        + '(2) If D/G exceeds 25×, trigger `/at admin recovery` — earlier activation is exponentially more effective. '
        + '(3) After recovery, keep tier3=30 (default) and monitor whether D/G stays below 15×. '
        + '(4) If D/G stays elevated, consider `/at admin recovery` again or reduce `market-event-frequency` to prevent further debt accumulation. '
        + '(5) For long-running servers (90d+), a scheduled quarterly `/at admin recovery` as preventive maintenance is recommended.',
    ],
    metrics: [
      { label: 'D/G at 14d', value: '8.31×', note: 'healthy' },
      { label: 'D/G at 30d', value: '7.50×', note: 'deceiving improvement' },
      { label: 'D/G at 60d', value: '20.1×', note: '⚠️ circuit fires — governor engages' },
      { label: 'D/G at 90d', value: '16.4×', note: '🟡 recovers — circuit contains oscillation' },
      { label: 'D/G at 180d', value: '42×', note: '🚨 CATASTROPHIC RELAPSE (seed 42); 26× (seed 12345)' },
      { label: 'Fix attempt', value: 'ALL FAILED', note: '7 fixes tested — architectural fix needed; circuit is contained not catastrophic' },
    ],
    relatedLinks: [
      { href: '/docs', label: 'Loan circuit breaker docs' },
      { href: '/simulator', label: 'Run your own simulation' },
    ],
  },
  {
    q: 'What happens if 40%+ of my players are Farmers?',
    verdict: '❌ Never',
    verdictClass: 'text-red-400 bg-red-950/60 border-red-800/50',
    answer: [
      'Farmer-heavy economies are sell-dominated: Farmers gather and sell but rarely buy, '
        + 'creating chronic oversupply. If your archetype mix shifts to 6+ Farmers in a '
        + '2MM+2GB+floor economy, GDP collapses −39% and D/G worsens.',
      'The mechanism: excess sell pressure → prices fall → GuildBuyers buy more aggressively '
        + 'to maintain their target → debt accumulates faster than the economy can service it.',
      'The standard mix (3Cas + 3Far + 2Tra) is the minimum viable balance. If your '
        + 'server is Farmer-heavy, add Hoarders or Newbies to offset the sell pressure. '
        + 'Never let Farmers exceed 50% of your active player base.',
    ],
    metrics: [
      { label: 'Casual-heavy GDP', value: '−39.0%', note: 'vs standard mix' },
      { label: 'Casual-heavy D/G', value: '+1.40× worse', note: 'debt accumulates' },
      { label: 'Volatility reduction', value: '−37.5%', note: 'but GDP cost is too high' },
    ],
  },
];

/* ─── ECONOMY BEHAVIOR ─────────────────────────────────────── */

const BEHAVIOR_FINDINGS: Finding[] = [
  {
    q: 'Why is my economy volatile even with the recommended config?',
    verdict: '💡 Key Insight',
    verdictClass: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    answer: [
      'Volatility in Auto-Tune is mostly structural — set by your player archetype mix and initial '
        + 'economy seed — not by your engine parameters. A 5-seed volatility test showed the same '
        + '2MM+2GB+floor config producing volatility ranging from 7.9% (stable) to 28.7% (volatile) '
        + 'across different random seeds.',
      'This is not a bug. It is a feature. Auto-Tune models a real economy — and real economies '
        + 'have good and bad years. Seeds 42 and 77777 are stable (7.9–8.8% CV). Seeds 12345, '
        + '98765, and 11111 are volatile (20–29% CV). The variance is inherent to the initial '
        + 'conditions.',
      'If your server is consistently volatile, the first question to ask is: has the player mix '
        + 'shifted (more Farmers, fewer Traders)? Engine parameters are a secondary concern.',
    ],
    metrics: [
      { label: 'Volatility range', value: '7.9%–28.7%', note: 'same config, different seeds' },
      { label: 'Most stable seeds', value: '42, 77777', note: '7.9–8.8% CV' },
      { label: 'Most volatile seeds', value: '12345, 98765, 11111', note: '20–29% CV' },
    ],
    relatedLinks: [
      { href: '/simulator', label: 'Run multi-seed tests' },
    ],
  },
  {
    q: 'Why does D/G sometimes look bad but the economy is fine?',
    verdict: '💡 Key Insight',
    verdictClass: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    answer: [
      'The Debt/GDP ratio can spike dramatically after loan defaults — but defaulted loans '
        + 'are written off. The spike is a stale-debt artifact, not active financial stress. '
        + 'After defaults resolve, the economy functions normally.',
      'Example: a 50% player exodus caused D/G to spike from 0.75× to 13.92× — but GDP '
        + 'only dropped −3.3%. The economy survived and recovered. D/G was high because '
        + 'defaults from departing players were still counted in total debt.',
      'Rule: always read D/G together with GDP trend, spread width, and volatility. '
        + 'Never diagnose an economy by D/G alone.',
    ],
    metrics: [
      { label: 'Post-exodus GDP', value: '−3.3%', note: 'economy survived' },
      { label: 'Post-exodus D/G', value: '13.92×', note: 'stale default debt artifact' },
    ],
  },
  {
    q: 'Why do prices keep falling even with a Diamond floor?',
    verdict: '💡 Key Insight',
    verdictClass: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    answer: [
      'The Diamond floor affects displayed prices but not internal price discovery. '
        + 'When prices hit the floor, players respond as if Diamond is worth $300 — gathering '
        + 'more of it. This increases supply → which would push internal prices below the floor. '
        + 'The floor prevents the displayed price from following.',
      + 'The result: players see Diamond at $300, but the internal economy treats it as worth '
        + '$180–285 (below the floor). This discrepancy means the GuildBuyer\'s perceived value '
        + 'is based on the suppressed internal price, not the displayed floor price.',
      'This is the Floor Paradox. It is not a bug — it is the correct behavior. '
        + 'Monitor whether internal prices (visible in web dashboard) are falling toward '
        + '$150 or below. If so, your floor is too high for your economy\'s natural equilibrium.',
    ],
    metrics: [
      { label: 'Diamond displayed', value: '$300', note: 'at 60% floor' },
      { label: 'Diamond internal avg', value: '$285', note: 'paradox: lower than floor' },
      { label: 'Natural equilibrium', value: '$241', note: 'without floor' },
    ],
    relatedLinks: [
      { href: '/how-it-works', label: 'Price update mechanics' },
    ],
  },
  {
    q: 'What is the minimum viable player count for Auto-Tune?',
    verdict: '💡 Key Insight',
    verdictClass: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    answer: [
      'Auto-Tune\'s player scaling uses a tanh curve that reaches 99% effect at 10 players. '
        + 'Below 5 players, the market engine has minimal price discovery power — spreads '
        + 'widen to 3–4× normal, and price movements become noisy.',
      'For a healthy economy: 10+ active traders provides full engine benefit. '
        + '5–9 active traders works but spreads are wider. Below 5: consider enabling '
        + 'MarketMaker bots to compensate for low human participation.',
      'GuildBuyers and MarketMakers count as active participants in spread computation. '
        + 'A server with 3 humans + 2 MM + 2 GB (5 active archetypes) can function '
        + 'better than a server with 8 humans and no structured market participants.',
    ],
    metrics: [
      { label: 'Full effect at', value: '10+ players', note: 'tanh curve at 99%' },
      { label: 'Reduced effect', value: '5–9 players', note: 'widened spreads' },
      { label: 'Minimum viable', value: '3 archetypes', note: 'including bots' },
    ],
  },
];

/* ─── ACCORDION ─────────────────────────────────────────────── */

function FindingRow({ finding }: { finding: Finding }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border border-gray-800 rounded-xl overflow-hidden bg-gray-900/40 hover:border-gray-700 transition-colors">
      <button
        onClick={() => setOpen(!open)}
        className="w-full text-left px-5 py-4 flex items-start gap-4"
      >
        <span className={cn('shrink-0 mt-0.5', open ? 'text-emerald-400' : 'text-gray-500')}>
          {open ? <ChevronDown className="w-4 h-4 rotate-180 transition-transform" /> : <ChevronDown className="w-4 h-4 transition-transform" />}
        </span>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-1">
            <span className="font-medium text-white text-sm">{finding.q}</span>
            <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium border', finding.verdictClass)}>
              {finding.verdict}
            </span>
          </div>
          {open && (
            <div className="mt-4 space-y-4">
              {finding.answer.map((para, i) => (
                <p key={i} className="text-sm text-gray-300 leading-relaxed">{para}</p>
              ))}
              {finding.metrics && finding.metrics.length > 0 && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {finding.metrics.map((m) => (
                    <div key={m.label} className="flex items-center gap-3 px-3 py-2 rounded-lg bg-gray-950/60 border border-gray-800">
                      <div>
                        <p className="text-xs text-gray-500">{m.label}</p>
                        <p className="text-sm font-mono font-bold text-white">{m.value}</p>
                        {m.note && <p className="text-[10px] text-gray-600">{m.note}</p>}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {finding.relatedLinks && finding.relatedLinks.length > 0 && (
                <div className="flex flex-wrap gap-3">
                  {finding.relatedLinks.map((l) => (
                    <Link
                      key={l.href}
                      href={l.href}
                      className="inline-flex items-center gap-1.5 text-xs text-emerald-400 hover:text-emerald-300 transition-colors"
                    >
                      <ArrowRight className="w-3 h-3" />
                      {l.label}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </button>
    </div>
  );
}

function CategorySection({ category }: { category: Category }) {
  const Icon = category.icon;
  return (
    <section id={category.id} className="mb-14">
      <div className="flex items-center gap-3 mb-5">
        <div className="w-8 h-8 rounded-lg bg-emerald-950/60 border border-emerald-800/50 flex items-center justify-center shrink-0">
          <Icon className="w-4 h-4 text-emerald-400" />
        </div>
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-widest">Category</p>
          <h2 className="text-lg font-bold text-white">{category.label}</h2>
        </div>
      </div>
      <div className="space-y-2 pl-0 sm:pl-11">
        {category.findings.map((f) => (
          <FindingRow key={f.q} finding={f} />
        ))}
      </div>
    </section>
  );
}

/* ─── PAGE ─────────────────────────────────────────────────── */

const CATEGORIES: Category[] = [
  { id: 'archetypes', label: 'Archetype Decisions', icon: Users, findings: ARCHETYPE_FINDINGS },
  { id: 'config', label: 'Config Decisions', icon: Sliders, findings: CONFIG_FINDINGS },
  { id: 'behavior', label: 'Economy Behavior', icon: BarChart2, findings: BEHAVIOR_FINDINGS },
  { id: 'exploit', label: 'Exploit Resistance', icon: Shield, findings: EXPLOIT_RESISTANCE_FINDINGS },
];

function StatBadge({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col items-center px-5 py-4 bg-gray-900/60 border border-gray-800 rounded-xl">
      <p className="text-2xl font-mono font-bold text-emerald-400">{value}</p>
      <p className="text-xs text-gray-500 mt-1">{label}</p>
    </div>
  );
}

export default function FindingsPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main>
        {/* Hero */}
        <div className="border-b border-gray-800/50 bg-gray-950">
          <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-14">
            <div className="flex items-center gap-2 mb-3">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-950/60 border border-emerald-800/50 text-emerald-400">
                <FlaskConical className="w-3 h-3" />
                Simulation Lab
              </span>
              <span className="text-xs text-gray-600">2026-04-21 · 26 findings · 90+ simulation runs</span>
            </div>
            <h1 className="text-3xl sm:text-4xl font-bold text-white mb-4">
              What the simulation proved
            </h1>
            <p className="text-gray-400 max-w-2xl text-sm sm:text-base leading-relaxed">
              Every finding on this page comes from automated 14–90 day economy simulations with real archetype
              decision logic — not guesswork, not spreadsheets. Auto-Tune is the only Minecraft economy plugin
              with a published evidence base for its recommendations.
            </p>
            {/* Stats strip */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-8">
              <StatBadge label="Simulation runs" value="90+" />
              <StatBadge label="Seeds tested" value="5" />
              <StatBadge label="Findings" value="26" />
              <StatBadge label="Days per run" value="14–90" />
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
          {/* Quick navigation */}
          <div className="flex flex-wrap gap-2 mb-10">
            {CATEGORIES.map((c) => (
              <a
                key={c.id}
                href={`#${c.id}`}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-gray-800 bg-gray-900/60 text-xs text-gray-400 hover:text-white hover:border-gray-700 transition-colors"
              >
                <c.icon className="w-3.5 h-3.5" />
                {c.label}
              </a>
            ))}
          </div>

          {/* Critical finding alert */}
          <a
            href="#economy-stability"
            className="group flex items-start gap-4 rounded-xl border border-amber-800/60 bg-amber-950/30 p-5 mb-10 hover:border-amber-700/80 hover:bg-amber-950/50 transition-colors"
          >
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-amber-200 mb-0.5">
                Critical: 60-day economy stability — all proposed fixes FAILED, but contained
              </p>
              <p className="text-xs text-amber-300/80 leading-relaxed">
                The 2MM+2GB+floor config is stable at 14d and 30d, but D/G peaks at ~20× around day 60. The counter-cyclical circuit is a governor, not a cure — it contains D/G within the 15–30× band. A 90-day test confirmed D/G partially recovers to ~16×. No config workaround exists; an architectural fix is needed. Admins of long-running servers should monitor D/G weekly.
              </p>
            </div>
          </a>

          <CategorySection category={CATEGORIES[0]} />
          <CategorySection category={CATEGORIES[1]} />
          <CategorySection category={CATEGORIES[2]} />
          <CategorySection category={CATEGORIES[3]} />
        </div>

        {/* CTA */}
        <div className="border-t border-gray-800/50 bg-gray-950">
          <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 text-center">
            <p className="text-gray-400 text-sm mb-6">
              Want to run your own tests? The full simulator is interactive and free to use.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Link
                href="/simulator"
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
              >
                Open the Simulator →
              </Link>
              <Link
                href="/sweep-results"
                className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
              >
                Explore 840-config sweep
              </Link>
            </div>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}


