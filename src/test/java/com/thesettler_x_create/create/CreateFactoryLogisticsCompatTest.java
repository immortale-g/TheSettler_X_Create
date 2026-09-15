package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateFactoryLogisticsCompatTest {

  @Test
  void reportsUnavailableWhenFactoryLogisticsIsAbsent() {
    // CFL is not on the test classpath, so resolution has to fail softly rather than throw.
    CreateFactoryLogisticsCompat.resetForTesting();
    assertFalse(CreateFactoryLogisticsCompat.isAvailable());
    assertThrows(
        IllegalStateException.class,
        () ->
            CreateFactoryLogisticsCompat.broadcastPackageRequest(
                java.util.UUID.randomUUID(), null, null, "addr"));
  }

  @Test
  void targetsTheVerifiedFactoryAbstractionsSignatures() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/create/CreateFactoryLogisticsCompat.java"));
    // Read out of create_factory_logistics-1.21.1-1.6.0-all.jar; the library is JarJar'd inside CFL
    // and cannot be a compile dependency, so these strings are the whole contract.
    assertTrue(
        source.contains("ru.zznty.create_factory_abstractions.generic.support.GenericOrder"));
    assertTrue(
        source.contains(
            "ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager"));
    assertTrue(source.contains("getMethod(\"of\", PackageOrderWithCrafts.class)"));
    assertTrue(source.contains("\"broadcastPackageRequest\""));
  }
}
