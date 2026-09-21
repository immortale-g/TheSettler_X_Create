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
     * The package the shop has ready for a waiting gauge task, or nothing.
     *
     * <p>Whatever the racks hold goes out now and the rest follows in a later package, the way
     * Create ships a partly covered order. A gauge order that waited for its last item would hold
     * back goods that are already there, sometimes for as long as the colony takes to craft the
     * remainder.
     *
     * <p>Served is the first task the racks can cover, not simply the first task. Tasks are queued
     * in the order the gauges asked, which says nothing about when their goods arrive: an order
     * waiting on a crafter would otherwise stand at the head of the queue and hold back every order
     * behind it, including ones a courier filled minutes ago.
     *
     * <p>Preview and real pull must agree on the amount, or the shop duplicates items: Create reads
     * this slot, ships what it sees, and asks again. Both walk the same queue in the same order and
     * take what the racks hold, so they pick the same task and the same amount, and the next look
     * finds the racks empty.
     */
    private ItemStack assemblePackage(boolean simulate) {
      BuildingCreateShop building = getBuilding();
      if (building == null) return ItemStack.EMPTY;
      GaugePackageSelection.Choice choice =
          GaugePackageSelection.select(
              building.getGaugeTasks(), OutputItemHandler.this::extractFromRacks, simulate);
      if (choice == null) return ItemStack.EMPTY;
      BuildingCreateShop.GaugePackagingTask task = choice.task();
      ItemStack extracted = choice.extracted();
      int open = task.amount() - extracted.getCount();
      if (!simulate) {
        int booked = building.deliverPartOfGaugeTask(task.requestId(), extracted.getCount());
        // The task was read a moment ago, so it is there. If it is not, the goods are already out
        // of the racks and travel anyway; saying nothing is owed is the honest answer then.
        open = booked < 0 ? 0 : booked;
        // Only the real pull is logged. Create polls the preview for every pending package, so
        // logging that one writes a line per tick, and debug logging is on by default until 1.0.
        // What the preview saw still shows up: a package that leaves without a line here is one
        // that was never pulled.
        if (DebugLog.enabled()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] gauge package taskAmount={} packaged={} stillOpen={} address={}",
              task.amount(),
              extracted.getCount(),
              open,
              task.gaugeAddress());
        }
      }
      return CreatePackageBridge.buildGaugePackage(extracted, task.gaugeAddress(), open);
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
