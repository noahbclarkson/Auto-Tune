'use client';

import { useWizard, GoalKey } from './wizard-context';
import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';

const GOALS: Array<{ key: GoalKey; name: string; description: string; icon: string }> = [
  {
    key: 'volume',
    name: '📈 Player Trading Volume',
    description: 'Encourage lots of buys and sells. Tighter spreads.',
    icon: '📈',
  },
  {
    key: 'seller_protection',
    name: '🛡️ Seller Protection',
    description: 'Protect sellers from price crashes. Floor at 60%.',
    icon: '🛡️',
  },
  {
    key: 'treasury',
    name: '💰 Server Treasury',
    description: 'Grow the server\'s war chest via taxes.',
    icon: '💰',
  },
  {
    key: 'fast_discovery',
    name: '⚡ Fast Price Discovery',
    description: 'Prices react quickly to new items.',
    icon: '⚡',
  },
  {
    key: 'low_debt',
    name: '🔒 Low Debt',
    description: 'Keep players out of trouble. Strict loan limits.',
    icon: '🔒',
  },
  {
    key: 'fun_volatility',
    name: '🎮 Fun Volatility',
    description: 'Prices that move a lot. Exciting for active traders.',
    icon: '🎮',
  },
];

export function EconomyGoalsSelector() {
  const { state, dispatch } = useWizard();
  const { goals } = state.answers;
  const maxReached = goals.length >= 2;

  return (
    <div>
      <div className="text-center mb-6">
        <h2 className="text-xl font-bold text-white mb-2">What matters most to your economy?</h2>
        <p className="text-gray-400 text-sm">
          Select up to 2 goals. Each adjusts 1-3 config values.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-2xl mx-auto">
        {GOALS.map((goal) => {
          const selected = goals.includes(goal.key);
          const disabled = !selected && maxReached;
          return (
            <button
              key={goal.key}
              onClick={() => dispatch({ type: 'TOGGLE_GOAL', goal: goal.key })}
              disabled={disabled}
              className={cn(
                'text-left p-4 rounded-xl border-2 transition-all',
                disabled && 'opacity-40 cursor-not-allowed',
                selected
                  ? 'border-emerald-500 bg-emerald-500/10 shadow-lg shadow-emerald-500/10'
                  : 'border-gray-700 bg-gray-900/60 hover:border-gray-600 hover:scale-[1.01]',
              )}
            >
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-semibold text-white text-sm mb-1">{goal.name}</div>
                  <div className="text-xs text-gray-400 leading-relaxed">{goal.description}</div>
                </div>
                <div className={cn(
                  'w-5 h-5 rounded-full flex items-center justify-center ml-3 mt-0.5 flex-shrink-0',
                  selected ? 'bg-emerald-500 text-white' : 'border border-gray-600',
                )}>
                  {selected && <Check className="w-3 h-3" />}
                </div>
              </div>
            </button>
          );
        })}
      </div>

      <p className="text-center text-gray-500 text-xs mt-4">
        {maxReached
          ? '2 goals selected. Deselect one to choose a different goal.'
          : 'Select up to 2 goals to fine-tune your economy.'}
      </p>
    </div>
  );
}
