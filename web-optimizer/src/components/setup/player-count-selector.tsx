'use client';

import { useWizard, PlayerCount } from './wizard-context';
import { cn } from '@/lib/utils';
import { Users } from 'lucide-react';

const PLAYER_COUNTS: Array<{ key: PlayerCount; name: string; description: string; icon: string }> = [
  {
    key: 'solo',
    name: 'Solo / Small',
    description: '1-10 players — tight spreads for low liquidity',
    icon: '1-10',
  },
  {
    key: 'medium',
    name: 'Medium',
    description: '10-50 players — default settings',
    icon: '10-50',
  },
  {
    key: 'large',
    name: 'Large',
    description: '50-200 players — wider spreads for high volume',
    icon: '50-200',
  },
  {
    key: 'massive',
    name: 'Massive',
    description: '200+ players — very wide, prevent manipulation',
    icon: '200+',
  },
];

export function PlayerCountSelector() {
  const { state, dispatch } = useWizard();
  const { playerCount } = state.answers;

  return (
    <div>
      <div className="text-center mb-6">
        <h2 className="text-xl font-bold text-white mb-2">How many active players do you expect?</h2>
        <p className="text-gray-400 text-sm">
          Auto-Tune adapts automatically. This just sets the starting spread.
        </p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 max-w-3xl mx-auto">
        {PLAYER_COUNTS.map((item) => {
          const selected = playerCount === item.key;
          return (
            <button
              key={item.key}
              onClick={() => dispatch({ type: 'SET_PLAYER_COUNT', playerCount: item.key })}
              className={cn(
                'text-center p-4 rounded-xl border-2 transition-all hover:scale-[1.02] active:scale-[0.99]',
                selected
                  ? 'border-emerald-500 bg-emerald-500/10 shadow-lg shadow-emerald-500/10'
                  : 'border-gray-700 bg-gray-900/60 hover:border-gray-600',
              )}
            >
              <div className={cn(
                'w-10 h-10 rounded-full flex items-center justify-center mx-auto mb-3 text-sm font-bold',
                selected ? 'bg-emerald-600/20 text-emerald-400' : 'bg-gray-800 text-gray-400',
              )}>
                <Users className="w-5 h-5" />
              </div>
              <div className="font-semibold text-white text-sm mb-1">{item.name}</div>
              <div className="text-xs text-gray-400 leading-relaxed">{item.description}</div>
            </button>
          );
        })}
      </div>

      {playerCount && (
        <p className="text-center text-gray-500 text-xs mt-4">
          Spread will be adjusted by{' '}
          <span className="text-emerald-400 font-medium">
            {playerCount === 'solo' ? '-0.05 (tighter)' :
             playerCount === 'medium' ? '±0 (default)' :
             playerCount === 'large' ? '+0.05 (wider)' :
             '+0.10 (much wider)'}
          </span>{' '}
          based on your player count.
        </p>
      )}
    </div>
  );
}
