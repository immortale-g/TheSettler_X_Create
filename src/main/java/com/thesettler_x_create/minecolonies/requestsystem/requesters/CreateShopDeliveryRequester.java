package com.thesettler_x_create.minecolonies.requestsystem.requesters;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Delivery requester for Create Shop sourced deliveries.
 *
 * <p>The delegate stays the native warehouse requester so MineColonies assigns and displays the
 * task in the target warehouse queue. The display name is the Create Shop source, matching the
 * delivery payload's start location.
 */
public final class CreateShopDeliveryRequester implements IRequester {
  private final IRequester delegate;
  private final ILocation sourceLocation;

  public CreateShopDeliveryRequester(IRequester delegate) {
    this(delegate, null);
  }

  public CreateShopDeliveryRequester(IRequester delegate, ILocation sourceLocation) {
    this.delegate = delegate;
    this.sourceLocation = sourceLocation;
  }

  public IRequester getDelegate() {
    return delegate;
  }

  public ILocation getSourceLocation() {
    return sourceLocation;
  }

  @Override
  public IToken<?> getId() {
    return delegate == null ? null : delegate.getId();
  }

  @Override
  public ILocation getLocation() {
    return sourceLocation == null
        ? (delegate == null ? null : delegate.getLocation())
        : sourceLocation;
  }

  @Override
  public void onRequestedRequestComplete(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver.onDeliveryComplete(manager, request);
    if (delegate != null) {
      delegate.onRequestedRequestComplete(manager, request);
    }
  }

  @Override
  public void onRequestedRequestCancelled(IRequestManager manager, IRequest<?> request) {
    CreateShopRequestResolver.onDeliveryCancelled(manager, request);
    if (delegate != null) {
      delegate.onRequestedRequestCancelled(manager, request);
    }
  }

  @Override
  public MutableComponent getRequesterDisplayName(IRequestManager manager, IRequest<?> request) {
    return Component.translatable("com.thesettler_x_create.coremod.buildings.createshop");
  }
}
