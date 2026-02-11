package com.thesettler_x_create.minecolonies.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import com.thesettler_x_create.minecolonies.ai.EntityAIWorkCreateShop;

public class JobCreateShop extends AbstractJob<AbstractAISkeleton<JobCreateShop>, JobCreateShop> {
    public JobCreateShop(final ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public AbstractAISkeleton<JobCreateShop> generateAI() {
        return new EntityAIWorkCreateShop(this);
    }

}
