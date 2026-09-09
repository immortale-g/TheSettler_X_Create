package com.thesettler_x_create.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-text guards for the two payloads carrying an ItemStack ({@code
 * CreateShopTestRequestPayload}, {@code CreateShopBatchRequestPayload}). ItemStack's own
 * STREAM_CODEC throws outside a bootstrapped game instance (confirmed empirically - this project
 * deliberately never bootstraps registries in tests), so unlike {@link NetworkPayloadRoundTripTest}
 * these can't be exercised as live round-trips; this locks in encode/decode field-order symmetry
 * instead.
 */
class ItemStackNetworkPayloadGuardTest {

  @Test
  void createShopTestRequestPayloadEncodeAndDecodeAgreeOnFieldOrder() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/network/CreateShopTestRequestPayload.java"));

    int encodeStart = source.indexOf("(buf, payload) -> {");
    int encodeEnd = source.indexOf("},", encodeStart);
    String encodeBody = source.substring(encodeStart, encodeEnd);
    assertTrue(encodeBody.indexOf("writeBlockPos") < encodeBody.indexOf("ItemStack.STREAM_CODEC"));
    assertTrue(encodeBody.indexOf("ItemStack.STREAM_CODEC") < encodeBody.indexOf("writeVarInt"));

    int decodeStart = source.indexOf("buf ->", encodeEnd);
    String decodeBody = source.substring(decodeStart);
    assertTrue(decodeBody.indexOf("readBlockPos") < decodeBody.indexOf("ItemStack.STREAM_CODEC"));
    assertTrue(decodeBody.indexOf("ItemStack.STREAM_CODEC") < decodeBody.indexOf("readVarInt"));
  }

  @Test
  void createShopBatchRequestPayloadWritesCountBeforeStacksAndReadsSameCount() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/network/CreateShopBatchRequestPayload.java"));

    // The stack count must be written before the stacks themselves, and decode must read the same
    // count back before looping - otherwise decode has no way to know how many entries to read.
    int encodeStart = source.indexOf("(buf, payload) -> {");
    int encodeEnd = source.indexOf("},", encodeStart);
    String encodeBody = source.substring(encodeStart, encodeEnd);
    assertTrue(
        encodeBody.indexOf("writeVarInt(payload.stacks.size())")
            < encodeBody.indexOf("BigItemStack.STREAM_CODEC.encode"));

    String decodeBody = source.substring(encodeEnd);
    assertTrue(
        decodeBody.indexOf("readVarInt()")
            < decodeBody.indexOf("BigItemStack.STREAM_CODEC.decode"));
    assertTrue(decodeBody.contains("new ArrayList<>(count)"));
  }

  @Test
  void createShopBatchRequestPayloadBoundsCountBeforeAllocating() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/thesettler_x_create/network/CreateShopBatchRequestPayload.java"));

    // A client-controlled VarInt must be range-checked before it drives an ArrayList allocation -
    // otherwise a crafted packet can force an oversized allocation attempt on the network thread.
    int countRead = source.indexOf("int count = buf.readVarInt();");
    int boundsCheck = source.indexOf("count > MAX_STACKS", countRead);
    int allocation = source.indexOf("new ArrayList<>(count)", countRead);
    assertTrue(countRead > 0 && boundsCheck > countRead && boundsCheck < allocation);
  }
}
