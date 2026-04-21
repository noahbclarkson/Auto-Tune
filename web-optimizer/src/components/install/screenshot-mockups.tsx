'use client';

import { Monitor, FileText, BarChart2 } from 'lucide-react';

// ─── Config File Mockup ────────────────────────────────────────────────────────

function ConfigMockup() {
  return (
    <div className="rounded-xl border border-gray-700 overflow-hidden bg-gray-900">
      {/* Window chrome */}
      <div className="flex items-center gap-1.5 px-4 py-2.5 bg-gray-800 border-b border-gray-700">
        <span className="w-3 h-3 rounded-full bg-red-500/70" />
        <span className="w-3 h-3 rounded-full bg-amber-500/70" />
        <span className="w-3 h-3 rounded-full bg-green-500/70" />
        <span className="ml-3 text-xs text-gray-400 font-mono">config.yml</span>
      </div>
      {/* Content */}
      <div className="p-5 font-mono text-xs leading-relaxed">
        <div className="text-gray-500"># ─── Auto-Tune Configuration ───</div>
        <div className="mt-2 text-gray-300">
          <span className="text-sky-400">economy</span>:&#123;
        </div>
        <div className="pl-4">
          <div><span className="text-emerald-400">base-spread</span>: <span className="text-amber-400">0.20</span>  <span className="text-gray-600"># 20% total spread</span></div>
          <div><span className="text-emerald-400">sell-pressure-multiplier</span>: <span className="text-amber-400">1.0</span>  <span className="text-gray-600"># symmetric (recommended)</span></div>
          <div><span className="text-emerald-400">max-price-change</span>: <span className="text-amber-400">1.5</span></div>
          <div className="text-gray-600"># Starting prices for items...</div>
        </div>
        <div className="text-gray-300">&#125;</div>
        <div className="mt-2 text-gray-300">
          <span className="text-sky-400">loans</span>:&#123;
        </div>
        <div className="pl-4">
          <div><span className="text-emerald-400">enabled</span>: <span className="text-amber-400">true</span></div>
          <div><span className="text-emerald-400">counter-cyclical</span>: <span className="text-amber-400">true</span></div>
          <div><span className="text-emerald-400">debt-gdp-tier3-ratio</span>: <span className="text-amber-400">30.0</span>  <span className="text-gray-600"># pause at 30x GDP</span></div>
          <div><span className="text-emerald-400">debt-gdp-tier2-ratio</span>: <span className="text-amber-400">5.0</span>  <span className="text-gray-600"># cap interest at 5x GDP</span></div>
          <div><span className="text-emerald-400">debt-gdp-tier1-ratio</span>: <span className="text-amber-400">3.0</span>  <span className="text-gray-600"># mild cap at 3x GDP</span></div>
        </div>
        <div className="text-gray-300">&#125;</div>
        <div className="mt-2 text-gray-300">
          <span className="text-sky-400">spread</span>:&#123;
        </div>
        <div className="pl-4">
          <div><span className="text-emerald-400">volume-impact</span>: <span className="text-amber-400">0.80</span></div>
          <div><span className="text-emerald-400">player-scaling</span>: <span className="text-amber-400">0.60</span></div>
          <div><span className="text-emerald-400">full-effect-players</span>: <span className="text-amber-400">10</span></div>
        </div>
        <div className="text-gray-300">&#125;</div>
      </div>
    </div>
  );
}

// ─── In-Game Shop GUI Mockup ──────────────────────────────────────────────────

