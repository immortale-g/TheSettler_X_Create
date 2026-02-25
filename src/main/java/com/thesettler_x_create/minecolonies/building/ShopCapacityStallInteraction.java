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

/** Shopkeeper interaction for temporary rack-capacity stalls. */
public class ShopCapacityStallInteraction extends ServerCitizenInteraction {
  private static final String TAG_STACK = "Stack";
  private static final String TAG_REQUESTED = "Requested";
  private static final String TAG_ACCEPTED = "Accepted";
  private static final String TAG_ACTIVE = "Active";

  private ItemStack stackKey = ItemStack.EMPTY;
  private int requested;
  private int accepted;
  private boolean active = true;

  public ShopCapacityStallInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopCapacityStallInteraction(ItemStack stackKey, int requested, int accepted) {
    super(
        buildInquiry(stackKey, requested, accepted),
        true,
        ChatPriority.IMPORTANT,
        data -> true,
        Component.translatable("com.thesettler_x_create.interaction.createshop.capacity_stall.id"),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.capacity_stall.response_upgrade"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.capacity_stall.answer_upgrade")),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.capacity_stall.response_courier"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.capacity_stall.answer_courier")));
    this.stackKey = stackKey == null ? ItemStack.EMPTY : stackKey.copy();
    this.stackKey.setCount(1);
    this.requested = Math.max(1, requested);
    this.accepted = Math.max(0, accepted);
  }

  @Override
  public void onServerResponseTriggered(int response, Player player, ICitizenData citizen) {
    active = false;
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
    tag.putInt(TAG_REQUESTED, requested);
    tag.putInt(TAG_ACCEPTED, accepted);
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
    requested = Math.max(1, tag.getInt(TAG_REQUESTED));
    accepted = Math.max(0, tag.getInt(TAG_ACCEPTED));
    active = !tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE);
  }

  private static Component buildInquiry(ItemStack stackKey, int requested, int accepted) {
    String itemLabel =
        stackKey == null || stackKey.isEmpty()
            ? "unknown item"
            : stackKey.getHoverName().getString();
    int req = Math.max(1, requested);
    int acc = Math.max(0, accepted);
    int blocked = Math.max(0, req - acc);
    return Component.translatable(
        "com.thesettler_x_create.interaction.createshop.capacity_stall.inquiry",
        itemLabel,
        blocked,
        req);
  }
}
