package com.thesettler_x_create.blockentity;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.create.CreatePackageBridge;
import com.thesettler_x_create.init.ModBlockEntities;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.tileentity.TileEntityCreateShop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class CreateShopOutputBlockEntity extends BlockEntity {
  private static final String TAG_SHOP_POS = "ShopPos";
  private static final String TAG_PACKAGE_ADDRESS = "PackageAddress";
  private final IItemHandler itemHandler = new OutputItemHandler();
  private BlockPos shopPos;
  private String packageAddress = "";

  public CreateShopOutputBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CREATE_SHOP_OUTPUT.get(), pos, state);
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

  public String getPackageAddress() {
    return packageAddress;
  }

  public void setPackageAddress(String address) {
    packageAddress = address == null ? "" : address;
    setChanged();
  }

  public IItemHandler getItemHandler(@Nullable Direction side) {
    return itemHandler;
  }

  @Override
  public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains(TAG_SHOP_POS)) {
      shopPos = BlockPos.of(tag.getLong(TAG_SHOP_POS));
    }
    packageAddress = tag.getString(TAG_PACKAGE_ADDRESS);
  }

  @Override
  public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (shopPos != null) {
      tag.putLong(TAG_SHOP_POS, shopPos.asLong());
    }
    if (!packageAddress.isEmpty()) {
      tag.putString(TAG_PACKAGE_ADDRESS, packageAddress);
    }
  }

  private final class OutputItemHandler implements IItemHandler {
    @Override
    public int getSlots() {
      return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      if (slot != 0 || !hasGaugeTask()) return ItemStack.EMPTY;
      return assemblePackage(true);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      if (slot != 0 || amount <= 0 || !hasGaugeTask()) return ItemStack.EMPTY;
      return assemblePackage(simulate);
    }

    private boolean hasGaugeTask() {
      BuildingCreateShop building = getBuilding();
      return building != null && building.hasGaugeTask();
    }

    @org.jetbrains.annotations.Nullable
    private BuildingCreateShop getBuilding() {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || !(shop.getBuilding() instanceof BuildingCreateShop b)) return null;
      return b;
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return false;
    }

    /**
     * The package the shop has ready for the next gauge task, or nothing.
     *
     * <p>Whatever the racks hold goes out now and the rest follows in a later package, the way
     * Create ships a partly covered order. A gauge order that waited for its last item would hold
     * back goods that are already there, sometimes for as long as the colony takes to craft the
     * remainder.
     *
     * <p>Preview and real pull must agree on the amount, or the shop duplicates items: Create reads
     * this slot, ships what it sees, and asks again. As long as both take what the racks hold, a
     * shipped package always books itself against the task and the next look finds the racks empty.
     */
    private ItemStack assemblePackage(boolean simulate) {
      BuildingCreateShop building = getBuilding();
      if (building == null) return ItemStack.EMPTY;
      BuildingCreateShop.GaugePackagingTask task = building.peekNextGaugeTask();
      if (task == null) return ItemStack.EMPTY;
      ItemStack extracted = extractFromRacks(task.item(), task.amount(), simulate);
      if (extracted.isEmpty()) return ItemStack.EMPTY;
      if (!simulate) building.deliverPartOfNextGaugeTask(extracted.getCount());
      if (DebugLog.enabled()) {
        // A preview builds a shippable package without taking anything. If Create ever ships one
        // of those, the goods stay in the racks and travel at the same time. On 2026-09-18 a
        // package of 12 torches arrived while only 4 had been booked, which is what that would
        // look like, so both calls say what they saw.
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] gauge package simulate={} taskAmount={} packaged={} address={}",
            simulate,
            task.amount(),
            extracted.getCount(),
            task.gaugeAddress());
      }
      return CreatePackageBridge.buildPackage(extracted, task.gaugeAddress());
    }

    /**
     * Pulls up to {@code amount} of {@code key} out of the shop's racks, and as much of it as is
     * there when that is less.
     *
     * <p>Seam-audit finding s1-6 made this all-or-nothing, because the caller had no way to say
     * "part of it": it packaged whatever it got and marked the whole gauge task done, losing the
     * shortfall. The caller books the amount against the task now, so a short pull is a partial
     * delivery and the rest stays owed. The important part is that the simulated and the real pull
     * return the same amount, since Create ships what the preview shows.
     */
    private ItemStack extractFromRacks(ItemStack key, int amount, boolean simulate) {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || shop.getBuilding() == null || shop.getLevel() == null) {
        return ItemStack.EMPTY;
      }
      int remaining = amount;
      ItemStack extracted = key.copy();
      extracted.setCount(0);

      for (TileEntityCreateShop.LoadedRack loaded : shop.getLoadedRacks()) {
        if (remaining <= 0) {
          break;
        }
        AbstractTileEntityRack rack = loaded.rack();
        IItemHandler handler = rack.getItemHandlerCap();
        if (handler == null) {
          continue;
        }
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
          ItemStack slotStack = handler.getStackInSlot(slot);
          if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, key)) {
            continue;
          }
          ItemStack pulled = handler.extractItem(slot, remaining, simulate);
          if (!pulled.isEmpty()) {
            extracted.grow(pulled.getCount());
            remaining -= pulled.getCount();
          }
        }
      }

      if (extracted.isEmpty()) {
        return ItemStack.EMPTY;
      }
      if (!simulate) {
        shop.noteRackStockChange(extracted, -extracted.getCount());
      }
      return extracted;
    }
  }
}
