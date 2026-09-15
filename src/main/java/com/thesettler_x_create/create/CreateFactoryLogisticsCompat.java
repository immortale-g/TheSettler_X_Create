package com.thesettler_x_create.create;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.thesettler_x_create.TheSettlerXCreate;
import java.lang.reflect.Method;
import java.util.UUID;
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
    try {
      Class<?> orderClass = Class.forName(ORDER_CLASS);
      Class<?> managerClass = Class.forName(MANAGER_CLASS);
      Class<?> inventoryClass = Class.forName(INVENTORY_CLASS);
      orderOf = orderClass.getMethod("of", PackageOrderWithCrafts.class);
      broadcast =
          managerClass.getMethod(
              "broadcastPackageRequest",
              UUID.class,
              LogisticallyLinkedBehaviour.RequestType.class,
              orderClass,
              inventoryClass,
              String.class);
      TheSettlerXCreate.LOGGER.info(
          "[CreateShop] Create Factory Logistics detected, routing package requests through its generic logistics layer");
    } catch (ClassNotFoundException notInstalled) {
      orderOf = null;
      broadcast = null;
    } catch (Exception | LinkageError ex) {
      orderOf = null;
      broadcast = null;
      TheSettlerXCreate.LOGGER.warn(
          "[CreateShop] Create Factory Logistics is installed but its logistics API did not resolve ({}), falling back to the stock Create path. Package requests may be dropped silently.",
          ex.toString());
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
