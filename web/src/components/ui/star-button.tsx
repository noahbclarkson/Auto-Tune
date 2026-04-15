'use client';

import { useState } from 'react';
import { Star } from 'lucide-react';

const FAVORITES_STORAGE_KEY = 'autotune-web-favorites';

function getStoredFavorites(): Set<number> {
  try {
    const raw = localStorage.getItem(FAVORITES_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return new Set(parsed);
    }
  } catch {
    // ignore
  }
  return new Set();
}

function saveFavorites(ids: Set<number>) {
  localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify([...ids]));
}

interface StarButtonProps {
  itemId: number;
  className?: string;
}

export function StarButton({ itemId, className = '' }: StarButtonProps) {
  const [favorites, setFavorites] = useState<Set<number>>(() => getStoredFavorites());
  const isFav = favorites.has(itemId);

  function handleToggle(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      saveFavorites(next);
      return next;
    });
  }

  return (
    <button
      onClick={handleToggle}
      className={`p-1 rounded transition-colors ${className}`}
      title={isFav ? 'Remove from favorites' : 'Add to favorites'}
      aria-label={isFav ? 'Remove from favorites' : 'Add to favorites'}
    >
      <Star
        className={`h-4 w-4 transition-colors ${
          isFav
            ? 'fill-amber-400 text-amber-400'
            : 'text-muted-foreground hover:text-amber-400'
        }`}
      />
    </button>
  );
}

export function getFavoritedIds(): number[] {
  return [...getStoredFavorites()];
}
