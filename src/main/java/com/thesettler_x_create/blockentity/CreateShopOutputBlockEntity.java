package com.thesettler_x_create.blockentity;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.simibubi.create.content.logistics.box.PackageItem;
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
import net.neoforged.neoforge.items.ItemStackHandler;
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

    private ItemStack assemblePackage(boolean simulate) {
      BuildingCreateShop building = getBuilding();
      if (building == null) return ItemStack.EMPTY;
      BuildingCreateShop.GaugePackagingTask task = building.peekNextGaugeTask();
      if (task == null) return ItemStack.EMPTY;
      ItemStack extracted = extractFromRacks(task.item(), task.amount(), simulate);
      if (extracted.isEmpty()) return ItemStack.EMPTY;
      if (!simulate) building.completeNextGaugeTask();
      ItemStackHandler handler = new ItemStackHandler(PackageItem.SLOTS);
      handler.setStackInSlot(0, extracted);
      ItemStack pkg = PackageItem.containing(handler);
      PackageItem.addAddress(pkg, task.gaugeAddress());
      return pkg;
    }

    /**
     * Seam-audit finding s1-6: a non-simulated pull that comes up short (racks only had part of
     * {@code amount} left, e.g. depleted by something else between the preview and this real call)
     * used to be treated as a full success anyway - the caller has no "partial" concept, it either
     * packages what it got and marks the gauge task complete, or does neither. Packaging a partial
     * amount as if it were the full order silently under-delivers and loses track of the shortfall
     * forever, since the task is marked done. A real (non-simulated) pull is therefore now
     * all-or-nothing: if it can't reach {@code amount}, every slot already pulled from this call is
     * put back before returning empty, so the racks are left exactly as found and the gauge task
     * stays pending for a later retry instead of quietly shipping less than ordered.
     */
    private ItemStack extractFromRacks(ItemStack key, int amount, boolean simulate) {
      TileEntityCreateShop shop = getShopTile();
      if (shop == null || shop.getBuilding() == null || shop.getLevel() == null) {
        return ItemStack.EMPTY;
      }
      int remaining = amount;
      ItemStack extracted = key.copy();
      extracted.setCount(0);
      java.util.List<Pull> pulls = simulate ? null : new java.util.ArrayList<>();

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
            if (pulls != null) {
              pulls.add(new Pull(handler, slot, pulled.copy()));
            }
          }
        }
      }

      if (extracted.isEmpty()) {
        return ItemStack.EMPTY;
      }
      if (!simulate && remaining > 0) {
        rollBack(pulls);
        return ItemStack.EMPTY;
      }
      return extracted;
    }

    private record Pull(IItemHandler handler, int slot, ItemStack amount) {}

    private void rollBack(java.util.List<Pull> pulls) {
      for (Pull pull : pulls) {
        ItemStack leftover = pull.handler().insertItem(pull.slot(), pull.amount(), false);
        if (!leftover.isEmpty()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.warn(
              "[CreateShop] output rollback could not fully restore {} to rack slot={}",
              leftover,
              pull.slot());
        }
      }
    }
  }
}
