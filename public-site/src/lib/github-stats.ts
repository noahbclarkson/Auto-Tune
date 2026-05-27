import { Star, Download, Server } from 'lucide-react';

interface GitHubRepo {
  stargazers_count: number;
  forks_count: number;
}

interface ReleaseAsset {
  download_count: number;
}

export interface GitHubStats {
  stars: number;
  forks: number;
  downloads: number;
}

const OWNER = 'noahbclarkson';
const REPO = 'Auto-Tune';

const FALLBACK: GitHubStats = {
  stars: 132,
  forks: 26,
  downloads: 3500,
};

export async function fetchGitHubStats(): Promise<GitHubStats> {
  try {
    const [repoRes, releasesRes] = await Promise.all([
      fetch(`https://api.github.com/repos/${OWNER}/${REPO}`, {
        next: { revalidate: 3600 }, // cache for 1 hour
        headers: { Accept: 'application/vnd.github+json' },
      }),
      fetch(`https://api.github.com/repos/${OWNER}/${REPO}/releases`, {
        next: { revalidate: 3600 },
        headers: { Accept: 'application/vnd.github+json' },
      }),
    ]);

    if (!repoRes.ok) throw new Error('Repo fetch failed');

    const repo: GitHubRepo = await repoRes.json();
    const releases: unknown[] = await releasesRes.json();

    let downloads = 0;
    if (Array.isArray(releases)) {
      for (const release of releases) {
        if (release && typeof release === 'object' && 'assets' in release) {
          const assets = (release as { assets: ReleaseAsset[] }).assets;
          for (const asset of assets) {
            downloads += asset.download_count ?? 0;
          }
        }
      }
    }

    return {
      stars: repo.stargazers_count,
      forks: repo.forks_count,
      downloads,
    };
  } catch {
    return FALLBACK;
  }
}