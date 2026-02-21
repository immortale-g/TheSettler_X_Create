package com.thesettler_x_create.minecolonies.debug;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.thesettler_x_create.Config;
import com.thesettler_x_create.TheSettlerXCreate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Global MineColonies-native request flow diagnostics (independent from Create Shop presence). */
public final class NativeRequestFlowDiagnostics {
  private static final long LOG_INTERVAL_TICKS = 100L;
  private final Map<Integer, Long> lastLogTick = new ConcurrentHashMap<>();
  private final Map<Integer, String> lastDump = new ConcurrentHashMap<>();

  public void tick(IColony colony, long serverTick) {
    if (!Config.DEBUG_LOGGING.getAsBoolean() || colony == null) {
      return;
    }
    if (!(colony.getRequestManager() instanceof IStandardRequestManager standard)) {
      return;
    }
    int colonyId = safeColonyId(colony);
    long last = lastLogTick.getOrDefault(colonyId, -1L);
    if (last >= 0L && serverTick - last < LOG_INTERVAL_TICKS) {
      return;
    }
    lastLogTick.put(colonyId, serverTick);

    String dump = buildDump(colony, standard);
    String previous = lastDump.put(colonyId, dump);
    if (!dump.equals(previous)) {
      TheSettlerXCreate.LOGGER.info("[MC-NativeFlow] colony={} {}", colonyId, dump);
    }
  }

  private String buildDump(IColony colony, IStandardRequestManager standard) {
    List<String> lines = new ArrayList<>();
    try {
      var assignmentStore = standard.getRequestResolverRequestAssignmentDataStore();
      Map<IToken<?>, java.util.Collection<IToken<?>>> assignments =
          assignmentStore == null
              ? java.util.Collections.emptyMap()
              : assignmentStore.getAssignments();
      lines.add("resolverAssignments=" + assignments.size());

      Set<IToken<?>> tokens = new LinkedHashSet<>();
      for (var entry : assignments.entrySet()) {
        if (entry.getValue() == null || entry.getValue().isEmpty()) {
          continue;
        }
        tokens.addAll(entry.getValue());
        if (tokens.size() >= 12) {
          break;
        }
      }

      List<String> requestStates = new ArrayList<>();
      var handler = standard.getRequestHandler();
      for (IToken<?> token : tokens) {
        IRequest<?> req;
        try {
          req = handler.getRequest(token);
        } catch (Exception ignored) {
          continue;
        }
        if (req == null) {
          continue;
        }
        String type =
            req.getRequest() == null ? "<null>" : req.getRequest().getClass().getSimpleName();
        String state = String.valueOf(req.getState());
        String parent = req.getParent() == null ? "-" : req.getParent().toString();
        int children = req.getChildren() == null ? 0 : req.getChildren().size();
        String resolver = "<none>";
        try {
          var owner = standard.getResolverHandler().getResolverForRequest(req);
          resolver = owner == null ? "<none>" : owner.getClass().getSimpleName();
        } catch (Exception ignored) {
          // Best effort.
        }
        String extra = "";
        if (req.getRequest() instanceof Delivery delivery) {
          int count = delivery.getStack() == null ? 0 : delivery.getStack().getCount();
          String item =
              delivery.getStack() == null || delivery.getStack().isEmpty()
                  ? "<empty>"
                  : delivery.getStack().getItem().toString();
          extra = " item=" + item + " x" + count;
        }
        String payload = describePayload(req.getRequest());
        String requester = describeRequester(req);
        String provider = describeProvider(req);
        String childSummary = describeChildren(handler, req);
        requestStates.add(
            token
                + "{"
                + type
                + ","
                + state
                + ",p="
                + parent
                + ",c="
                + children
                + ",r="
                + resolver
                + ",rq="
                + requester
                + ",pv="
                + provider
                + ",pl="
                + payload
                + ",ch="
                + childSummary
                + extra
                + "}");
        if (requestStates.size() >= 8) {
          break;
        }
      }
      lines.add("requests=" + requestStates.size());
      if (!requestStates.isEmpty()) {
        lines.add(String.join(" | ", requestStates));
      }
    } catch (Exception ex) {
      lines.add("requestDumpError=" + ex.getClass().getSimpleName());
    }

    try {
      List<String> queues = new ArrayList<>();
      var manager = colony.getServerBuildingManager();
      if (manager != null) {
        for (var entry : manager.getBuildings().entrySet()) {
          var building = entry.getValue();
          if (building == null) {
            continue;
          }
          WarehouseRequestQueueModule queue =
              building.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
          if (queue == null) {
            continue;
          }
          int size =
              queue.getMutableRequestList() == null ? 0 : queue.getMutableRequestList().size();
          if (size <= 0) {
            continue;
          }
          queues.add(building.getClass().getSimpleName() + ":" + size);
          if (queues.size() >= 6) {
            break;
          }
        }
      }
      lines.add("warehouseQueues=" + (queues.isEmpty() ? 0 : queues.size()));
      if (!queues.isEmpty()) {
        lines.add(String.join(" | ", queues));
      }
    } catch (Exception ex) {
      lines.add("queueDumpError=" + ex.getClass().getSimpleName());
    }
    return String.join(" || ", lines);
  }

