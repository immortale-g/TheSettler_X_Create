package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.thesettler_x_create.TheSettlerXCreate;
import java.lang.reflect.Method;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective bridge to Create Factory Logistics (CFL).
 *
 * <p>CFL replaces Create's package pipeline with a generic one that also carries fluids. It does
 * not mixin {@code LogisticsManager} itself, so {@code LogisticsManager.broadcastPackageRequest}
 * stays callable and never throws - it just stops producing packages, and Create's own
 * implementation returns {@code true} even when it found no packager at all. That combination is
 * invisible from the outside, which is why shop orders kept repeating forever on modpacks that ship
 * CFL.
 *
 * <p>The generic entry points live in {@code create_factory_abstractions}, a mandatory CFL
 * dependency that is JarJar'd inside the CFL jar and published nowhere else. It therefore cannot be
 * a compile dependency and has to be reached reflectively. Signatures below were read out of {@code
 * create_factory_logistics-1.21.1-1.6.0-all.jar}:
 *
 * <pre>
 *   GenericOrder.of(PackageOrderWithCrafts) -&gt; GenericOrder
 *   GenericLogisticsManager.broadcastPackageRequest(
 *       UUID, RequestType, GenericOrder, IdentifiedInventory, String) -&gt; boolean
 * </pre>
 *
 * <p>Unlike Create's version, CFL's {@code broadcastPackageRequest} reports an empty packager set
 * as {@code false}, so a plain boolean is enough here and we deliberately keep the reflective
 * surface down to two handles. If anything fails to resolve, {@link #isAvailable()} stays {@code
 * false} and callers fall back to the stock Create path.
 */
public final class CreateFactoryLogisticsCompat {
  private static final String MOD_ID = "create_factory_logistics";
  private static final String ORDER_CLASS =
      "ru.zznty.create_factory_abstractions.generic.support.GenericOrder";
  private static final String MANAGER_CLASS =
      "ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager";
  private static final String INVENTORY_CLASS =
      "com.simibubi.create.content.logistics.packager.IdentifiedInventory";

  private static boolean resolved;
  private static @Nullable Method orderOf;
  private static @Nullable Method broadcast;

  private CreateFactoryLogisticsCompat() {}

  /** True when CFL's generic logistics layer is present and usable. */
  public static synchronized boolean isAvailable() {
    resolve();
    return orderOf != null && broadcast != null;
  }

  private static void resolve() {
    if (resolved) {
      return;
    }
    resolved = true;
    orderOf = null;
    broadcast = null;
    try {
      // Loaded without initializing: resolving signatures must not run CFL's static setup, and the
      // compat test resolves these outside a running game.
      ClassLoader loader = CreateFactoryLogisticsCompat.class.getClassLoader();
      Class<?> orderClass = Class.forName(ORDER_CLASS, false, loader);
      Class<?> managerClass = Class.forName(MANAGER_CLASS, false, loader);
      Class<?> inventoryClass = Class.forName(INVENTORY_CLASS, false, loader);
      Method resolvedOrderOf = orderClass.getMethod("of", PackageOrderWithCrafts.class);
      Method resolvedBroadcast =
          managerClass.getMethod(
              "broadcastPackageRequest",
              UUID.class,
              LogisticallyLinkedBehaviour.RequestType.class,
              orderClass,
              inventoryClass,
              String.class);
      // A changed return type would not fail the lookup, it would just make every broadcast read
      // as refused. Treat it as a broken API instead.
      if (resolvedOrderOf.getReturnType() != orderClass
          || resolvedBroadcast.getReturnType() != boolean.class) {
        throw new NoSuchMethodException(
            "unexpected return types: "
                + resolvedOrderOf.getReturnType().getName()
                + ", "
                + resolvedBroadcast.getReturnType().getName());
      }
      orderOf = resolvedOrderOf;
      broadcast = resolvedBroadcast;
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Create Factory Logistics {} detected, routing package requests through its generic logistics layer",
          installedVersion());
    } catch (ClassNotFoundException missing) {
      // Absent classes mean one of two very different things. Without CFL this is the normal case.
      // With CFL loaded it means the API moved, and the shop would silently stop reaching its
      // packagers - exactly the endless-order bug this class exists to prevent.
      if (isInstalled()) {
        warnApiBroken(missing);
      }
    } catch (Exception | LinkageError ex) {
      warnApiBroken(ex);
    }
  }

  private static void warnApiBroken(Throwable cause) {
    TheSettlerXCreate.LOGGER.error(
        "[CreateShop] Create Factory Logistics {} is installed, but its logistics API was not found"
            + " ({}). The Create Shop falls back to the stock Create path, which does not produce"
            + " packages while CFL is installed: orders will not arrive. This build of"
            + " TheSettler_x_Create needs an update for this CFL version.",
        installedVersion(),
        cause.toString());
  }

  /** Whether the CFL mod itself is loaded, independent of whether its API resolved. */
  static boolean isInstalled() {
    try {
      ModList mods = ModList.get();
      return mods != null && mods.isLoaded(MOD_ID);
    } catch (RuntimeException | LinkageError noModLoader) {
      // Unit tests run without FML.
      return false;
    }
  }

  private static String installedVersion() {
    try {
      ModList mods = ModList.get();
      if (mods == null) {
        return "<unknown version>";
      }
      return mods.getModContainerById(MOD_ID)
          .map(container -> container.getModInfo().getVersion().toString())
          .orElse("<unknown version>");
    } catch (RuntimeException | LinkageError noModLoader) {
      return "<unknown version>";
    }
  }

  /**
   * Broadcasts an order through CFL's generic logistics manager.
   *
   * @return true when CFL packaged the order, false when it refused it (no reachable packager, or a
   *     packager that is too busy)
   * @throws IllegalStateException if called while {@link #isAvailable()} is false
   */
  public static boolean broadcastPackageRequest(
      UUID networkId,
      LogisticallyLinkedBehaviour.RequestType requestType,
      PackageOrderWithCrafts order,
      @Nullable String address)
      throws ReflectiveOperationException {
    if (!isAvailable()) {
      throw new IllegalStateException("Create Factory Logistics compat is not available");
    }
    Object genericOrder = orderOf.invoke(null, order);
    Object result =
        broadcast.invoke(
            null, networkId, requestType, genericOrder, null, address == null ? "" : address);
    return Boolean.TRUE.equals(result);
  }

  /** Test seam: forces the next {@link #isAvailable()} call to resolve again. */
  static synchronized void resetForTesting() {
    resolved = false;
    orderOf = null;
    broadcast = null;
  }
}
