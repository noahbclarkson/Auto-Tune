'use client';

import { useEffect, useRef, useState, useCallback } from 'react';

export interface LivePriceUpdate {
  price: number;
  buyPrice: number;
  sellPrice: number;
  bpd?: number;
  spd?: number;
}

interface PriceUpdateMessage {
  type: 'price_update';
  timestamp: number;
  prices: Record<string, LivePriceUpdate | number>;
}

export function useWebSocket(apiBase: string) {
  const [livePrices, setLivePrices] = useState<Map<number, LivePriceUpdate>>(new Map());
  const [isConnected, setIsConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const retriesRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const connect = useCallback(() => {
    if (typeof window === 'undefined') return;

    const apiUrl = new URL(apiBase || '/', window.location.href);
    apiUrl.protocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    apiUrl.pathname = '/ws/market';
    apiUrl.search = '';
    apiUrl.hash = '';

    try {
      const ws = new WebSocket(apiUrl.toString());
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
                next.set(
                  Number(key),
                  typeof value === 'number'
                    ? { price: value, buyPrice: value, sellPrice: value }
                    : value,
                );
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
