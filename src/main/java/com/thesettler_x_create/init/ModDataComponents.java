package com.thesettler_x_create.init;

import com.mojang.serialization.Codec;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components this mod attaches to item stacks. */
public final class ModDataComponents {
  private ModDataComponents() {}

  public static final DeferredRegister.DataComponents COMPONENTS =
      DeferredRegister.createDataComponents(
          Registries.DATA_COMPONENT_TYPE, TheSettlerXCreate.MODID);

  /**
   * How much of a gauge order is still owed after this package, written on every package the shop
   * ships to a gauge.
   *
   * <p>A gauge order is filled in several packages, and the gauge has to know when to stop waiting
   * for the rest. It cannot ask: the colony may close an order short of what was asked (a warehouse
   * drained in between hands over what is left, which is what the minimum of 1 is for), and a gauge
   * that kept its promise for goods nobody still owes it would sit satisfied forever without ever
   * reaching its target.
   *
   * <p>Telling it on arrival instead of asking is what keeps the two from ordering twice: the
   * promise only drops once the package that closes the order is actually unpacked, so goods still
   * travelling are never mistaken for goods that will never come. A package without this component
   * is one from an older world or from somewhere else, and is read as "nothing said".
   */
  public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
      GAUGE_ORDER_OPEN =
          COMPONENTS.registerComponentType(
              "gauge_order_open",
              builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

  public static void register(IEventBus bus) {
    COMPONENTS.register(bus);
  }
}
