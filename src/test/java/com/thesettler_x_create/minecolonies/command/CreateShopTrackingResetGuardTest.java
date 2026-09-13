package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thesettler_x_create.minecolonies.building.ShopTrackingScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateShopTrackingResetGuardTest {
  private static final Path COMMANDS =
      Path.of(
          "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopMaintenanceCommands.java");
  private static final Path RESET =
      Path.of("src/main/java/com/thesettler_x_create/minecolonies/building/ShopTrackingReset.java");

  @Test
  void resetCommandsAreOperatorCommandsWithoutTheDevTestGate() throws Exception {
    String source = Files.readString(COMMANDS);

    assertTrue(
        source.contains(
            "Commands.literal(\"thesettlerxcreate\").requires(source -> source.hasPermission(2))"));
    assertTrue(source.contains("root.then(CreateShopTrackingResetCommands.resetColony());"));
    assertTrue(source.contains("root.then(CreateShopTrackingResetCommands.resetAllColonies());"));
  }

  @Test
  void everyScopeIsHandledAndRequestsAreNeverCancelled() throws Exception {
    String source = Files.readString(RESET);

    for (ShopTrackingScope scope : ShopTrackingScope.values()) {
      assertTrue(
          source.contains("scopes.contains(ShopTrackingScope." + scope.name() + ")"),
          "scope not reset: " + scope);
    }
    assertFalse(source.contains("updateRequestState"));
    assertFalse(source.contains("RequestState.CANCELLED"));
  }

  @Test
  void suggestionsOfferAllAndEveryScope() {
    List<String> suggestions = CreateShopTrackingResetCommands.scopeSuggestions();

    assertEquals(ShopTrackingScope.values().length + 1, suggestions.size());
    assertEquals(ShopTrackingScope.ALL, suggestions.get(0));
  }
}
