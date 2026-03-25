import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_API_URL,
  fetchPriceHistory,
  fetchTruePrices,
  type TruePricesResponse,
} from './api-client';

describe('api-client reliability', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns validation error when item name is empty', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    const result = await fetchPriceHistory('   ');

    expect(result.data).toBeNull();
    expect(result.error).toBe('Item name is required');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('returns error for empty successful response bodies', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );

    const result = await fetchTruePrices();

    expect(result.data).toBeNull();
    expect(result.error).toBe('API returned an empty response body');
  });

  it('returns parsed payload for valid JSON', async () => {
    const payload: TruePricesResponse = {
      prices: [
        { item: 'diamond', price: 120, confidence: 0.88, servers: 3 },
      ],
      last_updated: '2026-02-26T00:00:00.000Z',
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchTruePrices();

    expect(result.error).toBeNull();
    expect(result.data).toEqual(payload);
  });

  it('encodes item names before calling history endpoint', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ item: 'gold ingot', history: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await fetchPriceHistory('gold ingot');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      `${DEFAULT_API_URL}/api/prices/history/gold%20ingot`,
      expect.objectContaining({ cache: 'no-store' }),
    );
  });
});
