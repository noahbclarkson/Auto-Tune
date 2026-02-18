'use client';

import { useEffect, useState, useCallback } from 'react';
import { useAppContext } from '@/context/app-context';
import { Header } from '@/components/layout/header';
import { LoansStatsBar } from '@/components/loans/loans-stats-bar';
import { LoansTable } from '@/components/loans/loans-table';
import { api, type Stats, type AnonLoanDto, type LoanStatsDto } from '@/lib/api';

export default function LoansPage() {
  const { apiBase } = useAppContext();
  const [stats, setStats] = useState<Stats | null>(null);
  const [loans, setLoans] = useState<AnonLoanDto[]>([]);
  const [loanStats, setLoanStats] = useState<LoanStatsDto | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [statsData, loansData, loanStatsData] = await Promise.all([
        api.stats(apiBase),
        api.loans.list(apiBase).catch(() => []),
        api.loans.stats(apiBase).catch(() => null),
      ]);
      setStats(statsData);
      setLoans(loansData as AnonLoanDto[]);
      setLoanStats(loanStatsData as LoanStatsDto | null);
    } catch {
      // silently fail
    }
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  return (
    <div className="min-h-screen bg-background">
      <Header totalItems={stats?.totalItems ?? 0} onlinePlayers={stats?.onlinePlayers ?? 0} />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <h2 className="text-2xl font-bold text-foreground">Loans</h2>
        <p className="text-sm text-muted-foreground">
          Loan data is displayed anonymously. No player names or identifiers are shown.
        </p>

        {loanStats && <LoansStatsBar stats={loanStats} />}
        <LoansTable loans={loans} />
      </main>
    </div>
  );
}
