package com.thesettler_x_create.create.compat;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers Structurize {@code IPlacementHandler}s that teach the colony builder how to actually
 * build Create blocks it otherwise can't: composite kinetic casings (which have no item of their
 * own) and belts (which are a multi-block structure, not a single independently-placeable block).
 */
public final class CreatePlacementHandlers {
  private CreatePlacementHandlers() {}

  public static void register() {
    Map<ResourceLocation, ResourceLocation> compositeBlockRequiredItems =
        Map.of(
            create("andesite_encased_shaft"), create("shaft"),
            create("brass_encased_shaft"), create("shaft"),
            create("andesite_encased_cogwheel"), create("cogwheel"),
            create("brass_encased_cogwheel"), create("cogwheel"),
            create("andesite_encased_large_cogwheel"), create("large_cogwheel"),
            create("brass_encased_large_cogwheel"), create("large_cogwheel"));

    PlacementHandlers.add(new CompositeBlockItemRemapHandler(compositeBlockRequiredItems));
    PlacementHandlers.add(new CreateBeltPlacementHandler());

    if (Config.DEBUG_LOGGING.getAsBoolean()) {
      TheSettlerXCreate.LOGGER.info(
          "[CreateCompat] registered Structurize placement handlers for {} composite Create blocks + belts",
          compositeBlockRequiredItems.size());
    }
  }

  private static ResourceLocation create(String path) {
    return ResourceLocation.fromNamespaceAndPath("create", path);
  }
}
