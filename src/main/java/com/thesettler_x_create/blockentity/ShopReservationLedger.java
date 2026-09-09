package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks per-request item reservations for a {@link CreateShopBlockEntity} - "this much of this
 * item is spoken for by request X, don't let another request double-claim it before the delivery
 * actually happens." Extracted from {@code CreateShopBlockEntity} (which held this state directly
 * until the pre-1.0 hardening pass) purely to keep that class's size manageable; behavior is
 * unchanged.
 */
class ShopReservationLedger {
  private static final String TAG_RESERVATIONS = "Reservations";
  private static final long RESERVATION_TTL = 20L * 60L * 5L;

  private final CreateShopBlockEntity owner;
  private final Map<UUID, Reservation> reservations = new HashMap<>();

  ShopReservationLedger(CreateShopBlockEntity owner) {
    this.owner = owner;
  }

  /** Reserve items for a specific request to avoid duplicate ordering. */
  void reserve(UUID requestId, ItemStack key, int amount) {
    if (!owner.ensureServerThread("reserve")) {
      return;
    }
    if (amount <= 0) {
      return;
    }
    cleanExpired();
    Reservation reservation = reservations.get(requestId);
    if (reservation == null) {
      reservations.put(
          requestId, new Reservation(requestId, makeKey(key), amount, getExpireTime()));
    } else {
      reservation.stackKey = makeKey(key);
      reservation.reservedAmount += amount;
      reservation.expiresAtGameTime = getExpireTime();
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Reserved {}x {} for {}", amount, key.getHoverName().getString(), requestId);
    }
    owner.setChanged();
  }

  /** Release all reservations for a request. */
  void release(UUID requestId) {
    if (!owner.ensureServerThread("release")) {
      return;
    }
    cleanExpired();
    if (reservations.remove(requestId) != null) {
      owner.setChanged();
    }
  }

  /** Returns total reserved count for a stack key. */
  int getReservedFor(ItemStack key) {
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (matches(reservation.stackKey, key)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  /** Returns total reserved count for a deliverable match. */
  int getReservedForDeliverable(IDeliverable deliverable) {
    if (deliverable == null) {
      return 0;
    }
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (deliverable.matches(reservation.stackKey)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  /** Returns reserved count for a specific request. */
  int getReservedForRequest(UUID requestId) {
    if (requestId == null) {
      return 0;
    }
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (requestId.equals(reservation.requestId)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  /** Consumes reserved items for a request when deliveries are created. */
  int consumeReservedForRequest(UUID requestId, ItemStack key, int amount) {
    if (!owner.ensureServerThread("consumeReservedForRequest")) {
      return 0;
    }
    if (requestId == null || key == null || key.isEmpty() || amount <= 0) {
      return 0;
    }
    cleanExpired();
    Reservation reservation = reservations.get(requestId);
    if (reservation == null || !matches(reservation.stackKey, key)) {
      return 0;
    }
    int taken = Math.min(amount, reservation.reservedAmount);
    reservation.reservedAmount -= taken;
    if (reservation.reservedAmount <= 0) {
      reservations.remove(requestId);
    }
    if (taken > 0) {
      owner.setChanged();
    }
    return taken;
  }

  java.util.List<ItemStack> getReservedStacksSnapshot() {
    cleanExpired();
    java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
    for (Reservation reservation : reservations.values()) {
      if (reservation.stackKey == null || reservation.stackKey.isEmpty()) {
        continue;
      }
      ItemStack stack = reservation.stackKey.copy();
      stack.setCount(Math.max(1, reservation.reservedAmount));
      stacks.add(stack);
    }
    return stacks;
  }

  int size() {
    return reservations.size();
  }

  void clear() {
    reservations.clear();
  }

  void load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    reservations.clear();
    if (!tag.contains(TAG_RESERVATIONS)) {
      return;
    }
    CompoundTag resTag = tag.getCompound(TAG_RESERVATIONS);
    for (String key : resTag.getAllKeys()) {
      CompoundTag entry = resTag.getCompound(key);
      try {
        UUID id = UUID.fromString(key);
        ItemStack stack =
            ItemStack.parse(registries, entry.getCompound("stack")).orElse(ItemStack.EMPTY);
        int amount = entry.getInt("amount");
        long expires = entry.getLong("expires");
        if (!stack.isEmpty() && amount > 0) {
          reservations.put(id, new Reservation(id, stack, amount, expires));
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore malformed reservation keys.
      }
    }
  }

  void save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    CompoundTag resTag = new CompoundTag();
    for (Map.Entry<UUID, Reservation> entry : reservations.entrySet()) {
      Reservation reservation = entry.getValue();
      CompoundTag data = new CompoundTag();
      data.put("stack", reservation.stackKey.save(registries));
      data.putInt("amount", reservation.reservedAmount);
      data.putLong("expires", reservation.expiresAtGameTime);
      resTag.put(entry.getKey().toString(), data);
    }
    tag.put(TAG_RESERVATIONS, resTag);
  }

  private void cleanExpired() {
    if (!owner.ensureServerThread("cleanExpired")) {
      return;
    }
    long now = owner.getGameTimeSafe();
    Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator();
    while (iterator.hasNext()) {
      Reservation reservation = iterator.next().getValue();
      if (reservation.expiresAtGameTime <= now) {
        iterator.remove();
      }
    }
  }

  private long getExpireTime() {
    return owner.getGameTimeSafe() + RESERVATION_TTL;
  }

  private static boolean matches(ItemStack a, ItemStack b) {
    return ItemStack.isSameItemSameComponents(a, b);
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  static class Reservation {
    final UUID requestId;
    ItemStack stackKey;
    int reservedAmount;
    long expiresAtGameTime;

    Reservation(UUID requestId, ItemStack stackKey, int reservedAmount, long expiresAtGameTime) {
      this.requestId = requestId;
      this.stackKey = stackKey;
      this.reservedAmount = reservedAmount;
      this.expiresAtGameTime = expiresAtGameTime;
    }
  }
}
