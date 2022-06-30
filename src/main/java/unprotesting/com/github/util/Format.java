package unprotesting.com.github.util;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;

import lombok.Getter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.AutoTune;

public class Format {

  // The locale to use.
  protected static Locale locale;
  // The currency format.
  protected static NumberFormat currency;
  // The percentage format.
  protected static NumberFormat percent;
  // The decimal format.
  protected static NumberFormat decimal;
  // The number format.
  protected static NumberFormat number;
  // The mini message format.
  protected static MiniMessage miniMessage;
  // The date format
  protected static DateFormat date;
  // The logger
  @Getter
  protected static AutoTuneLogger log;

  /**
   * Loads the locale and formats.
   * @param localeString the locale string
   */
  public static void loadLocale(String localeString) {
    String[] localeSplit = localeString.split("_");
    locale = new Locale(localeSplit[0], localeSplit[1]);
    currency = NumberFormat.getCurrencyInstance(locale);
    percent = NumberFormat.getPercentInstance(locale);
    percent.setMaximumFractionDigits(2);
    decimal = NumberFormat.getNumberInstance(locale);
    decimal.setMaximumFractionDigits(2);
    number = NumberFormat.getNumberInstance(locale);
    date = DateFormat.getDateInstance(DateFormat.SHORT, locale);
    miniMessage = MiniMessage.miniMessage();
  }

  /**
   * Loads the logger.
   */
  public static void loadLogger(Level level) {
    log = new AutoTuneLogger(AutoTune.getInstance());
    log.setLevel(level);
    log.info("Logger loaded with level " + log.getLevel().toString());
  }

  /**
   * Format a number to a currency string.
   * @param amount the amount to format
   * @return the formatted currency string
   */
  public static String currency(double amount) {
    return currency.format(amount);
  }

  /**
   * Format a number to a percentage string.
   * @param amount the amount to format
   * @return the formatted percentage string
   */
  public static String percent(double amount) {
    return percent.format(amount);
  }

  /**
   * Format a number to a decimal string.
   * @param amount the amount to format
   * @return the formatted decimal string
   */
  public static String decimal(double amount) {
    return decimal.format(amount);
  }

  /**
   * Format a number to a number string.
   * @param amount the amount to format
   * @return the formatted number string
   */
  public static String number(double amount) {
    return number.format(amount);
  }

  /**
   * Format a millis long to a date.
   * @param time the time to format
   * @return the formatted date string
   */
  public static String date(long time) {
    return date.format(time);
  }


  /**
   * Send a message to a player using the MiniMessage API and a tag resolver.
   * @param player the player to send the message to
   * @param message the message to send
   * @param resolver the tag resolver
   */
  public static void sendMessage(Player player, String message, TagResolver resolver) {
    player.sendMessage(miniMessage.deserialize(message, resolver));
  }

  /**
   * Send a message to a player using the MiniMessage API.
   * @param player the player to send the message to
   * @param message the message to send
   */
  public static void sendMessage(Player player, String message) {
    player.sendMessage(miniMessage.deserialize(message));
  }

  /**
   * Send a message to a CommandSender using the MiniMessage API and a tag resolver.
   * @param sender The command sender
   * @param message The message to send
   * @param resolver The tag resolver
   */
  public static void sendMessage(CommandSender sender, String message, TagResolver resolver) {
    sender.sendMessage(miniMessage.deserialize(message, resolver));
  }

  /**
   * Send a message to a CommandSender using the MiniMessage API.
   * @param sender The command sender
   * @param message The message to send
   */
  public static void sendMessage(CommandSender sender, String message) {
    sender.sendMessage(miniMessage.deserialize(message));
  }

  /**
   * Get the component of a message using the MiniMessage API and a tag resolver.
   */
  public static Component getComponent(String message, TagResolver resolver) {
    return miniMessage.deserialize(message, resolver);
  }

  /**
   * Get the component of a message using the MiniMessage API.
   */
  public static Component getComponent(String message) {
    return miniMessage.deserialize(message);
  }

}
