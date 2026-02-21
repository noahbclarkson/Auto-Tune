"use client";

import { useState } from "react";

interface ServerData {
  id: string;
  name: string;
  ratios: number[][];
  weight: number;
}

interface PriceResult {
  prices: number[];
  items: string[];
  anchorItem: number;
  anchorPrice: number;
}

export function PriceCalculator() {
  const [items, setItems] = useState<string[]>(["Dirt", "Cobblestone", "Diamond"]);
  const [servers, setServers] = useState<ServerData[]>([
    {
      id: "server-1",
      name: "Server A",
      ratios: [
        [1.0, 0.5, 0.01],
        [2.0, 1.0, 0.02],
        [100.0, 50.0, 1.0],
      ],
      weight: 1.0,
    },
    {
      id: "server-2",
      name: "Server B",
      ratios: [
        [1.0, 0.5, 0.005],
        [2.0, 1.0, 0.01],
        [200.0, 100.0, 1.0],
      ],
      weight: 1.0,
    },
  ]);
  const [anchorItem, setAnchorItem] = useState(0);
  const [anchorPrice, setAnchorPrice] = useState(0.1);
  const [result, setResult] = useState<PriceResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const addItem = () => {
    const newItem = `Item ${items.length + 1}`;
    const newItems = [...items, newItem];
    setItems(newItems);
    
    // Expand ratio matrices
    setServers(servers.map(server => {
      const n = newItems.length;
      const newRatios = Array(n).fill(0).map((_, i) =>
        Array(n).fill(0).map((_, j) => {
          if (i < server.ratios.length && j < server.ratios.length) {
            return server.ratios[i][j];
          }
          return i === j ? 1.0 : 0.0;
        })
      );
      return { ...server, ratios: newRatios };
    }));
  };

  const removeItem = (index: number) => {
    if (items.length <= 2) return;
    
    const newItems = items.filter((_, i) => i !== index);
    setItems(newItems);
    
    setServers(servers.map(server => ({
      ...server,
      ratios: server.ratios
        .filter((_, i) => i !== index)
        .map(row => row.filter((_, j) => j !== index)),
    })));
    
    if (anchorItem >= newItems.length) {
      setAnchorItem(0);
    }
  };

  const updateRatio = (serverIndex: number, i: number, j: number, value: number) => {
    setServers(servers.map((server, sIdx) => {
      if (sIdx !== serverIndex) return server;
      const newRatios = server.ratios.map(row => [...row]);
      newRatios[i][j] = value;
      // Set reciprocal
      if (value > 0) {
        newRatios[j][i] = 1.0 / value;
      }
      return { ...server, ratios: newRatios };
    }));
  };

  const calculatePrices = () => {
    setError(null);
    
    // Validate ratios
    for (const server of servers) {
      for (let i = 0; i < server.ratios.length; i++) {
        for (let j = 0; j < server.ratios[i].length; j++) {
          if (i !== j && server.ratios[i][j] <= 0) {
            setError(`Invalid ratio at ${server.name}: ${items[i]}/${items[j]} = ${server.ratios[i][j]}`);
            return;
          }
        }
      }
    }

    // Client-side solver (simplified version)
    const n = items.length;
    const m = servers.length;
    const weights = servers.map(s => s.weight);
    const wSum = weights.reduce((a, b) => a + b, 0);

    // Aggregate ratios (geometric mean in log space)
    const aggLogR: number[][] = Array(n).fill(0).map(() => Array(n).fill(0));
    for (let i = 0; i < n; i++) {
      for (let j = 0; j < n; j++) {
        if (i === j) {
          aggLogR[i][j] = 0;
          continue;
        }
        let sum = 0;
        for (let s = 0; s < m; s++) {
          sum += weights[s] * Math.log(servers[s].ratios[i][j]);
        }
        aggLogR[i][j] = sum / wSum;
      }
    }

    // Build least-squares system
    const numEdges = (n * (n - 1)) / 2;
    const rows = numEdges + 1;
    
    const A: number[][] = Array(rows).fill(0).map(() => Array(n).fill(0));
    const b: number[] = Array(rows).fill(0);
    
    let k = 0;
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        A[k][i] = 1;
        A[k][j] = -1;
        b[k] = aggLogR[i][j];
        k++;
      }
    }
    A[rows - 1][anchorItem] = 1;
    b[rows - 1] = Math.log(anchorPrice);

    // Solve using normal equations
    try {
      const x = solveNormalEquations(A, b, n);
      const prices = x.map(v => Math.exp(v));
      
      setResult({
        prices,
        items,
        anchorItem,
        anchorPrice,
      });
    } catch (e) {
      setError("Failed to solve: matrix may be singular");
    }
  };

  return (
    <div className="space-y-6">
      {/* Items Section */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-emerald-400 mb-4">Items</h3>
        <div className="flex flex-wrap gap-2 mb-4">
          {items.map((item, i) => (
            <button
              key={i}
              onClick={() => setAnchorItem(i)}
              className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${
                i === anchorItem
                  ? "bg-emerald-600 text-white"
                  : "bg-slate-700 text-slate-300 hover:bg-slate-600"
              }`}
            >
              {item} {i === anchorItem && "(anchor)"}
              {items.length > 2 && (
                <span
                  onClick={(e) => { e.stopPropagation(); removeItem(i); }}
                  className="ml-2 text-red-400 hover:text-red-300"
                >
                  ×
                </span>
              )}
            </button>
          ))}
          <button
            onClick={addItem}
            className="px-3 py-1 rounded-full text-sm font-medium border border-slate-600 text-slate-300 hover:bg-slate-800 transition-colors"
          >
            + Add Item
          </button>
        </div>
        
        <div className="flex items-center gap-4">
          <label className="text-slate-400 text-sm">Anchor Price for &quot;{items[anchorItem]}&quot;:</label>
          <input
            type="number"
            value={anchorPrice}
            onChange={(e) => setAnchorPrice(parseFloat(e.target.value) || 0.01)}
            className="w-32 px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            step="0.01"
            min="0.01"
          />
        </div>
      </div>

      {/* Servers Section */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-emerald-400 mb-4">Server Ratio Matrices</h3>
        <div className="space-y-6">
          {servers.map((server, sIdx) => (
            <div key={server.id} className="space-y-2">
              <h4 className="text-slate-300 font-medium">{server.name}</h4>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr>
                      <th className="px-2 py-1 text-slate-500"></th>
                      {items.map((item, j) => (
                        <th key={j} className="px-2 py-1 text-slate-400 font-medium">{item}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((rowItem, i) => (
                      <tr key={i}>
                        <td className="px-2 py-1 text-slate-400 font-medium">{rowItem}</td>
                        {items.map((_, j) => (
                          <td key={j} className="px-1 py-1">
                            <input
                              type="number"
                              value={server.ratios[i]?.[j] ?? 1}
                              onChange={(e) => updateRatio(sIdx, i, j, parseFloat(e.target.value) || 0)}
                              className="w-20 px-2 py-1 bg-slate-800 border border-slate-700 rounded text-white text-center focus:outline-none focus:ring-1 focus:ring-emerald-500"
                              disabled={i === j}
                              step="0.1"
                              min="0.01"
                            />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Calculate Button */}
      <button
        onClick={calculatePrices}
        className="w-full bg-emerald-600 hover:bg-emerald-700 text-white py-3 px-6 rounded-lg text-lg font-medium transition-colors"
      >
        Calculate True Prices
      </button>

      {/* Error */}
      {error && (
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4">
          <p className="text-red-400">{error}</p>
        </div>
      )}

      {/* Results */}
      {result && (
        <div className="bg-slate-900 border border-emerald-800 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-emerald-400 mb-4">True Prices</h3>
          <div className="grid gap-3">
            {result.prices.map((price, i) => (
              <div
                key={i}
                className={`flex justify-between items-center p-3 rounded-lg ${
                  i === result.anchorItem ? "bg-emerald-900/30 border border-emerald-700" : "bg-slate-800"
                }`}
              >
                <span className="text-slate-300">{result.items[i]}</span>
                <span className={`font-mono text-lg ${i === result.anchorItem ? "text-emerald-400" : "text-white"}`}>
                  ${price.toFixed(2)}
                </span>
              </div>
            ))}
          </div>
          <p className="text-slate-500 text-sm mt-4">
            Anchored on {result.items[result.anchorItem]} at ${result.anchorPrice}
          </p>
        </div>
      )}
    </div>
  );
}

// Simple normal equations solver (browser-compatible)
function solveNormalEquations(A: number[][], b: number[], n: number): number[] {
  // Compute A^T A
  const AtA: number[][] = Array(n).fill(0).map(() => Array(n).fill(0));
  const Atb: number[] = Array(n).fill(0);
  
  const rows = A.length;
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      for (let k = 0; k < rows; k++) {
        AtA[i][j] += A[k][i] * A[k][j];
      }
    }
    for (let k = 0; k < rows; k++) {
      Atb[i] += A[k][i] * b[k];
    }
  }
  
  // Solve using Gaussian elimination with partial pivoting
  const aug: number[][] = AtA.map((row, i) => [...row, Atb[i]]);
  
  // Forward elimination
  for (let col = 0; col < n; col++) {
    // Find pivot
    let maxRow = col;
    for (let row = col + 1; row < n; row++) {
      if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
        maxRow = row;
      }
    }
    [aug[col], aug[maxRow]] = [aug[maxRow], aug[col]];
    
    if (Math.abs(aug[col][col]) < 1e-10) {
      throw new Error("Singular matrix");
    }
    
    for (let row = col + 1; row < n; row++) {
      const factor = aug[row][col] / aug[col][col];
      for (let j = col; j <= n; j++) {
        aug[row][j] -= factor * aug[col][j];
      }
    }
  }
  
  // Back substitution
  const x: number[] = Array(n).fill(0);
  for (let i = n - 1; i >= 0; i--) {
    x[i] = aug[i][n];
    for (let j = i + 1; j < n; j++) {
      x[i] -= aug[i][j] * x[j];
    }
    x[i] /= aug[i][i];
  }
  
  return x;
}
