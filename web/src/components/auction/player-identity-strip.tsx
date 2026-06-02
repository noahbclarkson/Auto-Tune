'use client';

import { useState, type FormEvent } from 'react';
import { User, CheckCircle } from 'lucide-react';

interface PlayerIdentityStripProps {
  playerName: string;
  onPlayerNameChange: (name: string) => void;
}

export function PlayerIdentityStrip({ playerName, onPlayerNameChange }: PlayerIdentityStripProps) {
  const [editing, setEditing] = useState(!playerName);
  const [draft, setDraft] = useState(playerName);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = draft.trim();
    onPlayerNameChange(trimmed);
    setEditing(false);
  };

  if (!editing && playerName) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/30">
        <CheckCircle className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
        <span className="text-xs font-medium text-emerald-700 dark:text-emerald-300">
          Watching as <span className="font-semibold">{playerName}</span>
        </span>
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="text-xs text-emerald-600 dark:text-emerald-400 hover:underline ml-1"
        >
          change
        </button>
      </div>
    );
  }

  if (editing) {
    return (
      <form onSubmit={handleSubmit} className="flex items-center gap-2">
        <User className="w-4 h-4 text-muted-foreground" />
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Enter your Minecraft name..."
          className="flex-1 text-sm bg-transparent border-b border-border outline-none focus:border-primary transition-colors px-1 py-1"
          autoFocus
        />
        <button
          type="submit"
          disabled={!draft.trim()}
          className="text-xs px-2.5 py-1 bg-primary text-primary-foreground rounded hover:opacity-90 disabled:opacity-50 transition-opacity"
        >
          Save
        </button>
        {playerName && (
          <button
            type="button"
            onClick={() => { setEditing(false); setDraft(playerName); }}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            Cancel
          </button>
        )}
      </form>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-2">
      <User className="w-4 h-4 text-muted-foreground" />
      <input
        type="text"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder="Your Minecraft name for watch notifications..."
        className="flex-1 text-sm bg-transparent border-b border-border outline-none focus:border-primary transition-colors px-1 py-1"
        autoFocus
      />
      <button
        type="submit"
        disabled={!draft.trim()}
        className="text-xs px-2.5 py-1 bg-primary text-primary-foreground rounded hover:opacity-90 disabled:opacity-50 transition-opacity"
      >
        Enable notifications
      </button>
    </form>
  );
}
