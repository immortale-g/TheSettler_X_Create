package com.thesettler_x_create.minecolonies.module;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import java.util.ArrayList;

public class CreateShopCourierModule extends CourierAssignmentModule {
  @Override
  public int getModuleMax() {
    return 1;
  }

  @Override
  public boolean assignCitizen(ICitizenData citizen) {
    if (citizen == null) {
      return false;
    }
    if (citizen.getJob() instanceof JobCreateShop) {
      return false;
    }
    return super.assignCitizen(citizen);
  }

  @Override
  public void onColonyTick(IColony colony) {
    super.onColonyTick(colony);
    cleanupAssignments();
  }

  private void cleanupAssignments() {
    for (ICitizenData citizen : new ArrayList<>(assignedCitizen)) {
      if (citizen == null || !(citizen.getJob() instanceof JobDeliveryman)) {
        removeCitizen(citizen);
      }
    }
  }
}
