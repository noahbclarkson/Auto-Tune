export function getApiBase() {
  if (typeof window === 'undefined') return '';
  const port = window.location.port;
  if (port === '8989') return '';
  return `${window.location.protocol}//${window.location.hostname}:8989`;
}

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`API returned ${res.status}`);
  return res.json();
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error ?? `API returned ${res.status}`);
  }
  return res.json();
}

async function patchJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'PATCH' });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error ?? `API returned ${res.status}`);
  }
  return res.json();
}

async function deleteJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'DELETE' });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
    throw new Error(err.error ?? `API returned ${res.status}`);
  }
  return res.json();
}

export interface ItemDto {
  id: number;
  material: string;
  displayName: string;
  price: number;
  buyPrice: number;
  sellPrice: number;
  bpd: number;
  spd: number;
  section: string;
  buyable: boolean | null;
  hasCustomData: boolean;
  change24h: number;
}

export interface Stats {
  totalItems: number;
  onlinePlayers: number;
  serverName: string;
  timestamp: number;
}

export interface PriceHistoryDto {
  price: number;
  buyVolume: number;
  sellVolume: number;
  bpd: number;
  spd: number;
  timestamp: number;
}

export interface EconomySnapshotDto {
  gdp: number;
  totalDebt: number;
  activeLoans: number;
  playerCount: number;
  averagePriceChange: number;
  transactionVolume: number;
  timestamp: number;
}

export interface GdpData {
  gdp: number;
  timestamp: number;
}

export interface InflationData {
  averagePriceChange: number;
  label: string;
  timestamp: number;
}

export interface DebtData {
  totalDebt: number;
  activeLoans: number;
  debtPerCapita: number;
  timestamp: number;
}

export interface TrendDto {
  itemId: number;
  material: string;
  displayName: string;
  direction: 'UP' | 'DOWN' | 'STABLE';
  percentChange: number;
  label: string;
}

export interface ItemTrendDto {
  direction: 'UP' | 'DOWN' | 'STABLE';
  streak: number;
  percentChange: number;
  /** Estimated price in 24 hours (linear extrapolation from recent velocity) */
  projected24h: number;
}

export interface TransactionFeedDto {
  id: number;
  itemId: number;
  itemName: string;
  type: 'BUY' | 'SELL';
  amount: number;
  pricePerUnit: number;
  totalPrice: number;
  timestamp: number;
}

export interface AnonLoanDto {
  index: number;
  principal: number;
  balance: number;
  rate: number;
  status: string;
  createdAt: number;
  dueDate: number;
  overdue: boolean;
}

export interface LoanStatsDto {
  totalActive: number;
  totalPrincipal: number;
  totalBalance: number;
  avgRate: number;
  overdueCount: number;
}

export interface LeaderboardEntryDto {
  rank: number;
  username: string;
  totalTraded: number;
  totalBought: number;
  totalSold: number;
  transactionCount: number;
}

export interface VolumeMultiplierDto {
  multiplier: number;
  timestamp: number;
}

export interface HoldingDto {
  itemId: number;
  material: string;
  displayName: string;
  section: string;
  netQuantity: number;
  avgBuyPrice: number;
  currentPrice: number;
  currentValue: number;
  unrealizedPnl: number;
  pnlPct: number;
  realizedPnl: number;
}

export interface ActiveLoanDto {
  loanId: string;
  principal: number;
  currentBalance: number;
  interestRate: number;
  createdAt: number;
  dueDate: number;
  status: string;
}

export interface PortfolioDto {
  playerName: string;
  uuid: string | null;
  vaultBalance: number;
  holdingsValue: number;
  totalDebt: number;
  netWorth: number;
  totalRealizedPnl: number;
  creditScore: number;
  transactionCount: number;
  holdings: HoldingDto[];
  activeLoans: ActiveLoanDto[];
}

export interface AdminHealthDto {
  frozen: boolean;
  gdp: number;
  totalDebt: number;
  activeLoans: number;
  debtGdpRatio: number;
  debtGdpLabel: string;
  circuitBreakerTier: string;
  interestMultiplier: number;
  buyPct: number;
  sellPct: number;
  avgBpd: number;
  avgSpd: number;
  globalVolumeMultiplier: number;
  inflationLabel: string;
  topVolatile: Array<{
    id: number;
    material: string;
    displayName: string;
    pctChange: number;
  }>;
  topUndersold: Array<{
    id: number;
    material: string;
    displayName: string;
    pctChange: number;
  }>;
  avgVolatility: number;
  timestamp: number;
}

