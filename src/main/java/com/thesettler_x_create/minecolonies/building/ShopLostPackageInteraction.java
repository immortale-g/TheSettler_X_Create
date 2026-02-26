package com.thesettler_x_create.minecolonies.building;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.interactionhandling.ServerCitizenInteraction;
import com.minecolonies.core.network.messages.server.colony.InteractionResponse;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Shopkeeper chat interaction for lost package recovery actions. */
public class ShopLostPackageInteraction extends ServerCitizenInteraction {
  private static final AtomicLong DEBUG_INSTANCE_SEQ = new AtomicLong(1L);
  private static final String TAG_STACK = "Stack";
  private static final String TAG_REMAINING = "Remaining";
  private static final String TAG_REQUESTER = "Requester";
  private static final String TAG_ADDRESS = "Address";
  private static final String TAG_ACTIVE = "Active";
  private static final String TAG_REQUESTED_AT = "RequestedAt";
  private static final String TAG_RUNTIME_ID = "RuntimeId";

  private ItemStack stackKey = ItemStack.EMPTY;
  private int remaining;
  private String requesterName = "";
  private String address = "";
  private long requestedAt;
  private String runtimeId = "createshop_lost_package|unknown";
  private boolean active = true;
  private final long debugInstanceId = DEBUG_INSTANCE_SEQ.getAndIncrement();

  public ShopLostPackageInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopLostPackageInteraction(
      ItemStack stackKey, int remaining, String requesterName, String address) {
    this(stackKey, remaining, requesterName, address, 0L);
  }

  public ShopLostPackageInteraction(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
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
    this.requestedAt = Math.max(0L, requestedAt);
    this.runtimeId =
        buildRuntimeId(
            this.stackKey, this.remaining, this.requesterName, this.address, this.requestedAt);
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction created debugId={} runtimeId={} item={} remaining={} requester='{}' address='{}'",
          debugInstanceId,
          runtimeId,
          this.stackKey.isEmpty() ? "<empty>" : this.stackKey.getHoverName().getString(),
          this.remaining,
          this.requesterName,
          this.address);
    }
  }

  @Override
  public void onServerResponseTriggered(int response, Player player, ICitizenData citizen) {
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction response debugId={} response={} active={} player={} citizen={} item={} remaining={} requester='{}' address='{}'",
          debugInstanceId,
          response,
          active,
          player == null ? "<null>" : player.getName().getString(),
          citizen == null ? "<null>" : citizen.getName(),
          stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          remaining,
          requesterName,
          address);
    }
    if (!active || player == null || citizen == null) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction ignored active={} playerNull={} citizenNull={}",
            active,
            player == null,
            citizen == null);
      }
      return;
    }
    if (!(citizen.getWorkBuilding() instanceof BuildingCreateShop shop)) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction ignored: work building is not create shop");
      }
      return;
    }
    boolean handled = false;
    int consumed = 0;
    if (response == 0) {
      consumed = shop.restartLostPackage(stackKey, remaining, requesterName, address);
    } else if (response == 1) {
      consumed =
          shop.acceptLostPackageFromPlayer(player, stackKey, remaining, requesterName, address);
    }
    if (consumed > 0) {
      remaining = Math.max(0, remaining - consumed);
      handled = remaining <= 0;
    }
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction handled={} response={} consumed={} remaining={}",
          handled,
          response,
          consumed,
          remaining);
    }
    if (handled) {
      active = false;
      remaining = 0;
    }
  }

  @Override
  public boolean onClientResponseTriggered(
      int response, Player player, ICitizenDataView citizen, BOWindow window) {
    if (player == null || citizen == null) {
      return false;
    }
    if (response < 0 || response >= getPossibleResponses().size()) {
      return false;
    }
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction client response debugId={} runtimeId={} response={} player={} citizenId={}",
          debugInstanceId,
          runtimeId,
          response,
          player.getName().getString(),
          citizen.getId());
    }
    new InteractionResponse(
            citizen.getColonyId(), citizen.getId(), player.level().dimension(), getId(), response)
        .sendToServer();
    return true;
  }

  @Override
  public boolean isValid(ICitizenData citizen) {
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction isValid debugId={} active={} citizen={}",
          debugInstanceId,
          active,
          citizen == null ? "<null>" : citizen.getName());
    }
    return active;
  }

  @Override
  public Component getId() {
    return Component.literal(runtimeId);
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
    if (requestedAt > 0L) {
      tag.putLong(TAG_REQUESTED_AT, requestedAt);
    }
    if (!runtimeId.isEmpty()) {
      tag.putString(TAG_RUNTIME_ID, runtimeId);
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
    requestedAt = Math.max(0L, tag.getLong(TAG_REQUESTED_AT));
    active = !tag.contains(TAG_ACTIVE) || tag.getBoolean(TAG_ACTIVE);
    runtimeId = sanitize(tag.getString(TAG_RUNTIME_ID));
    if (runtimeId.isEmpty()) {
      runtimeId = buildRuntimeId(stackKey, remaining, requesterName, address, requestedAt);
    }
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction deserialize debugId={} runtimeId={} item={} remaining={} requester='{}' address='{}' active={}",
          debugInstanceId,
          runtimeId,
          stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
          remaining,
          requesterName,
          address,
          active);
    }
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
    return countMatchingInPackage(packageStack, key) >= Math.max(1, required);
  }

  static int countMatchingInPackage(@Nullable ItemStack packageStack, @Nullable ItemStack key) {
    if (packageStack == null || packageStack.isEmpty() || key == null || key.isEmpty()) {
      return 0;
    }
    if (!PackageItem.isPackage(packageStack)) {
      return 0;
    }
    ItemStackHandler contents = PackageItem.getContents(packageStack);
    if (contents == null) {
      return 0;
    }
    int found = 0;
    for (int i = 0; i < contents.getSlots(); i++) {
      ItemStack content = contents.getStackInSlot(i);
      if (!content.isEmpty() && matchesForRecovery(content, key)) {
        found += content.getCount();
      }
    }
    return found;
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

  private static boolean matchesForRecovery(ItemStack candidate, ItemStack key) {
    if (candidate == null || candidate.isEmpty() || key == null || key.isEmpty()) {
      return false;
    }
    if (ItemStack.isSameItemSameComponents(candidate, key)) {
      return true;
    }
    return ItemStack.isSameItem(candidate, key);
  }

  private static String buildRuntimeId(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    String itemId =
        stackKey == null || stackKey.isEmpty()
            ? "minecraft:air"
            : String.valueOf(BuiltInRegistries.ITEM.getKey(stackKey.getItem()));
    String destination = sanitize(address);
    int count = Math.max(1, remaining);
    long segment = Math.max(0L, requestedAt);
    return "createshop_lost_package|" + itemId + "|" + destination + "|" + count + "|" + segment;
  }
}
