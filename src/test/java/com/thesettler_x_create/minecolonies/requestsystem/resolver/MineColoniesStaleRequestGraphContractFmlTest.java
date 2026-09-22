package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minecolonies.api.colony.requestsystem.data.IRequestSystemBuildingDataStore;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.management.handlers.RequestHandler;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link StaleRequestGraphDetector} exists because of two concrete places in MineColonies that
 * throw when the request graph is already broken. Nothing in our code calls them directly, so a
 * rename or a fix on their side would otherwise pass unnoticed until the detector quietly stopped
 * matching anything. The nightly build runs against the newest MineColonies release, so this test
 * is where such a change shows up.
 */
@Tag("fml")
class MineColoniesStaleRequestGraphContractFmlTest {

  @Test
  void theRequestHandlerStillCancelsATokenWithoutCheckingItExists() {
    assertDoesNotThrow(
        () -> RequestHandler.class.getMethod("onRequestCancelledDirectly", IToken.class),
        "RequestHandler.onRequestCancelledDirectly is gone: it is the throw site behind the"
            + " IRequest.hasChildren() NPE that StaleRequestGraphDetector treats as a stale graph");
  }

  @Test
  void theBuildingStillHandlesACancelForARequestItMayHaveForgotten() {
    assertDoesNotThrow(
        () ->
            AbstractBuilding.class.getMethod(
                "onRequestedRequestCancelled", IRequestManager.class, IRequest.class),
        "AbstractBuilding.onRequestedRequestCancelled is gone: it is the throw site behind the"
            + " Integer.intValue() NPE that StaleRequestGraphDetector treats as a stale graph");
  }

  @Test
  void theCitizenMappingIsStillAnUnboxingMap() throws Exception {
    var method = IRequestSystemBuildingDataStore.class.getMethod("getCitizensByRequest");

    assertEquals(
        Map.class,
        method.getReturnType(),
        "getCitizensByRequest no longer returns a Map; AbstractBuilding.onRequestedRequestCancelled"
            + " unboxes its remove() result, which is what makes a forgotten request throw");
    assertEquals(
        Integer.class,
        ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[1],
        "getCitizensByRequest no longer maps to Integer, so the unboxing NPE"
            + " StaleRequestGraphDetector falls back on cannot happen any more");
  }
}