export interface AlertDto {
  id: string;
  playerUuid: string;
  itemId: number;
  itemName: string;
  alertType: 'ABOVE' | 'BELOW';
  targetPrice: number;
  currentPrice: number;
  enabled: boolean;
  triggered: boolean;
  createdAt: number;
  triggeredAt: number | null;
}

export const api = {
  items: {
    list: (base: string) => fetchJson<ItemDto[]>(`${base}/api/items`),
    get: (base: string, id: number) => fetchJson<ItemDto>(`${base}/api/items/${id}`),
    history: (base: string, id: number, limit = 100) =>
      fetchJson<PriceHistoryDto[]>(`${base}/api/items/${id}/history?limit=${limit}`),
    transactions: (base: string, id: number, limit = 50) =>
      fetchJson<TransactionFeedDto[]>(`${base}/api/items/${id}/transactions?limit=${limit}`),
    trend: (base: string, id: number) =>
      fetchJson<ItemTrendDto>(`${base}/api/items/${id}/trend`),
  },
  stats: (base: string) => fetchJson<Stats>(`${base}/api/stats`),
  prices: (base: string) => fetchJson<Record<number, number>>(`${base}/api/prices`),
  spreads: (base: string) =>
    fetchJson<Record<number, { bpd: number; spd: number }>>(`${base}/api/spreads`),
  transactions: {
    recent: (base: string, limit = 50) =>
      fetchJson<TransactionFeedDto[]>(`${base}/api/transactions?limit=${limit}`),
  },
  economy: {
    gdp: (base: string) => fetchJson<GdpData>(`${base}/api/economy/gdp`),
    inflation: (base: string) => fetchJson<InflationData>(`${base}/api/economy/inflation`),
    debt: (base: string) => fetchJson<DebtData>(`${base}/api/economy/debt`),
    history: (base: string, limit = 100) =>
      fetchJson<EconomySnapshotDto[]>(`${base}/api/economy/history?limit=${limit}`),
    trends: (base: string) => fetchJson<TrendDto[]>(`${base}/api/economy/trends`),
    volumeMultiplier: (base: string) =>
      fetchJson<VolumeMultiplierDto>(`${base}/api/economy/volume-multiplier`),
  },
  loans: {
    list: (base: string) => fetchJson<AnonLoanDto[]>(`${base}/api/loans`),
    stats: (base: string) => fetchJson<LoanStatsDto>(`${base}/api/loans/stats`),
  },
  leaderboard: (base: string, limit = 20) =>
    fetchJson<LeaderboardEntryDto[]>(`${base}/api/leaderboard?limit=${limit}`),
  portfolio: {
    get: (base: string, playerName: string) =>
      fetchJson<PortfolioDto>(`${base}/api/portfolio/${encodeURIComponent(playerName)}`),
    transactions: (base: string, playerName: string, limit = 50) =>
      fetchJson<TransactionFeedDto[]>(
        `${base}/api/portfolio/${encodeURIComponent(playerName)}/transactions?limit=${limit}`
      ),
  },
  admin: {
    health: (base: string) => fetchJson<AdminHealthDto>(`${base}/api/admin/health`),
  },
  alerts: {
    list: (base: string, playerName: string) =>
      fetchJson<AlertDto[]>(`${base}/api/alerts/${encodeURIComponent(playerName)}`),
    create: (base: string, playerName: string, itemId: number, alertType: 'ABOVE' | 'BELOW', targetPrice: number) =>
      postJson<AlertDto>(`${base}/api/alerts`, { playerName, itemId, alertType, targetPrice }),
    remove: (base: string, alertId: string, playerName: string) =>
      deleteJson<{ success: boolean }>(`${base}/api/alerts/${alertId}?playerName=${encodeURIComponent(playerName)}`),
    toggle: (base: string, alertId: string, playerName: string) =>
      patchJson<AlertDto>(`${base}/api/alerts/${alertId}/toggle?playerName=${encodeURIComponent(playerName)}`),
    rearm: (base: string, alertId: string, playerName: string) =>
      patchJson<AlertDto>(`${base}/api/alerts/${alertId}/rearm?playerName=${encodeURIComponent(playerName)}`),
  },
};
