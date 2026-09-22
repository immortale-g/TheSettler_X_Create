package com.thesettler_x_create.minecolonies.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A citizen interaction is answered by sending its inquiry back to the server, which looks the
 * interaction up in a map keyed by that very component ({@code CitizenData.onResponseTriggered} ->
 * {@code citizenChatOptions.containsKey(key)}). The component travels as serialized text, so an
 * argument that does not come back as the same type makes the lookup miss, and the button does
 * nothing at all: no error, no log line, nothing.
 */
@Tag("fml")
class ShopInteractionKeyFmlTest {

  @Test
  void theLostPackageInquirySurvivesTheTripBackToTheServer() {
    ShopLostPackageInteraction interaction =
        new ShopLostPackageInteraction(new ItemStack(Items.TORCH), 64, "Warehouse", "456", 0L, 0L);

    Component key = interaction.getInquiry();

    assertEquals(
        key,
        roundTrip(key),
        "the inquiry does not come back equal, so answering it finds no interaction");
  }

  @Test
  void theLostPackageInquirySurvivesBeingSavedAndSentToTheClient() {
    ShopLostPackageInteraction interaction =
        new ShopLostPackageInteraction(new ItemStack(Items.TORCH), 64, "Warehouse", "456", 0L, 0L);

    Component key = interaction.getInquiry();
    net.minecraft.nbt.Tag saved =
        ComponentSerialization.CODEC
            .encodeStart(RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE), key)
            .getOrThrow();
    Component loaded =
        ComponentSerialization.CODEC
            .parse(RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE), saved)
            .getOrThrow();

    assertEquals(
        key,
        loaded,
        "the inquiry the client gets back is not equal to the one the server keeps, so the answer"
            + " finds no interaction: "
            + describeArgs(key)
            + " became "
            + describeArgs(loaded));
  }

  @Test
  void theLostPackageInquirySurvivesTheWayMineColoniesActuallyStoresIt() {
    // AbstractInteractionResponseHandler writes the inquiry with Component.Serializer.toJson and
    // reads it back with fromJson. That is the copy the client answers with.
    ShopLostPackageInteraction interaction =
        new ShopLostPackageInteraction(new ItemStack(Items.TORCH), 64, "Warehouse", "456", 0L, 0L);

    Component key = interaction.getInquiry();
    String json = Component.Serializer.toJson(key, RegistryAccess.EMPTY);
    Component loaded = Component.Serializer.fromJson(json, RegistryAccess.EMPTY);

    assertEquals(
        key,
        loaded,
        "the inquiry the client answers with is not equal to the one the server keeps, so"
            + " CitizenData.onResponseTriggered finds nothing and the button does nothing: "
            + describeArgs(key)
            + " became "
            + describeArgs(loaded));
  }

  @Test
  void everyShopInteractionCanBeAnsweredAtAll() {
    assertSurvivesStorage(
        new ShopLostPackageInteraction(new ItemStack(Items.TORCH), 64, "Warehouse", "456", 0L, 0L));
    assertSurvivesStorage(
        new ShopLostPackageReorderUnavailableInteraction(
            new ItemStack(Items.TORCH), 64, "Warehouse", "456", 0L, 0L));
    assertSurvivesStorage(
        new ShopCapacityStallInteraction(new ItemStack(Items.TORCH), 64, 12, false));
    // The stall has a second wording for a shop whose pickup the player turned off.
    assertSurvivesStorage(
        new ShopCapacityStallInteraction(new ItemStack(Items.TORCH), 64, 12, true));
  }

  private static void assertSurvivesStorage(
      com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler interaction) {
    Component key = interaction.getInquiry();
    Component loaded =
        Component.Serializer.fromJson(
            Component.Serializer.toJson(key, RegistryAccess.EMPTY), RegistryAccess.EMPTY);

    assertEquals(
        key,
        loaded,
        interaction.getClass().getSimpleName()
            + " cannot be answered: its inquiry comes back as "
            + describeArgs(loaded)
            + " instead of "
            + describeArgs(key));
  }

  private static String describeArgs(Component component) {
    if (!(component.getContents() instanceof TranslatableContents contents)) {
      return component.getContents().getClass().getSimpleName();
    }
    StringBuilder out = new StringBuilder();
    for (Object arg : contents.getArgs()) {
      out.append(arg == null ? "null" : arg.getClass().getSimpleName() + "(" + arg + ")")
          .append(' ');
    }
    return out.toString().trim();
  }

  private static Component roundTrip(Component component) {
    RegistryFriendlyByteBuf buf =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    ComponentSerialization.STREAM_CODEC.encode(buf, component);
    return ComponentSerialization.STREAM_CODEC.decode(buf);
  }
}
