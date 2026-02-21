package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.token.IToken;

final class CreateShopFlowRecord {
  private final IToken<?> requestToken;
  private final long createdTick;
  private long lastProgressTick;
  private CreateShopFlowState state;
  private String detail;
  private String stackLabel;
  private int amount;

  CreateShopFlowRecord(IToken<?> requestToken, long now) {
    this.requestToken = requestToken;
    this.createdTick = now;
    this.lastProgressTick = now;
    this.state = CreateShopFlowState.NEW;
    this.detail = "";
    this.stackLabel = "";
    this.amount = 0;
  }

  IToken<?> getRequestToken() {
    return requestToken;
  }

  long getCreatedTick() {
    return createdTick;
  }

  long getLastProgressTick() {
    return lastProgressTick;
  }

  CreateShopFlowState getState() {
    return state;
  }

  String getDetail() {
    return detail;
  }

  String getStackLabel() {
    return stackLabel;
  }

  int getAmount() {
    return amount;
  }

  void setState(CreateShopFlowState state, long now, String detail, String stackLabel, int amount) {
    this.state = state;
    this.lastProgressTick = now;
    this.detail = detail == null ? "" : detail;
    this.stackLabel = stackLabel == null ? "" : stackLabel;
    this.amount = Math.max(0, amount);
  }

  void touch(long now, String detail) {
    this.lastProgressTick = now;
    this.detail = detail == null ? this.detail : detail;
  }
}
