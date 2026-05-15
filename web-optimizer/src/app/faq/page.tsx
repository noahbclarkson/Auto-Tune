'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { Metadata } from 'next';
import {
  Search, ChevronDown, ChevronRight, MessageSquare, ExternalLink,
  BookOpen, TrendingUp, DollarSign, Settings, Zap, BarChart2, Server, AlertTriangle, LifeBuoy
} from 'lucide-react';

const CATEGORIES = [
  { id: 'general', label: 'General', icon: BookOpen },
  { id: 'pricing', label: 'Pricing', icon: DollarSign },
  { id: 'distressed', label: 'Economy Distressed', icon: AlertTriangle },
  { id: 'configuration', label: 'Configuration', icon: Settings },
  { id: 'events', label: 'Market Events', icon: Zap },
  { id: 'dashboard', label: 'Dashboard', icon: BarChart2 },
  { id: 'cross-server', label: 'Cross-Server', icon: Server },
  { id: 'performance', label: 'Performance', icon: TrendingUp },
  { id: 'troubleshooting', label: 'Troubleshooting', icon: LifeBuoy },
  { id: 'still-stuck', label: 'Still Stuck', icon: MessageSquare },
] as const;

type CategoryId = typeof CATEGORIES[number]['id'];

interface FaqItem {
  q: string;
  a: React.ReactNode;
}

interface FaqSection {
  id: CategoryId;
  items: FaqItem[];
}

