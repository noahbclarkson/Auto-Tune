'use client';

import { useState, useRef, useEffect } from 'react';
import { X, Copy, Check, Server, Eye, EyeOff } from 'lucide-react';
import { registerServer } from '@/lib/api-client';

interface SuccessData {
  server_id: string;
  api_key: string;
}

export function RegisterServerModal() {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<SuccessData | null>(null);
  const [copied, setCopied] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  const backdropRef = useRef<HTMLDivElement>(null);

  // Reset state when opening
  function handleOpen() {
    setName('');
    setError(null);
    setSuccess(null);
    setCopied(false);
    setShowApiKey(false);
    setOpen(true);
  }

  function handleClose() {
    if (loading) return;
    setOpen(false);
  }

  // Close on backdrop click
  function handleBackdropClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target === backdropRef.current) {
      handleClose();
    }
  }

  // Close on Escape
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handleClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, loading]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Server name is required.');
      return;
    }

    setLoading(true);
    const result = await registerServer(name.trim());
    setLoading(false);

    if (result.error || !result.data) {
      setError(result.error ?? 'Registration failed. Please try again.');
      return;
    }

    setSuccess(result.data);
  }

  async function handleCopy() {
    if (!success) return;
    try {
      await navigator.clipboard.writeText(success.api_key);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback: silently fail
    }
  }

  return (
    <>
      <button
        onClick={handleOpen}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600/20 border border-emerald-600/40 text-emerald-400 text-sm font-medium hover:bg-emerald-600/30 transition-colors"
      >
        <Server className="w-4 h-4" />
        Register Your Server
      </button>

      {open && (
        <div
          ref={backdropRef}
          onClick={handleBackdropClick}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm px-4"
        >
          <div className="relative w-full max-w-md bg-gray-900 border border-gray-800/60 rounded-2xl shadow-2xl p-6">
            {/* Header */}
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-semibold text-white">Register Your Server</h2>
              <button
                onClick={handleClose}
                disabled={loading}
                className="text-gray-500 hover:text-gray-300 transition-colors disabled:opacity-50"
                aria-label="Close"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {success ? (
              /* ── Success state ── */
              <div className="space-y-4">
                <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-4">
                  <p className="text-emerald-300 font-medium mb-1">Server registered!</p>
                  <p className="text-xs text-gray-400">ID: <span className="font-mono text-gray-300">{success.server_id}</span></p>
                </div>

                <div>
                  <p className="text-sm font-medium text-white mb-1.5">Your API Key</p>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 bg-gray-950 border border-gray-700 rounded-lg px-3 py-2.5 text-sm text-emerald-300 font-mono break-all leading-relaxed tracking-widest select-all">
                      {showApiKey
                        ? success.api_key
                        : '•'.repeat(Math.min(success.api_key.length, 40))}
                    </code>
                    <button
                      onClick={() => setShowApiKey((v) => !v)}
                      className="shrink-0 flex items-center justify-center w-9 h-9 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 hover:text-white hover:border-gray-500 transition-colors"
                      aria-label={showApiKey ? 'Hide API key' : 'Reveal API key'}
                      title={showApiKey ? 'Hide API key' : 'Reveal API key'}
                    >
                      {showApiKey
                        ? <EyeOff className="w-4 h-4" />
                        : <Eye className="w-4 h-4" />}
                    </button>
                    <button
                      onClick={handleCopy}
                      className="shrink-0 flex items-center justify-center w-9 h-9 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 hover:text-white hover:border-gray-500 transition-colors"
                      aria-label="Copy API key"
                      title="Copy API key to clipboard"
                    >
                      {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-gray-500 mt-1.5">
                    Key is hidden by default. Reveal it or copy it before closing this dialog.
                  </p>
                </div>

                <div className="bg-amber-500/10 border border-amber-500/30 rounded-lg px-4 py-3">
                  <p className="text-amber-300 text-sm font-medium">Save this key now. It will never be shown again.</p>
                  <p className="text-amber-200/70 text-xs mt-1">
                    Store it in a secure location (e.g. a password manager or environment variable). Anyone with this key can submit price data on behalf of your server.
                  </p>
                </div>

                <button
                  onClick={handleClose}
                  className="w-full py-2.5 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 text-sm hover:bg-gray-700 transition-colors"
                >
                  Done
                </button>
              </div>
            ) : (
              /* ── Form state ── */
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label htmlFor="server-name" className="block text-sm font-medium text-gray-300 mb-1.5">
                    Server Name
                  </label>
                  <input
                    id="server-name"
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g. My SMP Server"
                    disabled={loading}
                    className="w-full bg-gray-950 border border-gray-700 rounded-lg px-3 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-emerald-600/60 focus:ring-1 focus:ring-emerald-600/40 disabled:opacity-50 transition-colors"
                  />
                </div>

                {error && (
                  <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-4 py-3">
                    <p className="text-red-300 text-sm">{error}</p>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-2.5 rounded-lg bg-emerald-600/20 border border-emerald-600/40 text-emerald-400 font-medium text-sm hover:bg-emerald-600/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {loading ? 'Registering...' : 'Register Server'}
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </>
  );
}
