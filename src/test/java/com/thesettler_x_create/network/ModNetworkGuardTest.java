package com.thesettler_x_create.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-text guard for the server-side packet handlers in {@code ModNetwork}. A real
 * colony/permissions bootstrap isn't available in unit tests (this project deliberately never
 * bootstraps registries in tests, see {@link ItemStackNetworkPayloadGuardTest}), so this locks in
 * that every handler routes its client-supplied {@code BlockPos} through {@code isAuthorized}
 * before touching any block entity - without it, a crafted packet naming an arbitrary position
 * could reconfigure or drain a building the sender has no relation to.
 */
class ModNetworkGuardTest {

  @Test
  void isAuthorizedChecksColonyMembershipAndManageHutsPermission() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));

    int method = source.indexOf("private static boolean isAuthorized(");
    assertTrue(method > 0);
    String body = source.substring(method);
    assertTrue(body.contains("getColonyByPosFromWorld"));
    assertTrue(body.contains("hasPermission(player, Action.MANAGE_HUTS)"));
  }

  @Test
  void getShopRoutesThroughAuthorizationCheck() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));

    int method = source.indexOf("private static TileEntityCreateShop getShop(ServerPlayer");
    int nextMethod = source.indexOf("private static boolean isAuthorized(");
    assertTrue(method > 0 && nextMethod > method);
    String body = source.substring(method, nextMethod);
    assertTrue(body.contains("if (!isAuthorized(player, pos)) {"));
  }

  @Test
  void getShopBuildingRoutesThroughGetShop() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));

    int method = source.indexOf("private static BuildingCreateShop getShopBuilding(");
    int nextMethod = source.indexOf("private static TileEntityCreateShop getShop(ServerPlayer");
    assertTrue(method > 0 && nextMethod > method);
    String body = source.substring(method, nextMethod);
    // Handlers are allowed to resolve through getShopBuilding(...) instead of getShop(...) only
    // because it delegates - so the authorization check still happens exactly once, in getShop.
    assertTrue(body.contains("getShop(context, pos)"));
  }

  @Test
  void everyHandlerResolvesShopThroughGetShopOrChecksAuthorizationDirectly() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/thesettler_x_create/network/ModNetwork.java"));

    Pattern handlerPattern = Pattern.compile("private static void (handle\\w+)\\(");
    Matcher matcher = handlerPattern.matcher(source);
    int handlerCount = 0;
    while (matcher.find()) {
      handlerCount++;
      int bodyStart = matcher.end();
      int bodyEnd = findMatchingBraceEnd(source, source.indexOf('{', bodyStart));
      String body = source.substring(bodyStart, bodyEnd);
      // getShopBuilding(...) is a thin delegate over getShop(...) - see the test above, which
      // pins that delegation so this alternative can't become an authorization bypass.
      boolean routesThroughGetShop = body.contains("getShop(") || body.contains("getShopBuilding(");
      boolean checksDirectly = body.contains("isAuthorized(");
      assertTrue(
          routesThroughGetShop || checksDirectly,
          matcher.group(1)
              + " must resolve its target through getShop(...), getShopBuilding(...) or"
              + " isAuthorized(...)");
    }
    // Sanity check: fail loudly instead of silently passing if the handler count ever drops to
    // zero (e.g. a refactor renames the handleXxx convention this test relies on).
    assertEquals(8, handlerCount);
  }

  private static int findMatchingBraceEnd(String source, int openBraceIndex) {
    int depth = 0;
    for (int i = openBraceIndex; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    throw new IllegalStateException("Unbalanced braces starting at " + openBraceIndex);
  }
}
