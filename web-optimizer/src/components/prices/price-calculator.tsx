"use client";

import { useState } from "react";
import { submitServerPrices } from "@/lib/api-client";

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

  const [liveServerId, setLiveServerId] = useState("");
  const [liveApiKey, setLiveApiKey] = useState("");
  const [livePlayerCount, setLivePlayerCount] = useState(20);
  const [selectedServerIndex, setSelectedServerIndex] = useState(0);
  const [submitStatus, setSubmitStatus] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const liveApiEnabled = Boolean(process.env.NEXT_PUBLIC_API_URL);

  const addItem = () => {
    const newItem = `Item ${items.length + 1}`;
    const newItems = [...items, newItem];
    setItems(newItems);

    setServers(
      servers.map((server) => {
        const n = newItems.length;
        const newRatios = Array(n)
          .fill(0)
          .map((_, i) =>
            Array(n)
              .fill(0)
              .map((__, j) => {
                if (i < server.ratios.length && j < server.ratios.length) {
                  return server.ratios[i][j];
                }
                return i === j ? 1.0 : 0.0;
              }),
          );
        return { ...server, ratios: newRatios };
      }),
    );
  };

  const removeItem = (index: number) => {
    if (items.length <= 2) return;

    const newItems = items.filter((_, i) => i !== index);
    setItems(newItems);

    setServers(
      servers.map((server) => ({
        ...server,
        ratios: server.ratios
          .filter((_, i) => i !== index)
          .map((row) => row.filter((__, j) => j !== index)),
      })),
    );

    if (anchorItem >= newItems.length) {
      setAnchorItem(0);
    }
  };

  const updateRatio = (serverIndex: number, i: number, j: number, value: number) => {
    setServers(
      servers.map((server, sIdx) => {
        if (sIdx !== serverIndex) return server;
        const newRatios = server.ratios.map((row) => [...row]);
        newRatios[i][j] = value;
        if (value > 0) {
          newRatios[j][i] = 1.0 / value;
        }
        return { ...server, ratios: newRatios };
      }),
    );
  };

  const calculatePrices = () => {
    setError(null);

    for (const server of servers) {
      for (let i = 0; i < server.ratios.length; i++) {
        for (let j = 0; j < server.ratios[i].length; j++) {
          if (i !== j && server.ratios[i][j] <= 0) {
            setError(
              `Invalid ratio at ${server.name}: ${items[i]}/${items[j]} = ${server.ratios[i][j]}`,
            );
            return;
          }
        }
      }
    }

    const n = items.length;
    const m = servers.length;
    const weights = servers.map((s) => s.weight);
    const wSum = weights.reduce((a, b) => a + b, 0);

    const aggLogR: number[][] = Array(n)
      .fill(0)
      .map(() => Array(n).fill(0));
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

    const numEdges = (n * (n - 1)) / 2;
    const rows = numEdges + 1;

    const A: number[][] = Array(rows)
      .fill(0)
      .map(() => Array(n).fill(0));
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

    try {
      const x = solveNormalEquations(A, b, n);
      const prices = x.map((v) => Math.exp(v));

      setResult({
        prices,
        items,
        anchorItem,
        anchorPrice,
      });
    } catch {
      setError("Failed to solve: matrix may be singular");
    }
  };

  const submitToLiveApi = async () => {
    setSubmitStatus(null);
    setSubmitError(null);

    if (!liveServerId || !liveApiKey) {
      setSubmitError("Enter your live server ID and API key first.");
      return;
    }

    setIsSubmitting(true);
    const selectedServer = servers[selectedServerIndex];
    const response = await submitServerPrices({
      serverId: liveServerId,
      apiKey: liveApiKey,
      itemNames: items,
      ratioMatrix: selectedServer.ratios,
      playerCount: livePlayerCount,
    });
    setIsSubmitting(false);

    if (response.error) {
      setSubmitError(response.error);
      return;
    }

    setSubmitStatus(
      `Uploaded ${response.data?.items_processed ?? items.length} items from ${selectedServer.name} to the live API.`,
    );
  };

  return (
    <div className="space-y-6">
      <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-white mb-1">Items & Anchor</h3>
        <p className="text-sm text-gray-400 mb-4">
          Select the anchor item and set its absolute price to scale the full solution.
        </p>

        <div className="flex flex-wrap gap-2 mb-4">
          {items.map((item, i) => (
            <button
              key={item}
              onClick={() => setAnchorItem(i)}
              className={`px-3 py-1 rounded-full text-sm font-medium transition-colors border ${
                i === anchorItem
                  ? "bg-emerald-600/20 text-emerald-400 border-emerald-500/40"
                  : "bg-gray-900 text-gray-300 border-gray-700 hover:border-gray-600"
              }`}
            >
              {item} {i === anchorItem && "(anchor)"}
              {items.length > 2 && (
                <span
                  onClick={(e) => {
                    e.stopPropagation();
                    removeItem(i);
                  }}
                  className="ml-2 text-red-400 hover:text-red-300"
                >
                  ×
                </span>
              )}
            </button>
          ))}
          <button
            onClick={addItem}
            className="px-3 py-1 rounded-full text-sm font-medium border border-gray-700 text-gray-300 hover:bg-gray-800 transition-colors"
          >
            + Add Item
          </button>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <label className="text-gray-400 text-sm">Anchor Price for &quot;{items[anchorItem]}&quot;:</label>
          <input
            type="number"
            value={anchorPrice}
            onChange={(e) => setAnchorPrice(parseFloat(e.target.value) || 0.01)}
            className="w-32 px-3 py-2 bg-gray-950 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
            step="0.01"
            min="0.01"
          />
        </div>
      </div>

      <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6">
        <h3 className="text-lg font-semibold text-white mb-1">Server Ratio Matrices</h3>
        <p className="text-sm text-gray-400 mb-4">
          Edit ratios directly. Reciprocal values are filled automatically.
        </p>
        <div className="space-y-6">
          {servers.map((server, sIdx) => (
            <div key={server.id} className="space-y-2">
              <h4 className="text-gray-300 font-medium">{server.name}</h4>
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr>
                      <th className="px-2 py-1 text-gray-500"></th>
                      {items.map((item) => (
                        <th key={`${server.id}-${item}`} className="px-2 py-1 text-gray-400 font-medium">
                          {item}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((rowItem, i) => (
                      <tr key={`${server.id}-${rowItem}`}>
                        <td className="px-2 py-1 text-gray-400 font-medium">{rowItem}</td>
                        {items.map((__, j) => (
                          <td key={`${server.id}-${i}-${j}`} className="px-1 py-1">
                            <input
                              type="number"
                              value={server.ratios[i]?.[j] ?? 1}
                              onChange={(e) => updateRatio(sIdx, i, j, parseFloat(e.target.value) || 0)}
                              className="w-20 px-2 py-1 bg-gray-950 border border-gray-700 rounded text-white text-center focus:outline-none focus:ring-1 focus:ring-emerald-500"
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

      <div className="grid gap-3 sm:grid-cols-2">
        <button
          onClick={calculatePrices}
          className="w-full bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white py-3 px-6 rounded-lg text-lg font-medium transition-colors shadow-lg shadow-emerald-600/20"
        >
          Calculate True Prices (Local)
        </button>

        {liveApiEnabled && (
          <button
            onClick={submitToLiveApi}
            disabled={isSubmitting}
            className="w-full bg-gradient-to-r from-indigo-600 to-cyan-500 hover:from-indigo-500 hover:to-cyan-400 disabled:opacity-60 text-white py-3 px-6 rounded-lg text-lg font-medium transition-colors shadow-lg shadow-indigo-600/20"
          >
            {isSubmitting ? "Submitting to Live API..." : "Submit Matrix to Live API"}
          </button>
        )}
      </div>

      {liveApiEnabled && (
        <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-white mb-1">Live API Upload</h3>
          <p className="text-sm text-gray-400 mb-4">
            Optional: push one matrix to your api-server using your registered server credentials.
          </p>

          <div className="grid gap-3 sm:grid-cols-2">
            <input
              type="text"
              value={liveServerId}
              onChange={(e) => setLiveServerId(e.target.value)}
              placeholder="Server ID (UUID)"
              className="px-3 py-2 bg-gray-950 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <input
              type="password"
              value={liveApiKey}
              onChange={(e) => setLiveApiKey(e.target.value)}
              placeholder="API key"
              className="px-3 py-2 bg-gray-950 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <select
              value={selectedServerIndex}
              onChange={(e) => setSelectedServerIndex(parseInt(e.target.value, 10))}
              className="px-3 py-2 bg-gray-950 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              {servers.map((server, idx) => (
                <option key={server.id} value={idx}>
                  Upload matrix: {server.name}
                </option>
              ))}
            </select>
            <input
              type="number"
              value={livePlayerCount}
              onChange={(e) => setLivePlayerCount(parseInt(e.target.value, 10) || 0)}
              min="0"
              className="px-3 py-2 bg-gray-950 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Player count"
            />
          </div>

          {submitError && <p className="text-red-300 text-sm mt-3">{submitError}</p>}
          {submitStatus && <p className="text-emerald-300 text-sm mt-3">{submitStatus}</p>}
        </div>
      )}

      {error && (
        <div className="bg-red-950/30 border border-red-900/60 rounded-lg p-4">
          <p className="text-red-300 text-sm">{error}</p>
        </div>
      )}

      {result && (
        <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6">
          <h3 className="text-lg font-semibold text-emerald-400 mb-4">True Prices</h3>
          <div className="grid gap-3">
            {result.prices.map((price, i) => (
              <div
                key={`${result.items[i]}-${i}`}
                className={`flex justify-between items-center p-3 rounded-lg border ${
                  i === result.anchorItem
                    ? "bg-emerald-600/10 border-emerald-600/30"
                    : "bg-gray-950/70 border-gray-800/60"
                }`}
              >
                <span className="text-gray-300">{result.items[i]}</span>
                <span className={`font-mono text-lg ${i === result.anchorItem ? "text-emerald-400" : "text-white"}`}>
                  ${price.toFixed(2)}
                </span>
              </div>
            ))}
          </div>
          <p className="text-gray-500 text-sm mt-4">
            Anchored on {result.items[result.anchorItem]} at ${result.anchorPrice}
          </p>
        </div>
      )}
    </div>
  );
}

function solveNormalEquations(A: number[][], b: number[], n: number): number[] {
  const AtA: number[][] = Array(n)
    .fill(0)
    .map(() => Array(n).fill(0));
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

  const aug: number[][] = AtA.map((row, i) => [...row, Atb[i]]);

  for (let col = 0; col < n; col++) {
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
