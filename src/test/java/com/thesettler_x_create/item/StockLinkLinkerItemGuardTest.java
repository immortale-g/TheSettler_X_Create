package com.thesettler_x_create.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StockLinkLinkerItemGuardTest {

  @Test
  void retuneRemovesFromOldNetworkBeforeReassigningFrequency() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/item/StockLinkLinkerItem.java"));

    // Retuning a Stock-Link must unregister the behaviour from Create's global logistics cache
    // under the OLD frequency before the frequency field changes, and only re-register (keepAlive)
    // once it already carries the NEW frequency. Reordering this either leaks the behaviour under
    // the old frequency forever, or removes it from the wrong (new) bucket.
    int removeIndex = source.indexOf("LogisticallyLinkedBehaviour.remove(behaviour);");
    int reassignIndex = source.indexOf("behaviour.freqId = newFreq;");
    int keepAliveIndex = source.indexOf("LogisticallyLinkedBehaviour.keepAlive(behaviour);");

    assertTrue(removeIndex >= 0, "remove(...) call not found");
    assertTrue(reassignIndex >= 0, "freqId reassignment not found");
    assertTrue(keepAliveIndex >= 0, "keepAlive(...) call not found");
    assertTrue(removeIndex < reassignIndex, "must remove from the old network before reassigning");
    assertTrue(reassignIndex < keepAliveIndex, "must reassign before re-registering the behaviour");
  }

  @Test
  void skipsRetuneWhenAlreadyLinkedToTargetNetwork() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/java/com/thesettler_x_create/item/StockLinkLinkerItem.java"));

    assertTrue(source.contains("if (newFreq.equals(oldFreq)) {"));
  }
}
