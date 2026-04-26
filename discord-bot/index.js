/**
 * Auto-Tune Discord Bot
 * 
 * Provides in-Discord access to Auto-Tune economy status and alerts.
 * 
 * Usage:
 *   DISCORD_BOT_TOKEN=... AUTOTUNE_API_URL=http://localhost:3000 node index.js
 * 
 * Commands:
 *   /at status          — Server economy health (D/G ratio, GDP, volatility)
 *   /at price <item>    — Current buy/sell price for an item
 *   /at top             — Top 5 traders by volume
 *   /at help            — Command reference
 * 
 * Setup:
 *   1. Create a Discord Application at https://discord.com/developers
 *   2. Enable Bot permissions: Send Messages, Embed Links, Read Messages
 *   3. Add bot to server with: https://discord.com/api/oauth2/authorize?client_id=<APP_ID>&permissions=...&scope=bot
 *   4. Set DISCORD_BOT_TOKEN env var
 *   5. Set AUTOTUNE_API_URL (optional, defaults to http://localhost:3000)
 */

import { Client, GatewayIntentBits, REST, SlashCommandBuilder, SlashCommandSubcommandGroupBuilder, SlashCommandSubcommandBuilder } from 'discord.js';
import dotenv from 'dotenv';

dotenv.config();

const BOT_TOKEN = process.env.DISCORD_BOT_TOKEN;
const API_BASE = process.env.AUTOTUNE_API_URL || 'http://localhost:3000';

// ── API Client ─────────────────────────────────────────────────────────────

