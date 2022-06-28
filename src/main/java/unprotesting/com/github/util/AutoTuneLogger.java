package unprotesting.com.github.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Setter
public class AutoTuneLogger {

  private Logger logger;
  private Level level;

  public AutoTuneLogger(JavaPlugin plugin) {
    this.logger = plugin.getLogger();
    this.level = Level.INFO;
  }

  private static String prefix(Level level, String msg) {
    return "[" + level.getName() + "] " + msg;
  }

  private boolean shouldLog(Level level) {
    return level.intValue() >= this.level.intValue();
  }

  /**
   * Logs a message at the given level with an optional prefix.
   * @param level the level to log at
   * @param message the message to log
   * @param param1 the parameters to use in the message
   */
  public void log(Level level, String message, Object param1) {
    if (shouldLog(level)) {
      logger.log(Level.INFO, prefix(level, message), param1);
    }
  }

  /**
   * Logs a message at the given level with optional prefixes.
   * @param level the level to log at
   * @param message the message to log
   * @param params the parameters to log
   */
  public void log(Level level, String message, Object... params) {
    if (shouldLog(level)) {
      logger.log(Level.INFO, prefix(level, message), params);
    }
  }


  /**
   * Logs a message at the given level.
   */
  public void log(Level level, String message) {
    if (shouldLog(level)) {
      logger.log(Level.INFO, prefix(level, message));
    }
  }

  public void fine(String message) {
    log(Level.FINE, message);
  }

  public void fine(String message, Object param1) {
    log(Level.FINE, message, param1);
  }

  public void fine(String message, Object... params) {
    log(Level.FINE, message, params);
  }

  public void finer(String message) {
    log(Level.FINER, message);
  }

  public void finer(String message, Object param1) {
    log(Level.FINER, message, param1);
  }

  public void finer(String message, Object... params) {
    log(Level.FINER, message, params);
  }

  public void finest(String message) {
    log(Level.FINEST, message);
  }

  public void finest(String message, Object param1) {
    log(Level.FINEST, message, param1);
  }

  public void finest(String message, Object... params) {
    log(Level.FINEST, message, params);
  }

  public void config(String message) {
    log(Level.CONFIG, message);
  }

  public void config(String message, Object param1) {
    log(Level.CONFIG, message, param1);
  }

  public void config(String message, Object... params) {
    log(Level.CONFIG, message, params);
  }

  public void info(String message) {
    log(Level.INFO, message);
  }

  public void info(String message, Object param1) {
    log(Level.INFO, message, param1);
  }

  public void info(String message, Object... params) {
    log(Level.INFO, message, params);
  }

  public void warning(String message) {
    log(Level.WARNING, message);
  }

  public void warning(String message, Object param1) {
    log(Level.WARNING, message, param1);
  }

  public void warning(String message, Object... params) {
    log(Level.WARNING, message, params);
  }

  public void severe(String message) {
    log(Level.SEVERE, message);
  }

  public void severe(String message, Object param1) {
    log(Level.SEVERE, message, param1);
  }

  public void severe(String message, Object... params) {
    log(Level.SEVERE, message, params);
  }

}
