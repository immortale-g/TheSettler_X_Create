package com.thesettler_x_create.create;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Resolves the reflective Create Factory Logistics bridge against a real CFL release.
 *
 * <p>Only runs from the testFactoryLogistics Gradle task, which puts create_factory_abstractions
 * from cfl_version on the classpath. The regular test run has no CFL and skips it. Because the
 * bridge uses reflection, this is the only place where a renamed class, a changed parameter list or
 * a changed return type in CFL fails a build instead of silently breaking shop orders in game.
 */
@EnabledIfSystemProperty(named = "thesettler.factoryLogisticsVersion", matches = ".+")
class CreateFactoryLogisticsLiveCompatTest {

  @Test
  void bridgeResolvesAgainstTheRealFactoryLogisticsApi() {
    String version = System.getProperty("thesettler.factoryLogisticsVersion");
    CreateFactoryLogisticsCompat.resetForTesting();
    assertTrue(
        CreateFactoryLogisticsCompat.isAvailable(),
        "CreateFactoryLogisticsCompat no longer resolves against Create Factory Logistics "
            + version
            + ". Check GenericOrder.of(PackageOrderWithCrafts) and"
            + " GenericLogisticsManager.broadcastPackageRequest in create_factory_abstractions,"
            + " and the resolve() error in the test log.");
  }
}
