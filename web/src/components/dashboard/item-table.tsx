'use client';

import { useState, useMemo, useEffect } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Pagination } from '@/components/ui/pagination';
import { Search, ArrowUpDown, ArrowUp, ArrowDown } from 'lucide-react';
import { formatCurrency, formatPercent } from '@/lib/format';
import type { ItemDto } from '@/lib/api';
import type { TrendDto } from '@/lib/api';

export type { ItemDto };

type SortField = 'displayName' | 'price' | 'buyPrice' | 'sellPrice' | 'change24h';
type SortDir = 'asc' | 'desc';

interface ItemTableProps {
  items: ItemDto[];
  selectedItemId?: number | null;
  onSelectItem?: (item: ItemDto) => void;
  trends?: TrendDto[];
  linkToDetail?: boolean;
  pageSize?: number;
}

export function ItemTable({
  items,
  selectedItemId,
  onSelectItem,
  trends,
  linkToDetail = false,
  pageSize = 25,
}: ItemTableProps) {
  const [search, setSearch] = useState('');
  const [activeSection, setActiveSection] = useState<string | null>(null);
  const [sortField, setSortField] = useState<SortField>('displayName');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [currentPage, setCurrentPage] = useState(1);

  const sections = useMemo(() => {
    const sectionSet = new Set(items.map((i) => i.section));
    return Array.from(sectionSet).sort();
  }, [items]);

  const filtered = useMemo(() => {
    let result = items;
    if (activeSection) {
      result = result.filter((i) => i.section === activeSection);
    }
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(
        (i) =>
          i.displayName.toLowerCase().includes(q) ||
          i.material.toLowerCase().includes(q)
      );
    }
    result = [...result].sort((a, b) => {
      const aVal = a[sortField];
      const bVal = b[sortField];
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return sortDir === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }
      return sortDir === 'asc'
        ? (aVal as number) - (bVal as number)
        : (bVal as number) - (aVal as number);
    });
    return result;
  }, [items, activeSection, search, sortField, sortDir]);

  const totalPages = Math.ceil(filtered.length / pageSize);
  const paged = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  useEffect(() => {
    setCurrentPage(1);
  }, [search, activeSection, sortField, sortDir]);

  function handleSort(field: SortField) {
    if (sortField === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDir('asc');
    }
  }

  function SortIcon({ field }: { field: SortField }) {
    if (sortField !== field) return <ArrowUpDown className="h-3 w-3 text-muted-foreground" />;
    return sortDir === 'asc' ? (
      <ArrowUp className="h-3 w-3 text-primary" />
    ) : (
      <ArrowDown className="h-3 w-3 text-primary" />
    );
  }

  const trendMap = useMemo(() => {
    if (!trends) return new Map<number, TrendDto>();
    const m = new Map<number, TrendDto>();
    for (const t of trends) m.set(t.itemId, t);
    return m;
  }, [trends]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle>Shop Items</CardTitle>
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search items..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-9 rounded-md border border-border bg-background pl-8 pr-3 text-sm outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
        </div>
        <div className="flex flex-wrap gap-1.5 pt-2">
          <button
            onClick={() => setActiveSection(null)}
            className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
              activeSection === null
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
            }`}
          >
            All
          </button>
          {sections.map((section) => (
            <button
              key={section}
              onClick={() => setActiveSection(activeSection === section ? null : section)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium capitalize transition-colors ${
                activeSection === section
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
              }`}
            >
              {section}
            </button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        <div className="rounded-md border border-border overflow-x-auto -mx-4 sm:mx-0">
          <table className="w-full text-sm min-w-[640px]">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th
                  className="px-3 py-2.5 text-left font-medium text-muted-foreground cursor-pointer select-none"
                  onClick={() => handleSort('displayName')}
                >
                  <span className="flex items-center gap-1">
                    Item <SortIcon field="displayName" />
                  </span>
                </th>
                <th
                  className="px-3 py-2.5 text-right font-medium text-muted-foreground cursor-pointer select-none"
                  onClick={() => handleSort('price')}
                >
                  <span className="flex items-center justify-end gap-1">
                    Base <SortIcon field="price" />
                  </span>
                </th>
                <th
                  className="px-3 py-2.5 text-right font-medium text-muted-foreground cursor-pointer select-none"
                  onClick={() => handleSort('buyPrice')}
                >
                  <span className="flex items-center justify-end gap-1">
                    Buy <SortIcon field="buyPrice" />
                  </span>
                </th>
                <th
                  className="px-3 py-2.5 text-right font-medium text-muted-foreground cursor-pointer select-none"
                  onClick={() => handleSort('sellPrice')}
                >
                  <span className="flex items-center justify-end gap-1">
                    Sell <SortIcon field="sellPrice" />
                  </span>
                </th>
                <th
                  className="px-3 py-2.5 text-right font-medium text-muted-foreground cursor-pointer select-none"
                  onClick={() => handleSort('change24h')}
                >
                  <span className="flex items-center justify-end gap-1">
                    24h <SortIcon field="change24h" />
                  </span>
                </th>
                <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Spread</th>
                {trends && (
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Trend</th>
                )}
                <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Section</th>
              </tr>
            </thead>
            <tbody>
              {paged.map((item) => {
                const spread = ((item.bpd + item.spd) * 100).toFixed(1);
                const trend = trendMap.get(item.id);
                const row = (
                  <tr
                    key={item.id}
                    onClick={() => onSelectItem?.(item)}
                    className={`border-b border-border last:border-0 cursor-pointer transition-colors hover:bg-muted/50 ${
                      selectedItemId === item.id ? 'bg-primary/5' : ''
                    }`}
                  >
                    <td className="px-3 py-2.5 font-medium text-foreground">
                      {linkToDetail ? (
                        <a
                          href={`/items/detail/?id=${item.id}`}
                          className="hover:text-primary transition-colors"
                        >
                          {item.displayName}
                        </a>
                      ) : (
                        item.displayName
                      )}
                      {item.buyable === false && (
                        <Badge variant="warning" className="ml-2 text-[10px]">
                          Sell Only
                        </Badge>
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-right text-muted-foreground">
                      {formatCurrency(item.price)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-emerald-600 dark:text-emerald-400 font-medium">
                      {formatCurrency(item.buyPrice)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-amber-600 dark:text-amber-400 font-medium">
                      {formatCurrency(item.sellPrice)}
                    </td>
                    <td
                      className={`px-3 py-2.5 text-right font-medium ${
                        item.change24h > 0
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : item.change24h < 0
                          ? 'text-red-500 dark:text-red-400'
                          : 'text-muted-foreground'
                      }`}
                    >
                      {formatPercent(item.change24h)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-muted-foreground">{spread}%</td>
                    {trends && (
                      <td className="px-3 py-2.5 text-center">
                        {trend && (
                          <Badge
                            variant={
                              trend.direction === 'UP'
                                ? 'success'
                                : trend.direction === 'DOWN'
                                ? 'destructive'
                                : 'secondary'
                            }
                            className="text-[10px]"
                          >
                            {trend.direction}
                          </Badge>
                        )}
                      </td>
                    )}
                    <td className="px-3 py-2.5 text-center">
                      <Badge variant="secondary" className="capitalize text-[10px]">
                        {item.section}
                      </Badge>
                    </td>
                  </tr>
                );
                return row;
              })}
              {paged.length === 0 && (
                <tr>
                  <td colSpan={trends ? 8 : 7} className="px-3 py-8 text-center text-muted-foreground">
                    No items found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && (
          <div className="mt-4">
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={setCurrentPage}
            />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
