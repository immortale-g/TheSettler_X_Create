package com.thesettler_x_create.minecolonies.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.thesettler_x_create.TheSettlerXCreate;
import com.thesettler_x_create.minecolonies.moduleview.CreateShopTaskModuleView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Task tab window that also renders icons for standard stack requests. */
public class CreateShopTaskModuleWindow extends AbstractModuleWindow<CreateShopTaskModuleView> {
  private static final String LIST_TASKS = "tasks";
  private static final String REQUESTER = "requester";
  private static final String DETAIL_ICON = "detailIcon";
  private static final String SHORT_DETAIL = "shortDetail";
  private static final String PRIORITY = "priority";

  public CreateShopTaskModuleWindow(CreateShopTaskModuleView moduleView) {
    super(
        moduleView,
        ResourceLocation.fromNamespaceAndPath(
            TheSettlerXCreate.MODID, "gui/layouthuts/layoutcreateshop_tasklist.xml"));
  }

  @Override
  public void onOpened() {
    super.onOpened();
    ScrollingList tasks = findPaneOfTypeByID(LIST_TASKS, ScrollingList.class);
    if (tasks != null) {
      tasks.setDataProvider(new TaskDataProvider(moduleView.getTasks()));
    }
  }

  private final class TaskDataProvider implements ScrollingList.DataProvider {
    private final List<IToken<?>> tasks;
    private final List<IToken<?>> visibleTasks = new ArrayList<>();

    private TaskDataProvider(List<IToken<?>> tasks) {
      this.tasks = tasks;
    }

    @Override
    public int getElementCount() {
      refreshVisibleTasks();
      return visibleTasks.size();
    }

    @Override
    public void updateElement(int index, Pane row) {
      IRequestManager manager = buildingView.getColony().getRequestManager();
      if (index >= visibleTasks.size()) {
        refreshVisibleTasks();
      }
      if (index >= visibleTasks.size()) {
        return;
      }

      IRequest<?> request = requestFor(manager, visibleTasks.get(index));
      if (request == null) {
        return;
      }

      updateRequester(row, manager, request);
      updateDetail(row, request);
      updatePriority(row, request);
    }

    private void refreshVisibleTasks() {
      IRequestManager manager = buildingView.getColony().getRequestManager();
      tasks.removeIf(token -> requestFor(manager, token) == null);

      Set<IToken<?>> allTokens = new HashSet<>(tasks);
      visibleTasks.clear();
      for (IToken<?> token : tasks) {
        IRequest<?> request = requestFor(manager, token);
        if (request == null || isDeliveryChildHidden(request, allTokens)) {
          continue;
        }
        visibleTasks.add(token);
      }
    }

    private boolean isDeliveryChildHidden(IRequest<?> request, Set<IToken<?>> allTokens) {
      return request.getRequest() instanceof Delivery
          && request.hasParent()
          && allTokens.contains(request.getParent());
    }
  }

  private void updateRequester(Pane row, IRequestManager manager, IRequest<?> request) {
    Text requesterText = row.findPaneOfTypeByID(REQUESTER, Text.class);
    if (requesterText == null) {
      return;
    }

    IRequest<?> parent = displayParent(manager, request);
    Component requesterName = request.getRequester().getRequesterDisplayName(manager, request);
    if (parent == null) {
      requesterText.setText(requesterName);
      return;
    }

    Component parentName = parent.getRequester().getRequesterDisplayName(manager, parent);
    requesterText.setText(
        Component.literal(requesterName.getString() + " -> " + parentName.getString()));
    PaneBuilders.tooltipBuilder()
        .hoverPane(requesterText)
        .build()
        .setText(
            Component.literal(
                request.getRequester().getLocation().getInDimensionLocation().toShortString()
                    + " -> "
                    + parent
                        .getRequester()
                        .getLocation()
                        .getInDimensionLocation()
                        .toShortString()));
  }

  private IRequest<?> displayParent(IRequestManager manager, IRequest<?> request) {
    IRequest<?> parent = request.hasParent() ? requestFor(manager, request.getParent()) : null;
    while (parent != null && sameRequesterLocation(parent, request) && parent.hasParent()) {
      IRequest<?> nextParent = requestFor(manager, parent.getParent());
      if (nextParent == null) {
        break;
      }
      parent = nextParent;
    }
    return parent;
  }

  private boolean sameRequesterLocation(IRequest<?> left, IRequest<?> right) {
    return left.getRequester().getLocation().equals(right.getRequester().getLocation());
  }

  private void updateDetail(Pane row, IRequest<?> request) {
    ItemIcon detailIcon = row.findPaneOfTypeByID(DETAIL_ICON, ItemIcon.class);
    Text shortDetail = row.findPaneOfTypeByID(SHORT_DETAIL, Text.class);
    if (detailIcon == null || shortDetail == null) {
      return;
    }

    if (request instanceof IStackBasedTask stackBasedTask) {
      ItemStack stack = stackBasedTask.getTaskStack().copy();
      stack.setCount(stackBasedTask.getDisplayCount());
      detailIcon.setItem(stack);
      detailIcon.setVisible(true);
      shortDetail.setText(stackBasedTask.getDisplayPrefix().withStyle(detailColor(request)));
      return;
    }

    setDisplayStackIcon(detailIcon, request);
    shortDetail.setText(
        Component.literal(sanitize(request.getShortDisplayString().getString()))
            .withStyle(detailColor(request)));
  }

  private void setDisplayStackIcon(ItemIcon detailIcon, IRequest<?> request) {
    List<ItemStack> stacks = request.getDisplayStacks();
    if (stacks.isEmpty() || stacks.get(0).isEmpty()) {
      detailIcon.setVisible(false);
      return;
    }

    detailIcon.setItem(stacks.get(0).copy());
    detailIcon.setVisible(true);
  }

  private ChatFormatting detailColor(IRequest<?> request) {
    return request.getState() == RequestState.IN_PROGRESS
        ? ChatFormatting.DARK_GREEN
        : ChatFormatting.BLACK;
  }

  private String sanitize(String value) {
    return value.replace("\u5442", "");
  }

  private void updatePriority(Pane row, IRequest<?> request) {
    Text priority = row.findPaneOfTypeByID(PRIORITY, Text.class);
    if (priority == null) {
      return;
    }

    if (request.getRequest() instanceof IDeliverymanRequestable delivery) {
      priority.setText(
          Component.translatable("com.minecolonies.coremod.gui.workerhuts.deliveryman.priority")
              .append(String.valueOf(delivery.getPriority())));
    } else {
      priority.setText(Component.empty());
    }
  }

  private IRequest<?> requestFor(IRequestManager manager, IToken<?> token) {
    if (token == null) {
      return null;
    }
    try {
      return manager.getRequestForToken(token);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
