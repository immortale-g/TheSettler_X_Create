package com.thesettler_x_create.stock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The stock bookkeeping stays free of Minecraft, NeoForge, Create and MineColonies types, so it can
 * be tested with plain JUnit. Adapters in {@code blockentity/} and sub-packages such as {@code
 * stock.nbt} connect it to the game.
 */
class StockPackageIsMinecraftFreeGuardTest {
  private static final Path STOCK_SOURCES = Path.of("src/main/java/com/thesettler_x_create/stock");
  private static final List<String> FORBIDDEN_IMPORTS =
      List.of(
          "import net.minecraft.",
          "import net.neoforged.",
          "import com.simibubi.",
          "import com.minecolonies.",
          "import com.ldtteam.");

  @Test
  void stockPackageHasNoGameImports() throws Exception {
    List<Path> sources;
    try (Stream<Path> files = Files.list(STOCK_SOURCES)) {
      sources = files.filter(path -> path.toString().endsWith(".java")).toList();
    }
    assertTrue(!sources.isEmpty(), "stock package sources not found");
    for (Path source : sources) {
      String content = Files.readString(source);
      for (String forbidden : FORBIDDEN_IMPORTS) {
        assertTrue(
            !content.contains(forbidden), source.getFileName() + " imports " + forbidden + "...");
      }
    }
  }
}
