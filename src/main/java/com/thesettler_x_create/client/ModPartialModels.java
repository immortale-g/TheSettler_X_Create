package com.thesettler_x_create.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class ModPartialModels {

  public static final PartialModel COLONY_GAUGE_PANEL =
      PartialModel.of(ResourceLocation.fromNamespaceAndPath("thesettler_x_create", "block/colony_gauge/panel"));

  public static final PartialModel COLONY_GAUGE_PANEL_WITH_BULB =
      PartialModel.of(ResourceLocation.fromNamespaceAndPath("thesettler_x_create", "block/colony_gauge/panel_with_bulb"));

  public static final PartialModel COLONY_GAUGE_BULB_LIGHT =
      PartialModel.of(ResourceLocation.fromNamespaceAndPath("thesettler_x_create", "block/colony_gauge/bulb_light"));

  /** Touch to trigger static initialization and register all models with Flywheel. */
  public static void init() {}
}
