import { Metadata } from 'next';
import Link from 'next/link';
import { Download, Server, FileText, Zap, CheckCircle, ExternalLink, BookOpen, AlertTriangle, Users, TrendingUp, Shield, ChevronDown, Monitor } from 'lucide-react';
import { InstallScreenshots } from '@/components/install/screenshot-mockups';

export const metadata: Metadata = {
  title: 'Install Guide | Auto-Tune',
  description:
    'Get Auto-Tune running on your Minecraft Paper server in under 10 minutes. Prerequisites, install steps, basic configuration, and first verification.',
  openGraph: {
    title: 'Install Guide | Auto-Tune',
    description: 'Get Auto-Tune running on your Minecraft Paper server in under 10 minutes.',
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'Auto-Tune Install Guide' }],
  },
  twitter: {
    card: 'summary_large_image',
    images: ['/og-image.png'],
  },
};

const PREREQUISITES = [
  { label: 'Paper 1.21.4+', detail: 'Auto-Tune targets Paper 1.21.4. Spigot may work; report issues on GitHub.' },
  { label: 'Java 21', detail: 'JDK 21 is required to run the plugin. Most Paper startup scripts use it by default.' },
  { label: 'Vault', detail: 'Required for economy integration (buy/sell processing, loans). Install any Vault-compatible economy plugin first.' },
  { label: 'A Vault-compatible economy', detail: 'EssentialsX, CMIE, or similar. Auto-Tune hooks into Vault for all money operations.' },
];

const STEPS = [
  {
    num: '01',
    icon: Download,
    title: 'Download the JAR',
    command: null,
    detail: (
      <>
        <p className="mb-3">Download the latest <code className="text-emerald-400 font-mono">Auto-Tune.jar</code> from the GitHub releases page.</p>
        <a
          href="https://github.com/noahbclarkson/Auto-Tune/releases"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 text-sm text-emerald-400 hover:text-emerald-300 transition-colors"
        >
          <ExternalLink className="w-3.5 h-3.5" />
          GitHub Releases
        </a>
      </>
    ),
  },
  {
    num: '02',
    icon: Server,
    title: 'Drop the JAR into your plugins folder',
    command: null,
    detail: (
      <>
        <p className="mb-3">Place the JAR in your server&apos;s <code className="text-sky-300 font-mono">/plugins/</code> directory. Create the folder if it doesn&apos;t exist.</p>
        <div className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-2.5 text-sm text-gray-400 font-mono">
          ~/.mc/server/plugins/Auto-Tune.jar
        </div>
      </>
    ),
  },
  {
    num: '03',
    icon: Zap,
    title: 'Start (or restart) your server',
    command: 'restart',
    detail: (
      <p className="mb-3">
        Auto-Tune generates its default <code className="text-sky-300 font-mono">config.yml</code> and database on first run.
        Restart the server or run <code className="text-sky-300 font-mono">/reload confirm</code> if using Paper.
      </p>
    ),
  },
  {
    num: '04',
    icon: FileText,
    title: 'Configure basic settings',
    command: null,
    detail: (
      <>
        <p className="mb-3">
          Open <code className="text-sky-300 font-mono">/plugins/Auto-Tune/config.yml</code> in your server files.
          The defaults are reasonable for most servers — here are the key values to review:
        </p>
        <div className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-xs font-mono text-gray-300 mb-3">
          <div className="text-gray-500 mb-2"># config.yml — key settings</div>
          <div><span className="text-sky-400">economy:</span></div>
          <div className="pl-4"><span className="text-gray-500"># Starting prices for items (JSON block)</span></div>
          <div className="pl-4">base-price-multiplier: <span className="text-amber-400">1.0</span>  <span className="text-gray-600"># Scale all prices up/down</span></div>
          <div className="pl-4">min-buy-price: <span className="text-amber-400">0.01</span></div>
          <div className="pl-4">max-buy-price: <span className="text-amber-400">1000000</span></div>
          <div className="mt-2"><span className="text-sky-400">loans:</span></div>
          <div className="pl-4">enabled: <span className="text-amber-400">true</span></div>
          <div className="pl-4">max-interest-rate: <span className="text-amber-400">0.10</span>  <span className="text-gray-600"># 10% per day</span></div>
          <div className="pl-4">debt-gdp-circuit-breaker: <span className="text-amber-400">10.0</span>  <span className="text-gray-600"># Pause loans at 10× GDP</span></div>
          <div className="mt-2"><span className="text-sky-400">autosell:</span></div>
          <div className="pl-4">enabled: <span className="text-amber-400">true</span></div>
          <div className="pl-4">sell-all-on-login: <span className="text-amber-400">false</span></div>
          <div className="mt-2"><span className="text-sky-400">web-server:</span></div>
          <div className="pl-4">enabled: <span className="text-amber-400">true</span></div>
          <div className="pl-4">port: <span className="text-amber-400">8989</span></div>
        </div>
        <p className="text-xs text-gray-500">
          Full config reference:{' '}
          <a href="https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/docs/CONFIG_GUIDE.md" target="_blank" rel="noopener noreferrer" className="text-emerald-400 hover:underline">
            docs/CONFIG_GUIDE.md
          </a>
        </p>
      </>
    ),
  },
  {
    num: '05',
    icon: CheckCircle,
    title: 'Verify the installation',
    command: 'in-game',
    detail: (
      <p>
        Join the server and run <code className="text-sky-300 font-mono">/at help</code> in chat.
        Try <code className="text-sky-300 font-mono">/shop</code> to browse prices, or{' '}
        <code className="text-sky-300 font-mono">/at admin health</code> to see the economy health dashboard.
        The web dashboard is available at <code className="text-sky-300 font-mono">http://your-server:8989</code>.
      </p>
    ),
  },
];

