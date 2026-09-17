package com.thesettler_x_create.stock;

/**
 * An amount of one item kind. Used for reserved amounts and, in {@link StockAging}, for unreserved
 * ones, so the name says nothing about which of the two it carries; the call site does.
 *
 * @param key the item kind, normalized by whoever counted it
 * @param amount the quantity, always positive
 */
public record StockAmount<K>(K key, int amount) {}
