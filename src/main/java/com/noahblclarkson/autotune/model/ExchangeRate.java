package com.noahblclarkson.autotune.model;

import java.time.Instant;

/**
 * Represents a server's exchange rate relative to the global true-price baseline.
 * Fetched from the cross-server API (/api/servers/exchange-rates).
 *
 * rate &gt; 1.0: server's economy is more expensive than the global average
 * rate &lt; 1.0: server's economy is cheaper than the global average
 * rate = 1.0: server is aligned with global average
 */
@SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
public record ExchangeRate(
        /** Unique server identifier. */
        String serverId,
        /** Display name of the server. */
        String name,
        /** Exchange rate relative to true-price baseline. */
        double rate,
        /** Number of players reported by the server at last submission. */
        int playerCount,
        /** When this rate was last updated from the API. */
        Instant fetchedAt
) {
    /** Returns a human-readable label for the rate relative to baseline. */
    public String label() {
        double pct = (rate - 1.0) * 100;
        if (pct > 5) return String.format("+%.1f%% (more expensive)", pct);
        if (pct < -5) return String.format("%.1f%% (cheaper)", pct);
        return "±0% (at baseline)";
    }

    /** Whether this server's economy is considered "aligned" with the global baseline. */
    public boolean isAligned() {
        return Math.abs(rate - 1.0) <= 0.1; // within 10%
    }
}
