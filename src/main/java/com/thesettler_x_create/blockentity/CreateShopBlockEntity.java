package com.thesettler_x_create.blockentity;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.create.VirtualCreateNetworkItemHandler;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import java.util.HashMap;
import java.util.Iterator;
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

public class CreateShopBlockEntity extends BlockEntity {
  private static final String TAG_SHOP_POS = "ShopPos";
  private static final String TAG_RESERVATIONS = "Reservations";
  private static final long RESERVATION_TTL = 20L * 60L * 5L;

  private final IItemHandler itemHandler = new VirtualCreateNetworkItemHandler(this);
  private final Map<UUID, Reservation> reservations = new HashMap<>();
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
    if (level == null || shopPos == null) {
      return null;
    }
    BlockEntity be = level.getBlockEntity(shopPos);
    if (be instanceof TileEntityCreateShop shop) {
      return shop;
    }
    return null;
  }

  public void reserve(UUID requestId, ItemStack key, int amount) {
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
    setChanged();
  }

  public void release(UUID requestId) {
    cleanExpired();
    if (reservations.remove(requestId) != null) {
      setChanged();
    }
  }

  public int getReservedFor(ItemStack key) {
    cleanExpired();
    int total = 0;
    for (Reservation reservation : reservations.values()) {
      if (matches(reservation.stackKey, key)) {
        total += reservation.reservedAmount;
      }
    }
    return total;
  }

  public int getReservedForDeliverable(IDeliverable deliverable) {
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

  public int getReservedForRequest(UUID requestId) {
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

  //    public int consumeReserved(ItemStack key, int amount) {
  //        if (amount <= 0) {
  //            return 0;
  //        }
  //        cleanExpired();
  //        int remaining = amount;
  //        Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator();
  //        while (iterator.hasNext() && remaining > 0) {
  //            Reservation reservation = iterator.next().getValue();
  //            if (!matches(reservation.stackKey, key)) {
  //                continue;
  //            }
  //            int taken = Math.min(remaining, reservation.reservedAmount);
  //            reservation.reservedAmount -= taken;
  //            remaining -= taken;
  //            if (reservation.reservedAmount <= 0) {
  //                iterator.remove();
  //            }
  //        }
  //        if (remaining != amount) {
  //            setChanged();
  //        }
  //        return amount - remaining;
  //    }

  public java.util.List<ItemStack> getReservedStacksSnapshot() {
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

  private void cleanExpired() {
    long now = getGameTimeSafe();
    Iterator<Map.Entry<UUID, Reservation>> iterator = reservations.entrySet().iterator();
    while (iterator.hasNext()) {
      Reservation reservation = iterator.next().getValue();
      if (reservation.expiresAtGameTime <= now) {
        iterator.remove();
      }
    }
  }

  private long getExpireTime() {
    return getGameTimeSafe() + RESERVATION_TTL;
  }

  private long getGameTimeSafe() {
    return level == null ? 0L : level.getGameTime();
  }

  private static boolean matches(ItemStack a, ItemStack b) {
    return ItemStack.isSameItemSameComponents(a, b);
  }

  private static ItemStack makeKey(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    return copy;
  }

  @Override
  public void loadAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains(TAG_SHOP_POS)) {
      shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    }
    reservations.clear();
    if (tag.contains(TAG_RESERVATIONS)) {
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
  }

  @Override
  public void saveAdditional(
      @NotNull CompoundTag tag, @NotNull net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (shopPos != null) {
      tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    }
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

  @SuppressWarnings("unused")
  public IItemHandler getItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  public static class Reservation {
    public final UUID requestId;
    public ItemStack stackKey;
    public int reservedAmount;
    public long expiresAtGameTime;

    public Reservation(
        UUID requestId, ItemStack stackKey, int reservedAmount, long expiresAtGameTime) {
      this.requestId = requestId;
      this.stackKey = stackKey;
      this.reservedAmount = reservedAmount;
      this.expiresAtGameTime = expiresAtGameTime;
    }
  }
}
