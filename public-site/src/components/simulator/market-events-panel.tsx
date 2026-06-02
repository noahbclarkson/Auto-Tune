'use client';

import { useState } from 'react';
import {
  MarketEvent,
  MarketEventType,
  EVENT_TYPE_DESCRIPTIONS,
  EVENT_TYPE_COLORS,
} from '@/lib/market-engine';

const MATERIAL_PRESETS: Record<string, string[]> = {
  'Diamond family': ['DIAMOND', 'DIAMOND_ORE', 'DEEPSLATE_DIAMOND_ORE'],
  'Gold family': ['GOLD_INGOT', 'GOLD_ORE', 'DEEPSLATE_GOLD_ORE', 'NETHER_GOLD_ORE'],
  'Iron family': ['IRON_INGOT', 'IRON_ORE', 'DEEPSLATE_IRON_ORE', 'RAW_IRON'],
  'Ores (all)': ['DIAMOND_ORE', 'GOLD_ORE', 'IRON_ORE', 'COAL_ORE', 'COPPER_ORE', 'EMERALD_ORE', 'LAPIS_ORE', 'REDSTONE_ORE'],
  'Building blocks': ['STONE', 'COBBLESTONE', 'DIRT', 'SAND', 'GRAVEL', 'OAK_LOG', 'OAK_PLANKS'],
  'Redstone': ['REDSTONE', 'REDSTONE_ORE', 'DEEPSLATE_REDSTONE_ORE', 'REDSTONE_TORCH'],
  'Potions': ['BREWING_STAND', 'BLAZE_POWDER', 'GHAST_TEAR', 'GLASS_BOTTLE'],
};

const ALL_MATERIALS = [
  'DIAMOND', 'EMERALD', 'GOLD_INGOT', 'IRON_INGOT', 'COPPER_INGOT',
  'NETHERITE_INGOT', 'COAL', 'LAPIS_LAZULI', 'REDSTONE',
  'ANCIENT_DEBRIS', 'ENCHANTED_GOLDEN_APPLE',
  'DIAMOND_SWORD', 'DIAMOND_PICKAXE', 'DIAMOND_CHESTPLATE',
  'NETHERITE_SWORD', 'NETHERITE_PICKAXE',
  'OAK_LOG', 'SPRUCE_LOG', 'Birch_LOG', 'JUNGLE_LOG', 'ACACIA_LOG', 'DARK_OAK_LOG',
  'STONE', 'COBBLESTONE', 'DIRT', 'SAND', 'GRAVEL', 'NETHERRACK', 'BASALT',
  'OAK_PLANKS', 'SPRUCE_PLANKS', 'BIRCH_PLANKS',
  'GLASS', 'TINTED_GLASS', 'GLASS_PANE',
  'IRON_BARS', 'LIGHTNING_ROD',
  'BLAZE_ROD', 'GHAST_TEAR', 'ENDER_PEARL', 'EYE_OF_ENDER',
  'SLIME_BALL', 'HONEY_BALL', 'PHANTOM_MEMBRANE',
  'FEATHER', 'LEATHER', 'RABBIT_HIDE', 'BONE', 'BONE_MEAL',
  'ARROW', 'BOW', 'CROSSBOW', 'TRIDENT',
  'BOOK', 'BOOKSHELF', 'KNOWLEDGE_BOOK',
  'PAPER', 'MAP', 'FILLED_MAP',
  'BREWING_STAND', 'CAULDRON', 'BLAST_FURNACE', 'SMOKER', 'FURNACE',
];

const EVENT_TYPES: MarketEventType[] = [
  'DEMAND_SURGE', 'SUPPLY_GLUT', 'INFLATION_BOOST', 'DEFLATION_DROP', 'GOLD_RUSH', 'CUSTOM',
];

interface MarketEventsPanelProps {
  events: MarketEvent[];
  onEventsChange: (events: MarketEvent[]) => void;
}

