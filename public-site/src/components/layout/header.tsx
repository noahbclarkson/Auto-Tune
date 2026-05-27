'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  BookOpen,
  ChevronDown,
  Code2,
  DollarSign,
  FileText,
  Github,
  Gavel,
  Menu,
  Network,
  Server,
  Settings,
  Shield,
  Sliders,
  TrendingUp,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '@/components/landing/theme-toggle';

type NavItem = {
  href: string;
  label: string;
  description?: string;
  icon?: React.ElementType;
};

const PRIMARY_LINKS: NavItem[] = [
  { href: '/install', label: 'Install' },
  { href: '/setup', label: 'Setup' },
  { href: '/simulator', label: 'Simulator' },
  { href: '/docs', label: 'Docs' },
];

const NAV_GROUPS: { label: string; items: NavItem[] }[] = [
  {
    label: 'Learn',
    items: [
      { href: '/how-it-works', label: 'Market Engine', description: 'Pricing and spread math', icon: TrendingUp },
      { href: '/economy', label: 'Economy Systems', description: 'Loans, treasury, events', icon: DollarSign },
      { href: '/auction', label: 'Auction House', description: 'Order book and player trades', icon: Gavel },
      { href: '/faq', label: 'FAQ', description: 'Common admin questions', icon: BookOpen },
    ],
  },
  {
    label: 'Tools',
    items: [
      { href: '/config-preview', label: 'Config Preview', description: 'Compare config changes', icon: FileText },
      { href: '/config-playground', label: 'Config Playground', description: 'Tune engine parameters', icon: Sliders },
      { href: '/health-badge', label: 'Health Badge', description: 'Generate a server badge', icon: Shield },
      { href: '/admin', label: 'Admin Commands', description: 'Command reference', icon: Settings },
    ],
  },
  {
    label: 'Network',
    items: [
      { href: '/true-prices', label: 'True Prices', description: 'Cross-server item values', icon: Network },
      { href: '/exchange-rates', label: 'Exchange Rates', description: 'Server multipliers', icon: TrendingUp },
      { href: '/servers', label: 'Servers', description: 'Registered API clients', icon: Server },
      { href: '/trust', label: 'Trust', description: 'Submission safeguards', icon: Shield },
    ],
  },
  {
    label: 'Project',
    items: [
      { href: '/roadmap', label: 'Roadmap', description: 'Current priorities', icon: TrendingUp },
      { href: '/changelog', label: 'Changelog', description: 'Rewrite-2 history', icon: FileText },
      { href: '/api-docs', label: 'API Docs', description: 'Rust price API', icon: Code2 },
      { href: '/findings', label: 'Simulation Findings', description: 'Market lab results', icon: Sliders },
    ],
  },
];

function isActive(pathname: string, href: string) {
  return pathname === href || (href !== '/' && pathname.startsWith(`${href}/`));
}

function DesktopGroup({ label, items, pathname }: { label: string; items: NavItem[]; pathname: string }) {
  const active = items.some((item) => isActive(pathname, item.href));

  return (
    <div className="group relative">
      <button
        className={cn(
          'inline-flex h-9 items-center gap-1.5 rounded-md px-3 text-sm transition-colors',
          active ? 'bg-emerald-500/10 text-emerald-300' : 'text-gray-400 hover:bg-gray-800/70 hover:text-gray-100',
        )}
      >
        {label}
        <ChevronDown className="h-3.5 w-3.5" />
      </button>
      <div className="invisible absolute left-0 top-full z-50 w-72 pt-2 opacity-0 transition group-hover:visible group-hover:opacity-100">
        <div className="rounded-lg border border-gray-800 bg-gray-950 p-2 shadow-2xl shadow-black/30">
          {items.map(({ href, label: itemLabel, description, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              className={cn(
                'flex gap-3 rounded-md px-3 py-2.5 transition-colors',
                isActive(pathname, href)
                  ? 'bg-emerald-500/10 text-emerald-300'
                  : 'text-gray-300 hover:bg-gray-900 hover:text-white',
              )}
            >
              {Icon && <Icon className="mt-0.5 h-4 w-4 shrink-0 text-gray-500" />}
              <span>
                <span className="block text-sm font-medium">{itemLabel}</span>
                {description && <span className="block text-xs text-gray-500">{description}</span>}
              </span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

export function Header() {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-gray-800/70 bg-gray-950/95 backdrop-blur">
      <nav className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href="/" className="flex items-center gap-2">
          <span className="flex h-7 w-7 items-center justify-center rounded-md border border-emerald-700/50 bg-emerald-950/50">
            <DollarSign className="h-4 w-4 text-emerald-400" />
          </span>
          <span className="text-sm font-semibold tracking-tight text-white">Auto-Tune</span>
        </Link>

        <div className="hidden items-center gap-1 lg:flex">
          {PRIMARY_LINKS.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className={cn(
                'inline-flex h-9 items-center rounded-md px-3 text-sm transition-colors',
                isActive(pathname, href)
                  ? 'bg-emerald-500/10 text-emerald-300'
                  : 'text-gray-400 hover:bg-gray-800/70 hover:text-gray-100',
              )}
            >
              {label}
            </Link>
          ))}
          {NAV_GROUPS.map((group) => (
            <DesktopGroup key={group.label} {...group} pathname={pathname} />
          ))}
        </div>

        <div className="flex items-center gap-2">
          <ThemeToggle />
          <a
            href="https://github.com/noahbclarkson/Auto-Tune"
            target="_blank"
            rel="noopener noreferrer"
            className="hidden h-9 items-center justify-center rounded-md px-2 text-gray-500 transition-colors hover:bg-gray-800/70 hover:text-gray-100 sm:inline-flex"
            aria-label="Open Auto-Tune on GitHub"
          >
            <Github className="h-4 w-4" />
          </a>
          <button
            type="button"
            onClick={() => setMobileOpen((open) => !open)}
            className="inline-flex h-9 items-center justify-center rounded-md border border-gray-800 px-2 text-gray-300 lg:hidden"
            aria-expanded={mobileOpen}
            aria-label="Toggle navigation"
          >
            {mobileOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
          </button>
        </div>
      </nav>

      {mobileOpen && (
        <div className="border-t border-gray-800 bg-gray-950 px-4 py-4 lg:hidden">
          <div className="mx-auto max-w-7xl space-y-5">
            <div className="grid grid-cols-2 gap-2">
              {[{ href: '/', label: 'Home' }, ...PRIMARY_LINKS].map(({ href, label }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMobileOpen(false)}
                  className={cn(
                    'rounded-md px-3 py-2 text-sm transition-colors',
                    isActive(pathname, href)
                      ? 'bg-emerald-500/10 text-emerald-300'
                      : 'text-gray-300 hover:bg-gray-900',
                  )}
                >
                  {label}
                </Link>
              ))}
            </div>

            {NAV_GROUPS.map(({ label, items }) => (
              <div key={label}>
                <p className="mb-2 px-1 text-xs font-semibold uppercase tracking-widest text-gray-600">{label}</p>
                <div className="grid gap-1 sm:grid-cols-2">
                  {items.map(({ href, label: itemLabel, icon: Icon }) => (
                    <Link
                      key={href}
                      href={href}
                      onClick={() => setMobileOpen(false)}
                      className={cn(
                        'flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors',
                        isActive(pathname, href)
                          ? 'bg-emerald-500/10 text-emerald-300'
                          : 'text-gray-300 hover:bg-gray-900',
                      )}
                    >
                      {Icon && <Icon className="h-4 w-4 text-gray-500" />}
                      {itemLabel}
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </header>
  );
}
