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

export interface WhatMovedEntry {
  itemId: number;
  displayName: string;
  percentChange: number;
  direction: string;
  emoji: string;
  explanation: string;
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

export interface PnLHistoryDto {
  timestamp: number;
  dayLabel: string;
  netPnl: number;
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

export interface PlayerMarketImpactDto {
  playerName: string;
  weeklyImpactPct: number;       // % of weekly market price movement this player drove
  monthlyImpactPct: number;      // % of monthly market price movement this player drove
  weeklyRank: number;             // player's rank this week (1 = most impactful)
  topItems: MarketImpactItemDto[];
}

export interface MarketImpactItemDto {
  itemName: string;
  material: string;
  playerVolume: number;           // player's units traded this period
  totalVolume: number;            // total market volume this period
  playerSharePct: number;         // playerVolume / totalVolume × 100
  priceChangePct: number;         // item's price change this period
  playerImpactPct: number;        // player's contribution to price change
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

export interface AdminAuditEntryDto {
  id: number;
  timestamp: string;
  adminUuid: string | null;
  adminName: string;
  actionType: string;
  target: string | null;
  oldValue: string | null;
  newValue: string | null;
  details: string | null;
  summary: string;
}

export interface AdminAuditResponseDto {
  entries: AdminAuditEntryDto[];
  count: number;
}

// Config health entry — single tunable parameter
export interface ConfigEntry {
  current: number | boolean;
  default?: number;
  rangeMin?: number;
  rangeMax?: number;
  unit: string;
  label: string;
}

export interface AdminConfigDto {
  spread: {
    baseSpread: ConfigEntry;
    volumeImpact: ConfigEntry;
    playerImpact: ConfigEntry;
  };
  loans: {
    baseInterestRate: ConfigEntry;
    debtGdpTier3Ratio: ConfigEntry;
    postDefaultCooldownHours: ConfigEntry;
    counterCyclical: boolean;
    singleLoanGdpCap: number;
  };
  economy: {
    tradeWindowDays: ConfigEntry;
    maxPriceChangePercent: ConfigEntry;
    minBuyQuantity: ConfigEntry;
    minSellQuantity: ConfigEntry;
  };
  marketDigest: {
    enabled: boolean;
    interval: string;
    includeTopMovers: boolean;
    includeHealthStats: boolean;
    includeActiveEvents: boolean;
    includeLoanStats: boolean;
  };
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

export interface PlayerBadgeDto {
  badgeType: string;
  displayName: string;
  description: string;
  material: string;
  color: string;
  rarity: 'common' | 'uncommon' | 'rare' | 'epic' | 'legendary';
  earnedAt: string; // ISO-8601
}

export interface PlayerBadgesResponse {
  playerName: string;
  earnedCount: number;
  totalPossible: number;
  badges: PlayerBadgeDto[];
}

export interface AuctionOrderDto {
  id: string;
  playerUuid: string;
  material: string;
  price: number;
  originalQuantity: number;
  remainingQuantity: number;
  filledQuantity: number;
  side: 'BUY' | 'SELL';
  status: 'ACTIVE' | 'FILLED' | 'CANCELLED' | 'EXPIRED';
  createdAt: number;
  expiresAt: number;
  isActive: boolean;
}

export interface AuctionFillDto {
  id: string;
  buyOrderId: string;
  sellOrderId: string;
  quantity: number;
  price: number;
  total: number;
  filledAt: number;
}

export interface AuctionMaterialDto {
  material: string;
  totalOrders: number;
  buyOrders: number;
  sellOrders: number;
  bestBid: number | null;
  bestAsk: number | null;
}

export interface PriceChangeDto {
  timestamp: number;
  currentPrice: number;
  previousPrice: number;
  percentChange: number;
  bpd: number;
  spd: number;
  totalVolume: number;
  volumeVsNormal: number;
  eventMultiplier: number;
  attribution: string;
  attributionKey: 'NORMAL' | 'EVENT' | 'VOLUME' | 'TREND' | 'STABLE';
  hasActiveEvent: boolean;
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
    attribution: (base: string, id: number, limit = 50) =>
      fetchJson<PriceChangeDto[]>(`${base}/api/items/${id}/attribution?limit=${limit}`),
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
    whatMoved: (base: string) => fetchJson<WhatMovedEntry[]>(`${base}/api/economy/what-moved`),
    volumeMultiplier: (base: string) =>
      fetchJson<VolumeMultiplierDto>(`${base}/api/economy/volume-multiplier`),
  },
  loans: {
    list: (base: string) => fetchJson<AnonLoanDto[]>(`${base}/api/loans`),
    stats: (base: string) => fetchJson<LoanStatsDto>(`${base}/api/loans/stats`),
  },
  leaderboard: (base: string, limit = 20, period = 'all') =>
    fetchJson<LeaderboardEntryDto[]>(
      `${base}/api/leaderboard?limit=${limit}&period=${period}`
    ),
  portfolio: {
    get: (base: string, playerName: string) =>
      fetchJson<PortfolioDto>(`${base}/api/portfolio/${encodeURIComponent(playerName)}`),
    transactions: (base: string, playerName: string, limit = 50) =>
      fetchJson<TransactionFeedDto[]>(
        `${base}/api/portfolio/${encodeURIComponent(playerName)}/transactions?limit=${limit}`
      ),
    pnlHistory: (base: string, playerName: string) =>
      fetchJson<PnLHistoryDto[]>(
        `${base}/api/portfolio/${encodeURIComponent(playerName)}/pnl-history`
      ),
    marketImpact: (base: string, playerName: string) =>
      fetchJson<PlayerMarketImpactDto>(
        `${base}/api/portfolio/${encodeURIComponent(playerName)}/market-impact`
      ),
  },
  admin: {
    health: (base: string) => fetchJson<AdminHealthDto>(`${base}/api/admin/health`),
    config: (base: string) => fetchJson<AdminConfigDto>(`${base}/api/admin/config`),
    audit: (base: string, limit = 20) =>
      fetchJson<AdminAuditResponseDto>(`${base}/api/admin/audit?limit=${limit}`),
  },
  badges: {
    player: (base: string, playerName: string) =>
      fetchJson<PlayerBadgesResponse>(
        `${base}/api/badges/player/${encodeURIComponent(playerName)}`
      ),
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
  shop: {
    favorites: {
      list: (base: string, playerName: string) =>
        fetchJson<{ playerName: string; favorites: ItemDto[]; count: number }>(
          `${base}/api/shop/favorites/${encodeURIComponent(playerName)}`
        ),
      toggle: (base: string, playerName: string, itemId: number) =>
        patchJson<{ playerName: string; itemId: number; favorited: boolean }>(
          `${base}/api/shop/favorites/${encodeURIComponent(playerName)}/${itemId}`
        ),
    },
  },
  auction: {
    stats: (base: string) =>
      fetchJson<{
        totalOrders: number;
        totalFills: number;
        activeOrders: number;
        materialsWithOrders: number;
        bookSummary: Record<string, { bestBid: number | null; bestAsk: number | null; bidCount: number; askCount: number }>;
        recentFills: Array<{ id: string; quantity: number; price: number; filledAt: number }>;
      }>(`${base}/api/auction/stats`),
    orders: (base: string, material?: string) =>
      fetchJson<AuctionOrderDto[]>(`${base}/api/auction/orders${material ? `?material=${encodeURIComponent(material)}` : ''}`),
    fills: (base: string, limit = 50) =>
      fetchJson<AuctionFillDto[]>(`${base}/api/auction/fills?limit=${limit}`),
    order: (base: string, orderId: string) =>
      fetchJson<AuctionOrderDto>(`${base}/api/auction/orders/${encodeURIComponent(orderId)}`),
    fillsForOrder: (base: string, orderId: string) =>
      fetchJson<Array<{ id: string; quantity: number; price: number; total: number; filledAt: number }>>(
        `${base}/api/auction/fills/${encodeURIComponent(orderId)}`
      ),
    materials: (base: string) =>
      fetchJson<AuctionMaterialDto[]>(`${base}/api/auction/materials`),
    player: (base: string, playerName: string) =>
      fetchJson<AuctionOrderDto[]>(`${base}/api/auction/player/${encodeURIComponent(playerName)}`),
    depth: (base: string, material: string, depth = 5) =>
      fetchJson<{
        material: string;
        depth: number;
        bids: Array<{ id: string; price: number; remainingQuantity: number; totalValue: number }>;
        asks: Array<{ id: string; price: number; remainingQuantity: number; totalValue: number }>;
      }>(`${base}/api/auction/depth?material=${encodeURIComponent(material)}&depth=${depth}`),
    fillRate: (base: string, days = 7) =>
      fetchJson<Array<{ date: string; count: number }>>(`${base}/api/auction/fill-rate?days=${days}`),
  },
};
