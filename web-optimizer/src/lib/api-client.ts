export const DEFAULT_API_URL = "http://localhost:3001";

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
}

export interface TruePricesResponse {
  prices: TruePrice[];
  last_updated: string;
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
  return fetchJson<TruePricesResponse>("/prices/true");
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

export async function registerServer(
  name: string,
  playerCount: number,
): Promise<ApiResult<{ id: string; api_key: string }>> {
  return fetchJson<{ id: string; api_key: string }>("/servers/register", {
    method: "POST",
    body: JSON.stringify({ name, player_count: playerCount }),
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
