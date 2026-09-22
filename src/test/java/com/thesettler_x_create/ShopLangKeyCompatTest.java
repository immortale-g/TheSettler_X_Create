package com.thesettler_x_create;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every translation key the mod uses has to be defined somewhere.
 *
 * <p>A key nobody translated is not an error: Minecraft draws the raw key and carries on, so a
 * typo, a renamed key, or a line forgotten in one of the two language files reaches a player
 * unnoticed. The same holds for the handful of keys borrowed from MineColonies and vanilla, which
 * an upstream release may rename without any of our code changing.
 *
 * <p>The keys are collected from the sources and the layouts, so a new one is covered the moment it
 * is written.
 */
class ShopLangKeyCompatTest {
  private static final String OWN_PREFIX = "com.thesettler_x_create.";
  private static final Path JAVA_ROOT = Path.of("src/main/java");
  private static final Path LANG_ROOT =
      Path.of("src/main/resources/assets/thesettler_x_create/lang");

  /**
   * A key in Java source: our prefix followed by lowercase segments. Class names ({@code
   * com.thesettler_x_create.minecolonies.building.BuildingCreateShop}) are left out by the case, so
   * a reflective or registry name is never mistaken for a translation.
   */
  private static final Pattern KEY_IN_SOURCE =
      Pattern.compile("\"(" + Pattern.quote(OWN_PREFIX) + "[a-z0-9_]+(?:\\.[a-z0-9_]+)+)\"");

  /** A key in a layout: BlockUI resolves {@code $(some.key)} as a translation. */
  private static final Pattern KEY_IN_LAYOUT = Pattern.compile("\\$\\(([^)]+)\\)");

  @Test
  void everyKeyTheCodeUsesIsInBothLanguageFiles() throws Exception {
    Map<String, Path> keys = keysInSources();

    assertTrue(
        keys.size() > 50,
        "only "
            + keys.size()
            + " translation keys found in "
            + JAVA_ROOT
            + "; has the mod's key"
            + " prefix changed and this test stopped looking at anything?");

    assertKeysAreTranslated(keys);
  }

  @Test
  void everyKeyTheLayoutsUseIsInBothLanguageFiles() throws Exception {
    Map<String, Path> keys = ownKeys(keysInLayouts());

    assertTrue(
        keys.size() > 10,
        "only "
            + keys.size()
            + " of our own translation keys found in the layouts; has the $(...)"
            + " form changed?");

    assertKeysAreTranslated(keys);
  }

  @Test
  void everyKeyTheLayoutsBorrowStillExistsUpstream() throws Exception {
    Map<String, Path> borrowed = new LinkedHashMap<>(keysInLayouts());
    borrowed.keySet().removeIf(key -> key.startsWith(OWN_PREFIX));

    assertTrue(
        borrowed.size() > 1,
        "the layouts no longer borrow a single label from MineColonies or vanilla, which would be"
            + " news - the task list and the reset button are theirs");

    JsonObject minecolonies = langFromClasspath("assets/minecolonies/lang/en_us.json");
    JsonObject vanilla = langFromClasspath("assets/minecraft/lang/en_us.json");

    borrowed.forEach(
        (key, layout) ->
            assertTrue(
                minecolonies.has(key) || vanilla.has(key),
                layout
                    + " uses the label "
                    + key
                    + ", which neither MineColonies nor Minecraft defines any more. The window"
                    + " shows the raw key instead of a word."));
  }

  private static void assertKeysAreTranslated(Map<String, Path> keys) throws IOException {
    JsonObject english = langFile("en_us.json");
    JsonObject german = langFile("de_de.json");

    keys.forEach(
        (key, source) -> {
          assertTrue(
              english.has(key), source + " uses " + key + ", which en_us.json does not have");
          assertTrue(german.has(key), source + " uses " + key + ", which de_de.json does not have");
        });
  }

  private static Map<String, Path> keysInSources() throws IOException {
    Map<String, Path> keys = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        collect(keys, file, KEY_IN_SOURCE);
      }
    }
    return keys;
  }

  private static Map<String, Path> keysInLayouts() throws IOException {
    Map<String, Path> keys = new LinkedHashMap<>();
    for (Path layout : ShopGuiLayouts.all()) {
      collect(keys, layout, KEY_IN_LAYOUT);
    }
    return keys;
  }

  private static Map<String, Path> ownKeys(Map<String, Path> keys) {
    Map<String, Path> own = new LinkedHashMap<>(keys);
    own.keySet().removeIf(key -> !key.startsWith(OWN_PREFIX));
    return own;
  }

  private static void collect(Map<String, Path> keys, Path file, Pattern pattern)
      throws IOException {
    Matcher matcher = pattern.matcher(Files.readString(file));
    while (matcher.find()) {
      keys.putIfAbsent(matcher.group(1), file);
    }
  }

  private static JsonObject langFile(String name) throws IOException {
    return JsonParser.parseString(Files.readString(LANG_ROOT.resolve(name))).getAsJsonObject();
  }

  private static JsonObject langFromClasspath(String resource) throws IOException {
    try (InputStream in =
        ShopLangKeyCompatTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull(in, resource + " is not on the test classpath");
      return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }
}
