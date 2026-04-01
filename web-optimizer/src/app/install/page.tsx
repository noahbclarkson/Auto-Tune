import { Metadata } from 'next';
import Link from 'next/link';
import { Download, Server, FileText, Zap, CheckCircle, ExternalLink, BookOpen, AlertTriangle, Play, Video } from 'lucide-react';
import { InstallScreenshots } from '@/components/install/screenshot-mockups';

export const metadata: Metadata = {
  title: 'Install Guide | Auto-Tune',
  description:
    'Get Auto-Tune running on your Minecraft Paper server in under 10 minutes. Prerequisites, install steps, basic configuration, and first verification.',
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
      </div>
    </div>
  );
}

function VideoDemoSection() {
  return (
    <section className="mb-16">
      <div className="mb-6">
        <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">See It Live</p>
        <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
          Watch Auto-Tune in action
        </h2>
        <p className="text-gray-400 text-sm leading-relaxed max-w-xl">
          See the full player experience — from joining a server to browsing the market, making a trade,
          and watching prices adjust in real time.
        </p>
      </div>

      {/* Video placeholder — replace src with actual video file or YouTube/Vimeo embed */}
      <div className="relative rounded-2xl overflow-hidden border border-gray-800 bg-gray-900 aspect-video flex items-center justify-center group cursor-pointer">
        {/* Background pattern */}
        <div className="absolute inset-0 bg-gradient-to-br from-gray-900 via-gray-950 to-emerald-950/20" />
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage: `radial-gradient(circle at 1px 1px, rgba(255,255,255,0.15) 1px, transparent 0)`,
            backgroundSize: '32px 32px',
          }}
        />

        {/* Content */}
        <div className="relative z-10 flex flex-col items-center text-center px-8">
          {/* Play button */}
          <div className="w-20 h-20 rounded-full bg-emerald-500/20 border-2 border-emerald-500/60 flex items-center justify-center mb-6 group-hover:scale-105 group-hover:bg-emerald-500/30 transition-all">
            <Play className="w-8 h-8 text-emerald-400 ml-1" fill="currentColor" />
          </div>
          <p className="text-white font-semibold text-lg mb-1">Demo Video — Coming Soon</p>
          <p className="text-gray-500 text-sm max-w-sm">
            30-second walkthrough of the full player experience
          </p>
        </div>

        {/* Corner decoration */}
        <div className="absolute top-4 right-4">
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-gray-800/80 border border-gray-700 text-xs text-gray-400 font-medium">
            <Video className="w-3 h-3" />
            0:30
          </span>
        </div>
      </div>

      {/* What to record instructions */}
      <div className="mt-4 rounded-xl border border-gray-800 bg-gray-900/40 p-4">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Recording instructions (for maintainers)</p>
        <div className="grid sm:grid-cols-2 gap-2 text-xs text-gray-400">
          {[
            { step: '1', cmd: '/shop', desc: 'Open the in-game market browser' },
            { step: '2', cmd: 'Buy an item', desc: 'Purchase Diamond or Emerald to show pricing' },
            { step: '3', cmd: '/sell', desc: 'Open the sell GUI and show pricing spread' },
            { step: '4', cmd: '/loans', desc: 'Show loan UI — interest rate, repay button' },
            { step: '5', cmd: ':8989', desc: 'Open the bundled web dashboard in a browser' },
            { step: '6', cmd: ':8989/economy', desc: 'Show GDP chart, Debt/GDP, volatility' },
          ].map(({ step, cmd, desc }) => (
            <div key={cmd} className="flex items-start gap-2.5">
              <span className="w-5 h-5 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center text-[10px] font-mono text-gray-500 shrink-0">
                {step}
              </span>
              <div>
                <code className="text-sky-300 font-mono">{cmd}</code>
                <span className="text-gray-600 ml-1.5">{desc}</span>
              </div>
            </div>
          ))}
        </div>
        <p className="text-xs text-gray-600 mt-3">
          Save as <code className="text-gray-500 font-mono">/public/demo.mp4</code> and update the <code className="text-gray-500 font-mono">src</code> attribute in the{' '}
          <code className="text-gray-500 font-mono">VideoDemoSection</code> above.
        </p>
      </div>
    </section>
  );
}

