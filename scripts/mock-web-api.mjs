#!/usr/bin/env node
import http from 'node:http';
import { URL } from 'node:url';

const port = Number(process.env.PORT || 8989);
const now = Date.now();

const items = [
  { id: 1, material: 'DIAMOND', displayName: 'Diamond', price: 1250, buyPrice: 1400, sellPrice: 1100, bpd: 0.12, spd: 0.12, section: 'Ore', buyable: true, hasCustomData: false, change24h: 2.1 },
  { id: 2, material: 'IRON_INGOT', displayName: 'Iron Ingot', price: 120, buyPrice: 132, sellPrice: 108, bpd: 0.1, spd: 0.1, section: 'Ore', buyable: true, hasCustomData: false, change24h: -1.2 },
  { id: 3, material: 'OAK_LOG', displayName: 'Oak Log', price: 24, buyPrice: 27, sellPrice: 21, bpd: 0.12, spd: 0.12, section: 'Blocks', buyable: true, hasCustomData: false, change24h: 0.4 }
];

const json = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(body));
};

const series = (base) => Array.from({ length: 50 }, (_, i) => ({
  price: Math.max(1, base + Math.sin(i / 5) * base * 0.1),
  buyVolume: Math.floor(20 + Math.random() * 40),
  sellVolume: Math.floor(20 + Math.random() * 40),
  bpd: 0.1,
  spd: 0.1,
  timestamp: now - (50 - i) * 3600_000,
}));

const server = http.createServer((req, res) => {
  if (!req.url) return json(res, 400, { error: 'Bad request' });
  const url = new URL(req.url, `http://localhost:${port}`);
  const path = url.pathname;

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    });
    return res.end();
  }

  if (path === '/health') return json(res, 200, { status: 'ok', mode: 'mock' });
  if (path === '/api/items') return json(res, 200, items);

  const itemDetail = path.match(/^\/api\/items\/(\d+)$/);
  if (itemDetail) {
    const id = Number(itemDetail[1]);
    const item = items.find(i => i.id === id);
    if (item) return json(res, 200, item);
    return json(res, 404, { error: 'Item not found', id });
  }
  if (path === '/api/stats') return json(res, 200, { totalItems: items.length, onlinePlayers: 17, serverName: 'Local E2E', timestamp: now });
  if (path === '/api/prices') return json(res, 200, Object.fromEntries(items.map(i => [i.id, i.price])));
  if (path === '/api/spreads') return json(res, 200, Object.fromEntries(items.map(i => [i.id, { bpd: i.bpd, spd: i.spd }])));
  if (path === '/api/transactions') return json(res, 200, [
    { id: 1, itemId: 1, itemName: 'Diamond', type: 'BUY', amount: 3, pricePerUnit: 1400, totalPrice: 4200, timestamp: now - 50000 },
    { id: 2, itemId: 2, itemName: 'Iron Ingot', type: 'SELL', amount: 64, pricePerUnit: 108, totalPrice: 6912, timestamp: now - 90000 }
  ]);
  if (path === '/api/economy/gdp') return json(res, 200, { gdp: 243920, timestamp: now });
  if (path === '/api/economy/inflation') return json(res, 200, { averagePriceChange: 1.14, label: 'Moderate Inflation', timestamp: now });
  if (path === '/api/economy/debt') return json(res, 200, { totalDebt: 85000, activeLoans: 12, debtPerCapita: 5000, timestamp: now });
  if (path === '/api/economy/history') return json(res, 200, Array.from({ length: 30 }, (_, i) => ({
    gdp: 180000 + i * 1500,
    totalDebt: 90000 - i * 500,
    activeLoans: 12,
    playerCount: 10 + (i % 6),
    averagePriceChange: 0.5 + (i % 5) * 0.1,
    transactionVolume: 700 + i * 15,
    timestamp: now - (30 - i) * 3600_000,
  })));
  if (path === '/api/economy/trends') return json(res, 200, [
    { itemId: 1, material: 'DIAMOND', displayName: 'Diamond', direction: 'UP', percentChange: 2.4, label: 'Rising' },
    { itemId: 2, material: 'IRON_INGOT', displayName: 'Iron Ingot', direction: 'DOWN', percentChange: -1.2, label: 'Falling' }
  ]);
  if (path === '/api/economy/volume-multiplier') return json(res, 200, { multiplier: 1.05, timestamp: now });
  if (path === '/api/loans') return json(res, 200, [
    { index: 1, principal: 10000, balance: 9400, rate: 0.05, status: 'ACTIVE', createdAt: now - 86400_000, dueDate: now + 6 * 86400_000, overdue: false }
  ]);
  if (path === '/api/loans/stats') return json(res, 200, { totalActive: 12, totalPrincipal: 120000, totalBalance: 85000, avgRate: 0.06, overdueCount: 1 });
  if (path === '/api/leaderboard') return json(res, 200, [
    { rank: 1, username: 'Alice', totalTraded: 540000, totalBought: 300000, totalSold: 240000, transactionCount: 440 },
    { rank: 2, username: 'Bob', totalTraded: 410000, totalBought: 200000, totalSold: 210000, transactionCount: 390 }
  ]);

  const itemHistory = path.match(/^\/api\/items\/(\d+)\/history$/);
  if (itemHistory) {
    const id = Number(itemHistory[1]);
    const item = items.find(i => i.id === id);
    return json(res, 200, series(item?.price ?? 100));
  }

  const itemTx = path.match(/^\/api\/items\/(\d+)\/transactions$/);
  if (itemTx) {
    const id = Number(itemTx[1]);
    const item = items.find(i => i.id === id);
    return json(res, 200, [
      { id: 91, itemId: id, itemName: item?.displayName ?? 'Unknown', type: 'BUY', amount: 5, pricePerUnit: (item?.buyPrice ?? 10), totalPrice: (item?.buyPrice ?? 10) * 5, timestamp: now - 30000 }
    ]);
  }

  const itemTrend = path.match(/^\/api\/items\/(\d+)\/trend$/);
  if (itemTrend) return json(res, 200, { direction: 'UP', streak: 4, percentChange: 1.8 });

  return json(res, 404, { error: 'Not Found', path });
});

server.listen(port, () => {
  console.log(`[mock-web-api] listening on :${port}`);
});
