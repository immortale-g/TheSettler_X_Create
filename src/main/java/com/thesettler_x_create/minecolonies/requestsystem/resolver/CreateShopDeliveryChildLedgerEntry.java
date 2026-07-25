package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;

/** Runtime lifecycle ledger entry for one delivery-child request token. */
final class CreateShopDeliveryChildLedgerEntry {
  final IToken<?> childToken;
  IToken<?> parentToken;
  long firstSeenAtTick = -1L;
  long createdSeenAtTick = -1L;
  long assignedSeenAtTick = -1L;
  long inProgressSeenAtTick = -1L;
  long terminalSeenAtTick = -1L;
  long lastSeenAtTick = -1L;
  String lastSeenState = "<none>";
  String lastOwnerResolver = "<none>";
  boolean lastQueueContains;
  int lastCourierCount = -1;
  int lastCourierTaskMatchCount;
  int lastCourierCarryMatchCount;
  int lastCourierAtSourceMatchCount;
  int lastCourierAtTargetMatchCount;
  long pickupConfirmedAtTick = -1L;
  String terminalSource = "<none>";
  String diagnosisCode = "<none>";
  String diagnosisDetail = "<none>";

  CreateShopDeliveryChildLedgerEntry(IToken<?> childToken) {
    this.childToken = childToken;
  }
}
