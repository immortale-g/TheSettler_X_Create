package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shop task-tab module that extends warehouse queue data with inflight Create Shop parent tokens. */
public class CreateShopTaskModule extends WarehouseRequestQueueModule {
  @Override
  public void serializeToView(RegistryFriendlyByteBuf buf) {
    super.serializeToView(buf);
    List<IToken<?>> inflight = getInflightTaskTokens();
    buf.writeInt(inflight.size());
    for (IToken<?> token : inflight) {
      StandardFactoryController.getInstance().serialize(buf, token);
    }
  }

  private List<IToken<?>> getInflightTaskTokens() {
    if (!(building instanceof BuildingCreateShop shop)
        || shop.getColony() == null
        || !(shop.getColony().getRequestManager() instanceof IStandardRequestManager manager)) {
      return List.of();
    }
    CreateShopRequestResolver resolver = shop.getOrCreateShopResolver();
    if (resolver == null || resolver.getId() == null) {
      return List.of();
    }
    IToken<?> resolverId = resolver.getId();
    var assignmentStore = manager.getRequestResolverRequestAssignmentDataStore();
    if (assignmentStore == null || assignmentStore.getAssignments() == null) {
      return List.of();
    }
    var assigned = assignmentStore.getAssignments().get(resolverId);
    if (assigned == null || assigned.isEmpty()) {
      return List.of();
    }
    var requestHandler = manager.getRequestHandler();
    var resolverHandler = manager.getResolverHandler();
    if (requestHandler == null || resolverHandler == null) {
      return List.of();
    }
    List<IToken<?>> inflight = new ArrayList<>();
    for (IToken<?> token : new ArrayList<>(assigned)) {
      if (token == null) {
        continue;
      }
      try {
        IRequest<?> request = requestHandler.getRequest(token);
        if (request == null || request.getRequest() == null) {
          continue;
        }
        if (isTerminalRequestState(request.getState())) {
          continue;
        }
        if (!(request.getRequest() instanceof IDeliverable)
            || request.getRequest() instanceof Delivery) {
          continue;
        }
        IRequestResolver<?> owner = resolverHandler.getResolverForRequest(request);
        if (!(owner instanceof CreateShopRequestResolver ownerResolver)
            || !resolverId.equals(ownerResolver.getId())) {
          continue;
        }
        if (request.getState() != RequestState.IN_PROGRESS && !request.hasChildren()) {
          continue;
        }
        inflight.add(token);
      } catch (Exception ignored) {
        // Ignore stale assignment/request links.
      }
    }
    if (inflight.isEmpty()) {
      return List.of();
    }
    return List.copyOf(new LinkedHashSet<>(inflight));
  }

  private static boolean isTerminalRequestState(RequestState state) {
    if (state == null) {
      return false;
    }
    return state == RequestState.CANCELLED
        || state == RequestState.COMPLETED
        || state == RequestState.FAILED
        || state == RequestState.RECEIVED
        || state == RequestState.RESOLVED;
  }
}
