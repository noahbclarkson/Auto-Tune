'use client';

import Link from 'next/link';
import { ArrowRight, FlaskConical, Download, BookOpen, Zap, ChevronRight } from 'lucide-react';

/* ------------------------------------------------------------------ */
/* Admin quickstart journey — "New here? Start here."                     */
/* ------------------------------------------------------------------ */

const STEPS = [
  {
    icon: FlaskConical,
    label: 'Try the Simulator',
    desc: 'See how prices react to different player mixes, market events, and config changes — in your browser, no server required.',
    href: '/simulator',
    cta: 'Open Simulator',
    accent: 'border-amber-800/50 bg-amber-950/20',
    iconBg: 'bg-amber-950/60 border-amber-800/50 text-amber-400',
    ctaStyle: 'bg-amber-600 hover:bg-amber-500 text-white',
  },
  {
    icon: BookOpen,
    label: 'Read the Admin Quickstart',
    desc: 'The 5 decisions before you launch — archetype mix, loans, floor, events, digest. Backed by 5-seed simulation evidence.',
    href: 'https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/docs/QUICKSTART.md',
    cta: 'Read Quickstart',
    accent: 'border-emerald-800/50 bg-emerald-950/20',
    iconBg: 'bg-emerald-950/60 border-emerald-800/50 text-emerald-400',
    ctaStyle: 'bg-emerald-600 hover:bg-emerald-500 text-white',
  },
  {
    icon: Download,
    label: 'Install Auto-Tune',
    desc: 'Drop the JAR into your Paper 1.21.4 plugins/ folder, restart, done. Works out of the box with sensible defaults.',
    href: '/install',
    cta: 'Install Guide',
    accent: 'border-sky-800/50 bg-sky-950/20',
    iconBg: 'bg-sky-950/60 border-sky-800/50 text-sky-400',
    ctaStyle: 'bg-sky-600 hover:bg-sky-500 text-white',
  },
];

export function AdminJourney() {
  return (
    <section className="border-b border-gray-800/50 bg-gray-950/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Section header */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-emerald-400" />
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-semibold">New here?</p>
          </div>
          <p className="text-xs text-gray-500 hidden sm:block">3 steps to a live economy</p>
        </div>

        {/* Journey cards */}
        <div className="grid sm:grid-cols-3 gap-3">
          {STEPS.map(({ icon: Icon, label, desc, href, cta, accent, iconBg, ctaStyle }, i) => (
            <div
              key={label}
              className={`relative rounded-xl border ${accent} p-5 flex flex-col gap-3 hover:-translate-y-0.5 transition-transform`}
            >
              {/* Step number */}
              <div className="absolute -top-2 left-4">
                <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-gray-900 border border-gray-700 text-[10px] font-mono font-bold text-gray-400">
                  {i + 1}
                </span>
              </div>

              <div className="flex items-start gap-3">
                <div className={`w-9 h-9 rounded-lg border flex items-center justify-center shrink-0 mt-0.5 ${iconBg}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-white mb-0.5">{label}</p>
                  <p className="text-xs text-gray-400 leading-relaxed">{desc}</p>
                </div>
              </div>

              <div className="flex items-center justify-between gap-2 mt-auto pt-2 border-t border-gray-800/50">
                <Link
                  href={href}
                  target={href.startsWith('http') ? '_blank' : undefined}
                  rel={href.startsWith('http') ? 'noopener noreferrer' : undefined}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-medium text-xs transition-colors ${ctaStyle}`}
                >
                  {cta}
                  <ChevronRight className="w-3 h-3" />
                </Link>
                {i < STEPS.length - 1 && (
                  <ArrowRight className="w-3 h-3 text-gray-600 hidden sm:block" />
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