const FAQ_CONTENT: FaqSection[] = [
  {
    id: 'general',
    items: [
      {
        q: 'What is Auto-Tune?',
        a: 'Auto-Tune is a Minecraft server plugin that replaces static item shops with a dynamic supply-and-demand economy. Prices adjust automatically based on what players are actually buying and selling — no admin price editing required.',
      },
      {
        q: 'What servers does it support?',
        a: 'Paper 1.21.4. Other versions may work but are not tested. Requires Java 21.',
      },
      {
        q: 'Does it work with Vault?',
        a: 'Yes. Auto-Tune uses Vault for economy integration (player balances, transactions). Install Vault first.',
      },
      {
        q: 'Does it replace EssentialsX shops?',
        a: 'Yes — and that\'s the point. Instead of a fixed-price shop grid, players can buy and sell any item any time at market prices. The /shop and /sell commands replace your existing shop plugin.',
      },
      {
        q: 'Does it work alongside existing economy plugins?',
        a: 'Auto-Tune manages item prices and the shop. It uses Vault for money. If your current economy plugin also uses Vault, they may conflict. Best results with Auto-Tune as the sole economy manager.',
      },
    ],
  },
  {
    id: 'pricing',
    items: [
      {
        q: 'Prices aren\'t doing what I expected. Help.',
        a: (
          <span>
            <strong>"I set Diamond to $500 but it settled at $200."</strong> — This is normal. Base prices are <em>starting points</em>, not targets. Minecraft economies are naturally seller-heavy (more players gather than spend). Prices settle where supply meets demand, typically 40–70% below your base price.{' '}
            <br /><br />
            <strong>"Prices barely moved since I installed."</strong> — Check your player volume. Auto-Tune needs active trading to update prices. A server with 3 players trading once a day won&apos;t have dynamic prices.{' '}
            <br /><br />
            <strong>"Prices changed too fast!"</strong> — Lower max-price-change-percent (try 1.0 or 0.5). Also check base-spread — wide spreads dampen price movement.{' '}
            <br /><br />
            <strong>"All my prices dropped 60% overnight."</strong> — You likely had a supply glut: a single player sold a huge volume of one item. The engine responded by lowering prices. This is correct behavior. Check <code>/at admin health</code> to see what happened.
          </span>
        ),
      },
    ],
  },
  {
    id: 'distressed',
    items: [
      {
        q: 'Debt is piling up. Is this normal?',
        a: (
          <span>
            First, check your D/G ratio: <code>/at admin health</code>. A healthy economy with active loans typically runs 3–8× D/G.
            <br /><br />
            If D/G is climbing rapidly:
            <br />1. <strong>Check if the circuit breaker fired</strong> — look for a yellow/red message about TIER 2 or TIER 3. The circuit breaker pauses interest when debt gets too high.
            <br />2. <strong>Check your archetype mix</strong> — if you have mostly Farmers and few active buyers, sellers accumulate debt because no one is buying their items.
            <br />3. <strong>Check your base prices</strong> — if items are priced too high, players can&apos;t afford to buy, creating a downward spiral.
            <br /><br />
            <strong>Recovery options:</strong>
            <br />— <code>/at admin prices reset &lt;item&gt;</code> — reset a specific item to its base price
            <br />— <code>/at admin prices reset all</code> — reset all prices (use with caution — notifies players)
            <br />— <strong>Add MarketMakers or GuildBuyers</strong> if your player economy has too many farmers
          </span>
        ),
      },
      {
        q: 'The circuit breaker keeps firing (TIER 3)',
        a: (
          <span>
            TIER 3 means debt-to-GDP exceeded 30×. Interest is paused.
            <br /><br />
            <strong>Why it fired:</strong> Multiple players or guilds accumulated debt faster than the economy could grow, or a mass player exodus left debts unpaid.
            <br /><br />
            <strong>What happens:</strong> Interest is paused until D/G drops below 15× (50% hysteresis band with default settings). New loans can still be taken but interest won&apos;t compound on existing debt.
            <br /><br />
            <strong>Recovery:</strong>
            <br />— <code>/at admin recovery start</code> — run this early (day 3–7) for best results. By day 10+ the circuit has contained the problem and recovery has diminishing effect.
            <br />— Let the economy grow naturally — GDP increases, D/G decreases
            <br />— The 60% price floor helps prevent cascading defaults
            <br /><br />
            <strong>Long-run warning (180+ days):</strong> Even with the circuit breaker, the economy does not fully stabilize past day 120. D/G can escalate from ~16× at day 90 to 40×+ by day 180. See the 60-day fix analysis for the full analysis.
            <br /><br />
            <strong>Prevention for next time:</strong> Keep loans.counter-cyclical: true (default). Use the 60% price floor on Diamond and Gold. Monitor D/G with /at admin health weekly. If D/G exceeds 25×, act proactively with /at admin recovery.
          </span>
        ),
      },
      {
        q: 'I want to disable the loan system entirely',
        a: (
          <span>
            Add to your config.yml:{' '}
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>loans:{"\n"}{"  "}enabled: false</code>
            </pre>
            All existing debts remain. No new loans can be taken. Run <code>/at admin health</code> to confirm the loan section shows 0 active loans.
          </span>
        ),
      },
    ],
  },
  {
    id: 'configuration',
    items: [
      {
        q: 'What config should I start with?',
        a: (
          <span>
            Use the Admin Quickstart for the 5 key decisions. For most SMP servers, the defaults are solid — just enable the 60% price floor on Diamond-type items:
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>/at admin item floor DIAMOND 300{"\n"}/at admin item floor GOLD_INGOT 150</code>
            </pre>
          </span>
        ),
      },
      {
        q: 'How do I set prices for items that don\'t have base prices?',
        a: (
          <span>
            Items must have a base_price in shops.yml to be tracked. Add items manually:
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>shops:{"\n"}{"  "}items:{"\n"}{"    "}DRAGON_HEAD:{"\n"}{"      "}base_price: 5000</code>
            </pre>
            Or use the admin command: <code>/at admin item baseprice DRAGON_HEAD 5000</code>
            <br /><br />
            Items without a base price can&apos;t be bought or sold through Auto-Tune&apos;s shop.
          </span>
        ),
      },
      {
        q: 'How do I add custom items (from other plugins)?',
        a: 'Same as above — add them to shops.yml with a base_price. Auto-Tune treats all items the same regardless of which plugin adds them to Minecraft.',
      },
      {
        q: 'Can I import prices from my existing shop?',
        a: (
          <span>
            Use the bulk export/import commands:
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>/at admin prices export my_prices.csv{"\n"}# Edit the CSV{"\n"}/at admin prices import my_prices.csv</code>
            </pre>
            The CSV format: <code>material,base_price,buy_enabled,sell_enabled</code>
          </span>
        ),
      },
    ],
  },
  {
    id: 'events',
    items: [
      {
        q: 'What are market events?',
        a: 'Temporary price amplifiers/suppressors. A DEMAND_SURGE on Diamond makes Diamond prices rise faster while the event is active. Events are great for server events (Christmas, seasonal content, in-game holidays). See the Server Admin Guide for full details.',
      },
      {
        q: 'My scheduled event didn\'t fire',
        a: (
          <span>
            Check: <code>/at event templates</code>. Is your template listed?
            <br />Check: <code>/at event list</code>. Is the event status SCHEDULED or ACTIVE?
            <br /><br />
            <strong>Common reasons events don&apos;t fire:</strong>
            <br />— Event start time is in the past (scheduled for yesterday)
            <br />— Event duration is too short (it ended before the market tick)
            <br />— The material pattern doesn&apos;t match any items (GOLD_* won&apos;t match GOLD_INGOT if formatted wrong)
          </span>
        ),
      },
      {
        q: 'Can players see active events?',
        a: 'Yes — if boss-bar.enabled: true (default), a boss bar appears when an event activates. Players can also check /at events to see active events.',
      },
    ],
  },
  {
    id: 'dashboard',
    items: [
      {
        q: 'The bundled dashboard (port 8989) shows no data',
        a: (
          <span>
            <strong>1.</strong> Is the web server enabled? Check config: <code>web-server.enabled: true</code>
            <br /><strong>2.</strong> Is port 8989 accessible? (check your firewall)
            <br /><strong>3.</strong> Has anyone traded yet? The dashboard shows data after the first trades are recorded.
            <br /><strong>4.</strong> Check <code>/at admin health</code> — does it show data? If not, the database isn&apos;t recording trades.
          </span>
        ),
      },
      {
        q: 'How do I embed the dashboard in a website?',
        a: (
          <span>
            The dashboard is a Next.js app bundled in the plugin JAR. It&apos;s designed to be served by the plugin&apos;s built-in Javalin web server, not embedded externally.
            <br /><br />
            For external access, proxy port 8989 through nginx/Caddy:
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>location /economy/ {"{"} {"\n"}{"  "}proxy_pass http://127.0.0.1:8989/;{"\n"}{"}"}</code>
            </pre>
          </span>
        ),
      },
      {
        q: 'Can I use the dashboard without in-game access?',
        a: 'Yes — the dashboard is a standalone web app. Any admin with the server URL can monitor the economy from a browser. Password-protect with a reverse proxy if needed.',
      },
    ],
  },
  {
    id: 'cross-server',
    items: [
      {
        q: 'How does cross-server pricing work?',
        a: 'Servers that opt in submit ratio matrices to the API server (not prices, not player data). The solver computes "true prices" — the set of prices consistent with all servers\' ratios. New servers can seed their prices from this consensus. See Economy Concepts for details.',
      },
      {
        q: 'I\'m getting "API unreachable" on the web dashboard',
        a: 'The bundled dashboard connects to http://localhost:8080 by default for API data. Update api-server.url in config.yml to point to your deployed API server.',
      },
      {
        q: 'How do I get an API key?',
        a: 'Contact the Auto-Tune maintainers. Server keys are issued manually to prevent Sybil attacks. Once you have a key, register at /servers on the web-optimizer site.',
      },
    ],
  },
  {
    id: 'performance',
    items: [
      {
        q: 'Does Auto-Tune lag my server?',
        a: 'Auto-Tune\'s market engine runs every 5 minutes (configurable: market.tick-interval-minutes). The tick processes all trades in the window, updates prices, and runs loan interest. On a server with <500 players and <10K items tracked, the tick takes <50ms. The web server (Javalin on port 8989) runs on a separate thread and doesn\'t affect game tick performance.',
      },
      {
        q: 'My database is huge',
        a: (
          <span>
            Enable the cleanup manager in config:
            <pre className="mt-2 p-3 bg-gray-900 rounded-lg text-sm text-gray-200 overflow-x-auto">
              <code>cleanup:{"\n"}{"  "}enabled: true{"\n"}{"  "}interval-hours: 24</code>
            </pre>
            This prunes old transactions (14d retention), market history (7d), economy snapshots (30d), and auction data (30d). Run <code>/at admin cleanup</code> to trigger a cleanup immediately.
          </span>
        ),
      },
      {
        q: 'Does it work with SQLite or MySQL?',
        a: 'Both. SQLite is the default (zero config). For production servers with >50 players, MariaDB/MySQL is recommended for performance.',
      },
    ],
  },
  {
    id: 'troubleshooting',
    items: [
      {
        q: '/shop says "No items available"',
        a: (
          <span>
            <strong>1.</strong> Check shops.yml has items with base_price set
            <br /><strong>2.</strong> Check items have buy: true or sell: true enabled
            <br /><strong>3.</strong> Check ShopManager loaded correctly: <code>/at admin debug</code> shows loaded item count
          </span>
        ),
      },
      {
        q: 'Players can\'t sell items',
        a: (
          <span>
            — Does the item have sell: true in shops.yml?
            <br />— Does the player actually have the item in their inventory?
            <br />— Is the sell price above the minimum transaction value (economy.min-sell-value, default $0.01)?
          </span>
        ),
      },
      {
        q: '/loans says I don\'t have permission',
        a: 'Players need autotune.loans permission. Give it with: <code>/perm user &lt;player&gt; add autotune.loans</code> — or add to your permissions plugin&apos;s groups.',
      },
      {
        q: 'The economy feels "stuck" — prices don\'t move',
        a: (
          <span>
            Likely causes:
            <br />— No recent trades (increase market.tick-interval-minutes or wait)
            <br />— max-price-change-percent too low
            <br />— Price is at floor or ceiling (check <code>/at admin item info &lt;item&gt;</code>)
            <br />— Everyone is hoarding (check buy ratio — if &gt;85%, sellers are rare)
          </span>
        ),
      },
    ],
  },
  {
    id: 'still-stuck',
    items: [
      {
        q: 'Still need help?',
        a: (
          <span>
            <strong>Discord:</strong> Ask in #autotune — we respond within a day or two.
            <br /><strong>GitHub Issues:</strong> Bug reports welcome, feature requests as well.
            <br /><br />
            When asking for help, include:
            <br />— <code>/at admin health</code> output
            <br />— Your config.yml (remove sensitive keys like database credentials)
            <br />— Steps to reproduce the issue
            <br />— Server specs (player count, Java version, Paper version)
          </span>
        ),
      },
    ],
  },
];

