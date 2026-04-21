package com.thesettler_x_create.minecolonies.building;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.PickupRequestResolver;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;

/** Builds resolver list for the Create Shop. */
final class ShopResolverFactory {
  private final BuildingCreateShop shop;

  ShopResolverFactory(BuildingCreateShop shop) {
    this.shop = shop;
  }

  ImmutableCollection<IRequestResolver<?>> createResolvers(
      ImmutableCollection<IRequestResolver<?>> baseResolvers) {
    ImmutableList.Builder<IRequestResolver<?>> builder = ImmutableList.builder();
    CreateShopRequestResolver existingShopResolver = null;

    for (IRequestResolver<?> resolver : baseResolvers) {
      if (resolver
          instanceof
          com.minecolonies.core.colony.requestsystem.resolvers.core
              .AbstractWarehouseRequestResolver) {
        // CreateShop is not a BuildingWareHouse; avoid MineColonies' warehouse resolver cast crash.
        continue;
      }
      if (resolver instanceof DeliveryRequestResolver) {
        // Deliverymen resolvers belong to real warehouses with courier modules.
        continue;
      }
      if (resolver instanceof CreateShopRequestResolver csr) {
        existingShopResolver = csr;
      } else if (resolver instanceof PickupRequestResolver) {
        // PickupRequestResolver also depends on warehouse couriers, so the Create Shop must not
        // keep a local one. Real MineColonies warehouses resolve shop pickup requests natively.
        continue;
      }
      builder.add(resolver);
    }

    ILocation location = shop.getRequester().getLocation();
    IFactoryController factory = shop.getColony().getRequestManager().getFactoryController();

    CreateShopRequestResolver shopResolver = existingShopResolver;

    if (shopResolver == null) {
      IToken<?> token = factory.getNewInstance(TypeConstants.ITOKEN);
      shopResolver = new CreateShopRequestResolver(location, token);
    }
    if (existingShopResolver == null) {
      builder.add(shopResolver);
    }

    shop.setResolverState(shopResolver, null, null);

    if (BuildingCreateShop.isDebugRequests()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] createResolvers at {} -> {}",
          shop.getLocation().getInDimensionLocation(),
          builder.build().size());
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] deliverymen resolvers skipped for CreateShop; real warehouses own courier tasks");
    }

    return builder.build();
  }
}
