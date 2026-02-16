package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.util.MessageUtils;
import com.thesettler_x_create.Config;
import java.util.List;
import net.minecraft.world.item.ItemStack;

final class CreateShopResolverMessaging {
  CreateShopResolverMessaging(CreateShopRequestResolver resolver) {}

  void sendShopChat(IRequestManager manager, String key, List<ItemStack> stacks) {
    if (!Config.CHAT_MESSAGES_ENABLED.getAsBoolean()) {
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
}
