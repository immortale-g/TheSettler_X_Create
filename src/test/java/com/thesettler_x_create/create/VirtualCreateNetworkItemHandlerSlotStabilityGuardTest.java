package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s2-6: {@code cachedStacks} is a rack-scan snapshot rebuilt every {@code
 * CACHE_TTL_TICKS}, and its slot indices were previously in raw rack-scan order - a caller that
 * peeks {@code getStackInSlot(N)} and later extracts from the same index {@code N} (rather than in
 * the same synchronous step) could silently extract a different item if a refresh reordered the
 * list in between. This is an inherent limitation of any slot-index-based virtual IItemHandler
 * (documented, not something a NeoForge-mandated interface lets us design away), but the practical
 * window is minimized by keeping the list in deterministic (registry-name) order instead of raw
 * scan order, so a slot keeps meaning the same item across refreshes as long as the same set of
 * distinct items remains present.
 */
class VirtualCreateNetworkItemHandlerSlotStabilityGuardTest {
  private static final Path SOURCE =
      Path.of("src/main/java/com/thesettler_x_create/create/VirtualCreateNetworkItemHandler.java");

  @Test
  void classDocumentsTheSlotStabilityCaveat() throws Exception {
    String source = Files.readString(SOURCE);
    int classDoc = source.indexOf("public class VirtualCreateNetworkItemHandler");
    assertTrue(classDoc > 0);
    String preamble = source.substring(0, classDoc);
    assertTrue(preamble.contains("not a stable handle to a"));
    assertTrue(preamble.contains("CACHE_TTL_TICKS"));
  }

  @Test
  void cacheRefreshSortsDeterministicallyByRegistryName() throws Exception {
    String source = Files.readString(SOURCE);
    int method = source.indexOf("private void refreshCacheIfNeeded(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 900));
    assertTrue(body.contains("cachedStacks.sort("));
    assertTrue(body.contains("BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()"));
  }
}