export default function FaqPage() {
  const [query, setQuery] = useState('');
  const [expandedCategory, setExpandedCategory] = useState<CategoryId>('general');
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  const allItems = useMemo(() => {
    return FAQ_CONTENT.flatMap((cat) =>
      cat.items.map((item) => ({ ...item, categoryId: cat.id }))
    );
  }, []);

  const filteredItems = useMemo(() => {
    if (!query.trim()) return null;
    const q = query.toLowerCase();
    return allItems.filter(
      (item) =>
        item.q.toLowerCase().includes(q) ||
        (typeof item.a === 'string' && item.a.toLowerCase().includes(q)) ||
        (typeof item.a !== 'string' && JSON.stringify(item.a).toLowerCase().includes(q))
    );
  }, [query, allItems]);

  const toggleItem = (categoryId: CategoryId, question: string) => {
    const key = `${categoryId}::${question}`;
    setExpandedItems((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const isItemOpen = (categoryId: CategoryId, question: string) => {
    return expandedItems.has(`${categoryId}::${question}`);
  };

  const toggleCategory = (id: CategoryId) => {
    setExpandedCategory((prev) => (prev === id ? 'general' : id));
  };

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="border-b border-gray-800/50 bg-gray-900/50">
        <div className="max-w-5xl mx-auto px-4 py-16 sm:px-6 lg:px-8">
          <div className="text-center">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 text-xs font-medium tracking-widest uppercase mb-4">
              <MessageSquare size={12} />
              Frequently Asked Questions
            </div>
            <h1 className="text-3xl sm:text-4xl font-bold text-white mb-4">
              Common questions, answered directly
            </h1>
            <p className="text-gray-400 max-w-xl mx-auto mb-8">
              Everything you need to know about running Auto-Tune. Can&apos;t find your question? Ask in Discord.
            </p>
            {/* Search */}
            <div className="relative max-w-md mx-auto">
              <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500" />
              <input
                type="text"
                placeholder="Search questions..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-3 bg-gray-800 border border-gray-700 rounded-xl text-gray-200 placeholder-gray-500 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500/50 transition-colors text-sm"
              />
              {query && (
                <button
                  onClick={() => setQuery('')}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300 text-xs"
                >
                  Clear
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 py-10 sm:px-6 lg:px-8">
        {filteredItems ? (
          /* Search results */
          <div>
            <div className="mb-6">
              <h2 className="text-sm font-medium text-gray-400">
                {filteredItems.length === 0
                  ? 'No results'
                  : `${filteredItems.length} result${filteredItems.length === 1 ? '' : 's'}`}{' '}
                for &ldquo;{query}&rdquo;
              </h2>
            </div>
            {filteredItems.length === 0 ? (
              <div className="text-center py-16 text-gray-500">
                <MessageSquare size={32} className="mx-auto mb-3 opacity-30" />
                <p className="text-sm">No questions match your search.</p>
                <p className="text-xs mt-1">Try different keywords or browse by category below.</p>
                <button
                  onClick={() => setQuery('')}
                  className="mt-4 text-emerald-400 hover:text-emerald-300 text-sm font-medium"
                >
                  Clear search
                </button>
              </div>
            ) : (
              <div className="space-y-3">
                {filteredItems.map((item, i) => (
                  <SearchResultCard
                    key={i}
                    question={item.q}
                    answer={item.a}
                    categoryId={item.categoryId}
                    isOpen={isItemOpen(item.categoryId as CategoryId, item.q)}
                    onToggle={() => toggleItem(item.categoryId as CategoryId, item.q)}
                  />
                ))}
              </div>
            )}
            {filteredItems.length > 0 && (
              <button
                onClick={() => setQuery('')}
                className="mt-6 text-sm text-gray-500 hover:text-gray-300"
              >
                ← Clear search and browse all
              </button>
            )}
          </div>
        ) : (
          /* Category list */
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            {/* Category sidebar */}
            <div className="lg:col-span-1">
              <div className="sticky top-6">
                <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-widest mb-3">
                  Categories
                </h3>
                <nav className="space-y-1">
                  {CATEGORIES.map((cat) => {
                    const Icon = cat.icon;
                    const isActive = expandedCategory === cat.id;
                    return (
                      <button
                        key={cat.id}
                        onClick={() => toggleCategory(cat.id)}
                        className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                          isActive
                            ? 'bg-emerald-500/10 text-emerald-400'
                            : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/50'
                        }`}
                      >
                        <Icon size={14} className={isActive ? 'text-emerald-400' : 'text-gray-600'} />
                        {cat.label}
                        {isActive ? (
                          <ChevronDown size={12} className="ml-auto" />
                        ) : (
                          <ChevronRight size={12} className="ml-auto opacity-50" />
                        )}
                      </button>
                    );
                  })}
                </nav>
              </div>
            </div>

            {/* Q&A list */}
            <div className="lg:col-span-3 space-y-3">
              {FAQ_CONTENT.find((c) => c.id === expandedCategory)?.items.map((item, i) => (
                <FaqAccordion
                  key={i}
                  question={item.q}
                  answer={item.a}
                  isOpen={isItemOpen(expandedCategory, item.q)}
                  onToggle={() => toggleItem(expandedCategory, item.q)}
                />
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Footer CTA */}
      <div className="border-t border-gray-800/50 mt-12">
        <div className="max-w-5xl mx-auto px-4 py-10 sm:px-6 lg:px-8 text-center">
          <p className="text-gray-500 text-sm mb-3">Still have questions?</p>
          <a
            href="https://discord.gg/autotune"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-emerald-400 hover:text-emerald-300 font-medium text-sm"
          >
            Ask in Discord
            <ExternalLink size={14} />
          </a>
          <span className="text-gray-700 mx-2">·</span>
          <a
            href="https://github.com/noahbclarkson/Auto-Tune/issues"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-gray-400 hover:text-gray-300 text-sm"
          >
            Open a GitHub Issue
            <ExternalLink size={14} />
          </a>
        </div>
      </div>
    </div>
  );
}

function FaqAccordion({
  question,
  answer,
  isOpen,
  onToggle,
}: {
  question: string;
  answer: React.ReactNode;
  isOpen: boolean;
  onToggle: () => void;
}) {
  return (
    <div
      className={`border rounded-xl overflow-hidden transition-colors ${
        isOpen
          ? 'border-emerald-500/30 bg-emerald-500/5'
          : 'border-gray-800/50 bg-gray-900/50 hover:border-gray-700/50'
      }`}
    >
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-3 px-5 py-4 text-left"
      >
        <span
          className={`flex-shrink-0 transition-transform ${isOpen ? 'rotate-90 text-emerald-400' : 'text-gray-600'}`}
        >
          <ChevronRight size={14} />
        </span>
        <span className={`font-medium text-sm ${isOpen ? 'text-emerald-300' : 'text-gray-200'}`}>
          {question}
        </span>
      </button>
      {isOpen && (
        <div className="px-5 pb-5 pl-12 text-sm text-gray-400 leading-relaxed">
          {typeof answer === 'string' ? <p>{answer}</p> : answer}
        </div>
      )}
    </div>
  );
}

function SearchResultCard({
  question,
  answer,
  categoryId,
  isOpen,
  onToggle,
}: {
  question: string;
  answer: React.ReactNode;
  categoryId: string;
  isOpen: boolean;
  onToggle: () => void;
}) {
  const cat = CATEGORIES.find((c) => c.id === categoryId);
  return (
    <div className="border border-gray-800/50 bg-gray-900/50 rounded-xl overflow-hidden">
      <div className="flex items-center gap-2 px-4 pt-3">
        {cat && (
          <span className="text-xs text-gray-500 bg-gray-800 px-2 py-0.5 rounded">
            {cat.label}
          </span>
        )}
      </div>
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-3 px-4 py-3 text-left"
      >
        <span
          className={`flex-shrink-0 transition-transform ${isOpen ? 'rotate-90 text-emerald-400' : 'text-gray-600'}`}
        >
          <ChevronRight size={14} />
        </span>
        <span className={`font-medium text-sm ${isOpen ? 'text-emerald-300' : 'text-gray-200'}`}>
          {question}
        </span>
      </button>
      {isOpen && (
        <div className="px-4 pb-4 pl-11 text-sm text-gray-400 leading-relaxed">
          {typeof answer === 'string' ? <p>{answer}</p> : answer}
        </div>
      )}
    </div>
  );
}