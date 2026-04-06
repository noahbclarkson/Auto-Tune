'use client';

import { useState, useEffect, useCallback } from 'react';
import { Lightbulb, X } from 'lucide-react';

const ITEMS_TIPS = [
  "Prices change based on supply and demand — check /compare to see how your item's price compares to yesterday.",
  "Taking a loan can help you buy in bulk when prices are low. Try /loans to explore options.",
  'Shift-click any item to see its full price history, trend, and what moved its price.',
];

const PORTFOLIO_TIPS = [
  'Your portfolio tracks your realized profits and losses from every trade you make.',
  'Watch the Trading Timeline to see your buy/sell pattern over time.',
  'The P&L chart shows if you\'re a net buyer or net seller — adjust your strategy accordingly!',
];

const STORAGE_KEYS = {
  items: 'autotune_discovery_items_seen',
  portfolio: 'autotune_discovery_portfolio_seen',
} as const;

type Page = keyof typeof STORAGE_KEYS;

interface DiscoveryOverlayProps {
  page: Page;
}

export function DiscoveryOverlay({ page }: DiscoveryOverlayProps) {
  const [visible, setVisible] = useState(false);
  const [hint, setHint] = useState('');

  useEffect(() => {
    const key = STORAGE_KEYS[page];
    if (localStorage.getItem(key)) return;

    // Pick a random tip once per session
    const tips = page === 'items' ? ITEMS_TIPS : PORTFOLIO_TIPS;
    setHint(tips[Math.floor(Math.random() * tips.length)]);
    setVisible(true);

    // Auto-dismiss after 8 seconds
    const timer = setTimeout(() => {
      handleDismiss();
    }, 8000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const handleDismiss = useCallback(() => {
    setVisible(false);
    localStorage.setItem(STORAGE_KEYS[page], new Date().toISOString());
  }, [page]);

  if (!visible) return null;

  return (
    <div
      className="fixed top-0 right-0 z-50 flex items-start justify-end p-4 pointer-events-none"
      style={{ maxWidth: '100vw' }}
    >
      <div
        className="relative max-w-xs w-full bg-gray-900/95 backdrop-blur-sm border border-emerald-500/40 rounded-xl shadow-2xl shadow-emerald-900/20"
        style={{
          animation: 'slideInFade 0.3s ease-out',
        }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 pt-4 pb-2">
          <div className="flex items-center gap-2">
            <Lightbulb className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span className="text-sm font-semibold text-emerald-300">New to Auto-Tune?</span>
          </div>
          <button
            onClick={handleDismiss}
            className="text-gray-400 hover:text-gray-200 transition-colors p-1 rounded pointer-events-auto"
            aria-label="Dismiss"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tip */}
        <div className="px-4 pb-4">
          <p className="text-sm text-gray-200 leading-relaxed">{hint}</p>
        </div>

        {/* Progress bar (8s auto-dismiss) */}
        <div className="h-0.5 bg-emerald-500/20 rounded-b-xl overflow-hidden">
          <div
            className="h-full bg-emerald-500"
            style={{
              animation: 'shrinkBar 8s linear forwards',
            }}
          />
        </div>
      </div>

      <style jsx>{`
        @keyframes slideInFade {
          from {
            opacity: 0;
            transform: translateY(-8px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
        @keyframes shrinkBar {
          from {
            width: 100%;
          }
          to {
            width: 0%;
          }
        }
      `}</style>
    </div>
  );
}