async function fetchJson(path) {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function getStats() {
  return fetchJson('/api/stats');
}

async function getHealth() {
  return fetchJson('/api/admin/health');
}

async function getItems() {
  return fetchJson('/api/items');
}

async function getLeaderboard(limit = 5) {
  return fetchJson(`/api/leaderboard?limit=${limit}`);
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function dgColor(dg) {
  if (dg < 1) return 0x10B981; // emerald
  if (dg < 3) return 0x22C55E; // green
  if (dg < 8) return 0xEAB308; // amber
  if (dg < 15) return 0xF97316; // orange
  return 0xEF4444; // red
}

function dgLabel(dg) {
  if (dg < 1) return 'Healthy';
  if (dg < 3) return 'Good';
  if (dg < 8) return 'Caution';
  if (dg < 15) return 'Warning';
  return 'Critical';
}

function formatCurrency(val) {
  if (val == null) return '—';
  const n = Number(val);
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(2)}M`;
  if (n >= 1_000) return `$${(n / 1_000).toFixed(1)}K`;
  return `$${n.toFixed(2)}`;
}

function formatNumber(n) {
  if (n == null) return '—';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function volColor(vol) {
  if (vol < 0.05) return 0x10B981;
  if (vol < 0.15) return 0xEAB308;
  return 0xEF4444;
}

// ── Commands ─────────────────────────────────────────────────────────────────

const commands = new SlashCommandBuilder()
  .setName('at')
  .setDescription('Auto-Tune Minecraft economy commands')
  .setDMPermission(false)
  .addSubcommandGroup(new SlashCommandSubcommandGroupBuilder()
    .setName('status')
    .setDescription('Server economy status')
    .addSubcommand(new SlashCommandSubcommandBuilder()
      .setName('full', 'Full economy status with D/G, GDP, and health')
    )
  )
  .addSubcommandGroup(new SlashCommandSubcommandGroupBuilder()
    .setName('price')
    .setDescription('Check item prices')
    .addSubcommand(new SlashCommandSubcommandBuilder()
      .setName('item', 'Price for a specific item')
      .addStringOption(o => o.setName('item').setDescription('Item material name (e.g. DIAMOND, GOLD_INGOT)').setRequired(false))
    )
  )
  .addSubcommandGroup(new SlashCommandSubcommandGroupBuilder()
    .setName('top')
    .setDescription('Top traders')
    .addSubcommand(new SlashCommandSubcommandBuilder()
      .setName('traders', 'Top 5 traders by trading volume')
    )
  )
  .addSubcommand(new SlashCommandSubcommandBuilder()
    .setName('help', 'Show command reference')
  );

// ── Bot ─────────────────────────────────────────────────────────────────────

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

client.once('ready', async () => {
  console.log(`[Auto-Tune Bot] Logged in as ${client.user.tag}`);
  
  try {
    const rest = new REST().setToken(BOT_TOKEN);
    await rest.put(
      // Global commands — changes propagate in ~1 hour
      // For faster testing use client.application.commands.set(commands, guildId)
      `/applications/${client.user.id}/commands`,
      { body: [commands.toJSON()] },
    );
    console.log('[Auto-Tune Bot] Slash commands registered globally');
  } catch (err) {
    console.error('[Auto-Tune Bot] Failed to register commands:', err.message);
  }
});

client.on('interactionCreate', async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  const { commandName, options } = interaction;

  if (commandName !== 'at') return;

  const subcommand = options.getSubcommandGroup(false) 
    ? `${options.getSubcommandGroup()}_${options.getSubcommand()}`
    : options.getSubcommand(false)
    ? options.getSubcommand()
    : null;

  const fullCmd = subcommand || 'help';

  try {
    switch (fullCmd) {
      case 'status_full': {
        const [stats, health] = await Promise.all([getStats(), getHealth()]);
        
        const dg = health?.debtGdpRatio ?? stats?.debtGdpRatio ?? 0;
        const gdp = stats?.totalGdp ?? 0;
        const vol = health?.avgVolatility ?? 0;
        const loans = stats?.activeLoans ?? 0;
        const items = stats?.totalItems ?? 0;
        const players = stats?.onlinePlayers ?? 0;
        
        const healthScore = Math.round(health?.healthScore ?? 50);
        const dgText = dg > 0 ? `${dg.toFixed(1)}x` : '—';

        const embed = {
          color: dgColor(dg),
          title: '⚒️ Auto-Tune Economy Status',
          description: `**D/G Ratio:** \`${dgText}\` — ${dgLabel(dg)}`,
          fields: [
            { name: 'GDP', value: formatCurrency(gdp), inline: true },
            { name: 'Volatility', value: vol > 0 ? `${(vol * 100).toFixed(1)}%` : '—', inline: true },
            { name: 'Health Score', value: `${healthScore}/100`, inline: true },
            { name: 'Online Players', value: String(players), inline: true },
            { name: 'Active Loans', value: formatNumber(loans), inline: true },
            { name: 'Items Tracked', value: formatNumber(items), inline: true },
          ],
          footer: { text: 'Auto-Tune economy engine · /at help for commands' },
          timestamp: new Date().toISOString(),
        };

        await interaction.reply({ embeds: [embed], ephemeral: false });
        break;
      }

      case 'price_item': {
        const material = options.getString('item')?.toUpperCase().trim();
        
        const [items, health] = await Promise.all([getItems(), getHealth().catch(() => null)]);
        
        if (!material) {
          // Show top items by volume
          const top = (items ?? []).slice(0, 8);
          const embed = {
            color: 0x10B981,
            title: '📊 Top Items by Volume',
            fields: top.map(item => ({
              name: item.displayName,
              value: `Buy: ${formatCurrency(item.buyPrice)} | Sell: ${formatCurrency(item.sellPrice)}`,
              inline: true,
            })),
            footer: { text: 'Use /at price <item> for specific item (e.g. /at price diamond)' },
          };
          await interaction.reply({ embeds: [embed], ephemeral: false });
          return;
        }

        const item = (items ?? []).find(i => 
          i.material.toUpperCase() === material ||
          i.displayName.toUpperCase().includes(material)
        );

        if (!item) {
          await interaction.reply({
            content: `❓ Item not found: \`${material}\`\nTry: DIAMOND, GOLD_INGOT, EMERALD, IRON_INGOT, NETHERITE_INGOT, LAPIS_LAZULI`,
            ephemeral: true,
          });
          return;
        }

        const spread = item.sellPrice > 0 
          ? ((item.buyPrice - item.sellPrice) / item.sellPrice * 100).toFixed(1)
          : '—';
        const dg = health?.debtGdpRatio ?? 0;
        
        const embed = {
          color: dgColor(dg),
          title: `💎 ${item.displayName}`,
          fields: [
            { name: 'Buy Price', value: formatCurrency(item.buyPrice), inline: true },
            { name: 'Sell Price', value: formatCurrency(item.sellPrice), inline: true },
            { name: 'Spread', value: spread !== '—' ? `${spread}%` : '—', inline: true },
            { name: '24h Change', value: item.change24h != null ? `${item.change24h >= 0 ? '+' : ''}${(item.change24h * 100).toFixed(1)}%` : '—', inline: true },
            { name: 'Material', value: `\`${item.material}\``, inline: true },
          ],
          footer: { text: `Auto-Tune · ${item.id ? `#${item.id}` : ''} · /at price for top items` },
        };

        await interaction.reply({ embeds: [embed], ephemeral: false });
        break;
      }

      case 'top_traders': {
        const lb = await getLeaderboard(5);
        
        if (!lb?.length) {
          await interaction.reply({ content: 'No trading data available yet.', ephemeral: true });
          return;
        }

        const dg = (await getHealth().catch(() => null))?.debtGdpRatio ?? 0;
        
        const fields = lb.map((entry, i) => ({
          name: `#${i + 1} — ${entry.playerName}`,
          value: `Net: ${entry.netTrade >= 0 ? '+' : ''}${formatCurrency(entry.netTrade)} | Volume: ${formatNumber(entry.totalVolume)} trades`,
          inline: false,
        }));

        const embed = {
          color: 0x10B981,
          title: '🏆 Top Traders',
          description: 'Top traders by trading volume this period',
          fields,
          footer: { text: 'Auto-Tune · /at top traders' },
        };

        await interaction.reply({ embeds: [embed], ephemeral: false });
        break;
      }

      case 'help':
      default: {
        const embed = {
          color: 0x10B981,
          title: '⚒️ Auto-Tune Commands',
          fields: [
            { name: '/at status full', value: 'Economy health, D/G ratio, GDP, volatility, loan count', inline: false },
            { name: '/at price [item]', value: 'Price for an item (e.g. /at price diamond) or top items if no item given', inline: false },
            { name: '/at top traders', value: 'Top 5 traders by trading volume', inline: false },
            { name: '/at help', value: 'Show this help message', inline: false },
          ],
          footer: { text: `API: ${API_BASE}` },
        };
        await interaction.reply({ embeds: [embed], ephemeral: true });
        break;
      }
    }
  } catch (err) {
    console.error(`[Auto-Tune Bot] Command error (${fullCmd}):`, err.message);
    
    const errorEmbed = {
      color: 0xEF4444,
      title: '⚠️ Auto-Tune Error',
      description: `Could not reach Auto-Tune API at \`${API_BASE}\`.\nIs the server online?`,
      footer: { text: 'Check AUTOTUNE_API_URL environment variable' },
    };
    
    if (interaction.deferred) {
      await interaction.editReply({ embeds: [errorEmbed] });
    } else {
      await interaction.reply({ embeds: [errorEmbed], ephemeral: true });
    }
  }
});

client.on('error', (err) => {
  console.error('[Auto-Tune Bot] Discord error:', err.message);
});

// ── Start ────────────────────────────────────────────────────────────────────

if (!BOT_TOKEN) {
  console.error('[Auto-Tune Bot] ERROR: DISCORD_BOT_TOKEN environment variable is required');
  console.error('  Set it in .env or: DISCORD_BOT_TOKEN=... node index.js');
  process.exit(1);
}

console.log(`[Auto-Tune Bot] Connecting to API: ${API_BASE}`);
client.login(BOT_TOKEN).catch((err) => {
  console.error('[Auto-Tune Bot] Failed to login:', err.message);
  process.exit(1);
});