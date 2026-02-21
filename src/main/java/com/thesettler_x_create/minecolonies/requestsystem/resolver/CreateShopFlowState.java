package com.thesettler_x_create.minecolonies.requestsystem.resolver;

enum CreateShopFlowState {
  NEW(0),
  ELIGIBILITY_CHECK(1),
  ORDERED_FROM_NETWORK(2),
  ARRIVED_IN_SHOP_RACK(3),
  RESERVED_FOR_DELIVERY(4),
  DELIVERY_CREATED(5),
  DELIVERY_COMPLETED(6),
  REQUEST_COMPLETED(7),
  CANCELLED(100),
  FAILED(101);

  private final int rank;

  CreateShopFlowState(int rank) {
    this.rank = rank;
  }

  int rank() {
    return rank;
  }

  boolean isTerminal() {
    return this == REQUEST_COMPLETED || this == CANCELLED || this == FAILED;
  }
}
