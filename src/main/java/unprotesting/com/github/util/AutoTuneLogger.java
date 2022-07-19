package unprotesting.com.github.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * The custom Auto-Tune logger class.
 */
@Getter
@Setter
public class AutoTuneLogger {

    private Logger logger;
    private Level level;

    public AutoTuneLogger(@NotNull JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.level = Level.INFO;
    }

    private static String prefix(@NotNull Level level, @NotNull String msg) {
        return "[" + level.getName() + "] " + msg;
    }

    private boolean shouldLog(@NotNull Level level) {
        return level.intValue() >= this.level.intValue();
    }

    /**
     * Logs a message at the given level with an optional prefix.
     *
     * @param level   the level to log at
     * @param message the message to log
     * @param param1  the parameters to use in the message
     */
    public void log(@NotNull Level level, @NotNull String message, @NotNull Object param1) {
        if (shouldLog(level)) {
            logger.log(Level.INFO, prefix(level, message), param1);
        }
    }

    /**
     * Logs a message at the given level with optional prefixes.
     *
     * @param level   the level to log at
     * @param message the message to log
     * @param params  the parameters to log
     */
    public void log(@NotNull Level level, @NotNull String message, Object... params) {
        if (shouldLog(level)) {
            logger.log(Level.INFO, prefix(level, message), params);
        }
    }

    /**
     * Logs a message at the given level.
     */
    public void log(@NotNull Level level, @NotNull String message) {
        if (shouldLog(level)) {
            logger.log(Level.INFO, prefix(level, message));
        }
    }

    public void fine(@NotNull String message) {
        log(Level.FINE, message);
    }

    public void fine(@NotNull String message, @NotNull Object param1) {
        log(Level.FINE, message, param1);
    }

    public void fine(@NotNull String message, @NotNull Object... params) {
        log(Level.FINE, message, params);
    }

    public void finer(@NotNull String message) {
        log(Level.FINER, message);
    }

    public void finer(@NotNull String message, @NotNull Object param1) {
        log(Level.FINER, message, param1);
    }

    public void finer(@NotNull String message, Object... params) {
        log(Level.FINER, message, params);
    }

    public void finest(@NotNull String message) {
        log(Level.FINEST, message);
    }

    public void finest(@NotNull String message, @NotNull Object param1) {
        log(Level.FINEST, message, param1);
    }

    public void finest(@NotNull String message, Object... params) {
        log(Level.FINEST, message, params);
    }

    public void config(@NotNull String message) {
        log(Level.CONFIG, message);
    }

    public void config(@NotNull String message, @NotNull Object param1) {
        log(Level.CONFIG, message, param1);
    }

    public void config(@NotNull String message, Object... params) {
        log(Level.CONFIG, message, params);
    }

    public void info(@NotNull String message) {
        log(Level.INFO, message);
    }

    public void info(@NotNull String message, @NotNull Object param1) {
        log(Level.INFO, message, param1);
    }

    public void info(@NotNull String message, Object... params) {
        log(Level.INFO, message, params);
    }

    public void warning(@NotNull String message) {
        log(Level.WARNING, message);
    }

    public void warning(@NotNull String message, @NotNull Object param1) {
        log(Level.WARNING, message, param1);
    }

    public void warning(@NotNull String message, Object... params) {
        log(Level.WARNING, message, params);
    }

    public void severe(@NotNull String message) {
        log(Level.SEVERE, message);
    }

    public void severe(@NotNull String message, @NotNull Object param1) {
        log(Level.SEVERE, message, param1);
    }

    public void severe(@NotNull String message, Object... params) {
        log(Level.SEVERE, message, params);
    }

}
