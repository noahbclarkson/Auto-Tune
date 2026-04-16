'use client';

import { Github, Server, Download, Star } from 'lucide-react';

const STATS = [
  { icon: Star, label: 'GitHub Stars', value: '130+', color: 'amber', href: 'https://github.com/noahbclarkson/Auto-Tune' },
  { icon: Server, label: 'Active Servers', value: 'Network growing', color: 'emerald', href: '/servers' },
  { icon: Download, label: 'Total Downloads', value: '3,400+', color: 'sky', href: 'https://github.com/noahbclarkson/Auto-Tune/releases' },
];

const TESTIMONIALS = [
  {
    quote: "Before Auto-Tune, our server's economy collapsed within a week. Prices were either too high or too low. Now they actually breathe with player activity.",
    author: "Server Admin",
    server: "Survival SMP · 40 players",
  },
  {
    quote: "The auction house is the feature I didn't know I needed. Players trade items they gather without me manually setting prices every update.",
    author: "Creative Build Team",
    server: "Creative World · 80 players",
  },
  {
    quote: "The simulation tool let us tune the economy before deploying. We knew exactly what spread settings would work for our player base.",
    author: "DevOps Lead",
    server: "Skyblock Network · 200 players",
  },
];

export function SocialProof() {
  return (
    <section className="py-20 border-t border-gray-800/60">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-12">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Server Admins</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            Used on servers from 5 to 200 players
          </h2>
          <p className="text-gray-400 leading-relaxed">
            Auto-Tune is open source and actively maintained. Server admins across Minecraft communities use it to keep economies healthy without manual intervention.
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

        {/* Testimonials */}
        <div className="mb-2 flex items-center justify-center gap-2">
          <span className="text-xs text-gray-500 italic">Example testimonials — real submissions welcome</span>
        </div>
        <div className="grid md:grid-cols-3 gap-5">
          {TESTIMONIALS.map(({ quote, author, server }) => (
            <div
              key={author}
              className="rounded-2xl border border-gray-800 bg-gray-900/50 p-6 hover:border-gray-700 transition-colors"
            >
              <p className="text-gray-300 text-sm leading-relaxed mb-4 italic">
                &ldquo;{quote}&rdquo;
              </p>
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-full bg-emerald-900/60 border border-emerald-800 flex items-center justify-center">
                  <span className="text-emerald-400 text-xs font-bold">{author[0]}</span>
                </div>
                <div>
                  <p className="text-xs font-medium text-white">{author}</p>
                  <p className="text-xs text-gray-500">{server}</p>
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
