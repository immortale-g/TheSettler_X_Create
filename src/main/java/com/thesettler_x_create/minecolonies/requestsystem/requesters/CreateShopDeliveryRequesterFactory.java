package com.thesettler_x_create.minecolonies.requestsystem.requesters;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.requester.IRequesterFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Factory for Create Shop delivery requester wrappers. */
public class CreateShopDeliveryRequesterFactory
    implements IRequesterFactory<IRequester, CreateShopDeliveryRequester> {
  private static final String TAG_DELEGATE = "Delegate";
  private static final String TAG_SOURCE_LOCATION = "SourceLocation";
  private static final short SERIALIZATION_ID = 3002;

  @Override
  public TypeToken<? extends CreateShopDeliveryRequester> getFactoryOutputType() {
    return TypeToken.of(CreateShopDeliveryRequester.class);
  }

  @Override
  public TypeToken<? extends IRequester> getFactoryInputType() {
    return TypeToken.of(IRequester.class);
  }

  @Override
  public CreateShopDeliveryRequester getNewInstance(
      IFactoryController factoryController, IRequester input, Object... context)
      throws IllegalArgumentException {
    if (input instanceof CreateShopDeliveryRequester requester) {
      return requester;
    }
    return new CreateShopDeliveryRequester(input);
  }

  @Override
  public CompoundTag serialize(
      HolderLookup.Provider registries,
      IFactoryController factoryController,
      CreateShopDeliveryRequester requester) {
    CompoundTag tag = new CompoundTag();
    if (requester == null) {
      return tag;
    }
    if (requester.getDelegate() != null) {
      tag.put(TAG_DELEGATE, factoryController.serializeTag(registries, requester.getDelegate()));
    }
    if (requester.getSourceLocation() != null) {
      tag.put(
          TAG_SOURCE_LOCATION,
          factoryController.serializeTag(registries, requester.getSourceLocation()));
    }
    return tag;
  }

  @Override
  public CreateShopDeliveryRequester deserialize(
      HolderLookup.Provider registries, IFactoryController factoryController, CompoundTag tag) {
    IRequester delegate = null;
    if (tag != null && tag.contains(TAG_DELEGATE)) {
      delegate =
          (IRequester) factoryController.deserializeTag(registries, tag.getCompound(TAG_DELEGATE));
    }
    ILocation sourceLocation = null;
    if (tag != null && tag.contains(TAG_SOURCE_LOCATION)) {
      sourceLocation =
          (ILocation)
              factoryController.deserializeTag(registries, tag.getCompound(TAG_SOURCE_LOCATION));
    }
    return new CreateShopDeliveryRequester(delegate, sourceLocation);
  }

  @Override
  public void serialize(
      IFactoryController factoryController,
      CreateShopDeliveryRequester requester,
      RegistryFriendlyByteBuf buffer) {
    IRequester delegate = requester == null ? null : requester.getDelegate();
    ILocation sourceLocation = requester == null ? null : requester.getSourceLocation();
    factoryController.serialize(buffer, delegate);
    factoryController.serialize(buffer, sourceLocation);
  }

  @Override
  public CreateShopDeliveryRequester deserialize(
      IFactoryController factoryController, RegistryFriendlyByteBuf buffer) throws Throwable {
    IRequester delegate = (IRequester) factoryController.deserialize(buffer);
    ILocation sourceLocation = (ILocation) factoryController.deserialize(buffer);
    return new CreateShopDeliveryRequester(delegate, sourceLocation);
  }

  @Override
  public short getSerializationId() {
    return SERIALIZATION_ID;
  }
}
