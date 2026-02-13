package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolverFactory;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.constant.TypeConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class CreateShopRequestResolverFactory
    implements IRequestResolverFactory<CreateShopRequestResolver> {
  private static final String TAG_TOKEN = "Token";
  private static final String TAG_LOCATION = "Location";
  private static final short SERIALIZATION_ID = 3000;

  @Override
  public TypeToken<? extends CreateShopRequestResolver> getFactoryOutputType() {
    return TypeToken.of(CreateShopRequestResolver.class);
  }

  @Override
  public TypeToken<? extends ILocation> getFactoryInputType() {
    return TypeConstants.ILOCATION;
  }

  @Override
  public CreateShopRequestResolver getNewInstance(
      IFactoryController factoryController, ILocation input, Object... context)
      throws IllegalArgumentException {
    IToken<?> token = factoryController.getNewInstance(TypeConstants.ITOKEN);
    return new CreateShopRequestResolver(input, token);
  }

  @Override
  public CompoundTag serialize(
      HolderLookup.Provider registries,
      IFactoryController factoryController,
      CreateShopRequestResolver resolver) {
    CompoundTag tag = new CompoundTag();
    tag.put(TAG_TOKEN, serializeToken(registries, factoryController, resolver));
    tag.put(TAG_LOCATION, serializeLocation(registries, factoryController, resolver));
    return tag;
  }

  @Override
  public CreateShopRequestResolver deserialize(
      HolderLookup.Provider registries, IFactoryController factoryController, CompoundTag tag) {
    CompoundTag tokenTag = requireTag(tag, TAG_TOKEN);
    CompoundTag locationTag = requireTag(tag, TAG_LOCATION);
    IToken<?> token = deserializeToken(registries, factoryController, tokenTag);
    ILocation location = deserializeLocation(registries, factoryController, locationTag);
    return new CreateShopRequestResolver(location, token);
  }

  @Override
  public void serialize(
      IFactoryController factoryController,
      CreateShopRequestResolver resolver,
      RegistryFriendlyByteBuf buffer) {
    factoryController.serialize(buffer, resolver.getId());
    factoryController.serialize(buffer, resolver.getLocation());
  }

  @Override
  public CreateShopRequestResolver deserialize(
      IFactoryController factoryController, RegistryFriendlyByteBuf buffer) throws Throwable {
    IToken<?> token = deserializeToken(factoryController, buffer);
    ILocation location = deserializeLocation(factoryController, buffer);
    return new CreateShopRequestResolver(location, token);
  }

  @Override
  public short getSerializationId() {
    return SERIALIZATION_ID;
  }

  private static CompoundTag serializeToken(
      HolderLookup.Provider registries,
      IFactoryController factoryController,
      CreateShopRequestResolver resolver) {
    return factoryController.serializeTag(registries, resolver.getId());
  }

  private static CompoundTag serializeLocation(
      HolderLookup.Provider registries,
      IFactoryController factoryController,
      CreateShopRequestResolver resolver) {
    return factoryController.serializeTag(registries, resolver.getLocation());
  }

  private static IToken<?> deserializeToken(
      HolderLookup.Provider registries, IFactoryController factoryController, CompoundTag tag) {
    return (IToken<?>) factoryController.deserializeTag(registries, tag);
  }

  private static ILocation deserializeLocation(
      HolderLookup.Provider registries, IFactoryController factoryController, CompoundTag tag) {
    return (ILocation) factoryController.deserializeTag(registries, tag);
  }

  private static IToken<?> deserializeToken(
      IFactoryController factoryController, RegistryFriendlyByteBuf buffer) throws Throwable {
    return (IToken<?>) factoryController.deserialize(buffer);
  }

  private static ILocation deserializeLocation(
      IFactoryController factoryController, RegistryFriendlyByteBuf buffer) throws Throwable {
    return (ILocation) factoryController.deserialize(buffer);
  }

  private static CompoundTag requireTag(CompoundTag tag, String key) {
    if (tag == null || !tag.contains(key)) {
      throw new IllegalArgumentException("Missing " + key + " tag for CreateShopRequestResolver");
    }
    return tag.getCompound(key);
  }
}
