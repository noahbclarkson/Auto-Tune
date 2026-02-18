package com.noahblclarkson.autotune.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

public record Section(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull Material icon,
        int priority
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String displayName;
        private Material icon = Material.CHEST;
        private int priority;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Section build() {
            return new Section(id, displayName, icon, priority);
        }
    }
}