export function MarketEventsPanel({ events, onEventsChange }: MarketEventsPanelProps) {
  const [showAdd, setShowAdd] = useState(false);
  const [newEvent, setNewEvent] = useState<Partial<MarketEvent>>({
    name: '',
    type: 'DEMAND_SURGE',
    materials: [],
    priceChangeMultiplier: 2.0,
  });
  const [materialInput, setMaterialInput] = useState('');
  const [selectedPreset, setSelectedPreset] = useState<string>('');

  const addEvent = () => {
    if (!newEvent.name || !newEvent.type || newEvent.materials?.length === 0) return;
    const event: MarketEvent = {
      id: Math.random().toString(36).slice(2),
      name: newEvent.name,
      type: newEvent.type as MarketEventType,
      materials: newEvent.materials!,
      priceChangeMultiplier: newEvent.priceChangeMultiplier ?? 2.0,
      description: EVENT_TYPE_DESCRIPTIONS[newEvent.type as MarketEventType],
    };
    onEventsChange([...events, event]);
    setShowAdd(false);
    setNewEvent({ name: '', type: 'DEMAND_SURGE', materials: [], priceChangeMultiplier: 2.0 });
    setMaterialInput('');
    setSelectedPreset('');
  };

  const removeEvent = (id: string) => {
    onEventsChange(events.filter((e) => e.id !== id));
  };

  const addMaterial = (mat: string) => {
    const upper = mat.toUpperCase().trim().replace(/ /g, '_');
    if (!upper) return;
    if (!newEvent.materials!.includes(upper)) {
      setNewEvent({ ...newEvent, materials: [...newEvent.materials!, upper] });
    }
    setMaterialInput('');
  };

  const addPreset = (name: string) => {
    const mats = MATERIAL_PRESETS[name] ?? [];
    const merged = Array.from(new Set([...newEvent.materials!, ...mats]));
    setNewEvent({ ...newEvent, materials: merged });
    setSelectedPreset(name);
  };

  const removeMaterial = (mat: string) => {
    setNewEvent({ ...newEvent, materials: newEvent.materials!.filter((m) => m !== mat) });
  };

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-white">Market Events</h3>
          {events.length > 0 && (
            <span className="inline-flex items-center rounded-full bg-emerald-900/50 text-emerald-400 text-xs font-medium px-2 py-0.5">
              {events.length} active
            </span>
          )}
        </div>
        <button
          onClick={() => setShowAdd(!showAdd)}
          className="text-xs font-medium text-emerald-400 hover:text-emerald-300 transition-colors"
        >
          {showAdd ? 'Cancel' : '+ Add Event'}
        </button>
      </div>

      <div className="p-4 space-y-3">
        {/* Active events list */}
        {events.length === 0 && !showAdd && (
          <p className="text-sm text-gray-500 italic py-2">
            No active events. Add one to see how it affects prices.
          </p>
        )}

        {events.map((event) => (
          <div
            key={event.id}
            className="rounded-lg border border-gray-800 bg-gray-950/40 px-3 py-2.5"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-sm font-semibold ${EVENT_TYPE_COLORS[event.type]}`}>
                    {event.name}
                  </span>
                  <span className="text-xs text-gray-600">×{event.priceChangeMultiplier.toFixed(1)}</span>
                  <span className={`text-xs font-mono px-1.5 py-0.5 rounded bg-gray-800 text-gray-400`}>
                    {event.type}
                  </span>
                </div>
                <p className="text-xs text-gray-500 mt-0.5">
                  {EVENT_TYPE_DESCRIPTIONS[event.type]}
                </p>
                <div className="flex flex-wrap gap-1 mt-1.5">
                  {event.materials.map((m) => (
                    <span
                      key={m}
                      className="text-xs font-mono px-1.5 py-0.5 rounded bg-gray-800 text-gray-400"
                    >
                      {m}
                    </span>
                  ))}
                </div>
              </div>
              <button
                onClick={() => removeEvent(event.id)}
                className="text-gray-600 hover:text-rose-400 transition-colors text-xs shrink-0 mt-0.5"
              >
                ✕
              </button>
            </div>
          </div>
        ))}

        {/* Add event form */}
        {showAdd && (
          <div className="rounded-lg border border-emerald-900/40 bg-emerald-950/10 px-3 py-3 space-y-3">
            <div className="grid grid-cols-2 gap-3">
              {/* Event name */}
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">Event name</label>
                <input
                  type="text"
                  value={newEvent.name}
                  onChange={(e) => setNewEvent({ ...newEvent, name: e.target.value })}
                  placeholder="e.g. Diamond Rush"
                  className="w-full rounded bg-gray-900 border border-gray-700 text-white text-sm px-2.5 py-1.5 placeholder-gray-600 focus:outline-none focus:border-emerald-600"
                />
              </div>

              {/* Event type */}
              <div>
                <label className="block text-xs text-gray-500 mb-1">Type</label>
                <select
                  value={newEvent.type}
                  onChange={(e) => setNewEvent({ ...newEvent, type: e.target.value as MarketEventType })}
                  className="w-full rounded bg-gray-900 border border-gray-700 text-white text-sm px-2.5 py-1.5 focus:outline-none focus:border-emerald-600"
                >
                  {EVENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-gray-500 mt-1">
                  {EVENT_TYPE_DESCRIPTIONS[newEvent.type as MarketEventType]}
                </p>
              </div>

              {/* Multiplier */}
              <div>
                <label className="block text-xs text-gray-500 mb-1">Multiplier</label>
                <input
                  type="number"
                  min="0.1"
                  max="10"
                  step="0.1"
                  value={newEvent.priceChangeMultiplier}
                  onChange={(e) =>
                    setNewEvent({ ...newEvent, priceChangeMultiplier: parseFloat(e.target.value) })
                  }
                  className="w-full rounded bg-gray-900 border border-gray-700 text-white text-sm px-2.5 py-1.5 focus:outline-none focus:border-emerald-600"
                />
                <p className="text-xs text-gray-500 mt-1">1.0 = no effect, 2.0 = 2× amplification</p>
              </div>

              {/* Material presets */}
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">Quick presets</label>
                <div className="flex flex-wrap gap-1.5">
                  {Object.keys(MATERIAL_PRESETS).map((name) => (
                    <button
                      key={name}
                      onClick={() => addPreset(name)}
                      className="text-xs px-2 py-1 rounded border border-gray-700 text-gray-400 hover:border-emerald-600 hover:text-emerald-400 transition-colors"
                    >
                      {name}
                    </button>
                  ))}
                </div>
              </div>

              {/* Material search */}
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">Add materials</label>
                <input
                  type="text"
                  value={materialInput}
                  onChange={(e) => setMaterialInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addMaterial(materialInput);
                    }
                  }}
                  placeholder="Type material name, press Enter"
                  className="w-full rounded bg-gray-900 border border-gray-700 text-white text-sm px-2.5 py-1.5 placeholder-gray-600 focus:outline-none focus:border-emerald-600"
                  list="material-suggestions"
                />
                <datalist id="material-suggestions">
                  {ALL_MATERIALS.map((m) => (
                    <option key={m} value={m} />
                  ))}
                </datalist>
                {/* Selected materials */}
                {newEvent.materials && newEvent.materials.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {newEvent.materials.map((m) => (
                      <span
                        key={m}
                        className="inline-flex items-center gap-1 text-xs font-mono px-2 py-0.5 rounded bg-emerald-950/40 border border-emerald-900/50 text-emerald-400"
                      >
                        {m}
                        <button
                          onClick={() => removeMaterial(m)}
                          className="text-emerald-600 hover:text-rose-400 ml-0.5"
                        >
                          ×
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Actions */}
            <div className="flex gap-2 pt-1">
              <button
                onClick={addEvent}
                disabled={!newEvent.name || !newEvent.materials?.length}
                className="flex-1 rounded bg-emerald-700 hover:bg-emerald-600 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm font-medium py-1.5 transition-colors"
              >
                Add Event
              </button>
              <button
                onClick={() => setShowAdd(false)}
                className="px-4 rounded border border-gray-700 text-gray-400 hover:text-white text-sm py-1.5 transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* Summary when events are active */}
        {events.length > 0 && !showAdd && (
          <div className="rounded border border-gray-800 bg-gray-950/20 px-3 py-2">
            <p className="text-xs text-gray-500">
              <span className="text-emerald-400 font-medium">{events.length}</span> event{events.length !== 1 ? 's' : ''} active — prices will{' '}
              {events.some((e) => e.type === 'INFLATION_BOOST')
                ? 'drift upward'
                : events.some((e) => e.type === 'DEFLATION_DROP')
                ? 'drift downward'
                : 'have amplified price swings'}
              {' '}for matching items.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
