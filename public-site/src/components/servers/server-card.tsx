import type { ManagedServer } from '@/lib/api-client';

interface ServerCardProps {
  server: ManagedServer;
}

function getServerStatus(lastSeen: string): { label: string; className: string } {
  const deltaMs = Date.now() - new Date(lastSeen).getTime();
  const fiveMinutes = 5 * 60 * 1000;
  const oneHour = 60 * 60 * 1000;

  if (deltaMs < fiveMinutes) {
    return { label: 'Online', className: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30' };
  }

  if (deltaMs < oneHour) {
    return { label: 'Idle', className: 'text-amber-300 bg-amber-500/10 border-amber-500/30' };
  }

  return { label: 'Offline', className: 'text-gray-300 bg-gray-700/20 border-gray-600/40' };
}

export function ServerCard({ server }: ServerCardProps) {
  const status = getServerStatus(server.last_seen);

  return (
    <article className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <h3 className="text-lg font-semibold text-white">{server.name}</h3>
          <p className="text-xs text-gray-500">Server ID: {server.id}</p>
        </div>
        <span className={`text-xs px-2.5 py-1 rounded-full border ${status.className}`}>{status.label}</span>
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="bg-gray-950/40 rounded-lg p-3 border border-gray-800/40">
          <p className="text-gray-500 text-xs uppercase tracking-wide mb-1">Players</p>
          <p className="text-gray-100 font-medium">{server.player_count}</p>
        </div>
        <div className="bg-gray-950/40 rounded-lg p-3 border border-gray-800/40">
          <p className="text-gray-500 text-xs uppercase tracking-wide mb-1">Last submission items</p>
          <p className="text-gray-100 font-medium">{server.last_submission_item_count ?? 0}</p>
        </div>
      </div>

      <div className="mt-3 text-xs text-gray-400 space-y-1">
        <p>Last seen: {new Date(server.last_seen).toLocaleString()}</p>
        <p>
          Last submission:{' '}
          {server.last_submission_at ? new Date(server.last_submission_at).toLocaleString() : 'No submissions yet'}
        </p>
      </div>
    </article>
  );
}
