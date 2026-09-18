package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.thesettler_x_create.DebugLog;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Which deliveries a courier is picking up right now, as MineColonies itself records it.
 *
 * <p>{@code EntityAIWorkDeliveryman.prepareDelivery} adds the delivery it is about to fetch to the
 * job's ongoing set immediately before it reaches into the source inventory, and the entry stays
 * until the courier hands the goods over. That set is the only place in the game that says which
 * delivery a given extraction belongs to: the inventory itself reports an amount, never an actor.
 * This class only ever reads it.
 *
 * <p>The set lives in the courier's job data store. {@code JobDeliveryman.getDataStore()} is
 * private, but the token that leads to it is written to the job's NBT under the public API constant
 * {@link NbtTagConstants#TAG_RS_DMANJOB_DATASTORE}, and the manager hands out the very instance the
 * job works with (see {@code StandardDataStoreManager.get}). So this reads MineColonies' own state
 * rather than rebuilding its decision, which is what keeps the two from drifting apart.
 *
 * <p>Resolving the token means serializing the job, so each courier is resolved once and kept. The
 * token only changes when the job is created anew, and a job that no longer answers is resolved
 * again on the next call.
 */
final class CourierOngoingDeliveries {
  private final Map<Integer, IToken<?>> dataStoreTokenByCitizen = new HashMap<>();

  /**
   * The deliveries this courier currently has in hand or is reaching for, or an empty set when
   * MineColonies does not tell us. Never guesses.
   */
  Set<IToken<?>> of(IColony colony, ICitizenData citizen, JobDeliveryman job) {
    if (colony == null || citizen == null || job == null) {
      return Set.of();
    }
    IRequestSystemDeliveryManJobDataStore store = storeFor(colony, citizen, job);
    if (store == null || store.getOngoingDeliveries() == null) {
      return Set.of();
    }
    return Set.copyOf(store.getOngoingDeliveries());
  }

  @Nullable
  private IRequestSystemDeliveryManJobDataStore storeFor(
      IColony colony, ICitizenData citizen, JobDeliveryman job) {
    if (colony.getRequestManager() == null
        || colony.getRequestManager().getDataStoreManager() == null) {
      return null;
    }
    IToken<?> token = dataStoreTokenByCitizen.get(citizen.getId());
    if (token == null) {
      token = readDataStoreToken(colony, job);
      if (token == null) {
        return null;
      }
      dataStoreTokenByCitizen.put(citizen.getId(), token);
    }
    try {
      return colony
          .getRequestManager()
          .getDataStoreManager()
          .get(token, TypeConstants.REQUEST_SYSTEM_DELIVERY_MAN_JOB_DATA_STORE);
    } catch (Exception ex) {
      // A store that cannot be read tells us nothing; the delivery books on arrival instead.
      dataStoreTokenByCitizen.remove(citizen.getId());
      return null;
    }
  }

  /**
   * The token from the job's own NBT. Returns null when the tag is missing, which is what a changed
   * MineColonies would look like: asking the manager for a token we made up would quietly create an
   * empty store instead of failing.
   */
  @Nullable
  private static IToken<?> readDataStoreToken(IColony colony, JobDeliveryman job) {
    if (colony.getWorld() == null) {
      return null;
    }
    try {
      HolderLookup.Provider provider = colony.getWorld().registryAccess();
      CompoundTag nbt = job.serializeNBT(provider);
      if (nbt == null || !nbt.contains(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE)) {
        if (DebugLog.enabled()) {
          TheSettlerXCreate.LOGGER.info(
              "[CreateShop] courier job has no {} tag; pickups book on arrival instead",
              NbtTagConstants.TAG_RS_DMANJOB_DATASTORE);
        }
        return null;
      }
      return StandardFactoryController.getInstance()
          .deserializeTag(provider, nbt.getCompound(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE));
    } catch (Exception ex) {
      if (DebugLog.enabled()) {
        TheSettlerXCreate.LOGGER.info(
            "[CreateShop] could not read the courier's data store token: {}",
            ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
      return null;
    }
  }

  /** Forgets the cached tokens, for a shop that is being taken down. */
  void clear() {
    dataStoreTokenByCitizen.clear();
  }
}
