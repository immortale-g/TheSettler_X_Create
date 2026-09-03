package com.thesettler_x_create.minecolonies.ai;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;

public enum CreateShopAIState implements IAIState {
  HOUSEKEEPING_FETCH,
  HOUSEKEEPING_DEPOSIT;

  @Override
  public boolean isOkayToEat() {
    return false;
  }
}
