package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.create.VirtualCreateNetworkItemHandler;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import com.thesettler_x_create.stock.InflightBook;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pickup block entity for the Create Shop. Tracks reservations and inflight stock orders.
 *
 * <p>The actual bookkeeping lives in two extracted collaborators - {@link ShopReservationLedger}
 * (per-request "this much is spoken for") and {@link ShopInflightLedger} ("this much is ordered but
 * not here yet") - so this class stays a thin, stable public-API surface over them; every public
 * method here delegates straight through.
 */
public class CreateShopBlockEntity extends BlockEntity {
  private static final String TAG_SHOP_POS = "ShopPos";

  private final IItemHandler itemHandler = new VirtualCreateNetworkItemHandler(this);
  private final LedgerHost ledgerHost =
      new LedgerHost() {
        @Override
        public boolean ensureServerThread(String action) {
          return CreateShopBlockEntity.this.ensureServerThread(action);
        }

        @Override
        public long gameTime() {
          return getGameTimeSafe();
        }

        @Override
        public boolean hasLevel() {
          return CreateShopBlockEntity.this.hasLevel();
        }

        @Override
        public void markChanged() {
          setChanged();
        }
      };
  private final ShopReservationLedger reservationLedger = new ShopReservationLedger(ledgerHost);
  private final ShopInflightLedger inflightLedger = new ShopInflightLedger(ledgerHost);
  private BlockPos shopPos;

  public CreateShopBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_PICKUP.get(), pos, state);
  }

  public void setShopPos(BlockPos pos) {
    shopPos = pos;
    setChanged();
  }

  @Nullable
  public BlockPos getShopPos() {
    return shopPos;
  }

  @Nullable
  public TileEntityCreateShop getShopTile() {
    return TileEntityCreateShop.fromLevel(level, shopPos);
  }

  /** Reserve items for a specific request to avoid duplicate ordering. */
  public void reserve(UUID requestId, ItemStack key, int amount) {
    reservationLedger.reserve(requestId, key, amount);
  }

  /** Release all reservations for a request. */
  public void release(UUID requestId) {
    reservationLedger.release(requestId);
  }

  /**
   * Keeps the reservations of still-active requests from expiring. Called every resolver tick with
   * the ids of requests that are known to be alive; everything else keeps its normal expiry.
   *
   * @return number of reservations whose expiry was extended
   */
  public int refreshReservations(java.util.Set<UUID> activeRequestIds) {
    return reservationLedger.refreshReservations(activeRequestIds);
  }

  /** Returns total reserved count for a stack key. */
  public int getReservedFor(ItemStack key) {
    return reservationLedger.getReservedFor(key);
  }

  /**
   * Returns the reserved count for a stack key by every request except the given ones, e.g. without
   * Colony Factory Gauge reservations, which cover goods not in the racks yet.
   */
  public int getReservedForExcluding(ItemStack key, java.util.Set<UUID> excludedRequests) {
    return reservationLedger.getReservedForExcluding(key, excludedRequests);
  }

  /** Returns total reserved count for a deliverable match. */
  public int getReservedForDeliverable(IDeliverable deliverable) {
    return reservationLedger.getReservedForDeliverable(deliverable);
  }

  /** Returns reserved count for a specific request. */
  public int getReservedForRequest(UUID requestId) {
    return reservationLedger.getReservedForRequest(requestId);
  }

  /** Consumes reserved items for a request when deliveries are created. */
  public int consumeReservedForRequest(UUID requestId, ItemStack key, int amount) {
    return reservationLedger.consumeReservedForRequest(requestId, key, amount);
  }

  public List<ItemStack> getReservedStacksSnapshot() {
    return reservationLedger.getReservedStacksSnapshot();
  }

  /** Returns unique stack keys currently tracked as inflight. */
  public List<ItemStack> getInflightKeys() {
    return inflightLedger.getInflightKeys();
  }

  /**
   * Records inflight orders and their baseline stock counts.
   *
   * @param baselines current rack counts to detect arrivals later
   */
  public void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address) {
    inflightLedger.recordInflight(stacks, baselines, requesterName, address, null);
  }

  public void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address,
      @Nullable UUID requestUuid) {
    inflightLedger.recordInflight(stacks, baselines, requesterName, address, requestUuid);
  }

  /**
   * Reports a rack change the shop caused or observed itself (a courier gathering, housekeeping,
   * packaging, a handover), so it is not mistaken for, or does not hide, an arrival.
   */
  public void noteRackStockChange(ItemStack key, int delta) {
    inflightLedger.noteStockChange(key, delta);
  }

  /**
   * Reconciles inflight entries against current rack counts to detect arrivals.
   *
   * @return what arrived, per owning request ({@code null}: nobody) and item
   */
  public List<InflightBook.Arrival<ItemStack>> reconcileInflight(
      Map<ItemStack, Integer> currentCounts) {
    return inflightLedger.reconcileInflight(currentCounts);
  }

  /** Marks overdue inflight entries as notified and returns notices to surface. */
  public List<InflightNotice> consumeOverdueNotices(long now, long timeout) {
    return inflightLedger.consumeOverdueNotices(now, timeout);
  }

  /** Consumes tracked inflight quantity for a specific overdue notice tuple. */
  public int consumeInflight(
      ItemStack stackKey, int amount, @Nullable String requesterName, @Nullable String address) {
    return inflightLedger.consumeInflight(stackKey, amount, requesterName, address, -1L);
  }

  public int consumeInflight(
      ItemStack stackKey,
      int amount,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    return inflightLedger.consumeInflight(stackKey, amount, requesterName, address, requestedAt);
  }

  /**
   * Returns currently tracked inflight remainder for a request, matched by its MineColonies request
   * UUID rather than the requester-name/address strings, which can drift when a citizen is renamed
   * or a request is reassigned.
   */
  public int getInflightRemaining(ItemStack stackKey, @Nullable UUID requestUuid) {
    return inflightLedger.getInflightRemaining(stackKey, requestUuid);
  }

  /** Returns currently tracked inflight remainder for a lost-package tuple. */
  public int getInflightRemaining(
      ItemStack stackKey, @Nullable String requesterName, @Nullable String address) {
    return inflightLedger.getInflightRemaining(stackKey, requesterName, address, -1L);
  }

  public int getInflightRemaining(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    return inflightLedger.getInflightRemaining(stackKey, requesterName, address, requestedAt);
  }

  /** Clears tracked inflight entries for a matching stack/requester/address tuple. */
  public int cancelInflight(
      ItemStack stackKey, @Nullable String requesterName, @Nullable String address) {
    return inflightLedger.cancelInflight(stackKey, requesterName, address, -1L);
  }

  public int cancelInflight(
      ItemStack stackKey,
      @Nullable String requesterName,
      @Nullable String address,
      long requestedAt) {
    return inflightLedger.cancelInflight(stackKey, requesterName, address, requestedAt);
  }

  /**
   * Cancels all inflight entries linked to the given request UUID.
   *
   * @return total remaining quantity removed
   */
  public int cancelInflightByUuid(@Nullable UUID requestUuid) {
    return inflightLedger.cancelInflightByUuid(requestUuid);
  }

  /** What is still on its way for a request, for every item it accepts. */
  public int getInflightRemainingFor(
      @Nullable UUID requestUuid, java.util.function.Predicate<ItemStack> accepts) {
    return inflightLedger.getInflightRemainingFor(requestUuid, accepts);
  }

  /**
   * A request ended: its orders on the way lose their owner instead of being forgotten, so a new
   * request for the item claims them instead of ordering again.
   *
   * @return the amount that is now unowned
   */
  public int detachInflight(@Nullable UUID requestUuid) {
    return inflightLedger.detach(requestUuid);
  }

  /**
   * Hands unowned incoming stock that a request accepts to that request, oldest first.
   *
   * @return the amount claimed
   */
  public int claimFreeInflight(
      @Nullable UUID requestUuid, java.util.function.Predicate<ItemStack> accepts, int amount) {
    return inflightLedger.claimFree(requestUuid, accepts, amount);
  }

  /**
   * Drops an amount of one item from a request's orders, for an order that was never sent.
   *
   * @return the amount removed
   */
  public int cancelInflight(@Nullable UUID requestUuid, ItemStack stackKey, int amount) {
    return inflightLedger.cancel(requestUuid, stackKey, amount);
  }

  /**
   * Drops unowned orders older than {@code timeout}; nobody waits for them.
   *
   * @return the dropped orders
   */
  public List<InflightBook.StoredEntry<ItemStack>> expireFreeInflight(long now, long timeout) {
    return inflightLedger.expireFree(now, timeout);
  }

  /**
   * Operator reset: drops every pickup reservation. Open requests reserve rack stock again on their
   * next tick.
   *
   * @return number of requests that held reservations
   */
  public int clearReservations() {
    if (!ensureServerThread("clearReservations")) {
      return 0;
    }
    int cleared = reservationLedger.size();
    if (cleared > 0) {
      reservationLedger.clear();
      setChanged();
    }
    return cleared;
  }

  /**
   * Operator reset: forgets every order on its way from the Create network. Open requests order it
   * again; goods that still arrive land in the racks unreserved.
   *
   * @return number of orders that were tracked
   */
  public int clearInflight() {
    if (!ensureServerThread("clearInflight")) {
      return 0;
    }
    int cleared = inflightLedger.entryCount();
    if (inflightLedger.size() > 0) {
      inflightLedger.clear();
      setChanged();
    }
    return cleared;
  }

  /** Number of orders currently tracked as on their way from the Create network. */
  public int getInflightEntryCount() {
    return inflightLedger.entryCount();
  }

  /** Clears reservations and inflight tracking for test/debug clean-state runs. */
  public int clearRuntimeTrackingForDebug() {
    if (!ensureServerThread("clearRuntimeTrackingForDebug")) {
      return 0;
    }
    int removed = reservationLedger.size() + inflightLedger.size();
    if (removed <= 0) {
      return 0;
    }
    reservationLedger.clear();
    inflightLedger.clear();
    setChanged();
    return removed;
  }

  /**
   * Debug helper: injects an artificial inflight tuple for automated lost-package harness tests.
   */
  public int debugInjectInflight(
      ItemStack stackKey,
      int amount,
      @Nullable String requesterName,
      @Nullable String address,
      long ageTicks) {
    return inflightLedger.debugInjectInflight(stackKey, amount, requesterName, address, ageTicks);
  }

  /** Debug helper: returns the oldest active inflight tuple, regardless of overdue state. */
  @Nullable
  public InflightNotice debugPeekOldestInflightNotice(long now) {
    return inflightLedger.debugPeekOldestInflightNotice(now);
  }

  long getGameTimeSafe() {
    return level == null ? 0L : level.getGameTime();
  }

  boolean ensureServerThread(String action) {
    if (level == null || level.isClientSide) {
      return false;
    }
    var server = level.getServer();
    if (server != null && !server.isSameThread()) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] inflight '{}' ignored off-server-thread", action);
      }
      return false;
    }
    return true;
  }

  @Override
  public void loadAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains(TAG_SHOP_POS)) {
      shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    }
    reservationLedger.load(tag, registries);
    inflightLedger.load(tag, registries);
  }

  @Override
  public void saveAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (shopPos != null) {
      tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    }
    reservationLedger.save(tag, registries);
    inflightLedger.save(tag, registries);
  }

  public IItemHandler getItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  public static class InflightNotice {
    public final ItemStack stackKey;
    public final int remaining;
    public final long age;
    public final String requesterName;
    public final String address;
    public final long requestedAt;

    /** UUID of the originating MineColonies request; null for legacy entries without UUID. */
    @Nullable public final UUID requestUuid;

    public InflightNotice(
        ItemStack stackKey,
        int remaining,
        long age,
        String requesterName,
        String address,
        long requestedAt,
        @Nullable UUID requestUuid) {
      this.stackKey = stackKey;
      this.remaining = remaining;
      this.age = age;
      this.requesterName = requesterName == null ? "" : requesterName;
      this.address = address == null ? "" : address;
      this.requestedAt = requestedAt;
      this.requestUuid = requestUuid;
    }
  }
}
