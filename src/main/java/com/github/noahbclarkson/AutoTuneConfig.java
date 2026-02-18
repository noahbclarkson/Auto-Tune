package com.github.noahbclarkson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public class AutoTuneConfig extends YamlConfiguration {

    private final Path file;

    public AutoTuneConfig(Path file) {
        this.file = file;
    }

    public void load() throws IOException, InvalidConfigurationException {
        if (Files.notExists(file)) {
            throw new IOException("Config file not found: " + file);
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            load(reader);
        }
    }

    public void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(saveToString());
        }
    }

}
