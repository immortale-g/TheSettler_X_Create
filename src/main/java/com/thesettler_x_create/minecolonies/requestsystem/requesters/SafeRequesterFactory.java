package com.thesettler_x_create.minecolonies.requestsystem.requesters;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.requester.IRequesterFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class SafeRequesterFactory implements IRequesterFactory<IRequester, SafeRequester> {
  private static final String TAG_DELEGATE = "Delegate";
  private static final short SERIALIZATION_ID = 3001;

  @Override
  public TypeToken<? extends SafeRequester> getFactoryOutputType() {
    return TypeToken.of(SafeRequester.class);
  }

  @Override
  public TypeToken<? extends IRequester> getFactoryInputType() {
    return TypeToken.of(IRequester.class);
  }

  @Override
  public SafeRequester getNewInstance(
      IFactoryController factoryController, IRequester input, Object... context)
      throws IllegalArgumentException {
    if (input instanceof SafeRequester safeRequester) {
      return safeRequester;
    }
    return new SafeRequester(input);
  }

  @Override
  public CompoundTag serialize(
      HolderLookup.Provider registries,
      IFactoryController factoryController,
      SafeRequester requester) {
    CompoundTag tag = new CompoundTag();
    if (requester == null) {
      return tag;
    }
    IRequester delegate = requester.getDelegate();
    if (delegate != null) {
      tag.put(TAG_DELEGATE, factoryController.serializeTag(registries, delegate));
    }
    return tag;
  }

  @Override
  public SafeRequester deserialize(
      HolderLookup.Provider registries, IFactoryController factoryController, CompoundTag tag) {
    IRequester delegate = null;
    if (tag != null && tag.contains(TAG_DELEGATE)) {
      delegate =
          (IRequester) factoryController.deserializeTag(registries, tag.getCompound(TAG_DELEGATE));
    }
    if (delegate instanceof SafeRequester safeRequester) {
      return safeRequester;
    }
    return new SafeRequester(delegate);
  }

  @Override
  public void serialize(
      IFactoryController factoryController,
      SafeRequester requester,
      RegistryFriendlyByteBuf buffer) {
    IRequester delegate = requester == null ? null : requester.getDelegate();
    factoryController.serialize(buffer, delegate);
  }

  @Override
  public SafeRequester deserialize(
      IFactoryController factoryController, RegistryFriendlyByteBuf buffer) throws Throwable {
    IRequester delegate = (IRequester) factoryController.deserialize(buffer);
    if (delegate instanceof SafeRequester safeRequester) {
      return safeRequester;
    }
    return new SafeRequester(delegate);
  }

  @Override
  public short getSerializationId() {
    return SERIALIZATION_ID;
  }
}
