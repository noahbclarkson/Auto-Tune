'use client';

import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { formatCurrency, formatDateTime } from '@/lib/format';
import type { AnonLoanDto } from '@/lib/api';

interface LoansTableProps {
  loans: AnonLoanDto[];
}

export function LoansTable({ loans }: LoansTableProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Active Loans</CardTitle>
      </CardHeader>
      <CardContent>
        {loans.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No active loans</p>
        ) : (
          <div className="rounded-md border border-border overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="px-3 py-2.5 text-left font-medium text-muted-foreground">#</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Principal</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Balance</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Rate</th>
                  <th className="px-3 py-2.5 text-center font-medium text-muted-foreground">Status</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Created</th>
                  <th className="px-3 py-2.5 text-right font-medium text-muted-foreground">Due Date</th>
                </tr>
              </thead>
              <tbody>
                {loans.map((loan) => (
                  <tr
                    key={loan.index}
                    className={`border-b border-border last:border-0 ${
                      loan.overdue ? 'border-l-2 border-l-red-500' : ''
                    }`}
                  >
                    <td className="px-3 py-2.5 text-muted-foreground">{loan.index}</td>
                    <td className="px-3 py-2.5 text-right text-foreground">
                      {formatCurrency(loan.principal)}
                    </td>
                    <td className="px-3 py-2.5 text-right font-medium text-foreground">
                      {formatCurrency(loan.balance)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-muted-foreground">
                      {(loan.rate * 100).toFixed(1)}%
                    </td>
                    <td className="px-3 py-2.5 text-center">
                      {loan.overdue ? (
                        <Badge variant="destructive" className="text-[10px]">
                          OVERDUE
                        </Badge>
                      ) : (
                        <Badge variant="success" className="text-[10px]">
                          ACTIVE
                        </Badge>
                      )}
                    </td>
                    <td className="px-3 py-2.5 text-right text-xs text-muted-foreground">
                      {formatDateTime(loan.createdAt)}
                    </td>
                    <td className="px-3 py-2.5 text-right text-xs text-muted-foreground">
                      {formatDateTime(loan.dueDate)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
