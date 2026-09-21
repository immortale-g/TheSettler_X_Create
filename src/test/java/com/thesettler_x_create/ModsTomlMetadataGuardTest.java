package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * What the mod list says about this mod.
 *
 * <p>0.6.0, the first public release in six versions, shipped with the NeoForge template's "Example
 * mod description." and no authors or links, because those lines sit commented out in the MDK and
 * nothing ever fails when they stay that way.
 */
class ModsTomlMetadataGuardTest {
  private static final Path TOML = Path.of("src/main/resources/META-INF/neoforge.mods.toml");
  private static final Path PROPERTIES = Path.of("gradle.properties");

  @Test
  void theModListDoesNotShowTemplateText() throws Exception {
    String toml = Files.readString(TOML);

    assertFalse(toml.contains("Example mod description."), "template description still shipped");
    assertFalse(toml.contains("change.me"), "template placeholder URL still shipped");
  }

  @Test
  void descriptionAuthorsAndLinksAreFilledIn() throws Exception {
    String toml = Files.readString(TOML);

    assertTrue(toml.contains("\nauthors=\""), "authors is commented out or missing");
    assertTrue(toml.contains("\ndisplayURL=\""), "displayURL is commented out or missing");
    assertTrue(
        toml.contains("\nissueTrackerURL=\""), "issueTrackerURL is commented out or missing");
    assertTrue(toml.contains("A bridge between MineColonies and Create"), "description is missing");
  }

  @Test
  void everyPlaceholderTheTomlUsesIsExpandedByTheBuild() throws Exception {
    String toml = Files.readString(TOML);
    String properties = Files.readString(PROPERTIES);
    String build = Files.readString(Path.of("build.gradle"));

    // An unexpanded ${...} reaches the jar verbatim and shows up in the mod list as itself.
    for (String key : new String[] {"mod_authors", "mod_homepage_url", "mod_issues_url"}) {
      if (!toml.contains("${" + key + "}")) {
        continue;
      }
      assertTrue(properties.contains("\n" + key + "="), key + " is not set in gradle.properties");
      assertTrue(build.contains(key + " "), key + " is not passed to processResources");
    }
  }
}
