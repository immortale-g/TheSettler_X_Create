package com.thesettler_x_create.minecolonies.requestsystem.requesters;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import net.minecraft.network.chat.MutableComponent;

/**
 * Legacy requester wrapper kept for deserialization compatibility with old worlds.
 *
 * <p>New flows no longer create this wrapper. It only exists so pre-0.0.12 serialized requester
 * entries can load safely.
 */
public final class SafeRequester implements IRequester {
  private final IRequester delegate;

  public SafeRequester(final IRequester delegate) {
    this.delegate = delegate;
  }

  public IRequester getDelegate() {
    return delegate;
  }

  @Override
  public IToken<?> getId() {
    return delegate == null ? null : delegate.getId();
  }

  @Override
  public ILocation getLocation() {
    return delegate == null ? null : delegate.getLocation();
  }

  @Override
  public void onRequestedRequestComplete(final IRequestManager manager, final IRequest<?> request) {
    com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver
        .onDeliveryComplete(manager, request);
    if (delegate == null) {
      return;
    }
    try {
      delegate.onRequestedRequestComplete(manager, request);
    } catch (Exception ex) {
      logCompatibilityCallbackError("complete", request, ex);
    }
  }

  @Override
  public void onRequestedRequestCancelled(
      final IRequestManager manager, final IRequest<?> request) {
    com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver
        .onDeliveryCancelled(manager, request);
    if (delegate == null) {
      return;
    }
    try {
      delegate.onRequestedRequestCancelled(manager, request);
    } catch (Exception ex) {
      logCompatibilityCallbackError("cancel", request, ex);
    }
  }

  @Override
  public MutableComponent getRequesterDisplayName(
      final IRequestManager manager, final IRequest<?> request) {
    return delegate == null ? null : delegate.getRequesterDisplayName(manager, request);
  }

  private void logCompatibilityCallbackError(
      final String action, final IRequest<?> request, final Exception ex) {
    if (!Config.DEBUG_LOGGING.getAsBoolean()) {
      return;
    }
    String token = request == null ? "<null>" : String.valueOf(request.getId());
    String message = ex.getMessage() == null ? "<null>" : ex.getMessage();
    TheSettlerXCreate.LOGGER.info(
        "[CreateShop] legacy SafeRequester {} callback error token={} type={} message={}",
        action,
        token,
        ex.getClass().getSimpleName(),
        message);
  }
}
