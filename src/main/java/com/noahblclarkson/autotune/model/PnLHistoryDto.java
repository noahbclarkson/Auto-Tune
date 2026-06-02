package com.noahblclarkson.autotune.model;

/**
 * A single data point in a player's P&L history chart.
 * Each point represents one day, with the cumulative net P&L up to and including that day.
 *
 * @param timestamp Epoch millis of the day's close (midnight UTC)
 * @param dayLabel  Human-readable label e.g. "Apr 3"
 * @param netPnl    Cumulative net P&L through this day (sells - buys)
 */
public record PnLHistoryDto(long timestamp, String dayLabel, double netPnl) {}
