package com.github.noahbclarkson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public class AutoTuneConfig extends YamlConfiguration {
    
    private Path file;

    public AutoTuneConfig(Path file) {
        this.file = file;
    }

    public void load() throws IOException, InvalidConfigurationException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
            loadFromString(builder.toString());
        }
    }

    public void save() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(saveToString());
        }
    }



}
