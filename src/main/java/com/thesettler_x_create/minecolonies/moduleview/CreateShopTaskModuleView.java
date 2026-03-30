package com.thesettler_x_create.minecolonies.moduleview;

import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** Task-tab view for Create Shop that merges warehouse queue and inflight parent request tokens. */
public class CreateShopTaskModuleView extends RequestTaskModuleView {
  private final List<IToken<?>> tasks = new ArrayList<>();

  @Override
  public List<IToken<?>> getTasks() {
    return tasks;
  }

  @Override
  public void deserialize(@NotNull RegistryFriendlyByteBuf buf) {
    tasks.clear();
    super.deserialize(buf);
    int queueSize = buf.readInt();
    for (int i = 0; i < queueSize; i++) {
      IToken<?> token = StandardFactoryController.getInstance().deserialize(buf);
      if (token != null) {
        tasks.add(token);
      }
    }
    int extra = buf.readInt();
    for (int i = 0; i < extra; i++) {
      IToken<?> token = StandardFactoryController.getInstance().deserialize(buf);
      if (token != null) {
        tasks.add(token);
      }
    }
    if (!tasks.isEmpty()) {
      LinkedHashSet<IToken<?>> deduped = new LinkedHashSet<>(tasks);
      tasks.clear();
      tasks.addAll(deduped);
    }
  }
}