function ShopGuiMockup() {
  // 6×9 = 54 slots, rendered as a grid
  const SLOTS = Array.from({ length: 54 }, (_, i) => i);
  const ITEMS: Record<number, { name: string; price: string; color: string; bar: number }> = {
    10: { name: 'DIAMOND', price: '$311.20', color: 'cyan', bar: 65 },
    12: { name: 'EMERALD', price: '$148.50', color: 'green', bar: 42 },
    14: { name: 'IRON INGOT', price: '$10.85', color: 'gray', bar: 18 },
    16: { name: 'GOLD INGOT', price: '$22.10', color: 'amber', bar: 28 },
    19: { name: 'COPPER', price: '$4.20', color: 'orange', bar: 12 },
    21: { name: 'ANCIENT_DEBRIS', price: '$1,240', color: 'red', bar: 88 },
    23: { name: 'NETHERITE', price: '$2,180', color: 'rose', bar: 95 },
    25: { name: 'COAL', price: '$3.15', color: 'gray', bar: 8 },
    28: { name: 'LAPIS', price: '$9.80', color: 'blue', bar: 22 },
    30: { name: 'REDSTONE', price: '$5.40', color: 'red', bar: 15 },
  };

  return (
    <div className="rounded-xl border border-gray-700 overflow-hidden bg-gray-900">
      {/* Window chrome */}
      <div className="flex items-center gap-1.5 px-4 py-2.5 bg-gray-800 border-b border-gray-700">
        <span className="w-3 h-3 rounded-full bg-red-500/70" />
        <span className="w-3 h-3 rounded-full bg-amber-500/70" />
        <span className="w-3 h-3 rounded-full bg-green-500/70" />
        <span className="ml-3 text-xs text-gray-400 font-mono">Auto-Tune Market</span>
      </div>
      {/* Shop grid */}
      <div className="p-4">
        <div className="grid grid-cols-9 gap-1">
          {SLOTS.map((slot) => {
            const item = ITEMS[slot];
            if (!item) {
              return (
                <div
                  key={slot}
                  className="aspect-square rounded bg-gray-800/60 border border-gray-700/40"
                />
              );
            }
            const colorMap: Record<string, string> = {
              cyan: 'border-cyan-500/60 bg-cyan-950/40',
              green: 'border-green-500/60 bg-green-950/40',
              gray: 'border-gray-500/60 bg-gray-800/40',
              amber: 'border-amber-500/60 bg-amber-950/40',
              orange: 'border-orange-500/60 bg-orange-950/40',
              red: 'border-red-500/60 bg-red-950/40',
              rose: 'border-rose-500/60 bg-rose-950/40',
              blue: 'border-blue-500/60 bg-blue-950/40',
            };
            const textColorMap: Record<string, string> = {
              cyan: 'text-cyan-300',
              green: 'text-green-300',
              gray: 'text-gray-300',
              amber: 'text-amber-300',
              orange: 'text-orange-300',
              red: 'text-red-300',
              rose: 'text-rose-300',
              blue: 'text-blue-300',
            };
            return (
              <div
                key={slot}
                className={`aspect-square rounded border flex flex-col items-center justify-center p-0.5 ${colorMap[item.color]}`}
              >
                <span className={`text-[7px] font-bold font-mono leading-none ${textColorMap[item.color]}`}>
                  {item.name.slice(0, 6)}
                </span>
                <span className={`text-[7px] font-mono mt-0.5 ${item.color === 'gray' ? 'text-gray-300' : `text-${item.color}-200`}`}>
                  {item.price}
                </span>
                {/* Mini spread bar */}
                <div className="w-full h-0.5 bg-gray-700 rounded mt-0.5 overflow-hidden">
                  <div
                    className={`h-full ${item.color === 'gray' ? 'bg-gray-400' : `bg-${item.color}-400`} rounded`}
                    style={{ width: `${item.bar}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
        {/* Footer */}
        <div className="mt-3 flex items-center justify-between text-xs text-gray-500">
          <span>Page 1 of 4</span>
          <div className="flex gap-3">
            <span className="text-emerald-400">▲ DIAMOND +2.4%</span>
            <span>SPD 0.77%</span>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Web Dashboard Mockup ──────────────────────────────────────────────────────

function DashboardMockup() {
  const bars = [
    { label: 'DIAMOND', buy: 68, sell: 66, trend: '+2.4%', up: true },
    { label: 'EMERALD', buy: 152, sell: 145, trend: '-1.1%', up: false },
    { label: 'IRON', buy: 11.2, sell: 10.6, trend: '+0.3%', up: true },
    { label: 'GOLD', buy: 22.8, sell: 21.4, trend: '-0.8%', up: false },
    { label: 'COAL', buy: 3.2, sell: 3.1, trend: '+0.1%', up: true },
  ];

  return (
    <div className="rounded-xl border border-gray-700 overflow-hidden bg-gray-900">
      {/* Window chrome */}
      <div className="flex items-center gap-1.5 px-4 py-2.5 bg-gray-800 border-b border-gray-700">
        <span className="w-3 h-3 rounded-full bg-red-500/70" />
        <span className="w-3 h-3 rounded-full bg-amber-500/70" />
        <span className="w-3 h-3 rounded-full bg-green-500/70" />
        <span className="ml-3 text-xs text-gray-400 font-mono">Auto-Tune Dashboard — your-server.net</span>
      </div>
      <div className="p-4 space-y-3">
        {/* Stat row */}
        <div className="grid grid-cols-4 gap-2">
          {[
            { label: 'GDP', value: '847K', color: 'text-emerald-400' },
            { label: 'D/G', value: '1.76×', color: 'text-emerald-400' },
            { label: 'Buy %', value: '73%', color: 'text-sky-400' },
            { label: 'Vol', value: '0.007', color: 'text-emerald-400' },
          ].map((stat) => (
            <div key={stat.label} className="rounded bg-gray-800/60 border border-gray-700/40 p-2 text-center">
              <div className={`text-sm font-bold font-mono ${stat.color}`}>{stat.value}</div>
              <div className="text-[9px] text-gray-500 uppercase">{stat.label}</div>
            </div>
          ))}
        </div>
        {/* Market movers */}
        <div className="rounded bg-gray-800/40 border border-gray-700/40 p-3">
          <div className="text-[9px] text-emerald-400 uppercase tracking-wider mb-2">Top Movers (24h)</div>
          <div className="space-y-1.5">
            {bars.map((bar) => (
              <div key={bar.label} className="flex items-center gap-2">
                <span className="text-[9px] font-mono text-gray-400 w-14 shrink-0">{bar.label}</span>
                <span className="text-[9px] font-mono text-gray-600 w-8">B${bar.buy.toFixed(bar.buy < 10 ? 2 : 0)}</span>
                <div className="flex-1 h-1.5 bg-gray-700 rounded overflow-hidden">
                  <div
                    className={`h-full rounded ${bar.up ? 'bg-emerald-500' : 'bg-rose-500'}`}
                    style={{ width: `${bar.buy * 2}%` }}
                  />
                </div>
                <span className={`text-[9px] font-mono ${bar.up ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {bar.trend}
                </span>
              </div>
            ))}
          </div>
        </div>
        {/* Volume sparkline (ASCII) */}
        <div className="rounded bg-gray-800/40 border border-gray-700/40 p-3">
          <div className="text-[9px] text-gray-500 uppercase mb-1.5">Volume — 7 day</div>
          <div className="font-mono text-[9px] leading-none text-emerald-500/70">
            {['▁', '▂', '▃', '▅', '▆', '▇', '█', '▇', '▆', '▅', '▃', '▂', '▁', '▂'].join(' ')}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Main Component ────────────────────────────────────────────────────────────

const screenshots = [
  {
    Icon: FileText,
    title: 'Simple YAML Configuration',
    description: 'One config file. Sensible defaults. The most important settings are at the top with clear comments. No external dependencies to configure — drop the JAR and it works.',
    mockup: <ConfigMockup />,
    tag: 'Easy Setup',
  },
  {
    Icon: Monitor,
    title: 'Built-in Web Dashboard',
    description: 'Every Auto-Tune install comes with a live web dashboard. Players check prices, trends, and market health without any extra setup. No separate web server required.',
    mockup: <DashboardMockup />,
    tag: 'Zero Extra Setup',
  },
  {
    Icon: BarChart2,
    title: 'In-Game Market Browser',
    description: 'Players browse the full economy directly in Minecraft. Search, filter by section, see live buy/sell prices and 24h trends. Prices update every 5 minutes automatically.',
    mockup: <ShopGuiMockup />,
    tag: 'For Your Players',
  },
];

export function InstallScreenshots() {
  return (
    <section className="py-16 border-t border-gray-800/60">
      <div className="mb-10 text-center">
        <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">What You Get</p>
        <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
          Three screens. Zero configuration required.
        </h2>
        <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
          Auto-Tune comes with a complete UI out of the box — config file, in-game shop, and web dashboard.
          Everything works immediately after install.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {screenshots.map(({ Icon, title, description, mockup, tag }) => (
          <div key={title} className="flex flex-col">
            {/* Tag */}
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xs text-emerald-400 bg-emerald-950/40 border border-emerald-800/40 px-2 py-0.5 rounded-full font-medium">
                {tag}
              </span>
            </div>

            {/* Mockup */}
            <div className="flex-1 mb-4">
              {mockup}
            </div>

            {/* Description */}
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <Icon className="w-4 h-4 text-gray-500 shrink-0" />
                <h3 className="text-sm font-semibold text-white">{title}</h3>
              </div>
              <p className="text-xs text-gray-500 leading-relaxed">{description}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
