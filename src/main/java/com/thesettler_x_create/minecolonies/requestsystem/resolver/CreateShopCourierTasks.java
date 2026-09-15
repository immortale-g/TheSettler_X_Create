package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Read-only views on a courier's task list. */
final class CreateShopCourierTasks {
  private CreateShopCourierTasks() {}

  /**
   * Returns the request a courier is working on, without assigning it anything.
   *
   * <p>{@link JobDeliveryman#getCurrentTask()} is not a getter: when the courier's own queue is
   * empty it takes requests out of its warehouse queue and puts them into the courier's queue.
   * Called from diagnostics for every courier, that hands out colony-wide courier work on our
   * schedule instead of MineColonies' and can make a delivery look picked up because we assigned
   * it. The first token of {@link JobDeliveryman#getTaskQueue()} is the same task without that side
   * effect.
   */
  @Nullable
  static IRequest<?> peekCurrentTask(IRequestManager manager, JobDeliveryman job) {
    if (manager == null || job == null) {
      return null;
    }
    List<IToken<?>> queue = job.getTaskQueue();
    if (queue == null || queue.isEmpty()) {
      return null;
    }
    return manager.getRequestForToken(queue.get(0));
  }
}
