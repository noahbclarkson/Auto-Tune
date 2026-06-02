"""
Auto-Tune Market Engine Visualization

Plots the curves and relationships between all market engine metrics:
1.  Player scaling curve (tanh with derived coefficient)
2.  Trade ratio vs price change
3.  BPD/SPD vs buy/sell ratio (volume imbalance)
4.  Player count effect on spread
5.  Global volume multiplier (z-score based)
6.  Per-item liquidity reduction curve
7.  Combined buy/sell prices vs buy ratio
8.  Total spread heatmap
9.  Spread asymmetry heatmap
10. Spread at key player counts
11. Recency weighting decay (trade window)
12. Price simulation over time
"""

import numpy as np
import matplotlib.pyplot as plt
from matplotlib.gridspec import GridSpec

# Default configuration values (must match config.yml)
BASE_SPREAD = 0.30
VOLUME_IMPACT = 0.5
PLAYER_IMPACT = 0.7
FULL_EFFECT_PLAYERS = 20
MAX_PRICE_CHANGE_PERCENT = 3.0
LIQUIDITY_COEFF = 0.05
TRADE_WINDOW_DAYS = 7
ATANH_099 = np.arctanh(0.99)  # ~2.6467


def player_scaling(online_count, full_effect_players=FULL_EFFECT_PLAYERS):
    """Calculate player scaling using tanh with derived coefficient."""
    if online_count == 0:
        return 0.0
    coefficient = ATANH_099 / full_effect_players
    return np.tanh(online_count * coefficient)


def player_scaling_vec(online_counts, full_effect_players=FULL_EFFECT_PLAYERS):
    """Vectorized player scaling."""
    coefficient = ATANH_099 / full_effect_players
    result = np.tanh(online_counts * coefficient)
    result[online_counts == 0] = 0.0
    return result


def global_volume_multiplier(z):
    """
    Calculate spread multiplier from z-score of recent bucket volume.

    |z| <= 1: multiplier = 1.0
    z = +2: multiplier = 0.5 (half spread)
    z = -2: multiplier = 2.0 (double spread)
    Linear interpolation between 1-2 SD, capped beyond 2 SD.
    """
    z = np.asarray(z, dtype=float)
    result = np.ones_like(z)

    high_mask = z > 1.0
    t_high = np.clip(z[high_mask] - 1.0, 0.0, 1.0)
    result[high_mask] = 1.0 - 0.5 * t_high

    low_mask = z < -1.0
    t_low = np.clip(-z[low_mask] - 1.0, 0.0, 1.0)
    result[low_mask] = 1.0 + t_low

    return result


def liquidity_reduction(total_weighted_volume, liquidity_coeff=LIQUIDITY_COEFF):
    """Per-item liquidity reduction: tighter spreads for heavily traded items."""
    return 1.0 / (1.0 + total_weighted_volume * liquidity_coeff)


def calculate_spread(buy_ratio, online_count, z_score,
                     base_spread=BASE_SPREAD, volume_impact=VOLUME_IMPACT,
                     player_impact=PLAYER_IMPACT,
                     full_effect_players=FULL_EFFECT_PLAYERS,
                     total_weighted_volume=0.0,
                     liquidity_coeff=LIQUIDITY_COEFF):
    """
    Calculate BPD and SPD given trade parameters.

    Returns (bpd, spd) tuple.
    """
    half_spread = base_spread / 2.0

    imbalance = (buy_ratio - 0.5) * 2.0

    bpd = half_spread + max(0, imbalance) * half_spread * volume_impact
    spd = half_spread + max(0, -imbalance) * half_spread * volume_impact

    liq = liquidity_reduction(total_weighted_volume, liquidity_coeff)
    bpd *= liq
    spd *= liq

    ps = player_scaling(online_count, full_effect_players)
    player_factor = 1.0 - player_impact * ps
    bpd *= player_factor
    spd *= player_factor

    gvm = global_volume_multiplier(np.array([z_score]))[0]
    bpd *= gvm
    spd *= gvm

    return bpd, spd


def recency_weight(age_days, window_days=TRADE_WINDOW_DAYS):
    """Linear recency weighting: weight = max(0, 1 - age/window)."""
    return np.maximum(0.0, 1.0 - age_days / window_days)


def simulate_price(n_ticks, initial_price, buy_probability, online_players,
                   max_change=MAX_PRICE_CHANGE_PERCENT,
                   full_effect=FULL_EFFECT_PLAYERS):
    """Simulate price evolution over multiple ticks given constant buy probability."""
    prices = [initial_price]
    ps = player_scaling(online_players, full_effect)
    for _ in range(n_ticks):
        trade_ratio = (buy_probability - 0.5) * 2.0
        change_pct = trade_ratio * ps * (max_change / 100.0)
        new_price = prices[-1] * (1 + change_pct)
        prices.append(max(0.01, new_price))
    return np.array(prices)


