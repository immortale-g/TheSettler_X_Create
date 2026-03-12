package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.interactionhandling.ServerCitizenInteraction;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One-shot info interaction for reorder failures due to unavailable stock network items. */
public class ShopLostPackageReorderUnavailableInteraction extends ServerCitizenInteraction {
  private static final String TAG_STACK = "Stack";
  private static final String TAG_REMAINING = "Remaining";
  private static final String TAG_REQUESTER = "Requester";
  private static final String TAG_ADDRESS = "Address";
  private static final String TAG_REQUESTED_AT = "RequestedAt";
  private static final String TAG_EPOCH = "Epoch";
  private static final String TAG_ACTIVE = "Active";

  private ItemStack stackKey = ItemStack.EMPTY;
  private int remaining;
  private String requesterName = "";
  private String address = "";
  private long requestedAt = -1L;
  private long interactionEpoch;
  private boolean active = true;

  public ShopLostPackageReorderUnavailableInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopLostPackageReorderUnavailableInteraction(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    this(stackKey, remaining, requesterName, address, requestedAt, 0L);
  }

  public ShopLostPackageReorderUnavailableInteraction(
      ItemStack stackKey,
      int remaining,
      String requesterName,
      String address,
      long requestedAt,
      long interactionEpoch) {
    super(
        buildInquiry(stackKey, remaining, requesterName, address),
        true,
        ChatPriority.IMPORTANT,
        data -> true,
        Component.translatable(
            "com.thesettler_x_create.interaction.createshop.lost_package.reorder_unavailable.id"),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.reorder_unavailable.response_back"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.reorder_unavailable.answer_back")));
    this.stackKey = stackKey == null ? ItemStack.EMPTY : stackKey.copy();
    this.stackKey.setCount(1);
    this.remaining = Math.max(1, remaining);
    this.requesterName = sanitize(requesterName);
    this.address = sanitize(address);
    this.requestedAt = requestedAt;
    this.interactionEpoch = interactionEpoch;
  }

  @Override
  public void onServerResponseTriggered(int response, Player player, ICitizenData citizen) {
    active = false;
    if (citizen == null || remaining <= 0 || stackKey.isEmpty()) {
      return;
    }
    if (!(citizen.getWorkBuilding() instanceof BuildingCreateShop shop)) {
      return;
    }
    if (interactionEpoch > 0L && interactionEpoch != shop.getLostPackageInteractionEpoch()) {
      return;
    }
    var pickup = shop.getPickupBlockEntity();
    if (pickup == null
        || pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt) <= 0) {
      return;
    }
    citizen.triggerInteraction(
        new ShopLostPackageInteraction(
            stackKey.copy(), remaining, requesterName, address, requestedAt, interactionEpoch));
  }

  @Override
  public boolean isValid(ICitizenData citizen) {
    return active;
  }

  @Override
  public String getType() {
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
    if (requestedAt > 0L) {
      tag.putLong(TAG_REQUESTED_AT, requestedAt);
    }
    if (interactionEpoch > 0L) {
      tag.putLong(TAG_EPOCH, interactionEpoch);
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
    requestedAt = tag.contains(TAG_REQUESTED_AT) ? tag.getLong(TAG_REQUESTED_AT) : -1L;
    interactionEpoch = tag.contains(TAG_EPOCH) ? tag.getLong(TAG_EPOCH) : 0L;
    active = !tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE);
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
        "com.thesettler_x_create.interaction.createshop.lost_package.reorder_unavailable.inquiry",
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
