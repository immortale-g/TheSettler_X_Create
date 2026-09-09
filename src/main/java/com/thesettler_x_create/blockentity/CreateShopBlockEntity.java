package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.create.VirtualCreateNetworkItemHandler;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
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
  private final ShopReservationLedger reservationLedger = new ShopReservationLedger(this);
  private final ShopInflightLedger inflightLedger = new ShopInflightLedger(this);
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

  /** Returns total reserved count for a stack key. */
  public int getReservedFor(ItemStack key) {
    return reservationLedger.getReservedFor(key);
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
    inflightLedger.recordInflight(stacks, baselines, requesterName, address);
  }

  public void recordInflight(
      List<ItemStack> stacks,
      Map<ItemStack, Integer> baselines,
      String requesterName,
      String address,
      @Nullable UUID requestUuid) {
    inflightLedger.recordInflight(stacks, baselines, requesterName, address, requestUuid);
  }

  /** Reconciles inflight entries against current rack counts to detect arrivals. */
  public void reconcileInflight(Map<ItemStack, Integer> currentCounts) {
    inflightLedger.reconcileInflight(currentCounts);
  }

  /** Marks overdue inflight entries as notified and returns notices to surface. */
  public List<InflightNotice> consumeOverdueNotices(long now, long timeout) {
    return inflightLedger.consumeOverdueNotices(now, timeout);
  }

  /** Consumes tracked inflight quantity for a specific overdue notice tuple. */
  public int consumeInflight(
      ItemStack stackKey, int amount, @Nullable String requesterName, @Nullable String address) {
    return inflightLedger.consumeInflight(stackKey, amount, requesterName, address);
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
   * UUID rather than the requester-name/address strings {@link #getInflightRemaining( ItemStack,
   * String, String, long)} relies on — those can drift if a citizen is renamed or a resolver
   * reassigns the request before this check runs, causing a false "nothing inflight" read and a
   * duplicate network order. Every other lifecycle step (cancel, clear, hand-off) was already
   * UUID-first; this was the one read path still on strings-only.
   */
  public int getInflightRemaining(ItemStack stackKey, @Nullable UUID requestUuid) {
    return inflightLedger.getInflightRemaining(stackKey, requestUuid);
  }

  /** Returns currently tracked inflight remainder for a lost-package tuple. */
  public int getInflightRemaining(
      ItemStack stackKey, @Nullable String requesterName, @Nullable String address) {
    return inflightLedger.getInflightRemaining(stackKey, requesterName, address);
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
    return inflightLedger.cancelInflight(stackKey, requesterName, address);
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
   * <p>UUID-based cancel is precise and drift-free. String-matching fallback is still needed for
   * legacy entries (requestUuid == null) recorded before Phase 3.1.
   *
   * @return total remaining quantity removed
   */
  public int cancelInflightByUuid(@Nullable UUID requestUuid) {
    return inflightLedger.cancelInflightByUuid(requestUuid);
  }

  public void markInflightHandedOff(@Nullable UUID requestUuid) {
    inflightLedger.markInflightHandedOff(requestUuid);
  }

  public int clearInflightByUuid(@Nullable UUID requestUuid) {
    return inflightLedger.clearInflightByUuid(requestUuid);
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
