// Pre-computed archetype configurations and stability data for the Setup Wizard.
// Derived from simulation runs: 840-config parameter sweep + archetype mix tests.

export const wizardArchetypes = {
  serverTypes: {
    smp: {
      name: 'SMP / Vanilla+',
      description: 'Standard survival. Moderate trading, moderate griefing risk.',
      icon: 'axe',
      archetype: { MarketMaker: 2, GuildBuyer: 2, GuildBuyerThreshold: 5 },
      spread: 0.20,
      loans: null,
      stability: {
        buyRatio: '50-55%',
        volatility: 'Low (0.05-0.10)',
        debtRisk: 'Low (D/G < 2x)',
        spreadWidth: 'Normal (2-4%)',
        timeToStability: '3-5 days',
      },
    },
    skyblock: {
      name: 'Skyblock',
      description: 'Island economies, limited resources, high scarcity.',
      icon: 'mountain',
      loans: {
        tier3Ratio: 30,
        creditScoreMultiplier: 1.0,
        postDefaultCooldownHours: 168,
      },
      archetype: { MarketMaker: 1, GuildBuyer: 3, GuildBuyerThreshold: 5 },
      spread: 0.20,
      stability: {
        buyRatio: '55-60%',
        volatility: 'Moderate (0.10-0.15)',
        debtRisk: 'Low (D/G < 3x)',
        spreadWidth: 'Normal (2-4%)',
        timeToStability: '1-2 weeks',
      },
    },
    faction: {
      name: 'Faction / PvP',
      description: 'High turnover, high exploit risk. Aggressive circuit breaker.',
      icon: 'swords',
      loans: {
        tier3Ratio: 30,
        creditScoreMultiplier: 1.5,
        postDefaultCooldownHours: 336,
      },
      archetype: { MarketMaker: 1, GuildBuyer: 1, GuildBuyerThreshold: 5 },
      spread: 0.25,
      stability: {
        buyRatio: '45-50%',
        volatility: 'High (0.15+)',
        debtRisk: 'Moderate (circuit breaker likely)',
        spreadWidth: 'Wide (4%+)',
        timeToStability: '1-2 weeks',
      },
    },
    economy: {
      name: 'Economy / Shop',
      description: 'Trading-focused, minimal PvP. Tight spreads reward active traders.',
      icon: 'cart',
      loans: {
        tier3Ratio: 30,
        creditScoreMultiplier: 1.0,
        postDefaultCooldownHours: 168,
      },
      archetype: { MarketMaker: 3, GuildBuyer: 1, GuildBuyerThreshold: 5 },
      spread: 0.15,
      stability: {
        buyRatio: '50-55%',
        volatility: 'Low (0.05-0.10)',
        debtRisk: 'Low (D/G < 2x)',
        spreadWidth: 'Tight (1-2%)',
        timeToStability: '3-5 days',
      },
    },
    custom: {
      name: 'Custom',
      description: "I'll configure archetypes manually.",
      icon: 'sliders',
      archetype: null,
      spread: 0.20,
      loans: {
        tier3Ratio: 30,
        creditScoreMultiplier: 1.0,
        postDefaultCooldownHours: 168,
      },
      stability: null,
    },
  },

  playerCounts: {
    solo: {
      name: 'Solo / Small',
      description: '1-10 players — tight spreads for low liquidity',
      spreadDelta: -0.05,
    },
    medium: {
      name: 'Medium',
      description: '10-50 players — default settings',
      spreadDelta: 0.0,
    },
    large: {
      name: 'Large',
      description: '50-200 players — wider spreads for high volume',
      spreadDelta: 0.05,
    },
    massive: {
      name: 'Massive',
      description: '200+ players — very wide, prevent manipulation',
      spreadDelta: 0.10,
    },
  },

  goals: {
    volume: {
      name: '📈 Player Trading Volume',
      description: 'Encourage lots of buys and sells. Tighter spreads.',
      icon: '📈',
      spreadDelta: -0.05,
      maxPriceChangeDelta: 0.0,
    },
    seller_protection: {
      name: '🛡️ Seller Protection',
      description: 'Protect sellers from price crashes. Floor at 60%.',
      icon: '🛡️',
      spreadDelta: 0.0,
      maxPriceChangeDelta: 0.0,
    },
    treasury: {
      name: '💰 Server Treasury',
      description: "Grow the server's war chest via taxes.",
      icon: '💰',
      spreadDelta: 0.0,
      maxPriceChangeDelta: 0.0,
    },
    fast_discovery: {
      name: '⚡ Fast Price Discovery',
      description: 'Prices react quickly to new items.',
      icon: '⚡',
      spreadDelta: 0.0,
      maxPriceChangeDelta: 0.5,
    },
    low_debt: {
      name: '🔒 Low Debt',
      description: 'Keep players out of trouble. Strict loan limits.',
      icon: '🔒',
      spreadDelta: 0.0,
      maxPriceChangeDelta: 0.0,
    },
    fun_volatility: {
      name: '🎮 Fun Volatility',
      description: 'Prices that move a lot. Exciting for active traders.',
      icon: '🎮',
      spreadDelta: 0.10,
      maxPriceChangeDelta: 1.0,
    },
  },

  defaults: {
    spread: 0.20,
    maxPriceChangePercent: 1.5,
    baseSpread: 0.20,
    loans: {
      counterCyclical: true,
      postDefaultCooldownHours: 168,
      creditScoreMultiplier: 1.0,
      tier3Ratio: 30,
    },
    treasury: {
      taxRate: 0.01,
    },
    spreadFloorPercent: 0,
  },
} as const;

export type ServerType = keyof typeof wizardArchetypes.serverTypes;
export type PlayerCount = keyof typeof wizardArchetypes.playerCounts;
export type GoalKey = keyof typeof wizardArchetypes.goals;
