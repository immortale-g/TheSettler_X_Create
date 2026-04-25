package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopResolverFactoryDeliveryResolverGuardTest {
  @Test
  void createShopDoesNotRegisterLocalDeliverymenResolvers() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopResolverFactory.java"));

    assertTrue(source.contains("resolver instanceof DeliveryRequestResolver"));
    assertTrue(source.contains("resolver instanceof PickupRequestResolver"));
    assertTrue(source.contains("deliverymen resolvers skipped for CreateShop"));
    assertFalse(source.contains("builder.add(new DeliveryRequestResolver"));
    assertFalse(source.contains("builder.add(new PickupRequestResolver"));
  }
}
