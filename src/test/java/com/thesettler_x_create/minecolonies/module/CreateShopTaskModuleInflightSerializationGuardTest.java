package com.thesettler_x_create.minecolonies.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CreateShopTaskModuleInflightSerializationGuardTest {
  @Test
  void serializesQueueThenInflightTaskTokens() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/module/CreateShopTaskModule.java"));

    assertTrue(source.contains("super.serializeToView(buf);"));
    assertTrue(source.contains("List<IToken<?>> inflight = getInflightTaskTokens();"));
    assertTrue(source.contains("buf.writeInt(inflight.size());"));
    assertTrue(source.contains("StandardFactoryController.getInstance().serialize(buf, token);"));
  }

  @Test
  void taskViewUsesCreateShopTaskWindow() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/moduleview/CreateShopTaskModuleView.java"));

    assertTrue(source.contains("new CreateShopTaskModuleWindow(this)"));
  }

  @Test
  void taskWindowUsesDisplayStacksAsIconFallback() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopTaskModuleWindow.java"));

    assertTrue(source.contains("request.getDisplayStacks()"));
    assertTrue(source.contains("detailIcon.setItem(stacks.get(0).copy())"));
    assertTrue(source.contains("detailIcon.setVisible(true)"));
    assertTrue(source.contains("layoutcreateshop_tasklist.xml"));
  }

  @Test
  void taskWindowCollapsesDeliveryChildWhenParentInflightIsVisible() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/minecolonies/client/gui/CreateShopTaskModuleWindow.java"));

    assertTrue(source.contains("isDeliveryChildHidden"));
    assertTrue(source.contains("request.getRequest() instanceof Delivery"));
    assertTrue(source.contains("allTokens.contains(request.getParent())"));
  }

  @Test
  void taskLayoutUsesLeftItemIconInsteadOfDeliveryImage() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/resources/assets/thesettler_x_create/gui/layouthuts/layoutcreateshop_tasklist.xml"));

    assertTrue(source.contains("<itemicon id=\"detailIcon\" size=\"16 16\" pos=\"1 3\""));
    assertFalse(source.contains("deliveryImage"));
  }
}
