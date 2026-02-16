package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;

final class CreateShopResolverChain {
  private final CreateShopRequestResolver resolver;

  CreateShopResolverChain(CreateShopRequestResolver resolver) {
    this.resolver = resolver;
  }

  void sanitizeRequestChain(IRequestManager manager, IRequest<?> root) {
    if (manager == null || root == null) {
      return;
    }
    IStandardRequestManager standard = CreateShopRequestResolver.unwrapStandardManager(manager);
    if (standard == null) {
      return;
    }
    var handler = standard.getRequestHandler();
    if (handler == null) {
      return;
    }
    java.util.Set<IToken<?>> visiting = new java.util.HashSet<>();
    java.util.Set<IToken<?>> visited = new java.util.HashSet<>();
    java.util.ArrayDeque<IRequest<?>> stack = new java.util.ArrayDeque<>();
    java.util.ArrayDeque<java.util.Iterator<IToken<?>>> itStack = new java.util.ArrayDeque<>();
    IToken<?> rootToken = root.getId();
    if (rootToken == null) {
      return;
    }
    stack.push(root);
    itStack.push(root.getChildren().iterator());
    visiting.add(rootToken);
    visited.add(rootToken);
    int steps = 0;
    while (!stack.isEmpty() && steps < resolver.getMaxChainSanitizeNodes()) {
      steps++;
      var it = itStack.peek();
      if (it == null || !it.hasNext()) {
        IRequest<?> done = stack.pop();
        itStack.pop();
        if (done.getId() != null) {
          visiting.remove(done.getId());
        }
        continue;
      }
      IToken<?> childToken = it.next();
      if (childToken == null) {
        continue;
      }
      IRequest<?> parent = stack.peek();
      IToken<?> parentToken = parent.getId();
      if (childToken.equals(parentToken)) {
        parent.removeChild(childToken);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "self:" + parentToken;
          if (resolver.getChainCycleLogged().add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed self-cycle in request chain {}", parentToken);
          }
        }
        continue;
      }
      if (visiting.contains(childToken)) {
        parent.removeChild(childToken);
        if (Config.DEBUG_LOGGING.getAsBoolean()) {
          String key = "cycle:" + parentToken + ":" + childToken;
          if (resolver.getChainCycleLogged().add(key)) {
            TheSettlerXCreate.LOGGER.info(
                "[CreateShop] removed request chain cycle parent={} child={}",
                parentToken,
                childToken);
          }
        }
        continue;
      }
      if (visited.contains(childToken)) {
        continue;
      }
      IRequest<?> child;
      try {
        child = handler.getRequest(childToken);
      } catch (IllegalArgumentException ex) {
        child = null;
      }
      if (child == null) {
        continue;
      }
      visited.add(childToken);
      visiting.add(childToken);
      stack.push(child);
      itStack.push(child.getChildren().iterator());
    }
    if (steps >= resolver.getMaxChainSanitizeNodes() && Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] request chain sanitize aborted after {} steps for {}", steps, rootToken);
    }
  }

  boolean safeIsRequestChainValid(
      IRequestManager manager, IRequest<? extends IDeliverable> request) {
    try {
      return resolver.isRequestChainValid(manager, request);
    } catch (StackOverflowError error) {
      if (Config.DEBUG_LOGGING.getAsBoolean()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] request chain validation overflow for {}", request.getId());
      }
      return false;
    }
  }
}
