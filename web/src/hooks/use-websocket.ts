'use client';

import { useEffect, useRef, useState, useCallback } from 'react';

interface PriceUpdateMessage {
  type: 'price_update';
  timestamp: number;
  prices: Record<string, number>;
}

export function useWebSocket(apiBase: string) {
  const [livePrices, setLivePrices] = useState<Map<number, number>>(new Map());
  const [isConnected, setIsConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const retriesRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const connect = useCallback(() => {
    if (typeof window === 'undefined') return;

    const wsBase = apiBase || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`;
    const wsUrl = `${wsBase.replace(/^http/, 'ws')}/ws/market`;

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        retriesRef.current = 0;
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as PriceUpdateMessage;
          if (data.type === 'price_update' && data.prices) {
            setLivePrices((prev) => {
              const next = new Map(prev);
              for (const [key, value] of Object.entries(data.prices)) {
                next.set(Number(key), value);
              }
              return next;
            });
          }
        } catch {
          // ignore malformed messages
        }
      };

      ws.onclose = () => {
        setIsConnected(false);
        wsRef.current = null;
        const delay = Math.min(1000 * Math.pow(2, retriesRef.current), 30000);
        retriesRef.current++;
        timerRef.current = setTimeout(connect, delay);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch {
      const delay = Math.min(1000 * Math.pow(2, retriesRef.current), 30000);
      retriesRef.current++;
      timerRef.current = setTimeout(connect, delay);
    }
  }, [apiBase]);

  useEffect(() => {
    connect();
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
      }
    };
  }, [connect]);

  return { livePrices, isConnected };
}
