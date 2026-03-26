package com.noahblclarkson.autotune.economy;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * Minimal fake Economy implementation for unit tests.
 * Extends AbstractEconomy and implements all remaining abstract methods
 * from the Economy interface with stub responses.
 */
@SuppressWarnings("deprecation")
public class FakeEconomy extends AbstractEconomy {

    public FakeEconomy() {}

    // --- Core abstract methods from Economy ---
    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "FakeEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double v) { return "$" + v; }
    @Override public String currencyNamePlural() { return "dollars"; }
    @Override public String currencyNameSingular() { return "dollar"; }

    // --- Account methods ---
    @Override public boolean hasAccount(String player) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(String player, String world) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return true; }

    // --- Balance methods ---
    @Override public double getBalance(String player) { return 0; }
    @Override public double getBalance(OfflinePlayer player) { return 0; }
    @Override public double getBalance(String player, String world) { return 0; }
    @Override public double getBalance(OfflinePlayer player, String world) { return 0; }

    // --- Has (can afford) methods ---
    @Override public boolean has(String player, double amount) { return true; }
    @Override public boolean has(OfflinePlayer player, double amount) { return true; }
    @Override public boolean has(String player, String world, double amount) { return true; }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return true; }

    // --- Withdraw methods ---
    @Override public EconomyResponse withdrawPlayer(String player, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(String player, String world, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return new EconomyResponse(amount, 0, ResponseType.SUCCESS, null);
    }

    // --- Deposit methods ---
    @Override public EconomyResponse depositPlayer(String player, double amount) {
        return new EconomyResponse(amount, amount, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return new EconomyResponse(amount, amount, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(String player, String world, double amount) {
        return new EconomyResponse(amount, amount, ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return new EconomyResponse(amount, amount, ResponseType.SUCCESS, null);
    }

    // --- Bank methods ---
    @Override public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse isBankOwner(String name, String player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse isBankMember(String name, String player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }
    @Override public List<String> getBanks() { return List.of(); }

    // --- Player account methods ---
    @Override public boolean createPlayerAccount(String player) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String player, String world) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return true; }
}
