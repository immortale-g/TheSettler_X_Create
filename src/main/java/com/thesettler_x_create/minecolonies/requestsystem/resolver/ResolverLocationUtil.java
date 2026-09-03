package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import org.jetbrains.annotations.Nullable;

/** Shared location-equality check used to decide whether a resolver belongs to this shop. */
public final class ResolverLocationUtil {
  private ResolverLocationUtil() {}

  public static boolean sameLocation(@Nullable ILocation a, @Nullable ILocation b) {
    if (a == null || b == null) {
      return false;
    }
    return a.getDimension().equals(b.getDimension())
        && a.getInDimensionLocation().equals(b.getInDimensionLocation());
  }
}
