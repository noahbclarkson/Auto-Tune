'use client';

import { type AdminHealthDto } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertTriangle, CheckCircle, Wrench, ChevronDown, ChevronUp, Copy } from 'lucide-react';
import { useState } from 'react';

interface Diagnosis {
  severity: 'ok' | 'warning' | 'critical';
  title: string;
  description: string;
  action?: {
    summary: string;
    yaml?: string;
    yamlLabel?: string;
  };
}

function diagnose(health: AdminHealthDto): Diagnosis[] {
  const results: Diagnosis[] = [];

  // Circuit breaker
  if (health.circuitBreakerTier !== 'NORMAL') {
    if (health.circuitBreakerTier === 'TIER3') {
      results.push({
        severity: 'critical',
        title: 'Emergency: Circuit breaker has paused all loan interest',
        description: `Debt/GDP has exceeded 10x — the loan interest circuit breaker has fired and all interest accrual is paused. Players can still trade but loans are frozen. This is a serious economic distress signal.`,
        action: {
          summary: `Emergency economy reset: pause new loans, consider running /at admin prices reset all to restore item prices, then review archetype config.`,
          yaml: undefined,
        },
      });
    } else if (health.circuitBreakerTier === 'TIER2') {
      results.push({
        severity: 'critical',
        title: 'Danger: Loan interest rate reduced to 25% of normal',
        description: `Debt/GDP is between 5x and 10x. Interest is severely capped — this means loan revenue to the server treasury is greatly reduced, and debt is not compounding normally. Unusual economic stress is likely.`,
        action: {
          summary: `Review why debt has grown this large. Check for: players with large unpaid loans, MarketMakers taking oversized opening loans, or an economy event that caused mass borrowing. Consider temporary archetype adjustments.`,
        },
      });
    } else {
      results.push({
        severity: 'warning',
        title: 'Warning: Loan interest rate reduced to 50% of normal',
        description: `Debt/GDP is between 3x and 5x. Interest is capped at half the normal rate. Debt is growing faster than the economy can support.`,
        action: {
          summary: `Monitor closely. If debt continues to grow, the economy will enter TIER2. Consider reducing MarketMaker count or adjusting loan-to-GDP cap.`,
        },
      });
    }
  }

  // Sell-heavy
  if (health.buyPct < 35) {
    results.push({
      severity: health.circuitBreakerTier !== 'NORMAL' ? 'warning' : 'critical',
      title: `Sell-heavy economy: only ${health.buyPct.toFixed(1)}% of trades are buys`,
      description: `Players are overwhelmingly selling items rather than buying. This means oversupply — item prices are likely declining. Without intervention, prices will continue falling until players stop farming.`,
      action: {
        summary: `Add more GuildBuyer archetypes to create demand. Each GuildBuyer proactively buys from players, restoring buy-side pressure.`,
        yaml: `# Add 1-2 more GuildBuyers in config.yml
archetypes:
  guild_buyer:
    count: 4  # was likely 2
    guild_price_dip_threshold: 0.07`,
        yamlLabel: 'GuildBuyer boost (add to shops.yml)',
      },
    });
  } else if (health.buyPct < 45) {
    results.push({
      severity: 'warning',
      title: `Mildly sell-heavy: ${health.buyPct.toFixed(1)}% buy ratio`,
      description: `Below the ideal 45-55% balanced range. Prices may drift downward over time.`,
      action: {
        summary: `Consider adding 1 more GuildBuyer or reducing farmer gather rates slightly.`,
        yaml: `archetypes:
  guild_buyer:
    count: 3  # add one more`,
        yamlLabel: 'GuildBuyer boost',
      },
    });
  }

  // Buy-heavy
  if (health.buyPct > 65) {
    results.push({
      severity: 'warning',
      title: `Buy-heavy economy: ${health.buyPct.toFixed(1)}% of trades are buys`,
      description: `Players are buying more than selling — this is the opposite of the typical Minecraft oversupply problem. Item prices are likely rising. This is generally healthy but watch for inflation.`,
      action: {
        summary: `Monitor inflation. If Diamond buy prices are rising more than 5%/day, consider slightly increasing sell_pressure_multiplier in config.`,
      },
    });
  }

  // High volatility
  if (health.avgVolatility >= 0.15) {
    results.push({
      severity: 'critical',
      title: 'Economy is volatile: prices oscillating wildly',
      description: `Aggregate volatility of ${(health.avgVolatility * 100).toFixed(1)}% per tick — players experience erratic price swings. This makes economic planning impossible and erodes trust in the economy.`,
      action: {
        summary: `Add 1-2 MarketMakers to provide two-sided liquidity. MarketMakers post buy and sell orders simultaneously, dampening price swings. Also check for Exploiter archetypes which amplify volatility.`,
        yaml: `archetypes:
  market_maker:
    count: 3  # was likely 1 or 2
  # Consider reducing Exploiters if present`,
        yamlLabel: 'MarketMaker boost',
      },
    });
  } else if (health.avgVolatility >= 0.05) {
    results.push({
      severity: 'warning',
      title: `Moderate volatility: ${(health.avgVolatility * 100).toFixed(1)}% price swings`,
      description: `Some oscillation is normal, but this level may be noticeable to players.`,
      action: {
        summary: `Consider adding 1 MarketMaker to smooth price movements, or reduce trend_dampening to let prices settle faster.`,
      },
    });
  }

  // High spread
  if (health.avgBpd >= 5) {
    results.push({
      severity: 'warning',
      title: `Wide spreads: ${health.avgBpd.toFixed(2)}% buy/sell spread`,
      description: `Players lose ${health.avgBpd.toFixed(1)}% on every round-trip buy+sell. This makes casual trading feel punishing and discourages market activity.`,
      action: {
        summary: `Lower base_spread in config, or increase player count / trade volume to tighten spreads naturally via the liquidity factor.`,
        yaml: `spread:
  base_spread: 0.20  # was likely 0.30
  # Or add more players to tighten spreads via liquidity`,
        yamlLabel: 'Tighter spreads config',
      },
    });
  }

  // Debt warning
  if (health.debtGdpRatio >= 3 && health.circuitBreakerTier === 'NORMAL') {
    results.push({
      severity: 'warning',
      title: `Elevated debt: Debt/GDP = ${health.debtGdpRatio.toFixed(2)}x`,
      description: `Total debt is ${health.debtGdpRatio.toFixed(1)}x the economy's GDP. This is sustainable in the short term but the circuit breaker will fire if it continues growing.`,
      action: {
        summary: `Monitor for players with large outstanding loans. Consider reducing loan interest_rate slightly or increasing post-default cooldown.`,
      },
    });
  }

  // All-clear
  if (results.length === 0) {
    results.push({
      severity: 'ok',
      title: 'Economy is healthy',
      description: `All metrics are within normal ranges. GDP is ${health.gdp > 0 ? 'positive' : 'not yet established'}, D/G is manageable, trade mix is balanced (${health.buyPct.toFixed(0)}% buy), and volatility is low.`,
    });
  }

  return results;
}

