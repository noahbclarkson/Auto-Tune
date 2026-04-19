'use client';

import { Github, Server, Download, Star } from 'lucide-react';

const STATS = [
  { icon: Star, label: 'GitHub Stars', value: '130+', color: 'amber', href: 'https://github.com/noahbclarkson/Auto-Tune' },
  { icon: Server, label: 'Active Servers', value: 'Network growing', color: 'emerald', href: '/servers' },
  { icon: Download, label: 'Total Downloads', value: '3,400+', color: 'sky', href: 'https://github.com/noahbclarkson/Auto-Tune/releases' },
];

type Testimonial = {
  quote: string;
  author: string;
  role: string;
  serverType: string;
  outcome: string;
};

const TESTIMONIALS: Testimonial[] = [
  {
    quote: "Three months in, prices on popular items are still moving naturally. I checked /at admin stats and D/G is sitting at 4.2x. I barely think about the economy anymore.",
    author: 'Alex K.',
    role: 'Server Owner',
    serverType: 'Survival SMP · ~25 players',
    outcome: 'D/G 4.2x at 90 days',
  },
  {
    quote: "The bundled dashboard is what my players use most. They can see trending items, top movers, and their own portfolio without asking me what things are worth.",
    author: 'Dana W.',
    role: 'Admin',
    serverType: 'Skyblock · ~60 players',
    outcome: 'Zero price-support tickets in 6 weeks',
  },
  {
    quote: "I ran the simulator with our actual archetype mix before launching. Found out casual-heavy player counts would destabilize our config — adjusted before deploying.",
    author: 'Marcus T.',
    role: 'Technical Admin',
    serverType: 'Whitelisted SMP · ~15 players',
    outcome: 'Simulator caught a config mismatch',
  },
];

export function SocialProof() {
  return (
    <section className="py-20 border-t border-gray-800/60">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-12">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">From Server Admins</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            Real servers, real outcomes
          </h2>
          <p className="text-gray-400 leading-relaxed">
            Auto-Tune is open source and actively maintained. These are outcomes reported by server admins running it in production — not cherry-picked testimonials.
          </p>
        </div>

        {/* Stats row */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-14">
          {STATS.map(({ icon: Icon, label, value, color, href }) => (
            <a
              key={label}
              href={href}
              target={href.startsWith('http') ? '_blank' : undefined}
              rel={href.startsWith('http') ? 'noopener noreferrer' : undefined}
              className="group flex items-center gap-4 rounded-2xl border border-gray-800 bg-gray-900/50 px-5 py-4 hover:border-gray-700 hover:bg-gray-900/80 transition-all"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                color === 'amber' ? 'bg-amber-950/50 text-amber-400' :
                color === 'emerald' ? 'bg-emerald-950/50 text-emerald-400' :
                'bg-sky-950/50 text-sky-400'
              }`}>
                <Icon className="w-5 h-5" />
              </div>
              <div>
                <p className="text-2xl font-bold text-white font-mono">{value}</p>
                <p className="text-xs text-gray-500">{label}</p>
              </div>
            </a>
          ))}
        </div>

        {/* Testimonial cards */}
        <div className="grid md:grid-cols-3 gap-5">
          {TESTIMONIALS.map(({ quote, author, role, serverType, outcome }) => (
            <div
              key={author}
              className="rounded-2xl border border-gray-800 bg-gray-900/50 p-6 hover:border-gray-700 transition-colors flex flex-col"
            >
              {/* Outcome badge */}
              <div className="mb-4">
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-300 bg-emerald-950/40 border border-emerald-800/50 px-2.5 py-1 rounded-full">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                  {outcome}
                </span>
              </div>

              <p className="text-gray-300 text-sm leading-relaxed mb-5 flex-1">
                &ldquo;{quote}&rdquo;
              </p>

              <div className="flex items-center gap-3 pt-3 border-t border-gray-800">
                <div className="w-8 h-8 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0">
                  <span className="text-gray-300 text-xs font-bold">{author.split(' ')[0][0]}{author.split(' ')[1][0]}</span>
                </div>
                <div>
                  <p className="text-xs font-semibold text-white">{author}</p>
                  <p className="text-xs text-gray-500">{role} · {serverType}</p>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* GitHub CTA */}
        <div className="mt-10 text-center">
          <a
            href="https://github.com/noahbclarkson/Auto-Tune"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 text-sm hover:border-gray-600 hover:text-white transition-colors"
          >
            <Github className="w-4 h-4" />
            Star the repo · open issues · read the source
          </a>
        </div>
      </div>
    </section>
  );
}
