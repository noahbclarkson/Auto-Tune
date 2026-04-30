'use client';

import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import { getApiBase } from '@/lib/api';
import { useWebSocket } from '@/hooks/use-websocket';

interface AppContextValue {
  apiBase: string;
  theme: 'light' | 'dark';
  toggleTheme: () => void;
  livePrices: Map<number, number>;
  isWsConnected: boolean;
  playerName: string;
  setPlayerName: (name: string) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const apiBase = getApiBase();
  const [playerName, setPlayerNameState] = useState('');

  useEffect(() => {
    const stored = localStorage.getItem('autotune:player-name');
    if (stored) setPlayerNameState(stored);
  }, []);

  const setPlayerName = useCallback((name: string) => {
    setPlayerNameState(name);
    if (name) {
      localStorage.setItem('autotune:player-name', name);
    } else {
      localStorage.removeItem('autotune:player-name');
    }
  }, []);

  useEffect(() => {
    const stored = localStorage.getItem('theme');
    if (stored === 'dark' || stored === 'light') {
      setTheme(stored);
    } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      setTheme('dark');
    }
  }, []);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }, []);

  const { livePrices, isConnected } = useWebSocket(apiBase);

  return (
    <AppContext.Provider
      value={{
        apiBase,
        theme,
        toggleTheme,
        livePrices,
        isWsConnected: isConnected,
        playerName,
        setPlayerName,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useAppContext() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useAppContext must be used within AppProvider');
  return ctx;
}