export function EconomyRecoveryAdvisor({ health }: { health: AdminHealthDto }) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({});
  const [copied, setCopied] = useState<number | null>(null);
  const diagnoses = diagnose(health);

  const toggle = (i: number) =>
    setExpanded((prev) => ({ ...prev, [i]: !prev[i] }));

  const copy = (yaml: string, i: number) => {
    navigator.clipboard.writeText(yaml).catch(() => {});
    setCopied(i);
    setTimeout(() => setCopied(null), 1500);
  };

  const criticalCount = diagnoses.filter((d) => d.severity === 'critical').length;
  const warningCount = diagnoses.filter((d) => d.severity === 'warning').length;

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-semibold flex items-center gap-2">
          <Wrench className="w-4 h-4 text-sky-400" />
          Economy Recovery Advisor
          {criticalCount > 0 && (
            <span className="ml-2 px-2 py-0.5 rounded text-xs font-medium bg-red-500/15 text-red-400 border border-red-500/30">
              {criticalCount} critical
            </span>
          )}
          {warningCount > 0 && (
            <span className="px-2 py-0.5 rounded text-xs font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30">
              {warningCount} warning{warningCount > 1 ? 's' : ''}
            </span>
          )}
          {diagnoses.length === 1 && diagnoses[0].severity === 'ok' && (
            <span className="ml-2 px-2 py-0.5 rounded text-xs font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
              All clear
            </span>
          )}
        </CardTitle>
        <p className="text-xs text-muted-foreground mt-1">
          Automatic diagnosis based on current economy metrics — run{' '}
          <code className="text-xs text-sky-400">/at admin health</code> for the raw data.
        </p>
      </CardHeader>
      <CardContent className="space-y-2">
        {diagnoses.map((d, i) => (
          <div key={i} className="rounded-lg border border-border bg-card">
            {/* Header row — always visible */}
            <button
              className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-muted/30 transition-colors"
              onClick={() => toggle(i)}
            >
              <div className={`flex-shrink-0 mt-0.5 ${
                d.severity === 'critical' ? 'text-red-400'
                  : d.severity === 'warning' ? 'text-amber-400'
                  : 'text-emerald-400'
              }`}>
                {d.severity === 'critical' ? <AlertTriangle className="w-4 h-4" />
                  : d.severity === 'warning' ? <AlertTriangle className="w-4 h-4" />
                  : <CheckCircle className="w-4 h-4" />}
              </div>
              <span className={`text-sm font-medium flex-1 ${
                d.severity === 'critical' ? 'text-red-300'
                  : d.severity === 'warning' ? 'text-amber-200'
                  : 'text-emerald-300'
              }`}>
                {d.title}
              </span>
              <div className="flex-shrink-0 text-muted-foreground">
                {expanded[i] ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </div>
            </button>

            {/* Expanded content */}
            {expanded[i] && (
              <div className="px-4 pb-4 border-t border-border/50">
                <p className="text-xs text-muted-foreground mt-3 leading-relaxed">
                  {d.description}
                </p>
                {d.action && (
                  <div className="mt-3 rounded-md bg-muted/40 border border-border p-3">
                    <div className="flex items-start gap-2">
                      <div className="flex-shrink-0 mt-0.5">
                        <Wrench className="w-3 h-3 text-sky-400" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium text-foreground">{d.action.summary}</p>
                        {d.action.yaml && (
                          <div className="mt-2">
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-xs text-muted-foreground">{d.action.yamlLabel ?? 'Example config'}</span>
                              <button
                                onClick={() => copy(d.action!.yaml!, i)}
                                className="flex items-center gap-1 text-xs text-sky-400 hover:text-sky-300 transition-colors"
                              >
                                <Copy className="w-3 h-3" />
                                {copied === i ? 'Copied!' : 'Copy'}
                              </button>
                            </div>
                            <pre className="text-xs text-emerald-400 bg-black/40 rounded p-2 overflow-x-auto leading-relaxed">
                              {d.action.yaml}
                            </pre>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
