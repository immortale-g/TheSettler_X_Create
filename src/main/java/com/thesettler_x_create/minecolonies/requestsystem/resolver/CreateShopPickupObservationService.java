package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.blockentity.CreateShopBlockEntity;
import com.thesettler_x_create.minecolonies.building.BuildingCreateShop;
import com.thesettler_x_create.stock.PickupTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Consumes a request's reservation at the moment a courier takes its items out of the shop.
 *
 * <p>The shop hut reports every item taken out of its combined inventory, but never who took it.
 * The delivery an extraction belongs to is therefore read from MineColonies itself: a courier marks
 * the delivery it is reaching for in its job before touching the inventory (see {@link
 * CourierOngoingDeliveries}). Only those deliveries are booked, and their parent request's
 * reservation shrinks by exactly that much.
 *
 * <p>When MineColonies does not tell us, nothing is booked. The reservation is then consumed on
 * arrival, the way it was before the pickup observer existed. Guessing from the courier queue would
 * book the amount on a sibling delivery and leave the reservation of the real one hanging, so a
 * later booking is preferred over a wrong one. Anything else taking items (a player, another
 * worker) consumes nothing either way.
 */
final class CreateShopPickupObservationService {
  private final CourierOngoingDeliveries ongoingDeliveries = new CourierOngoingDeliveries();

  void onHutItemsTaken(
      CreateShopRequestResolver resolver,
      PickupTracker<IToken<?>> tracker,
      IRequestManager manager,
      ItemStack taken) {
    if (resolver == null
        || tracker == null
        || manager == null
        || taken == null
        || taken.isEmpty()) {
      return;
    }
    BuildingCreateShop shop = resolver.getShop(manager);
    CreateShopBlockEntity pickup = shop == null ? null : shop.getPickupBlockEntity();
    if (pickup == null) {
      return;
    }
    Map<IToken<?>, IToken<?>> parentByDelivery = new HashMap<>();
    List<PickupTracker.QueuedDelivery<IToken<?>>> candidates = new ArrayList<>();
    collectDeliveriesBeingPickedUp(manager, shop, taken, parentByDelivery, candidates);
    tracker.retainOnly(parentByDelivery.keySet());
    if (candidates.isEmpty()) {
      return;
    }
    Level level = manager.getColony() == null ? null : manager.getColony().getWorld();
    for (PickupTracker.Allocation<IToken<?>> allocation :
        tracker.recordTaken(taken.getCount(), candidates)) {
      resolver.observeDeliveryChildPickup(
          level, parentByDelivery.get(allocation.delivery()), allocation.delivery());
      int consumed =
          pickup.consumeReservedForRequest(allocation.owner(), taken, allocation.amount());
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] pickup observed delivery={} request={} item={} taken={} reservationConsumed={}",
            allocation.delivery(),
            allocation.owner(),
            CreateShopStackMetrics.describeStack(taken),
            allocation.amount(),
            consumed);
      }
    }
  }

  /**
   * The deliveries the colony's couriers are picking up from this shop at this moment, for this
   * item. A courier that is walking or delivering contributes nothing.
   */
  private void collectDeliveriesBeingPickedUp(
      IRequestManager manager,
      BuildingCreateShop shop,
      ItemStack taken,
      Map<IToken<?>, IToken<?>> parentByDelivery,
      List<PickupTracker.QueuedDelivery<IToken<?>>> candidates) {
    IColony colony = manager.getColony();
    if (colony == null || colony.getCitizenManager() == null) {
      return;
    }
    for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
      if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman job)) {
        continue;
      }
      for (IToken<?> token : ongoingDeliveries.of(colony, job)) {
        IRequest<?> request = requestOrNull(manager, token);
        if (request == null
            || !request.hasParent()
            || !(request.getRequest() instanceof Delivery delivery)
            || !CreateShopDeliveryOriginMatcher.isDeliveryFromShopHut(delivery, shop)) {
          continue;
        }
        parentByDelivery.put(token, request.getParent());
        if (ItemStack.isSameItemSameComponents(delivery.getStack(), taken)) {
          candidates.add(
              new PickupTracker.QueuedDelivery<>(
                  token,
                  CreateShopRequestResolver.toRequestId(request.getParent()),
                  delivery.getStack().getCount()));
        }
      }
    }
  }

  private static IRequest<?> requestOrNull(IRequestManager manager, IToken<?> token) {
    if (token == null) {
      return null;
    }
    try {
      return manager.getRequestForToken(token);
    } catch (Exception ignored) {
      // A token can outlive its request for a moment; it is simply not a candidate then.
      return null;
    }
  }
}
