import { formatDistanceToNow, format } from 'date-fns';

export function formatCurrency(value: number): string {
  return `$${value.toFixed(2)}`;
}

export function formatLargeCurrency(value: number): string {
  if (Math.abs(value) >= 1_000_000) {
    return `$${(value / 1_000_000).toFixed(1)}M`;
  }
  if (Math.abs(value) >= 1_000) {
    return `$${(value / 1_000).toFixed(1)}K`;
  }
  return `$${value.toFixed(2)}`;
}

export function formatPercent(value: number): string {
  const prefix = value > 0 ? '+' : '';
  return `${prefix}${value.toFixed(2)}%`;
}

export function formatTimeAgo(timestamp: number): string {
  return formatDistanceToNow(new Date(timestamp), { addSuffix: true });
}

export function formatDateTime(timestamp: number): string {
  return format(new Date(timestamp), 'MMM d, yyyy HH:mm');
}

export function formatShortTime(timestamp: number): string {
  return format(new Date(timestamp), 'HH:mm');
}

export function formatShortDate(timestamp: number): string {
  return format(new Date(timestamp), 'MMM d');
}
