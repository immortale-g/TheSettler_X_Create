package com.thesettler_x_create.minecolonies.requestsystem.resolver;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CreateShopCourierTasksTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void peekReturnsTheFirstQueuedTaskWithoutAssigningWork() {
    IRequestManager manager = mock(IRequestManager.class);
    JobDeliveryman job = mock(JobDeliveryman.class);
    IToken<?> first = mock(IToken.class);
    IToken<?> second = mock(IToken.class);
    IRequest request = mock(IRequest.class);
    when(job.getTaskQueue()).thenReturn(List.of(first, second));
    when(manager.getRequestForToken(first)).thenReturn(request);

    assertSame(request, CreateShopCourierTasks.peekCurrentTask(manager, job));
    verify(job, never()).getCurrentTask();
  }

  @Test
  void peekOnAnIdleCourierReturnsNothingAndLeavesTheWarehouseQueueAlone() {
    IRequestManager manager = mock(IRequestManager.class);
    JobDeliveryman job = mock(JobDeliveryman.class);
    when(job.getTaskQueue()).thenReturn(List.of());

    assertNull(CreateShopCourierTasks.peekCurrentTask(manager, job));
    verify(job, never()).getCurrentTask();
  }

  /** getCurrentTask() pulls requests out of the warehouse queue; our code may only peek. */
  @Test
  void mainSourcesNeverCallTheAssigningGetCurrentTask() throws Exception {
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String activeCode =
            Files.readString(file)
                .lines()
                .filter(line -> !line.stripLeading().startsWith("//"))
                .filter(line -> !line.stripLeading().startsWith("*"))
                .reduce("", (joined, line) -> joined + line + "\n");
        assertTrue(
            !activeCode.contains(".getCurrentTask()"),
            file
                + " calls JobDeliveryman.getCurrentTask(); use CreateShopCourierTasks.peekCurrentTask");
      }
    }
  }
}
