package com.thesettler_x_create.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the UUID-based cancel flow introduced in Phase 3.1.
 *
 * <p>InflightEntry gained a nullable requestUuid field so that cancel operations can target entries
 * precisely by UUID instead of relying on string-based name/address matching. The UUID is threaded
 * from AttemptResolveService through recordInflight all the way to cancelInflightByUuid. Legacy
 * entries without a UUID fall back to string matching.
 */
class CreateShopBlockEntityUuidCancelGuardTest {

  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/blockentity/CreateShopBlockEntity.java";

  /** InflightEntry must carry a nullable UUID field so precise cancel is possible. */
  @Test
  void inflightEntryHasNullableRequestUuidField() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    assertTrue(
        source.contains("@Nullable public UUID requestUuid"),
        "InflightEntry must declare @Nullable public UUID requestUuid");
  }

  /** UUID is written to NBT during save and read back with a null-safe guard during load. */
  @Test
  void requestUuidIsSavedAndLoadedWithNullSafety() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    // Save path: only written when non-null.
    assertTrue(
        source.contains("data.putUUID(\"requestUuid\", entry.requestUuid)"),
        "requestUuid must be persisted to NBT during save");

    // Load path: null-safe read — old entries without the tag get null.
    assertTrue(
        source.contains("entry.hasUUID(\"requestUuid\") ? entry.getUUID(\"requestUuid\") : null"),
        "requestUuid must be loaded with hasUUID null-guard for pre-3.1 save compatibility");
  }

  /**
   * cancelInflightByUuid must exist and perform UUID-first matching. String-fallback must remain to
   * handle legacy entries recorded before Phase 3.1.
   */
  @Test
  void cancelInflightByUuidExistsWithStringFallback() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    assertTrue(
        source.contains("cancelInflightByUuid("),
        "cancelInflightByUuid method must exist for precise UUID-based cancel");

    // UUID-equality check on the entry field.
    assertTrue(
        source.contains("requestUuid.equals(entry.requestUuid)"),
        "cancelInflightByUuid must match entries by requestUuid equality");

    // Null guard on the incoming UUID — callers may pass null for legacy paths.
    assertTrue(
        source.contains("requestUuid == null"),
        "cancelInflightByUuid must guard against null requestUuid");
  }
}
