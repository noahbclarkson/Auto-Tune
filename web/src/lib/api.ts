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
};
