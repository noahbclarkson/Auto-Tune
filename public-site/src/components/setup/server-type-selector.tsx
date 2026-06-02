'use client';

import { useWizard, ServerType } from './wizard-context';
import { cn } from '@/lib/utils';
import { Axe, Mountain, Sword, ShoppingCart, Sliders } from 'lucide-react';

const ICON_MAP: Record<string, React.ElementType> = {
  axe: Axe,
  mountain: Mountain,
  swords: Sword,
  cart: ShoppingCart,
  sliders: Sliders,
};

const SERVER_TYPES: Array<{ key: ServerType; name: string; description: string; icon: string; badge: string }> = [
  {
    key: 'smp',
    name: 'SMP / Vanilla+',
    description: 'Standard survival. Moderate trading, moderate griefing risk.',
    icon: 'axe',
    badge: 'Recommended',
  },
  {
    key: 'skyblock',
    name: 'Skyblock',
    description: 'Island economies, limited resources, high scarcity.',
    icon: 'mountain',
    badge: 'More demand floor',
  },
  {
    key: 'faction',
    name: 'Faction / PvP',
    description: 'High turnover, high exploit risk. Aggressive circuit breaker.',
    icon: 'swords',
    badge: 'Tight security',
  },
  {
    key: 'economy',
    name: 'Economy / Shop',
    description: 'Trading-focused, minimal PvP. Tight spreads reward active traders.',
    icon: 'cart',
    badge: 'Dense liquidity',
  },
  {
    key: 'custom',
    name: 'Custom',
    description: "I'll configure archetypes manually.",
    icon: 'sliders',
    badge: '',
  },
];

export function ServerTypeSelector() {
  const { state, dispatch } = useWizard();
  const { serverType } = state.answers;

  return (
    <div>
      <div className="text-center mb-6">
        <h2 className="text-xl font-bold text-white mb-2">What kind of server are you running?</h2>
        <p className="text-gray-400 text-sm">Choose your server type to get started with recommended settings.</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 max-w-3xl mx-auto">
        {SERVER_TYPES.map((item) => {
          const Icon = ICON_MAP[item.icon] ?? Sliders;
          const selected = serverType === item.key;
          return (
            <button
              key={item.key}
              onClick={() => dispatch({ type: 'SET_SERVER_TYPE', serverType: item.key })}
              className={cn(
                'relative text-left p-4 rounded-xl border-2 transition-all hover:scale-[1.02] active:scale-[0.99]',
                selected
                  ? 'border-emerald-500 bg-emerald-500/10 shadow-lg shadow-emerald-500/10'
                  : 'border-gray-700 bg-gray-900/60 hover:border-gray-600',
              )}
            >
              {item.badge && (
                <span className={cn(
                  'absolute top-2 right-2 text-xs px-2 py-0.5 rounded-full',
                  selected ? 'bg-emerald-600 text-white' : 'bg-gray-700 text-gray-400',
                )}>
                  {item.badge}
                </span>
              )}
              <div className={cn(
                'w-9 h-9 rounded-lg flex items-center justify-center mb-3',
                selected ? 'bg-emerald-600/20' : 'bg-gray-800',
              )}>
                <Icon className={cn('w-5 h-5', selected ? 'text-emerald-400' : 'text-gray-400')} />
              </div>
              <div className="font-semibold text-white text-sm mb-1">{item.name}</div>
              <div className="text-xs text-gray-400 leading-relaxed">{item.description}</div>
              {selected && (
                <div className="absolute -top-2 -left-2 w-5 h-5 rounded-full bg-emerald-500 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-white" />
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
