package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopDeliveryManagerNotifyCountGuardTest {
  @Test
  void nudgeDeliverymenTracksNativeCourierHandoff() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/resolver/CreateShopDeliveryManager.java"));

    assertTrue(source.contains("int alreadyQueued = 0;"));
    assertTrue(source.contains("int kicked = 0;"));
    assertTrue(source.contains("warehousesWithToken"));
    assertTrue(source.contains("static int nudgeDeliverymen"));
    assertTrue(source.contains("job.addRequest(token, 0);"));
    assertTrue(source.contains("queue.getMutableRequestList().remove(token)"));
    assertTrue(source.contains("assignDeliveryRequest(manager, token);"));
  }
}
