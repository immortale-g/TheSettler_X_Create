package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;

/** Runtime lifecycle ledger entry for one delivery-child request token. */
final class CreateShopDeliveryChildLedgerEntry {
  private final IToken<?> childToken;
  private IToken<?> parentToken;
  private long firstSeenAtTick = -1L;
  private long createdSeenAtTick = -1L;
  private long assignedSeenAtTick = -1L;
  private long inProgressSeenAtTick = -1L;
  private long terminalSeenAtTick = -1L;
  private long lastSeenAtTick = -1L;
  private String lastSeenState = "<none>";
  private String lastOwnerResolver = "<none>";
  private boolean lastQueueContains;
  private int lastCourierCount = -1;
  private int lastCourierTaskMatchCount;
  private int lastCourierCarryMatchCount;
  private int lastCourierAtSourceMatchCount;
  private int lastCourierAtTargetMatchCount;
  private long pickupConfirmedAtTick = -1L;
  private String terminalSource = "<none>";
  private String diagnosisCode = "<none>";
  private String diagnosisDetail = "<none>";

  CreateShopDeliveryChildLedgerEntry(IToken<?> childToken) {
    this.childToken = childToken;
  }

  IToken<?> getChildToken() {
    return childToken;
  }

  IToken<?> getParentToken() {
    return parentToken;
  }

  void setParentToken(IToken<?> parentToken) {
    this.parentToken = parentToken;
  }

  long getFirstSeenAtTick() {
    return firstSeenAtTick;
  }

  void setFirstSeenAtTick(long firstSeenAtTick) {
    this.firstSeenAtTick = firstSeenAtTick;
  }

  long getCreatedSeenAtTick() {
    return createdSeenAtTick;
  }

  void setCreatedSeenAtTick(long createdSeenAtTick) {
    this.createdSeenAtTick = createdSeenAtTick;
  }

  long getAssignedSeenAtTick() {
    return assignedSeenAtTick;
  }

  void setAssignedSeenAtTick(long assignedSeenAtTick) {
    this.assignedSeenAtTick = assignedSeenAtTick;
  }

  long getInProgressSeenAtTick() {
    return inProgressSeenAtTick;
  }

  void setInProgressSeenAtTick(long inProgressSeenAtTick) {
    this.inProgressSeenAtTick = inProgressSeenAtTick;
  }

  long getTerminalSeenAtTick() {
    return terminalSeenAtTick;
  }

  void setTerminalSeenAtTick(long terminalSeenAtTick) {
    this.terminalSeenAtTick = terminalSeenAtTick;
  }

  long getLastSeenAtTick() {
    return lastSeenAtTick;
  }

  void setLastSeenAtTick(long lastSeenAtTick) {
    this.lastSeenAtTick = lastSeenAtTick;
  }

  String getLastSeenState() {
    return lastSeenState;
  }

  void setLastSeenState(String lastSeenState) {
    this.lastSeenState = lastSeenState;
  }

  String getLastOwnerResolver() {
    return lastOwnerResolver;
  }

  void setLastOwnerResolver(String lastOwnerResolver) {
    this.lastOwnerResolver = lastOwnerResolver;
  }

  boolean isLastQueueContains() {
    return lastQueueContains;
  }

  void setLastQueueContains(boolean lastQueueContains) {
    this.lastQueueContains = lastQueueContains;
  }

  int getLastCourierCount() {
    return lastCourierCount;
  }

  void setLastCourierCount(int lastCourierCount) {
    this.lastCourierCount = lastCourierCount;
  }

  int getLastCourierTaskMatchCount() {
    return lastCourierTaskMatchCount;
  }

  void setLastCourierTaskMatchCount(int lastCourierTaskMatchCount) {
    this.lastCourierTaskMatchCount = lastCourierTaskMatchCount;
  }

  int getLastCourierCarryMatchCount() {
    return lastCourierCarryMatchCount;
  }

  void setLastCourierCarryMatchCount(int lastCourierCarryMatchCount) {
    this.lastCourierCarryMatchCount = lastCourierCarryMatchCount;
  }

  int getLastCourierAtSourceMatchCount() {
    return lastCourierAtSourceMatchCount;
  }

  void setLastCourierAtSourceMatchCount(int lastCourierAtSourceMatchCount) {
    this.lastCourierAtSourceMatchCount = lastCourierAtSourceMatchCount;
  }

  int getLastCourierAtTargetMatchCount() {
    return lastCourierAtTargetMatchCount;
  }

  void setLastCourierAtTargetMatchCount(int lastCourierAtTargetMatchCount) {
    this.lastCourierAtTargetMatchCount = lastCourierAtTargetMatchCount;
  }

  long getPickupConfirmedAtTick() {
    return pickupConfirmedAtTick;
  }

  void setPickupConfirmedAtTick(long pickupConfirmedAtTick) {
    this.pickupConfirmedAtTick = pickupConfirmedAtTick;
  }

  String getTerminalSource() {
    return terminalSource;
  }

  void setTerminalSource(String terminalSource) {
    this.terminalSource = terminalSource;
  }

  String getDiagnosisCode() {
    return diagnosisCode;
  }

  void setDiagnosisCode(String diagnosisCode) {
    this.diagnosisCode = diagnosisCode;
  }

  String getDiagnosisDetail() {
    return diagnosisDetail;
  }

  void setDiagnosisDetail(String diagnosisDetail) {
    this.diagnosisDetail = diagnosisDetail;
  }
}