def main():
    fig = plt.figure(figsize=(18, 46))
    fig.suptitle("Auto-Tune Market Engine Curves", fontsize=16, fontweight="bold")
    gs = GridSpec(6, 2, figure=fig, hspace=0.5, wspace=0.35)

    # --- 1. Player Scaling Curve ---
    ax1 = fig.add_subplot(gs[0, 0])
    players = np.arange(0, 61)
    for fep in [10, 20, 30, 50]:
        scaling = player_scaling_vec(players.astype(float), fep)
        ax1.plot(players, scaling, label=f"fullEffectPlayers={fep}")
    ax1.set_xlabel("Online Players")
    ax1.set_ylabel("Scaling Factor")
    ax1.set_title("Player Scaling: tanh(n × atanh(0.99)/FEP)")
    ax1.legend(fontsize=8)
    ax1.grid(True, alpha=0.3)
    ax1.set_ylim(-0.05, 1.05)
    ax1.axhline(y=0.99, color="gray", linestyle="--", alpha=0.5, label="99%")

    # --- 2. Trade Ratio → Price Change ---
    ax2 = fig.add_subplot(gs[0, 1])
    trade_ratios = np.linspace(-1, 1, 200)
    for n_players in [1, 5, 10, 20, 40]:
        ps = player_scaling(n_players)
        price_change = trade_ratios * ps * (MAX_PRICE_CHANGE_PERCENT / 100.0) * 100
        ax2.plot(trade_ratios, price_change, label=f"{n_players} players")
    ax2.set_xlabel("Trade Ratio (buys−sells)/(buys+sells)")
    ax2.set_ylabel("Price Change (%)")
    ax2.set_title("Price Change per Tick vs Trade Ratio")
    ax2.legend(fontsize=8)
    ax2.grid(True, alpha=0.3)
    ax2.axhline(y=0, color="black", linewidth=0.5)
    ax2.axvline(x=0, color="black", linewidth=0.5)

    # --- 3. BPD/SPD vs Buy Ratio (Volume Imbalance) ---
    ax3 = fig.add_subplot(gs[1, 0])
    buy_ratios = np.linspace(0, 1, 200)
    half = BASE_SPREAD / 2.0

    for vi in [0.1, 0.25, 0.5, 1.0]:
        imbalance = (buy_ratios - 0.5) * 2.0
        bpd_vals = half + np.maximum(0, imbalance) * half * vi
        spd_vals = half + np.maximum(0, -imbalance) * half * vi

        ax3.plot(buy_ratios, bpd_vals * 100, label=f"BPD (vi={vi})", linestyle="-")
        ax3.plot(buy_ratios, spd_vals * 100, label=f"SPD (vi={vi})", linestyle="--")

    ax3.set_xlabel("Buy Ratio (buys / total)")
    ax3.set_ylabel("Spread Component (%)")
    ax3.set_title("BPD & SPD vs Buy Ratio (no player/global adj.)")
    ax3.legend(fontsize=7, ncol=2)
    ax3.grid(True, alpha=0.3)
    ax3.axvline(x=0.5, color="gray", linestyle=":", alpha=0.5)

    # --- 4. Player Count Effect on Spread ---
    ax4 = fig.add_subplot(gs[1, 1])
    players_range = np.arange(0, 61).astype(float)

    for pi_val in [0.3, 0.5, 0.7, 0.9]:
        ps_vals = player_scaling_vec(players_range)
        reduction = 1.0 - pi_val * ps_vals
        bpd_base = (BASE_SPREAD / 2.0) * reduction * 100
        ax4.plot(players_range, bpd_base, label=f"playerImpact={pi_val}")

    ax4.set_xlabel("Online Players")
    ax4.set_ylabel("BPD/SPD at balanced volume (%)")
    ax4.set_title("Spread vs Player Count (base=30%)")
    ax4.legend(fontsize=8)
    ax4.grid(True, alpha=0.3)
    ax4.set_ylim(bottom=0)
    ax4.axhline(y=(BASE_SPREAD / 2.0) * 100, color="gray", linestyle=":", alpha=0.4,
                label="Base half-spread")

    # --- 5. Global Volume Multiplier ---
    ax5 = fig.add_subplot(gs[2, 0])
    z_scores = np.linspace(-4, 4, 400)
    multiplier = global_volume_multiplier(z_scores)

    ax5.plot(z_scores, multiplier, color="darkblue", linewidth=2)
    ax5.fill_between(z_scores, multiplier, 1.0, alpha=0.1,
                     where=multiplier < 1.0, color="green", label="Spread reduced")
    ax5.fill_between(z_scores, multiplier, 1.0, alpha=0.1,
                     where=multiplier > 1.0, color="red", label="Spread increased")

    ax5.axhline(y=1.0, color="gray", linestyle="--", alpha=0.5)
    ax5.axvline(x=-1, color="gray", linestyle=":", alpha=0.4)
    ax5.axvline(x=1, color="gray", linestyle=":", alpha=0.4)
    ax5.axvline(x=-2, color="gray", linestyle=":", alpha=0.4)
    ax5.axvline(x=2, color="gray", linestyle=":", alpha=0.4)

    ax5.annotate("Flat zone\n(|z| ≤ 1)", xy=(0, 1.0), ha="center", va="bottom", fontsize=8)
    ax5.annotate("0.5×", xy=(2.5, 0.5), ha="center", fontsize=9, color="green")
    ax5.annotate("2.0×", xy=(-2.5, 2.0), ha="center", fontsize=9, color="red")

    ax5.set_xlabel("Z-Score of Recent Bucket Volume")
    ax5.set_ylabel("Spread Multiplier")
    ax5.set_title("Global Volume Adjustment")
    ax5.legend(fontsize=8)
    ax5.grid(True, alpha=0.3)
    ax5.set_ylim(0, 2.5)

    # --- 6. Per-Item Liquidity Reduction ---
    ax6 = fig.add_subplot(gs[2, 1])
    volumes = np.linspace(0, 500, 500)

    for lc in [0.01, 0.02, 0.05, 0.1]:
        reduction = liquidity_reduction(volumes, lc)
        ax6.plot(volumes, reduction * 100, label=f"liquidityCoeff={lc}")

    ax6.set_xlabel("Total Weighted Volume (buys + sells)")
    ax6.set_ylabel("Spread Retention (%)")
    ax6.set_title("Per-Item Liquidity Reduction")
    ax6.legend(fontsize=8)
    ax6.grid(True, alpha=0.3)
    ax6.set_ylim(0, 105)
    ax6.axhline(y=100, color="gray", linestyle=":", alpha=0.4)

    # --- 7. Combined: Buy/Sell Price vs Buy Ratio ---
    ax7 = fig.add_subplot(gs[3, 0])
    base_price = 100.0
    buy_ratios_full = np.linspace(0, 1, 200)

    for n_players, z in [(1, 0), (2, 0), (5, 0), (20, 0), (20, 2)]:
        buy_prices = []
        sell_prices = []
        for br in buy_ratios_full:
            bpd, spd = calculate_spread(br, n_players, z)
            buy_prices.append(base_price * (1 + bpd))
            sell_prices.append(base_price * (1 - spd))

        label = f"p={n_players}, z={z}"
        ax7.plot(buy_ratios_full, buy_prices, label=f"Buy ({label})", linestyle="-")
        ax7.plot(buy_ratios_full, sell_prices, label=f"Sell ({label})", linestyle="--")

    ax7.axhline(y=base_price, color="black", linewidth=0.5, linestyle=":")
    ax7.set_xlabel("Buy Ratio")
    ax7.set_ylabel("Price ($)")
    ax7.set_title(f"Buy & Sell Prices (base=${base_price})")
    ax7.legend(fontsize=6, ncol=2)
    ax7.grid(True, alpha=0.3)

    # --- 8. Total Spread (BPD + SPD) Heatmap ---
    ax8 = fig.add_subplot(gs[3, 1])
    buy_ratios_heat = np.linspace(0, 1, 50)
    players_heat = np.arange(0, 41)
    total_spread = np.zeros((len(players_heat), len(buy_ratios_heat)))

    for i, p in enumerate(players_heat):
        for j, br in enumerate(buy_ratios_heat):
            bpd, spd = calculate_spread(br, p, 0)
            total_spread[i, j] = (bpd + spd) * 100

    im = ax8.imshow(total_spread, aspect="auto", origin="lower",
                    extent=[0, 1, 0, 40], cmap="RdYlGn_r")
    ax8.set_xlabel("Buy Ratio")
    ax8.set_ylabel("Online Players")
    ax8.set_title("Total Spread (BPD+SPD) % Heatmap (z=0)")
    plt.colorbar(im, ax=ax8, label="Total Spread %")

    # --- 9. Spread Asymmetry (BPD - SPD) ---
    ax9 = fig.add_subplot(gs[4, 0])
    asymmetry = np.zeros((len(players_heat), len(buy_ratios_heat)))

    for i, p in enumerate(players_heat):
        for j, br in enumerate(buy_ratios_heat):
            bpd, spd = calculate_spread(br, p, 0)
            asymmetry[i, j] = (bpd - spd) * 100

    im2 = ax9.imshow(asymmetry, aspect="auto", origin="lower",
                     extent=[0, 1, 0, 40], cmap="RdBu_r",
                     vmin=-np.max(np.abs(asymmetry)),
                     vmax=np.max(np.abs(asymmetry)))
    ax9.set_xlabel("Buy Ratio")
    ax9.set_ylabel("Online Players")
    ax9.set_title("Spread Asymmetry (BPD−SPD) % Heatmap (z=0)")
    plt.colorbar(im2, ax=ax9, label="BPD − SPD %")

    # --- 10. Spread at Key Player Counts ---
    ax10 = fig.add_subplot(gs[4, 1])
    player_counts = np.arange(0, 41)
    for label_name, br in [("Balanced (50/50)", 0.5), ("Heavy buy (80/20)", 0.8),
                           ("Heavy sell (20/80)", 0.2)]:
        total_spreads = []
        for p in player_counts:
            bpd, spd = calculate_spread(br, p, 0)
            total_spreads.append((bpd + spd) * 100)
        ax10.plot(player_counts, total_spreads, label=label_name, linewidth=2)

    ax10.set_xlabel("Online Players")
    ax10.set_ylabel("Total Spread (%)")
    ax10.set_title("Total Spread vs Players (z=0)")
    ax10.legend(fontsize=8)
    ax10.grid(True, alpha=0.3)
    ax10.set_ylim(bottom=0)

    # Annotate key points
    for p in [1, 2, 5, 20]:
        bpd, spd = calculate_spread(0.5, p, 0)
        total = (bpd + spd) * 100
        ax10.annotate(f"{total:.1f}%", xy=(p, total),
                      textcoords="offset points", xytext=(5, 5), fontsize=7)

    # --- 11. Recency Weighting Decay ---
    ax11 = fig.add_subplot(gs[5, 0])
    age_days = np.linspace(0, 10, 500)

    for window in [3, 7, 14]:
        weights = recency_weight(age_days, window)
        ax11.plot(age_days, weights, label=f"window={window}d", linewidth=2)

    ax11.set_xlabel("Transaction Age (days)")
    ax11.set_ylabel("Weight")
    ax11.set_title("Recency Weighting: Linear Decay Over Trade Window")
    ax11.legend(fontsize=8)
    ax11.grid(True, alpha=0.3)
    ax11.set_ylim(-0.05, 1.05)
    ax11.axhline(y=0, color="black", linewidth=0.5)
    ax11.axvline(x=TRADE_WINDOW_DAYS, color="gray", linestyle=":", alpha=0.5,
                 label=f"Default window ({TRADE_WINDOW_DAYS}d)")
    ax11.annotate(f"Default: {TRADE_WINDOW_DAYS}d", xy=(TRADE_WINDOW_DAYS, 0.02),
                  ha="center", fontsize=8, color="gray")

    # --- 12. Price Simulation Over Time ---
    ax12 = fig.add_subplot(gs[5, 1])
    n_ticks = 288  # 24h at 5-min intervals

    scenarios = [
        ("70% buy, 20 players", 0.70, 20),
        ("60% buy, 20 players", 0.60, 20),
        ("50% (balanced)", 0.50, 20),
        ("40% buy, 20 players", 0.40, 20),
        ("70% buy, 5 players", 0.70, 5),
    ]

    for label_name, buy_prob, n_p in scenarios:
        prices = simulate_price(n_ticks, 100.0, buy_prob, n_p)
        hours = np.arange(len(prices)) * 5 / 60
        ax12.plot(hours, prices, label=label_name, linewidth=1.5)

    ax12.set_xlabel("Time (hours)")
    ax12.set_ylabel("Base Price ($)")
    ax12.set_title("Price Simulation Over 24h (5-min ticks)")
    ax12.legend(fontsize=7)
    ax12.grid(True, alpha=0.3)
    ax12.axhline(y=100, color="black", linewidth=0.5, linestyle=":")

    plt.savefig("scripts/market_curves.png", dpi=150, bbox_inches="tight")
    plt.show()
    print("Saved to scripts/market_curves.png")


if __name__ == "__main__":
    main()
