'use client';

import { useAppContext } from '@/context/app-context';

export function LiveIndicator() {
  const { isWsConnected } = useAppContext();

  return (
    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <span
        className={`h-2 w-2 rounded-full ${
          isWsConnected ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'
        }`}
      />
      {isWsConnected ? 'Live' : 'Offline'}
    </div>
  );
}
