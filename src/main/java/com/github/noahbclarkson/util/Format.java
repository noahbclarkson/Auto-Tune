package com.github.noahbclarkson.util;

import java.util.logging.Level;

import org.jetbrains.annotations.NotNull;

import com.github.noahbclarkson.AutoTune;

public class Format {

    /**
     * Loads the logger.
     */
    public static AutoTuneLogger loadLogger(@NotNull Level level) {
        AutoTuneLogger log = new AutoTuneLogger(AutoTune.getInstance());
        log.setLevel(level);
        log.info("Logger loaded with level " + log.getLevel().toString());
        return log;
    }
    
}
