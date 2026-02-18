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
    DeliveryRequestResolver existingDeliveryResolver = null;
    PickupRequestResolver existingPickupResolver = null;

    for (IRequestResolver<?> resolver : baseResolvers) {
      if (resolver
          instanceof
          com.minecolonies.core.colony.requestsystem.resolvers.core
              .AbstractWarehouseRequestResolver) {
        // CreateShop is not a BuildingWareHouse; avoid MineColonies' warehouse resolver cast crash.
        continue;
      }
      if (resolver instanceof CreateShopRequestResolver csr) {
        existingShopResolver = csr;
      } else if (resolver instanceof DeliveryRequestResolver dr) {
        existingDeliveryResolver = dr;
      } else if (resolver instanceof PickupRequestResolver pr) {
        existingPickupResolver = pr;
      }
      builder.add(resolver);
    }

    ILocation location = shop.getRequester().getLocation();
    IFactoryController factory = shop.getColony().getRequestManager().getFactoryController();

    CreateShopRequestResolver shopResolver =
        existingShopResolver != null ? existingShopResolver : shop.getExistingShopResolver();
    IToken<?> deliveryResolverToken =
        existingDeliveryResolver != null
            ? existingDeliveryResolver.getId()
            : shop.getDeliveryResolverToken();
    IToken<?> pickupResolverToken =
        existingPickupResolver != null
            ? existingPickupResolver.getId()
            : shop.getPickupResolverToken();

    if (shopResolver == null) {
      IToken<?> token = factory.getNewInstance(TypeConstants.ITOKEN);
      shopResolver = new CreateShopRequestResolver(location, token);
      builder.add(shopResolver);
    }

    if (existingDeliveryResolver == null && deliveryResolverToken == null) {
      deliveryResolverToken = factory.getNewInstance(TypeConstants.ITOKEN);
    }
    if (existingPickupResolver == null && pickupResolverToken == null) {
      pickupResolverToken = factory.getNewInstance(TypeConstants.ITOKEN);
    }
    if (existingDeliveryResolver == null) {
      builder.add(new DeliveryRequestResolver(location, deliveryResolverToken));
    }
    if (existingPickupResolver == null) {
      builder.add(new PickupRequestResolver(location, pickupResolverToken));
    }

    shop.setResolverState(shopResolver, deliveryResolverToken, pickupResolverToken);

    if (BuildingCreateShop.isDebugRequests()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] createResolvers at {} -> {}",
          shop.getLocation().getInDimensionLocation(),
          builder.build().size());
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] delivery resolver token={} pickup resolver token={}",
          deliveryResolverToken,
          pickupResolverToken);
    }

    return builder.build();
  }
}
