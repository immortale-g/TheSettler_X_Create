package com.thesettler_x_create.minecolonies.building;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.interactionhandling.ServerCitizenInteraction;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.HolderLookup;
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
  private final long debugInstanceId = DEBUG_INSTANCE_SEQ.getAndIncrement();

  public ShopLostPackageInteraction(ICitizen citizen) {
    super(citizen);
  }

  public ShopLostPackageInteraction(
      ItemStack stackKey, int remaining, String requesterName, String address, long requestedAt) {
    this(stackKey, remaining, requesterName, address, requestedAt, 0L);
  }

  public ShopLostPackageInteraction(
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
                "com.thesettler_x_create.interaction.createshop.lost_package.answer_handover")),
        new Tuple<>(
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.response_cancel"),
            Component.translatable(
                "com.thesettler_x_create.interaction.createshop.lost_package.answer_cancel")));
    this.stackKey = stackKey == null ? ItemStack.EMPTY : stackKey.copy();
    this.stackKey.setCount(1);
    this.remaining = Math.max(0, remaining);
    this.requesterName = sanitize(requesterName);
    this.address = sanitize(address);
    this.requestedAt = requestedAt;
    this.interactionEpoch = interactionEpoch;
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction created debugId={} item={} remaining={} requester='{}' address='{}'",
          debugInstanceId,
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
    if (!isStillTracked(shop)) {
      active = false;
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction stale debugId={} item={} remaining={} requester='{}' address='{}' requestedAt={} epoch={} shopEpoch={}",
            debugInstanceId,
            stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
            remaining,
            requesterName,
            address,
            requestedAt,
            interactionEpoch,
            shop.getLostPackageInteractionEpoch());
      }
      return;
    }
    // Lock interaction immediately to avoid duplicate reopen windows while handling a response.
    active = false;
    removeQueuedLostPackageInteractions(citizen, false);
    boolean handled = false;
    int consumed = 0;
    if (response == 0) {
      BuildingCreateShop.LostPackageReorderResult reorder =
          shop.restartLostPackageDetailed(stackKey, remaining, requesterName, address, requestedAt);
      consumed = reorder.consumed();
      if (reorder.status() == BuildingCreateShop.LostPackageReorderStatus.NO_NETWORK_STOCK) {
        active = false;
        if (remaining > 0) {
          deferReorderUnavailableInteraction(citizen);
        }
        if (BuildingCreateShop.isDebugRequests()) {
          com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
              "[CreateShop] lost-package interaction reorder unavailable debugId={} item={} remaining={}",
              debugInstanceId,
              stackKey.isEmpty() ? "<empty>" : stackKey.getHoverName().getString(),
              remaining);
        }
        return;
      }
    } else if (response == 1) {
      consumed =
          shop.acceptLostPackageFromPlayer(
              player, stackKey, remaining, requesterName, address, requestedAt);
    } else if (response == 2) {
      consumed =
          shop.cancelLostPackageRequestAndInflight(
              stackKey, remaining, requesterName, address, requestedAt);
    }
    if (consumed > 0) {
      remaining = Math.max(0, remaining - consumed);
      handled = remaining <= 0;
    }
    if (response == 2) {
      // Cancel is a terminal player decision for this dialog instance.
      handled = true;
      remaining = 0;
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
      removeQueuedLostPackageInteractions(citizen, true);
    } else if (consumed <= 0) {
      // Re-arm the dialog when nothing was consumed and no dedicated fallback interaction took
      // over.
      active = true;
    }
  }

  @Override
  public boolean isValid(ICitizenData citizen) {
    if (active && citizen != null && citizen.getWorkBuilding() instanceof BuildingCreateShop shop) {
      if (!isStillTracked(shop)) {
        active = false;
      }
    }
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

  private void deferReorderUnavailableInteraction(ICitizenData citizen) {
    if (citizen == null || remaining <= 0 || stackKey.isEmpty()) {
      return;
    }
    Runnable trigger =
        () ->
            citizen.triggerInteraction(
                new ShopLostPackageReorderUnavailableInteraction(
                    stackKey.copy(),
                    remaining,
                    requesterName,
                    address,
                    requestedAt,
                    interactionEpoch));
    net.minecraft.world.level.Level level =
        citizen.getColony() == null ? null : citizen.getColony().getWorld();
    if (level != null && level.getServer() != null) {
      level.getServer().execute(trigger);
      return;
    }
    trigger.run();
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
    if (BuildingCreateShop.isDebugRequests()) {
      com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
          "[CreateShop] lost-package interaction deserialize debugId={} item={} remaining={} requester='{}' address='{}' active={}",
          debugInstanceId,
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
    return Component.literal(
        "Delivery seems lost for "
            + requester
            + ". Item: "
            + itemLabel
            + " x"
            + Math.max(1, remaining)
            + " (address: "
            + destination
            + ").");
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  private boolean isStillTracked(BuildingCreateShop shop) {
    if (shop == null || stackKey == null || stackKey.isEmpty() || remaining <= 0) {
      return false;
    }
    if (interactionEpoch > 0L && interactionEpoch != shop.getLostPackageInteractionEpoch()) {
      return false;
    }
    var pickup = shop.getPickupBlockEntity();
    if (pickup == null) {
      return false;
    }
    int strictRemaining =
        pickup.getInflightRemaining(stackKey, requesterName, address, requestedAt);
    if (strictRemaining > 0) {
      return true;
    }
    if (requestedAt > 0L) {
      return false;
    }
    return pickup.getInflightRemaining(stackKey, requesterName, address) > 0;
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

  private void removeQueuedLostPackageInteractions(ICitizenData citizen, boolean includeSelf) {
    if (citizen == null) {
      return;
    }
    Field field = findField(citizen.getClass(), "citizenChatOptions");
    if (field == null) {
      return;
    }
    try {
      field.setAccessible(true);
      Object raw = field.get(citizen);
      if (!(raw instanceof Map<?, ?> rawMap)) {
        return;
      }
      @SuppressWarnings("unchecked")
      Map<Object, Object> interactions = (Map<Object, Object>) rawMap;
      int before = interactions.size();
      interactions.entrySet().removeIf(entry -> isSameLostPackageInteraction(entry, includeSelf));
      int removed = Math.max(0, before - interactions.size());
      if (removed > 0) {
        citizen.markDirty(0);
      }
      if (removed > 0 && BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction immediate-remove debugId={} removed={}",
            debugInstanceId,
            removed);
      }
    } catch (Exception ex) {
      if (BuildingCreateShop.isDebugRequests()) {
        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
            "[CreateShop] lost-package interaction immediate-remove failed debugId={} err={}",
            debugInstanceId,
            ex.getClass().getSimpleName());
      }
    }
  }

  private static Field findField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private boolean isSameLostPackageInteraction(
      Map.Entry<Object, Object> entry, boolean includeSelf) {
    Object value = entry == null ? null : entry.getValue();
    if (!(value instanceof ShopLostPackageInteraction other)) {
      return false;
    }
    if (!includeSelf && other == this) {
      // Keep the currently handled interaction queued so a no-op response can re-arm it.
      return false;
    }
    if (!sameStackKey(other.stackKey)) {
      return false;
    }
    if (other.requestedAt != requestedAt) {
      return false;
    }
    if (!sanitize(other.requesterName).equals(sanitize(requesterName))) {
      return false;
    }
    if (!sanitize(other.address).equals(sanitize(address))) {
      return false;
    }
    return true;
  }

  private boolean sameStackKey(ItemStack otherKey) {
    if (stackKey == null || stackKey.isEmpty() || otherKey == null || otherKey.isEmpty()) {
      return false;
    }
    if (ItemStack.isSameItemSameComponents(stackKey, otherKey)) {
      return true;
    }
    return ItemStack.isSameItem(stackKey, otherKey);
  }
}
