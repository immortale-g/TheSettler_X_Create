package com.thesettler_x_create.minecolonies.module;

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
}
