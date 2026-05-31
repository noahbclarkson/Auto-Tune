export const DEFAULT_API_URL = "http://localhost:8080";

const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL?.trim();
export const API_BASE_URL = configuredApiUrl || DEFAULT_API_URL;
export const hasConfiguredApiUrl = Boolean(configuredApiUrl);

export interface ApiResult<T> {
  data: T | null;
  error: string | null;
}

export interface TruePrice {
  item: string;
  price: number;
  confidence: number;
  servers: number;
  anchored: boolean;
  /** ISO timestamp of when this item's true price was last recomputed */
  lastUpdated: string | null;
}

export interface TruePricesResponse {
  prices: TruePrice[];
  last_updated: string;
}

interface ApiTruePrice {
  item: string;
  price: number;
  confidence: number;
  servers: number;
  anchored: boolean;
  last_updated: string | null;
}

interface ApiTruePricesResponse {
  prices: ApiTruePrice[];
  last_updated: string | null;
}

export interface PriceHistoryPoint {
  price: number;
  server_count: number;
  timestamp: string;
}

export interface PriceHistoryResponse {
  item: string;
  history: PriceHistoryPoint[];
}

export interface ManagedServer {
  id: string;
  name: string;
  player_count: number;
  created_at: string;
  last_seen: string;
  last_submission_at?: string | null;
  last_submission_item_count?: number | null;
}

export interface ExchangeRate {
  server_id: string;
  name: string;
  rate: number;
  player_count: number;
  last_seen: string;
}

export interface ExchangeRatesResponse {
  base: string;
  rates: ExchangeRate[];
}

export interface ExchangeRateHistoryPoint {
  rate: number;
  server_count: number;
  timestamp: string;
}

export interface ExchangeRateHistoryResponse {
  server_id: string;
  server_name: string;
  history: ExchangeRateHistoryPoint[];
}

interface ApiExchangeRateHistoryPoint {
  rate: number;
  player_count: number;
  snapshot_at: string;
}

interface ApiExchangeRateHistoryResponse {
  server_id: string;
  server_name?: string;
  history: ApiExchangeRateHistoryPoint[];
}

export interface SubmitServerPricesRequest {
  serverId: string;
  apiKey: string;
  itemNames: string[];
  ratioMatrix: number[][];
  playerCount: number;
}

export interface SubmitServerPricesResponse {
  success: boolean;
  items_processed: number;
}

function buildApiUrl(path: string): string {
  const baseUrl = API_BASE_URL.replace(/\/$/, "");
  const endpoint = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl}/api${endpoint}`;
}

async function fetchJson<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  try {
    const response = await fetch(buildApiUrl(path), {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(init?.headers ?? {}),
      },
      cache: "no-store",
    });

    if (!response.ok) {
      const errorText = await response.text();
      return {
        data: null,
        error: errorText || `Request failed with status ${response.status}`,
      };
    }

    const payload = (await response.json()) as T;
    return { data: payload, error: null };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown network error";
    return { data: null, error: message };
  }
}

export async function fetchTruePrices(): Promise<ApiResult<TruePricesResponse>> {
  const result = await fetchJson<ApiTruePricesResponse>("/prices/true");
  if (!result.data) return { data: null, error: result.error };
  return {
    data: {
      prices: result.data.prices.map((price) => ({
        item: price.item,
        price: price.price,
        confidence: price.confidence,
        servers: price.servers,
        anchored: price.anchored,
        lastUpdated: price.last_updated,
      })),
      last_updated: result.data.last_updated ?? "",
    },
    error: null,
  };
}

export async function fetchPriceHistory(item: string): Promise<ApiResult<PriceHistoryResponse>> {
  const encodedItem = encodeURIComponent(item);
  return fetchJson<PriceHistoryResponse>(`/prices/history/${encodedItem}`);
}

export async function fetchServers(): Promise<ApiResult<ManagedServer[]>> {
  return fetchJson<ManagedServer[]>("/servers");
}

export async function fetchExchangeRates(): Promise<ApiResult<ExchangeRatesResponse>> {
  return fetchJson<ExchangeRatesResponse>("/servers/exchange-rates");
}

export async function fetchExchangeRateHistory(serverId: string): Promise<ApiResult<ExchangeRateHistoryResponse>> {
  const result = await fetchJson<ApiExchangeRateHistoryResponse>(`/servers/${serverId}/exchange-rate-history`);
  if (!result.data) return { data: null, error: result.error };
  return {
    data: {
      server_id: result.data.server_id,
      server_name: result.data.server_name ?? result.data.server_id,
      history: result.data.history.map((point) => ({
        rate: point.rate,
        server_count: point.player_count,
        timestamp: point.snapshot_at,
      })),
    },
    error: null,
  };
}

export async function registerServer(
  name: string,
): Promise<ApiResult<{ server_id: string; api_key: string }>> {
  return fetchJson<{ server_id: string; api_key: string }>("/servers/register", {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}

export async function submitServerPrices(
  payload: SubmitServerPricesRequest,
): Promise<ApiResult<SubmitServerPricesResponse>> {
  return fetchJson<SubmitServerPricesResponse>(`/servers/${payload.serverId}/prices`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${payload.apiKey}`,
    },
    body: JSON.stringify({
      item_names: payload.itemNames,
      ratio_matrix: payload.ratioMatrix,
      player_count: payload.playerCount,
    }),
  });
}
