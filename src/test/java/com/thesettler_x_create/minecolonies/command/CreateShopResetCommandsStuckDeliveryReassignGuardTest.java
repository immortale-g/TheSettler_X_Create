package com.thesettler_x_create.minecolonies.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Seam-audit finding s3-2 (verification pass): {@code reconcileAssignmentsAndKickCouriers} kicked a
 * stuck {@code CREATED}-state Delivery request with a raw {@code standard.assignRequest(token)}
 * call. That token is only visible in this loop because it's already present in {@code
 * store.getAssignments()} - a second raw assign can leave a stale/duplicate assignment-store entry
 * behind instead of cleanly replacing the existing one, since {@code assignRequest} never clears a
 * prior assignment. Fixed by switching to {@code standard.reassignRequest(token, ...)}, which
 * explicitly unassigns via the same resolver-assignment data store before reassigning - the same
 * repair idiom {@code BuildingCreateShop.repairOpenPickupRequest} already uses for an analogous
 * stuck-pickup-request repair.
 */
class CreateShopResetCommandsStuckDeliveryReassignGuardTest {

  private static final String SOURCE =
      "src/main/java/com/thesettler_x_create/minecolonies/command/CreateShopAssignmentReconciler.java";

  @Test
  void stuckDeliveryKickUsesReassignNotRawAssign() throws Exception {
    String source = Files.readString(Path.of(SOURCE));

    int method = source.indexOf("static void reconcileAssignmentsAndKickCouriers(");
    assertTrue(method > 0);
    String body = source.substring(method, Math.min(source.length(), method + 3200));

    assertTrue(
        body.contains("standard.reassignRequest(token, java.util.Collections.emptyList())"),
        "expected the CREATED-state Delivery kick to go through reassignRequest, not a raw assign");
    assertFalse(
        body.contains("standard.assignRequest(token)"),
        "expected no raw assignRequest(token) left in this method");
  }
}
