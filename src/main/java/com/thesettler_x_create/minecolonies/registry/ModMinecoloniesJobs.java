package com.thesettler_x_create.minecolonies.registry;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.job.JobCreateShop;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMinecoloniesJobs {
  private ModMinecoloniesJobs() {}

  public static final DeferredRegister<JobEntry> JOBS =
      DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, TheSettlerXCreate.MODID);

  public static final DeferredHolder<JobEntry, JobEntry> CREATE_SHOP =
      JOBS.register(
          "createshop",
          () ->
              new JobEntry.Builder()
                  .setRegistryName(
                      ResourceLocation.fromNamespaceAndPath(TheSettlerXCreate.MODID, "createshop"))
                  .setJobProducer(JobCreateShop::new)
                  .setJobViewProducer(() -> DefaultJobView::new)
                  .createJobEntry());

  public static void register(IEventBus bus) {
    JOBS.register(bus);
  }
}
