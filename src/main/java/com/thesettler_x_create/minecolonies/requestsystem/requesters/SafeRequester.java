package com.thesettler_x_create.minecolonies.requestsystem.requesters;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a requester and shields MineColonies from requester callback exceptions.
 */
public final class SafeRequester implements IRequester {
    private static final Map<String, String> LAST_ERROR = new ConcurrentHashMap<>();
    private static final java.util.Set<String> CANCEL_TRACE_LOGGED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final java.util.Set<String> FORCE_COMPLETE_LOGGED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final java.util.Set<String> COMPLETE_CHILD_LOGGED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

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
        com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver.onDeliveryComplete(
                manager, request);
        if (request != null
                && request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
                && manager instanceof com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager standardManager) {
            try {
                var handler = standardManager.getRequestHandler();
                IToken<?> token = request.getId();
                IToken<?> parentToken = request.getParent();
                IRequest<?> parent = parentToken == null || handler == null ? null : handler.getRequest(parentToken);
                String parentState = parent == null ? "<null>" : String.valueOf(parent.getState());
                boolean parentChildren = parent != null && parent.hasChildren();
                com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                        "[CreateShop] delivery requester complete token={} state={} parent={} parentState={} parentHasChildren={}",
                        token,
                        request.getState(),
                        parentToken == null ? "<none>" : parentToken,
                        parentState,
                        parentChildren);
                if (handler != null && parent != null && parentChildren) {
                    var children = parent.getChildren();
                    boolean containsChild = children != null && children.contains(token);
                    String key = String.valueOf(token);
                    if (COMPLETE_CHILD_LOGGED.add(key)) {
                        int childCount = children == null ? 0 : children.size();
                        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                                "[CreateShop] delivery requester child check token={} containsChild={} childCount={}",
                                token,
                                containsChild,
                                childCount);
                    }
                    if (containsChild && FORCE_COMPLETE_LOGGED.add(key)) {
                        try {
                            handler.onRequestCompleted(token);
                            var parentAfter = handler.getRequest(parentToken);
                            String parentStateAfter = parentAfter == null ? "<null>" : String.valueOf(parentAfter.getState());
                            int childCountAfter = parentAfter == null || parentAfter.getChildren() == null
                                    ? 0
                                    : parentAfter.getChildren().size();
                            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                                    "[CreateShop] delivery requester forced handler completion child={} parent={} parentState={} childCount={}",
                                    token,
                                    parentToken,
                                    parentStateAfter,
                                    childCountAfter);
                        } catch (Exception ex) {
                            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                                    "[CreateShop] delivery requester forced handler completion failed child={} parent={} error={}",
                                    token,
                                    parentToken,
                                    ex.getMessage() == null ? "<null>" : ex.getMessage());
                        }
                    } else if (containsChild && !FORCE_COMPLETE_LOGGED.add(key)) {
                        com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                                "[CreateShop] delivery requester forced handler completion skipped child={} parent={}",
                                token,
                                parentToken);
                    }
                }
            } catch (Exception ignored) {
                // Ignore logging errors.
            }
        }
        if (delegate == null) {
            return;
        }
        try {
            delegate.onRequestedRequestComplete(manager, request);
        } catch (Exception ex) {
            logOnce("complete", request, ex);
        }
    }

    @Override
    public void onRequestedRequestCancelled(final IRequestManager manager, final IRequest<?> request) {
        com.thesettler_x_create.minecolonies.requestsystem.resolver.CreateShopRequestResolver.onDeliveryCancelled(
                manager, request);
        if (request != null
                && request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
                && manager instanceof com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager standardManager) {
            try {
                var handler = standardManager.getRequestHandler();
                IToken<?> token = request.getId();
                IToken<?> parentToken = request.getParent();
                IRequest<?> parent = parentToken == null || handler == null ? null : handler.getRequest(parentToken);
                String parentState = parent == null ? "<null>" : String.valueOf(parent.getState());
                boolean parentChildren = parent != null && parent.hasChildren();
                com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                        "[CreateShop] delivery requester cancel token={} state={} parent={} parentState={} parentHasChildren={}",
                        token,
                        request.getState(),
                        parentToken == null ? "<none>" : parentToken,
                        parentState,
                        parentChildren);
            } catch (Exception ignored) {
                // Ignore logging errors.
            }
        }
        String token = request == null ? "<null>" : String.valueOf(request.getId());
        if (request != null
                && request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery
                && CANCEL_TRACE_LOGGED.add(token)) {
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] delivery cancel trace {}",
                    token,
                    new RuntimeException("delivery cancel trace"));
        }
        if (delegate == null) {
            return;
        }
        try {
            delegate.onRequestedRequestCancelled(manager, request);
        } catch (Exception ex) {
            logOnce("cancel", request, ex);
        }
    }

    @Override
    public MutableComponent getRequesterDisplayName(final IRequestManager manager, final IRequest<?> request) {
        return delegate == null ? null : delegate.getRequesterDisplayName(manager, request);
    }

    private void logOnce(final String action, final IRequest<?> request, final Exception ex) {
        String token = request == null ? "<null>" : String.valueOf(request.getId());
        String msg = ex.getClass().getSimpleName() + ":" + (ex.getMessage() == null ? "<null>" : ex.getMessage());
        String key = action + ":" + token;
        String last = LAST_ERROR.put(key, msg);
        if (!msg.equals(last)) {
            String delegateInfo = delegate == null ? "<null>" : delegate.getClass().getName();
            com.thesettler_x_create.TheSettlerXCreate.LOGGER.info(
                    "[CreateShop] requester {} error {} -> {} delegate={}", action, token, msg, delegateInfo);
        }
    }
}
