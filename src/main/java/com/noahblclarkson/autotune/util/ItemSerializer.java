package com.noahblclarkson.autotune.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class ItemSerializer {

    private ItemSerializer() {
    }

    @NotNull
    public static String getItemHash(@NotNull ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) {
            return getMaterialHash(itemStack.getType());
        }

        ItemMeta meta = itemStack.getItemMeta();
        StringBuilder sb = new StringBuilder();
        sb.append(itemStack.getType().name());

        if (meta.hasDisplayName()) {
            sb.append(":name=").append(meta.displayName());
        }

        if (meta.hasLore() && meta.lore() != null) {
            sb.append(":lore=").append(meta.lore().hashCode());
        }

        if (meta.hasEnchants()) {
            sb.append(":enchants=").append(meta.getEnchants().hashCode());
        }

        if (meta.hasCustomModelData()) {
            sb.append(":cmd=").append(meta.getCustomModelData());
        }

        return hashString(sb.toString());
    }

    @NotNull
    public static String getMaterialHash(@NotNull Material material) {
        return hashString("MATERIAL:" + material.name());
    }

    @NotNull
    public static String serializeItemStack(@NotNull ItemStack itemStack) {
        byte[] bytes = itemStack.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    @NotNull
    public static ItemStack deserializeItemStack(@NotNull String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ItemStack.deserializeBytes(bytes);
    }

    @Nullable
    public static ItemStack tryDeserializeItemStack(@Nullable String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return deserializeItemStack(base64);
        } catch (Exception e) {
            return null;
        }
    }

    @NotNull
    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(input.getBytes(StandardCharsets.UTF_8))
                    .substring(0, Math.min(32, input.length()));
        }
    }

    public static boolean matchesItem(@NotNull ItemStack itemStack, @NotNull String hash) {
        return getItemHash(itemStack).equals(hash);
    }

    public static boolean isSimilar(@NotNull ItemStack a, @NotNull ItemStack b) {
        return getItemHash(a).equals(getItemHash(b));
    }
}