  private int safeColonyId(IColony colony) {
    try {
      return colony.getID();
    } catch (Exception ignored) {
      return colony.hashCode();
    }
  }

  private String describeChildren(Object handler, IRequest<?> request) {
    if (handler == null
        || request == null
        || request.getChildren() == null
        || request.getChildren().isEmpty()) {
      return "-";
    }
    List<String> children = new ArrayList<>();
    int logged = 0;
    for (IToken<?> childToken : request.getChildren()) {
      if (logged >= 3) {
        break;
      }
      try {
        IRequest<?> child = resolveRequest(handler, childToken);
        if (child == null) {
          children.add(childToken + ":<missing>");
        } else {
          String cType =
              child.getRequest() == null ? "<null>" : child.getRequest().getClass().getSimpleName();
          children.add(childToken + ":" + cType + ":" + child.getState());
        }
      } catch (Exception ignored) {
        children.add(childToken + ":<err>");
      }
      logged++;
    }
    return children.isEmpty() ? "-" : String.join(",", children);
  }

  @SuppressWarnings("unchecked")
  private IRequest<?> resolveRequest(Object handler, IToken<?> token) {
    if (handler == null || token == null) {
      return null;
    }
    try {
      var method = handler.getClass().getMethod("getRequest", IToken.class);
      Object result = method.invoke(handler, token);
      return result instanceof IRequest<?> request ? request : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private String describeRequester(IRequest<?> request) {
    if (request == null || request.getRequester() == null) {
      return "<none>";
    }
    Object requester = request.getRequester();
    String clazz = requester.getClass().getSimpleName();
    String loc = invokeLocation(requester);
    return loc == null ? clazz : clazz + "@" + loc;
  }

  private String describeProvider(IRequest<?> request) {
    if (request == null) {
      return "<none>";
    }
    Object provider = tryInvoke(request, "getProvider");
    if (provider == null) {
      return "<none>";
    }
    String clazz = provider.getClass().getSimpleName();
    String loc = invokeLocation(provider);
    return loc == null ? clazz : clazz + "@" + loc;
  }

  private String invokeLocation(Object target) {
    Object location = tryInvoke(target, "getLocation");
    return location == null ? null : String.valueOf(location);
  }

  private String describePayload(Object payload) {
    if (payload == null) {
      return "<null>";
    }
    if (payload instanceof Delivery delivery) {
      String item =
          delivery.getStack() == null || delivery.getStack().isEmpty()
              ? "<empty>"
              : delivery.getStack().getItem().toString();
      int count = delivery.getStack() == null ? 0 : delivery.getStack().getCount();
      return "Delivery("
          + item
          + "x"
          + count
          + " from="
          + delivery.getStart()
          + " to="
          + delivery.getTarget()
          + ")";
    }
    Object result = tryInvoke(payload, "getResult");
    Object count = tryInvoke(payload, "getCount");
    Object min = tryInvoke(payload, "getMinimalCount");
    if (result != null || count != null || min != null) {
      return payload.getClass().getSimpleName()
          + "(result="
          + shorten(String.valueOf(result))
          + ",count="
          + String.valueOf(count)
          + ",min="
          + String.valueOf(min)
          + ")";
    }
    Object stack = tryInvoke(payload, "getStack");
    if (stack != null) {
      return payload.getClass().getSimpleName() + "(stack=" + shorten(String.valueOf(stack)) + ")";
    }
    return shorten(payload.toString());
  }

  private Object tryInvoke(Object target, String methodName) {
    if (target == null) {
      return null;
    }
    try {
      var method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String shorten(String input) {
    if (input == null) {
      return "<null>";
    }
    return input.length() > 120 ? input.substring(0, 120) + "..." : input;
  }
}
