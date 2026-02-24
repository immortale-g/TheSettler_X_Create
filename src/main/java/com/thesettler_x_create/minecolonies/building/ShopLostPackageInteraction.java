package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.interactionhandling.ServerCitizenInteraction;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Shopkeeper chat interaction for lost package recovery actions. */
public class ShopLostPackageInteraction extends ServerCitizenInteraction {
  private static final String TAG_STACK = "Stack";
  private static final String TAG_REMAINING = "Remaining";
  private static final String TAG_REQUESTER = "Requester";
  private static final String TAG_ADDRESS = "Address";
  private static final String TAG_ACTIVE = "Active";

  private ItemStack stackKey = ItemStack.EMPTY;
  private int remaining;
  private String requesterName = "";
  private String address = "";
  private boolean active = true;

  public ShopLostPackageInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopLostPackageInteraction(
      ItemStack stackKey, int remaining, String requesterName, String address) {
    super(
        buildInquiry(stackKey, remaining, requesterName, address),
        true,
        ChatPriority.IMPORTANT,
        data -> true,
        Component.translatable("com.thesettler_x_create.interaction.createshop.lost_package.id"),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.response_reorder"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.answer_reorder")),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.response_handover"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.answer_handover")));
    this.stackKey = stackKey == null ? ItemStack.EMPTY : stackKey.copy();
    this.stackKey.setCount(1);
    this.remaining = Math.max(0, remaining);
    this.requesterName = sanitize(requesterName);
    this.address = sanitize(address);
  }

  @Override
  public void onServerResponseTriggered(int response, Player player, ICitizenData citizen) {
    if (!active || player == null || citizen == null) {
      return;
    }
    if (!(citizen.getWorkBuilding() instanceof BuildingCreateShop shop)) {
      return;
    }
    boolean handled = false;
    if (response == 0) {
      handled = shop.restartLostPackage(stackKey, remaining, requesterName, address);
    } else if (response == 1) {
      handled =
          shop.acceptLostPackageFromPlayer(player, stackKey, remaining, requesterName, address);
    }
    if (handled) {
      active = false;
    }
  }

  @Override
  public boolean isValid(ICitizenData citizen) {
    return active;
  }

  @Override
  public String getType() {
    // Reuse standard interaction type for compatibility with MineColonies interaction storage.
    return com.minecolonies.api.colony.interactionhandling.ModInteractionResponseHandlers.STANDARD
        .getPath();
  }

  @Override
  public List<com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler>
      genChildInteractions() {
    return Collections.emptyList();
  }

  @Override
  public CompoundTag serializeNBT(HolderLookup.Provider provider) {
    CompoundTag tag = super.serializeNBT(provider);
    if (!stackKey.isEmpty()) {
      tag.put(TAG_STACK, stackKey.save(provider));
    }
    if (remaining > 0) {
      tag.putInt(TAG_REMAINING, remaining);
    }
    if (!requesterName.isEmpty()) {
      tag.putString(TAG_REQUESTER, requesterName);
    }
    if (!address.isEmpty()) {
      tag.putString(TAG_ADDRESS, address);
    }
    if (!active) {
      tag.putBoolean(TAG_ACTIVE, false);
    }
    return tag;
  }

  @Override
  public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
    super.deserializeNBT(provider, tag);
    stackKey =
        tag.contains(TAG_STACK)
            ? ItemStack.parse(provider, tag.getCompound(TAG_STACK)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    remaining = Math.max(0, tag.getInt(TAG_REMAINING));
    requesterName = sanitize(tag.getString(TAG_REQUESTER));
    address = sanitize(tag.getString(TAG_ADDRESS));
    active = !tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE);
  }

  static boolean packageContains(ItemStack packageStack, ItemStack key, int required) {
    if (packageStack == null || packageStack.isEmpty() || key == null || key.isEmpty()) {
      return false;
    }
    if (!PackageItem.isPackage(packageStack)) {
      return false;
    }
    ItemStackHandler contents = PackageItem.getContents(packageStack);
    if (contents == null) {
      return false;
    }
    int found = 0;
    for (int i = 0; i < contents.getSlots(); i++) {
      ItemStack content = contents.getStackInSlot(i);
      if (!content.isEmpty() && ItemStack.isSameItemSameComponents(content, key)) {
        found += content.getCount();
        if (found >= Math.max(1, required)) {
          return true;
        }
      }
    }
    return false;
  }

  static List<ItemStack> unpackPackage(ItemStack packageStack) {
    List<ItemStack> unpacked = new ArrayList<>();
    if (packageStack == null || packageStack.isEmpty() || !PackageItem.isPackage(packageStack)) {
      return unpacked;
    }
    ItemStackHandler contents = PackageItem.getContents(packageStack);
    if (contents == null) {
      return unpacked;
    }
    for (int i = 0; i < contents.getSlots(); i++) {
      ItemStack content = contents.getStackInSlot(i);
      if (!content.isEmpty()) {
        unpacked.add(content.copy());
      }
    }
    return unpacked;
  }

  private static Component buildInquiry(
      ItemStack stackKey, int remaining, String requesterName, String address) {
    String requester = sanitize(requesterName);
    if (requester.isEmpty()) {
      requester = "unknown requester";
    }
    String destination = sanitize(address);
    if (destination.isEmpty()) {
      destination = "unknown address";
    }
    String itemLabel =
        stackKey == null || stackKey.isEmpty()
            ? "unknown item"
            : stackKey.getHoverName().getString();
    return Component.translatable(
        "com.thesettler_x_create.interaction.createshop.lost_package.inquiry",
        requester,
        itemLabel,
        Math.max(1, remaining),
        destination);
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }
}
