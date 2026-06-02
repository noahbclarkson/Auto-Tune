'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { formatLargeCurrency } from '@/lib/format';
import type { LoanStatsDto } from '@/lib/api';

interface LoansStatsBarProps {
  stats: LoanStatsDto;
}

export function LoansStatsBar({ stats }: LoansStatsBarProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Total Active</p>
          <p className="text-xl font-bold text-foreground">{stats.totalActive}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Total Principal</p>
          <p className="text-xl font-bold text-foreground">{formatLargeCurrency(stats.totalPrincipal)}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Total Outstanding</p>
          <p className="text-xl font-bold text-foreground">{formatLargeCurrency(stats.totalBalance)}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Overdue</p>
          <div className="text-xl font-bold text-foreground">
            {stats.overdueCount > 0 ? (
              <Badge variant="destructive" className="text-sm">
                {stats.overdueCount}
              </Badge>
            ) : (
              '0'
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
