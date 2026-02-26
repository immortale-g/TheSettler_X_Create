package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.util.MessageUtils;
import com.thesettler_x_create.Config;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class CreateShopResolverMessaging {
  private final Map<String, Long> lastFlowChatTick = new HashMap<>();

  CreateShopResolverMessaging(CreateShopRequestResolver resolver) {}

  void sendShopChat(IRequestManager manager, String key, List<ItemStack> stacks) {
    if (!isChatMessagesEnabled()) {
      return;
    }
    if (stacks == null || stacks.isEmpty()) {
      return;
    }
    for (ItemStack stack : stacks) {
      if (stack.isEmpty()) {
        continue;
      }
      MessageUtils.format(key, stack.getHoverName().getString(), stack.getCount())
          .sendTo(manager.getColony())
          .forAllPlayers();
    }
  }

  void sendFlowStep(
      IRequestManager manager,
      String key,
      IRequest<?> request,
      @Nullable String stackLabel,
      int amount) {
    if (!isChatMessagesEnabled()
        || !isFlowChatMessagesEnabled()
        || manager == null
        || request == null) {
      return;
    }
    String token = String.valueOf(request.getId());
    String item = stackLabel == null || stackLabel.isBlank() ? "-" : stackLabel;
    int count = Math.max(0, amount);
    long now = 0L;
    if (manager.getColony() != null && manager.getColony().getWorld() != null) {
      now = manager.getColony().getWorld().getGameTime();
    }
    String dedupeKey = key + "|" + token + "|" + item + "|" + count;
    Long lastTick = lastFlowChatTick.get(dedupeKey);
    if (lastTick != null && lastTick.longValue() == now) {
      return;
    }
    lastFlowChatTick.put(dedupeKey, now);
    MessageUtils.format(key, token, item, count).sendTo(manager.getColony()).forAllPlayers();
  }

  String resolveRequesterName(IRequestManager manager, IRequest<?> request) {
    if (request == null) {
      return "unknown";
    }
    try {
      com.minecolonies.api.colony.requestsystem.requester.IRequester requester =
          request.getRequester();
      if (requester == null) {
        return "unknown";
      }
      net.minecraft.network.chat.MutableComponent component =
          requester.getRequesterDisplayName(manager, request);
      if (component == null) {
        return "unknown";
      }
      String text = component.getString();
      return text == null || text.isBlank() ? "unknown" : text;
    } catch (Exception ignored) {
      return "unknown";
    }
  }

  private static boolean isChatMessagesEnabled() {
    try {
      return Config.CHAT_MESSAGES_ENABLED.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }

  private static boolean isFlowChatMessagesEnabled() {
    try {
      return Config.FLOW_CHAT_MESSAGES_ENABLED.getAsBoolean();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }
}