function StepCard({
  num,
  icon: Icon,
  title,
  command,
  detail,
}: (typeof STEPS)[number]) {
  return (
    <div className="flex gap-5">
      <div className="shrink-0 flex flex-col items-center">
        <div className="w-14 h-14 rounded-xl bg-gray-900/80 border border-gray-800 flex flex-col items-center justify-center">
          <Icon className="w-5 h-5 text-gray-400" />
        </div>
        {command && (
          <div className="mt-2 w-14 text-center">
            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-mono bg-gray-800 border border-gray-700 text-gray-400">
              {command}
            </span>
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 pb-8">
        <h3 className="text-lg font-semibold text-white mb-2">{title}</h3>
        {detail}
      </div>
    </div>
  );
}

export default function InstallPage() {
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      {/* Page header */}
      <div className="mb-10">
        <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Setup</p>
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-4">
          Install Auto-Tune
        </h1>
        <p className="text-gray-400 text-sm leading-relaxed max-w-xl">
          Get Auto-Tune running on your Paper 1.21.4+ server in under 10 minutes.
          The plugin is self-contained — drop in the JAR, start the server, done.
        </p>
      </div>

      {/* Why Auto-Tune — comparison table */}
      <section className="mb-14">
        <div className="mb-6">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Why Auto-Tune</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
            Better economies, built-in
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed max-w-2xl">
            Most Minecraft servers run static pricing — the same diamond price on day one as on day 100. Auto-Tune replaces that with a real market that responds to what players actually do.
          </p>
        </div>

        {/* Server type targeting */}
        <div className="grid sm:grid-cols-3 gap-4 mb-8">
          {[
            {
              icon: Users,
              title: 'Survival SMPs',
              description: 'The sweet spot. Players gather resources, trade, and compete. Auto-Tune gives every item a living price that reflects scarcity and demand.',
              badge: 'Most common use',
              badgeColor: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
            },
            {
              icon: TrendingUp,
              title: 'Economy Servers',
              description: 'Where the economy IS the game. Auto-Tune turns price discovery into gameplay — players research, speculate, and profit from mispriced items.',
              badge: null,
              badgeColor: '',
            },
            {
              icon: Shield,
              title: 'PvP / Faction Servers',
              description: 'Resources have context-sensitive value. Scarcity near spawn, abundance in the wild. Auto-Tune captures that without manual price tables.',
              badge: null,
              badgeColor: '',
            },
          ].map(({ icon: Icon, title, description, badge, badgeColor }) => (
            <div key={title} className="rounded-xl border border-gray-800 bg-gray-900/60 p-5 flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <div className="w-9 h-9 rounded-lg bg-gray-800 border border-gray-700 flex items-center justify-center">
                  <Icon className="w-4 h-4 text-gray-400" />
                </div>
                {badge && (
                  <span className={`text-xs px-2 py-0.5 rounded border font-medium shrink-0 ${badgeColor}`}>
                    {badge}
                  </span>
                )}
              </div>
              <div>
                <p className="text-sm font-semibold text-white mb-1">{title}</p>
                <p className="text-xs text-gray-500 leading-relaxed">{description}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Comparison table */}
        <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800/60 bg-gray-900/40">
                  <th className="py-3.5 px-5 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider w-2/5">Feature</th>
                  <th className="py-3.5 px-4 text-center text-xs font-semibold text-gray-500 w-1/5">Static Pricing</th>
                  <th className="py-3.5 px-4 text-center text-xs font-semibold text-emerald-400 w-1/5 bg-emerald-950/10">Auto-Tune</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/40">
                {[
                  {
                    feature: 'Prices change with scarcity',
                    static: { text: '✗ Never', negative: true },
                    autotune: { text: '✓ Every 5 minutes', positive: true },
                  },
                  {
                    feature: 'Buy/sell spread reflects activity',
                    static: { text: '✗ Fixed spread', negative: true },
                    autotune: { text: '✓ Dynamic, liquidity-adjusted', positive: true },
                  },
                  {
                    feature: 'New item discovery (e.g. new ore)',
                    static: { text: '✗ Admin must set price', negative: true },
                    autotune: { text: '✓ Auto-priced from true prices API', positive: true },
                  },
                  {
                    feature: 'Players can take loans',
                    static: { text: '✗ Not built in', negative: true },
                    autotune: { text: '✓ Full loan system + circuit breaker', positive: true },
                  },
                  {
                    feature: 'Players can place buy/sell orders',
                    static: { text: '✗ Instant only', negative: true },
                    autotune: { text: '✓ Order book + auction system', positive: true },
                  },
                  {
                    feature: 'Market health dashboard',
                    static: { text: '✗ None', negative: true },
                    autotune: { text: '✓ Admin health panel + /at admin health', positive: true },
                  },
                  {
                    feature: 'Web dashboard for players',
                    static: { text: '✗ Not included', negative: true },
                    autotune: { text: '✓ Bundled, no extra setup', positive: true },
                  },
                  {
                    feature: 'Taxes / treasury pool',
                    static: { text: '✗ Not included', negative: true },
                    autotune: { text: '✓ Built-in treasury + tax config', positive: true },
                  },
                  {
                    feature: 'Cross-server price discovery',
                    static: { text: '✗ Not possible', negative: true },
                    autotune: { text: '✓ True Prices API (opt-in)', positive: true },
                  },
                  {
                    feature: 'Players affected by exploits',
                    static: { text: '✓ Fully affected', negative: true },
                    autotune: { text: '≈ Bounded by spread + circuit breaker', neutral: true },
                  },
                ].map(({ feature, static: sv, autotune: av }) => (
                  <tr key={feature} className="hover:bg-gray-900/30 transition-colors">
                    <td className="py-3 px-5 text-gray-300 text-xs">{feature}</td>
                    <td className={`py-3 px-4 text-center text-xs font-medium ${sv.negative ? 'text-gray-600' : ''}`}>
                      {sv.text}
                    </td>
                    <td className={`py-3 px-4 text-center text-xs font-semibold ${av.positive ? 'text-emerald-400 bg-emerald-950/10' : av.neutral ? 'text-amber-400 bg-amber-950/10' : 'text-red-400'}`}>
                      {av.text}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="border-t border-gray-800/60 px-5 py-3 bg-gray-900/20">
            <p className="text-xs text-gray-500">
              ≈ Cross-server price manipulation is bounded by outlier filtering and server key authentication.{' '}
              <Link href="/true-prices" className="text-emerald-400 hover:underline">Learn how true prices work →</Link>
            </p>
          </div>
        </div>
      </section>

      {/* Prerequisites */}
      <div className="mb-12 rounded-xl border border-gray-800 bg-gray-900/60 p-6">
        <h2 className="text-base font-semibold text-white mb-4 flex items-center gap-2">
          <span className="text-emerald-400">Prerequisites</span>
        </h2>
        <div className="grid sm:grid-cols-2 gap-4">
          {PREREQUISITES.map((p) => (
            <div key={p.label} className="flex items-start gap-3">
              <div className="w-5 h-5 rounded bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center shrink-0 mt-0.5">
                <CheckCircle className="w-3 h-3 text-emerald-400" />
              </div>
              <div>
                <p className="text-sm font-medium text-white">{p.label}</p>
                <p className="text-xs text-gray-500 mt-0.5">{p.detail}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-4 pt-4 border-t border-gray-800 flex items-start gap-2 text-xs text-amber-400/80 bg-amber-500/5 border border-amber-500/20 rounded-lg px-4 py-3">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <span>
            Vault must be installed <strong className="text-amber-300">before</strong> Auto-Tune. If Vault is missing, the plugin will log errors and the economy commands won&apos;t work.
          </span>
        </div>
      </div>

      {/* Video demo */}
      <VideoDemoSection />

      {/* Install steps */}
      <InstallScreenshots />

      <div className="mb-12 mt-16">
        <h2 className="text-base font-semibold text-white mb-6">Install Steps</h2>

        {/* Vertical line */}
        <div className="relative">
          <div className="absolute left-[28px] top-8 bottom-8 w-px bg-gray-800 hidden sm:block" />
          <div className="space-y-0">
            {STEPS.map((step) => (
              <StepCard key={step.num} {...step} />
            ))}
          </div>
        </div>
      </div>

      {/* Optional: cross-server */}
      <div className="mb-12 rounded-xl border border-emerald-800/30 bg-emerald-950/20 p-6">
        <h2 className="text-base font-semibold text-emerald-300 mb-3">
          Optional: Enable Cross-Server True Prices
        </h2>
        <p className="text-sm text-gray-400 mb-4 leading-relaxed">
          Auto-Tune can submit your server&apos;s price ratios to a shared API server, which aggregates
          data across servers to compute &quot;true&quot; universal prices. New servers can seed their
          economy from these prices instead of starting from scratch.
        </p>
        <div className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-3 text-xs font-mono text-gray-300 mb-4">
          <div className="text-gray-500 mb-1"># In config.yml</div>
          <div><span className="text-sky-400">cross-server:</span></div>
          <div className="pl-4">enabled: <span className="text-amber-400">true</span></div>
          <div className="pl-4">api-server: <span className="text-amber-400">&quot;https://autotune-api.example.com&quot;</span></div>
          <div className="pl-4">api-key: <span className="text-amber-400">&quot;your-server-key-here&quot;</span></div>
          <div className="pl-4">submit-interval-minutes: <span className="text-amber-400">30</span></div>
        </div>
        <p className="text-xs text-gray-500">
          See <Link href="/true-prices" className="text-emerald-400 hover:underline">True Prices</Link> and{' '}
          <Link href="/servers" className="text-emerald-400 hover:underline">Server Explorer</Link> to learn more.
        </p>
      </div>

      {/* Commands reference */}
      <div className="mb-12 rounded-xl border border-gray-800 bg-gray-900/60 p-6">
        <h2 className="text-base font-semibold text-white mb-4">Core Commands</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800/60">
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Command</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Description</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Who</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800/40">
              {[
                { cmd: '/shop', desc: 'Browse and buy/sell items at live prices', who: 'All players' },
                { cmd: '/sell', desc: 'Sell items from your inventory', who: 'All players' },
                { cmd: '/autosell', desc: 'Configure which items auto-sell on pickup', who: 'All players' },
                { cmd: '/loans', desc: 'Take, repay, and manage loans', who: 'All players' },
                { cmd: '/auction', desc: 'Place and fill auction orders', who: 'All players' },
                { cmd: '/at event invoke <name>', desc: 'Trigger a seasonal market event by template name', who: 'Admins' },
                { cmd: '/at event schedule <type> <mats> <mult> <dur>', desc: 'Schedule a market event to start in N minutes', who: 'Admins' },
                { cmd: '/at event templates', desc: "List event templates defined in config.yml", who: 'Admins' },
                { cmd: '/treasury', desc: 'View and manage server treasury (tax pool)', who: 'Admins' },
                { cmd: '/at admin health', desc: 'Economy health dashboard with Health Score', who: 'Admins' },
                { cmd: '/at admin reload', desc: 'Reload config without restarting server', who: 'Admins' },
                { cmd: '/badges', desc: 'View your earned achievement badges', who: 'All players' },
              ].map(({ cmd, desc, who }) => (
                <tr key={cmd}>
                  <td className="py-2.5 pr-4 font-mono text-sky-300 text-xs whitespace-nowrap">{cmd}</td>
                  <td className="py-2.5 pr-4 text-gray-400 text-xs">{desc}</td>
                  <td className="py-2.5 text-gray-600 text-xs">{who}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* FAQ */}
      <section className="mb-12">
        <h2 className="text-xl font-bold text-white mb-6">Frequently Asked Questions</h2>
        <div className="space-y-3">
          {[
            {
              q: 'Will Auto-Tune break my existing economy?',
              a: 'No. Auto-Tune runs alongside your existing Vault economy plugin. It doesn\'t modify player balances or existing shop data. The only thing that changes is the price of items — which is exactly the point.',
            },
            {
              q: 'What if Auto-Tune\'s prices go crazy?',
              a: 'There\'s a circuit breaker. If debt-to-GDP exceeds 10×, loans pause and interest stops accruing. Prices freeze at their last valid point until the economy stabilises. Admins can also set floor and ceiling prices per item.',
            },
            {
              q: 'Does it work with EssentialsX, CMIE, or other economy plugins?',
              a: 'Yes — as long as the economy plugin implements Vault\'s economy API, Auto-Tune will hook into it. Your players keep their money, their ranks, and their balances.',
            },
            {
              q: 'What happens to prices when the server restarts?',
              a: 'Prices are stored in SQLite and survive restarts. The market state (trade history, debt, loan positions) is fully persisted. Players joining after a restart see the same prices they would have seen before.',
            },
            {
              q: 'Can I use Auto-Tune without cross-server features?',
              a: 'Yes — the cross-server submission is entirely opt-in. Without it, your server runs a fully self-contained economy with no external dependencies.',
            },
            {
              q: 'How is Auto-Tune different from ShopGUI+ or other shop plugins?',
              a: 'ShopGUI+ lets you define fixed prices manually. Auto-Tune sets prices automatically based on player activity — the more an item is bought, the more expensive it gets; the more it\'s sold, the cheaper it gets. You spend less time managing prices and players get a more dynamic, engaging economy.',
            },
            {
              q: 'Can I tune how fast prices move?',
              a: 'Yes. The key knob is maxPriceChange (default 1.5%). You can make prices more stable (0.5%) or more volatile (3.0%). Volume impact, player scaling, and liquidity factors can also be tuned independently.',
            },
          ].map(({ q, a }) => (
            <details key={q} className="group rounded-xl border border-gray-800 bg-gray-900/40 overflow-hidden">
              <summary className="flex items-center justify-between gap-4 px-5 py-4 cursor-pointer list-none">
                <span className="text-sm font-medium text-white group-hover:text-emerald-400 transition-colors">{q}</span>
                <ChevronDown className="w-4 h-4 text-gray-500 shrink-0 transition-transform group-open:rotate-180" />
              </summary>
              <div className="px-5 pb-5">
                <p className="text-sm text-gray-400 leading-relaxed">{a}</p>
              </div>
            </details>
          ))}
        </div>
      </section>

      {/* Hosting guide */}
      <section className="mb-12">
        <h2 className="text-xl font-bold text-white mb-6">Where to host your server</h2>
        <p className="text-sm text-gray-400 mb-6 leading-relaxed">
          Auto-Tune runs on any Paper 1.21.4+ server with Java 21 and Vault. Here are hosting options
          that handle Auto-Tune&apos;s requirements well, from free options to production-grade servers.
       ide
        </p>
        <div className="grid sm:grid-cols-2 gap-4">
          {[
            {
              tier: 'Free / Cheap',
              providers: [
                { name: 'MineOS (self-hosted)', url: 'https://www.mino-s.io/', note: 'Free. Run on your own hardware. Full control.' },
                { name: 'Aternos', url: 'https://aternos.org/', note: 'Free tier available. Good for testing.' },
                { name: 'ServerJars', url: 'https://serverjars.com/', note: 'Simple JAR hosting. No panel, SSH only.' },
              ],
            },
            {
              tier: 'Reliable ($5–15/mo)',
              providers: [
                { name: 'ScalaCube', url: 'https://scalacube.com/', note: 'Minecraft-specialised. One-click Paper installs.' },
                { name: 'BloomVPS', url: 'https://bloomvps.com/', note: 'Budget-friendly KVM VPS. Root access.' },
                { name: 'Linode / DigitalOcean', url: 'https://www.linode.com/', note: 'General VPS. Install Paper manually. Scales well.' },
              ],
            },
            {
              tier: 'Production ($15–50/mo)',
              providers: [
                { name: 'WitherHosting', url: 'https://witherhosting.com/', note: 'High-performance, Minecraft-optimised. 24/7 uptime.' },
                { name: 'Shockbyte', url: 'https://shockbyte.com/', note: 'Global data centers. One-click Auto-Tune install coming.' },
                { name: 'PebbleHost', url: 'https://pebblehost.com/', note: 'Good panel, fast support. Java 21 pre-configured.' },
              ],
            },
            {
              tier: 'Self-hosted (full control)',
              providers: [
                { name: 'Ubuntu 22.04 + Paper', url: 'https://docs.papermc.io/paper/getting-started', note: 'Install Java 21, download Paper JAR, run. ~30 min setup.' },
                { name: 'Docker + Paper', url: 'https://docker.com/', note: 'Containerised. Reproducible. Good for power users.' },
              ],
            },
          ].map(({ tier, providers }) => (
            <div key={tier} className="rounded-xl border border-gray-800 bg-gray-900/40 p-4">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">{tier}</p>
              <div className="space-y-2.5">
                {providers.map(({ name, url, note }) => (
                  <div key={name} className="flex items-start gap-2">
                    <div className="w-1.5 h-1.5 rounded-full bg-gray-600 mt-1.5 shrink-0" />
                    <div>
                      <a
                        href={url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-xs font-medium text-sky-400 hover:text-sky-300 transition-colors"
                      >
                        {name}
                      </a>
                      <p className="text-xs text-gray-500 mt-0.5">{note}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div className="mt-4 rounded-lg border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-xs text-amber-400/80">
          <strong>Java 21 is required.</strong> Most shared hosts now support it by default. If yours doesn&apos;t, open a support ticket — most will install it on request. Auto-Tune will fail to load gracefully and log an error if Java version is insufficient.
        </div>
      </section>

      {/* Next steps */}
      <div className="flex flex-wrap gap-3">
        <Link
          href="/how-it-works"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors text-sm"
        >
          <BookOpen className="w-4 h-4" />
          How it works
        </Link>
        <Link
          href="/simulator"
          className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
        >
          Test in simulator →
        </Link>
        <Link
          href="/true-prices"
          className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
        >
          View true prices
        </Link>
        <Link
          href="/docs"
          className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
        >
          Admin docs →
        </Link>
      </div>
    </div>
  );
}

// ─── Player flow mini-demo ──────────────────────────────────────────────────────
// Three illustrated steps showing the core player loop.
// Each card has a terminal-style header and a CSS-rendered UI mockup.

function PlayerFlowCard({
  step,
  title,
  subtitle,
  terminal,
  mockup,
}: {
  step: number;
  title: string;
  subtitle: string;
  terminal: string;
  mockup: React.ReactNode;
}) {
  return (
    <div className="flex flex-col rounded-xl border border-gray-800 bg-gray-900 overflow-hidden">
      {/* Terminal header */}
      <div className="flex items-center gap-1.5 px-4 py-2.5 bg-gray-800 border-b border-gray-700">
        <span className="w-3 h-3 rounded-full bg-red-500/70" />
        <span className="w-3 h-3 rounded-full bg-amber-500/70" />
        <span className="w-3 h-3 rounded-full bg-green-500/70" />
        <span className="ml-3 text-xs text-gray-500 font-mono">{terminal}</span>
      </div>
      {/* Mockup */}
      <div className="p-4 flex-1">{mockup}</div>
      {/* Label */}
      <div className="px-4 pb-4">
        <div className="flex items-center gap-2 mb-1">
          <span className="w-5 h-5 rounded-full bg-emerald-900 border border-emerald-700 flex items-center justify-center text-[10px] font-mono text-emerald-400">
            {step}
          </span>
          <span className="text-xs text-gray-500 uppercase tracking-wider">{title}</span>
        </div>
        <p className="text-xs text-gray-400">{subtitle}</p>
      </div>
    </div>
  );
}

function VideoDemoSection() {
  return (
    <section className="mb-16">
      <div className="mb-8">
        <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">See It Live</p>
        <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
          The player experience in 3 steps
        </h2>
        <p className="text-gray-400 text-sm leading-relaxed max-w-xl">
          From opening the shop to watching prices react — Auto-Tune is designed around
          the natural loop of browsing, trading, and observing consequences.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        {/* Step 1 — /shop */}
        <PlayerFlowCard
          step={1}
          title="Browse & Buy"
          subtitle='Player runs /shop and browses live prices'
          terminal="Minecraft Chat"
          mockup={
            <div className="rounded-lg border border-gray-700 bg-gray-950 p-3 space-y-1.5">
              {[
                { item: 'DIAMOND', buy: '$311.20', sell: '$308.90', trend: '+2.4%' },
                { item: 'EMERALD', buy: '$148.50', sell: '$146.80', trend: '-1.1%' },
                { item: 'IRON INGOT', buy: '$10.85', sell: '$10.72', trend: '+0.3%' },
                { item: 'NETHERITE', buy: '$2,180', sell: '$2,155', trend: '+0.8%' },
              ].map(({ item, buy, sell, trend }) => (
                <div key={item} className="flex items-center justify-between text-xs">
                  <span className="font-mono text-gray-300 w-24">{item}</span>
                  <span className="text-emerald-400 font-mono">B {buy}</span>
                  <span className="text-amber-400 font-mono">S {sell}</span>
                  <span className={trend.startsWith('+') ? 'text-emerald-400' : 'text-rose-400'}>{trend}</span>
                </div>
              ))}
              <div className="pt-1.5 border-t border-gray-800 text-[10px] text-gray-600 font-mono">
                7-day average · Updated just now
              </div>
            </div>
          }
        />

        {/* Step 2 — Price reacts */}
        <PlayerFlowCard
          step={2}
          title="Prices Update"
          subtitle='Each trade nudges prices — supply and demand in action'
          terminal="Market Tick (every 5 min)"
          mockup={
            <div className="space-y-2">
              <div className="text-[10px] text-gray-500 uppercase tracking-wider mb-2">Last trade activity</div>
              {[
                { player: 'Notch', item: 'DIAMOND ×64', action: 'BUY', price: '$311', color: 'text-emerald-400' },
                { player: 'Herobrine', item: 'IRON ×128', action: 'SELL', price: '$10.72', color: 'text-amber-400' },
                { player: 'Steve', item: 'EMERALD ×16', action: 'BUY', price: '$148', color: 'text-emerald-400' },
              ].map(({ player, item, action, price, color }) => (
                <div key={player} className="flex items-center gap-2 text-[11px]">
                  <span className="text-gray-600 font-mono w-16 shrink-0">{player}</span>
                  <span className="text-gray-400 font-mono flex-1 truncate">{item}</span>
                  <span className={`font-bold font-mono ${color}`}>{action}</span>
                  <span className="text-gray-500 font-mono">{price}</span>
                </div>
              ))}
              <div className="rounded bg-gray-950 border border-gray-800 p-2 mt-2">
                <div className="text-[10px] text-emerald-400 mb-1">→ DIAMOND buy pressure +3</div>
                <div className="text-[10px] text-gray-600">DIAMOND price trending up +$7.40</div>
              </div>
            </div>
          }
        />

        {/* Step 3 — Admin health */}
        <PlayerFlowCard
          step={3}
          title="Admin Dashboard"
          subtitle='Admins see GDP, debt, volatility, and top movers'
          terminal="your-server.net:8989/economy"
          mockup={
            <div className="space-y-2">
              <div className="grid grid-cols-2 gap-1.5">
                {[
                  { label: 'GDP', value: '847K', sub: '+$124K today', good: true },
                  { label: 'D/G Ratio', value: '1.76×', sub: 'Healthy', good: true },
                  { label: 'Buy %', value: '73%', sub: 'Slightly buy-heavy', good: true },
                  { label: 'Volatility', value: '0.007', sub: 'Stable', good: true },
                ].map(({ label, value, sub, good }) => (
                  <div key={label} className="rounded bg-gray-950 border border-gray-800 p-2 text-center">
                    <div className={`text-sm font-bold font-mono ${good ? 'text-emerald-400' : 'text-rose-400'}`}>{value}</div>
                    <div className="text-[9px] text-gray-500">{label}</div>
                    <div className={`text-[9px] ${good ? 'text-emerald-500/60' : 'text-rose-500/60'}`}>{sub}</div>
                  </div>
                ))}
              </div>
              <div className="rounded bg-emerald-950/40 border border-emerald-800/40 p-2">
                <div className="text-[10px] text-emerald-400 font-medium">● Economy Health: HEALTHY</div>
                <div className="text-[10px] text-gray-500 mt-0.5">Circuit breaker: STANDBY</div>
              </div>
            </div>
          }
        />
      </div>

      <div className="flex items-center gap-3 p-4 rounded-xl border border-gray-800 bg-gray-900/40">
        <div className="flex items-center gap-2 text-emerald-400">
          <Monitor className="w-5 h-5 shrink-0" />
          <span className="text-sm font-medium">Full interactive simulator</span>
        </div>
        <p className="text-gray-500 text-xs flex-1">
          Run your own scenarios — test market events, archetype mixes, and parameter changes before deploying.
        </p>
        <Link
          href="/simulator"
          className="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition-colors"
        >
          Open Simulator
          <ExternalLink className="w-3.5 h-3.5" />
        </Link>
      </div>
    </section>
  );
}

