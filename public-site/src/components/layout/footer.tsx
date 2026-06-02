import Link from 'next/link';
import { Github, Network, TrendingUp } from 'lucide-react';

const FOOTER_GROUPS = [
  {
    label: 'Start',
    links: [
      { href: '/install', label: 'Install' },
      { href: '/setup', label: 'Setup Wizard' },
      { href: '/docs', label: 'Docs' },
      { href: '/faq', label: 'FAQ' },
    ],
  },
  {
    label: 'Tools',
    links: [
      { href: '/simulator', label: 'Simulator' },
      { href: '/config-preview', label: 'Config Preview' },
      { href: '/health-badge', label: 'Health Badge' },
      { href: '/admin', label: 'Admin Commands' },
    ],
  },
  {
    label: 'Network',
    links: [
      { href: '/true-prices', label: 'True Prices' },
      { href: '/exchange-rates', label: 'Exchange Rates' },
      { href: '/servers', label: 'Servers' },
      { href: '/trust', label: 'Trust' },
    ],
  },
  {
    label: 'Project',
    links: [
      { href: '/roadmap', label: 'Roadmap' },
      { href: '/changelog', label: 'Changelog' },
      { href: '/api-docs', label: 'API Docs' },
      { href: 'https://github.com/noahbclarkson/Auto-Tune', label: 'GitHub' },
    ],
  },
];

export function Footer() {
  return (
    <footer className="border-t border-gray-800/70 bg-gray-950">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="grid gap-8 lg:grid-cols-[1.2fr_2fr]">
          <div>
            <div className="mb-3 flex items-center gap-2">
              <span className="flex h-7 w-7 items-center justify-center rounded-md border border-emerald-700/50 bg-emerald-950/50">
                <TrendingUp className="h-4 w-4 text-emerald-400" />
              </span>
              <span className="text-sm font-semibold text-white">Auto-Tune</span>
            </div>
            <p className="max-w-sm text-sm leading-relaxed text-gray-500">
              Adaptive economy tooling for Minecraft Paper servers: plugin, bundled dashboard,
              simulation lab, and optional cross-server price network.
            </p>
            <div className="mt-4 flex items-center gap-3 text-xs text-gray-600">
              <span className="inline-flex items-center gap-1">
                <Network className="h-3.5 w-3.5" />
                rewrite-2
              </span>
              <span>Paper 1.21.4</span>
              <span>Java 21</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6 sm:grid-cols-4">
            {FOOTER_GROUPS.map(({ label, links }) => (
              <div key={label}>
                <p className="mb-3 text-xs font-semibold uppercase tracking-widest text-gray-600">{label}</p>
                <div className="space-y-2">
                  {links.map(({ href, label: linkLabel }) => {
                    const external = href.startsWith('http');
                    return external ? (
                      <a
                        key={href}
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-1.5 text-sm text-gray-500 transition-colors hover:text-emerald-300"
                      >
                        {linkLabel}
                        {linkLabel === 'GitHub' && <Github className="h-3.5 w-3.5" />}
                      </a>
                    ) : (
                      <Link
                        key={href}
                        href={href}
                        className="block text-sm text-gray-500 transition-colors hover:text-emerald-300"
                      >
                        {linkLabel}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 flex flex-col gap-2 border-t border-gray-800/60 pt-5 text-xs text-gray-600 sm:flex-row sm:items-center sm:justify-between">
          <p>MIT License. Built as part of the Auto-Tune monorepo.</p>
          <p>Public site is separate from the bundled plugin dashboard.</p>
        </div>
      </div>
    </footer>
  );
}
