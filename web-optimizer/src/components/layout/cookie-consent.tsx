'use client';

import { useState, useEffect } from 'react';
import { Cookie } from 'lucide-react';

const CONSENT_KEY = 'autotune_cookie_consent';

export function CookieConsent() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const stored = localStorage.getItem(CONSENT_KEY);
    if (stored !== 'accepted') {
      // Small delay so it doesn't flash on initial load
      const timer = setTimeout(() => setVisible(true), 800);
      return () => clearTimeout(timer);
    }
  }, []);

  const accept = () => {
    localStorage.setItem(CONSENT_KEY, 'accepted');
    setVisible(false);
  };

  if (!visible) return null;

  return (
    <div className="fixed bottom-0 inset-x-0 z-50 flex items-end justify-center px-4 pb-4 sm:pb-6 pointer-events-none">
      <div className="pointer-events-auto w-full max-w-lg rounded-xl border border-gray-700/60 bg-gray-900/95 backdrop-blur-sm p-4 shadow-2xl shadow-black/40 animate-in slide-in-from-bottom-4 fade-in duration-300">
        <div className="flex items-start gap-3">
          <div className="shrink-0 mt-0.5">
            <Cookie className="h-5 w-5 text-gray-400" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-200">
              Cookies on Auto-Tune
            </p>
            <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">
              This is a static informational site. We don&apos;t collect personal data, track users, or use analytics cookies. We use a single localStorage value to remember your preferences (theme, dismissed banners).
            </p>
          </div>
          <button
            onClick={accept}
            className="shrink-0 ml-2 inline-flex items-center px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition-colors"
          >
            Got it
          </button>
        </div>
      </div>
    </div>
  );
}
