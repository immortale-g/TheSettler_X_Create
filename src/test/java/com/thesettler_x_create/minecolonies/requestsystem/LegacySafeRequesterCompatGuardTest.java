package com.thesettler_x_create.minecolonies.requestsystem;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacySafeRequesterCompatGuardTest {
  @Test
  void safeRequesterFactoryKeepsLegacySerializationId3001() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/requestsystem/requesters/SafeRequesterFactory.java"));
    assertTrue(source.contains("SERIALIZATION_ID = 3001"));
  }

  @Test
  void commonSetupRegistersLegacySafeRequesterFactory() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/TheSettlerXCreate.java"));
    assertTrue(source.contains("registerNewFactory(new SafeRequesterFactory())"));
  }
}
