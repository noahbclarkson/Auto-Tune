'use client';

import { useWizard } from './wizard-context';
import { cn } from '@/lib/utils';
import { wizardArchetypes } from '@/lib/wizard-data';
import { Activity, TrendingUp, DollarSign, Clock, Shield, Zap } from 'lucide-react';

type StabilityData = typeof wizardArchetypes.serverTypes.smp.stability;

interface StatCardProps {
  icon: React.ElementType;
  label: string;
  value: string;
  color: string;
}

function StatCard({ icon: Icon, label, value, color }: StatCardProps) {
  return (
    <div className="flex items-start gap-3 p-3 rounded-lg bg-gray-900/60 border border-gray-800">
      <div className={cn('w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0', color)}>
        <Icon className="w-4 h-4" />
      </div>
      <div>
        <div className="text-xs text-gray-500 mb-0.5">{label}</div>
        <div className="text-sm font-medium text-white">{value}</div>
      </div>
    </div>
  );
}

export function StabilityPreview() {
  const { state } = useWizard();
  const { serverType, playerCount, goals } = state.answers;

  if (!serverType) return null;

  const serverData = wizardArchetypes.serverTypes[serverType];
  const playerData = wizardArchetypes.playerCounts[playerCount ?? 'medium'];
  const stability: StabilityData | null = serverData.stability;

  const archetype = serverData.archetype;
  const archetypeLabel = archetype
    ? `${archetype.MarketMaker} MarketMaker${archetype.MarketMaker !== 1 ? 's' : ''} + ${archetype.GuildBuyer} GuildBuyer${archetype.GuildBuyer !== 1 ? 's' : ''} @ ${archetype.GuildBuyerThreshold}%`
    : 'Custom (you configure)';

  const finalSpread = Math.round((serverData.spread + playerData.spreadDelta) * 100) / 100;

  // Goal descriptions for preview
  const goalLabels: Record<string, string> = {
    volume: '📈 Tight spreads',
    seller_protection: '🛡️ 60% floor',
    treasury: '💰 Tax 3%',
    fast_discovery: '⚡ Fast reactions',
    low_debt: '🔒 Strict loans',
    fun_volatility: '🎮 High movement',
  };

  const activeGoalLabels = goals.map((g) => goalLabels[g]).filter(Boolean);

  return (
    <div>
      <div className="text-center mb-6">
        <h2 className="text-xl font-bold text-white mb-2">Here's how your economy should behave</h2>
        <p className="text-gray-400 text-sm">Based on your selections and simulation data.</p>
      </div>

      <div className="max-w-2xl mx-auto space-y-5">
        {/* Recommended archetype */}
        <div className="p-4 rounded-xl bg-emerald-500/5 border border-emerald-500/20">
          <div className="text-xs text-emerald-400 font-semibold mb-1">Recommended Archetype Config</div>
          <div className="text-white font-bold text-lg">{archetypeLabel}</div>
          <div className="text-xs text-gray-400 mt-1">
            Starting spread: {finalSpread} (base {serverData.spread} ± {playerData.spreadDelta >= 0 ? '+' : ''}{playerData.spreadDelta})
          </div>
        </div>

        {/* Stability metrics */}
        {stability && (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <StatCard
              icon={Activity}
              label="Buy/Sell Balance"
              value={stability.buyRatio}
              color="bg-blue-500/10 text-blue-400"
            />
            <StatCard
              icon={TrendingUp}
              label="Volatility"
              value={stability.volatility}
              color="bg-amber-500/10 text-amber-400"
            />
            <StatCard
              icon={Shield}
              label="Debt Risk"
              value={stability.debtRisk}
              color={stability.debtRisk.startsWith('Low') ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'}
            />
            <StatCard
              icon={DollarSign}
              label="Spread Width"
              value={stability.spreadWidth}
              color="bg-purple-500/10 text-purple-400"
            />
            <StatCard
              icon={Clock}
              label="Time to Stability"
              value={stability.timeToStability}
              color="bg-cyan-500/10 text-cyan-400"
            />
            {goals.length > 0 && (
              <StatCard
                icon={Zap}
                label="Active Goals"
                value={activeGoalLabels.join(', ') || 'None'}
                color="bg-emerald-500/10 text-emerald-400"
              />
            )}
          </div>
        )}

        {/* Tune-up note */}
        <div className="p-3 rounded-lg bg-gray-900/60 border border-gray-800 text-xs text-gray-400">
          <span className="text-white font-medium">Key parameter to tune after 1 week: </span>
          {finalSpread > 0.25
            ? 'baseSpread — reduce if spreads feel too wide for your players'
            : finalSpread < 0.18
            ? 'baseSpread — increase if liquidity is high and spreads feel too tight'
            : 'baseSpread — adjust if economy feels sluggish or too volatile'}
        </div>
      </div>
    </div>
  );
}
