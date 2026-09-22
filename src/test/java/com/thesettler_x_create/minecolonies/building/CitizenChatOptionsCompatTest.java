package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.core.colony.CitizenData;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Pins the one private MineColonies detail the shop still reaches for: {@code
 * CitizenData.citizenChatOptions}.
 *
 * <p>{@link ShopLostPackageInteraction} removes stale lost-package dialogs from a shopkeeper
 * through that map, because MineColonies offers no API to remove a single queued interaction - only
 * to add one, and a player-close callback. If a release renames the field or changes its shape, the
 * mod still compiles and the shopkeeper simply accumulates dialogs nobody asked for. This test asks
 * the compiled class instead, so the nightly run against the newest MineColonies says so.
 */
class CitizenChatOptionsCompatTest {
  @Test
  void citizenDataStillKeepsItsChatOptionsInAMapUnderThatName() throws Exception {
    Field field = CitizenData.class.getDeclaredField("citizenChatOptions");

    assertTrue(
        Map.class.isAssignableFrom(field.getType()),
        "CitizenData.citizenChatOptions is no longer a Map but a "
            + field.getType().getName()
            + ". ShopLostPackageInteraction removes stale lost-package dialogs through it.");
    assertTrue(
        !Modifier.isStatic(field.getModifiers()),
        "CitizenData.citizenChatOptions became static; it is read per citizen.");
  }

  @Test
  void theChatOptionsMapStillGoesFromComponentToInteraction() throws Exception {
    Field field = CitizenData.class.getDeclaredField("citizenChatOptions");
    ParameterizedType type = assertInstanceOf(ParameterizedType.class, field.getGenericType());

    assertEquals(
        Component.class,
        type.getActualTypeArguments()[0],
        "the chat options are no longer keyed by Component. ShopLostPackageInteraction matches its"
            + " own queued dialogs by value, but MineColonies keys them by the interaction's"
            + " inquiry component.");
    assertEquals(
        IInteractionResponseHandler.class,
        type.getActualTypeArguments()[1],
        "the chat options no longer hold IInteractionResponseHandler values, so the"
            + " ShopLostPackageInteraction instanceof check that selects what to remove cannot"
            + " match.");
  }

  @Test
  void theShopCanStillOpenTheField() throws Exception {
    Field field = CitizenData.class.getDeclaredField("citizenChatOptions");

    // Throws InaccessibleObjectException if MineColonies ever stops being open to us, which is
    // exactly the failure this test is here to name.
    field.setAccessible(true);

    assertTrue(field.trySetAccessible(), "CitizenData.citizenChatOptions cannot be opened");
  }
}
