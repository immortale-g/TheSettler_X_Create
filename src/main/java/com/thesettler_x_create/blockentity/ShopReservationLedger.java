package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.stock.ReservationBook;
import com.thesettler_x_create.stock.ReservedAmount;
import com.thesettler_x_create.stock.nbt.ReservationNbt;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft side of the per-request pickup reservations of a {@link CreateShopBlockEntity}: "this
 * much of this item is spoken for by request X, don't let another request double-claim it before
 * the delivery actually happens".
 *
 * <p>The bookkeeping itself is a {@link ReservationBook} over item stacks. This class adds the
 * server thread guard, marks the block entity dirty, logs, and saves through {@link
 * ReservationNbt}.
 */
class ShopReservationLedger {
  private static final String TAG_RESERVATIONS = "Reservations";

  private final LedgerHost host;
  private final ReservationBook<ItemStack> book;

  ShopReservationLedger(LedgerHost host) {
    this.host = host;
    this.book =
        new ReservationBook<>(
            ItemStack::isSameItemSameComponents, ShopReservationLedger::makeKey, host::gameTime);
  }

  /** Reserve items for a specific request to avoid duplicate ordering. */
  void reserve(UUID requestId, ItemStack key, int amount) {
    if (!host.ensureServerThread("reserve")) {
      return;
    }
    if (key == null || key.isEmpty() || amount <= 0) {
      return;
    }
    book.expire();
    if (!book.reserve(requestId, key, amount)) {
      return;
    }
    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Reserved {}x {} for {}", amount, key.getHoverName().getString(), requestId);
    }
    host.markChanged();
  }

  /** Release all reservations for a request. */
  void release(UUID requestId) {
    if (!host.ensureServerThread("release")) {
      return;
    }
    book.expire();
    if (book.release(requestId)) {
      host.markChanged();
    }
  }

  /**
   * Keeps the reservations of still-active requests from expiring. Called every resolver tick with
   * the ids of requests that are known to be alive; everything else keeps its normal expiry.
   *
   * @return number of requests whose expiry was extended
   */
  int refreshReservations(Set<UUID> activeRequestIds) {
    if (!host.ensureServerThread("refreshReservations")) {
      return 0;
    }
    int refreshed = book.refresh(activeRequestIds);
    if (refreshed > 0) {
      host.markChanged();
    }
    return refreshed;
  }

  /** Returns total reserved count for a stack key. */
  int getReservedFor(ItemStack key) {
    expireOnServerThread();
    return key == null || key.isEmpty() ? 0 : book.reservedFor(key);
  }

  /** Returns total reserved count for a deliverable match. */
  int getReservedForDeliverable(IDeliverable deliverable) {
    if (deliverable == null) {
      return 0;
    }
    expireOnServerThread();
    return book.reservedMatching(deliverable::matches);
  }

  /** Returns reserved count for a specific request. */
  int getReservedForRequest(UUID requestId) {
    if (requestId == null) {
      return 0;
    }
    expireOnServerThread();
    return book.reservedForOwner(requestId);
  }

  /** Consumes reserved items of one kind for a request. */
  int consumeReservedForRequest(UUID requestId, ItemStack key, int amount) {
    if (!host.ensureServerThread("consumeReservedForRequest")) {
      return 0;
    }
    if (requestId == null || key == null || key.isEmpty() || amount <= 0) {
      return 0;
    }
    book.expire();
    int taken = book.consume(requestId, key, amount);
    if (taken > 0) {
      host.markChanged();
    }
    return taken;
  }

  /** One stack per request and item kind, sized to the reserved amount. */
  List<ItemStack> getReservedStacksSnapshot() {
    expireOnServerThread();
    List<ItemStack> stacks = new java.util.ArrayList<>();
    for (ReservedAmount<ItemStack> reserved : book.snapshot()) {
      ItemStack stack = reserved.key().copy();
      stack.setCount(Math.max(1, reserved.amount()));
      stacks.add(stack);
    }
    return stacks;
  }

  int size() {
    return book.ownerCount();
  }

  void clear() {
    book.clear();
  }

  void load(CompoundTag tag, HolderLookup.Provider registries) {
    if (!tag.contains(TAG_RESERVATIONS)) {
      book.restore(List.of());
      return;
    }
    book.restore(
        ReservationNbt.read(
            tag.getCompound(TAG_RESERVATIONS), stackTag -> readStack(stackTag, registries)));
  }

  void save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.put(TAG_RESERVATIONS, ReservationNbt.write(book.stored(), stack -> stack.save(registries)));
  }

  // Expiry mutates the book, so reads only trigger it on the server thread; off-thread reads see
  // the last state, as before.
  private void expireOnServerThread() {
    if (host.ensureServerThread("cleanExpired") && book.expire()) {
      host.markChanged();
    }
  }

  private static Optional<ItemStack> readStack(Tag stackTag, HolderLookup.Provider registries) {
    if (!(stackTag instanceof CompoundTag compound)) {
      return Optional.empty();
    }
    return ItemStack.parse(registries, compound).filter(stack -> !stack.isEmpty());
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }
}
