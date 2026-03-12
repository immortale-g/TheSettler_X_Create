package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;

/** Routes MineColonies delivery callbacks to the matching local Create Shop resolver instance. */
final class CreateShopDeliveryCallbackService {
  void onDeliveryCancelled(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver =
        CreateShopDeliveryResolverLocator.findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = CreateShopDeliveryResolverLocator.findResolverByDeliveryToken(manager, request);
    }
    if (resolver != null) {
      resolver.handleDeliveryCancelledForOps(manager, request);
      return;
    }
    CreateShopDeliveryResolverLocator.logUnresolvedDeliveryCallback("cancelled", manager, request);
  }

  void onDeliveryComplete(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver resolver =
        CreateShopDeliveryResolverLocator.findResolverForDelivery(manager, request);
    if (resolver == null) {
      resolver = CreateShopDeliveryResolverLocator.findResolverByDeliveryToken(manager, request);
    }
    if (resolver != null) {
      resolver.handleDeliveryCompleteForOps(manager, request);
      return;
    }
    CreateShopDeliveryResolverLocator.logUnresolvedDeliveryCallback("complete", manager, request);
  }
}
