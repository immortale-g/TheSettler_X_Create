package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShopLostPackageInteractionCancelOptionGuardTest {
  @Test
  void interactionExposesCancelOptionAndRoutesResponse() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/building/ShopLostPackageInteraction.java"));

    assertTrue(source.contains("lost_package.response_cancel"));
    assertTrue(source.contains("lost_package.answer_cancel"));
    assertTrue(source.contains("response == 2"));
    assertTrue(source.contains("cancelLostPackageRequestAndInflight("));
  }
}
