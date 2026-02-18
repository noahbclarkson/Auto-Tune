package com.noahblclarkson.autotune.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record CartItem(
        @NotNull ShopItem shopItem,
        int quantity,
        boolean isBuying
) {

    public BigDecimal getTotalPrice() {
        return shopItem.price().multiply(BigDecimal.valueOf(quantity));
    }

    public CartItem withQuantity(int newQuantity) {
        return new CartItem(shopItem, newQuantity, isBuying);
    }

    public CartItem addQuantity(int delta) {
        return new CartItem(shopItem, Math.max(0, quantity + delta), isBuying);
    }
}
