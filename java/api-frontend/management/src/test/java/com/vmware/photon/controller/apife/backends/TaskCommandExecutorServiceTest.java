/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.common.exceptions.ApiFeException;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.OutOfThreadPoolWorkerException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.DeployerClient;
import com.vmware.photon.controller.apife.backends.clients.HousekeeperXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.SchedulerXenonRestClient;
import com.vmware.photon.controller.apife.commands.tasks.TaskCommand;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link TaskCommandExecutorService}.
 */
public class TaskCommandExecutorServiceTest {
  private static final Logger logger = LoggerFactory.getLogger(TaskCommandExecutorServiceTest.class);
  private ApiFeXenonRestClient dcpClient = mock(ApiFeXenonRestClient.class);
  private HousekeeperClient housekeeperClient = mock(HousekeeperClient.class);
  private HousekeeperXenonRestClient housekeeperXenonRestClient = mock(HousekeeperXenonRestClient.class);
  private DeployerClient deployerClient = mock(DeployerClient.class);
  private HostClient hostClient = mock(HostClient.class);
  private EntityLockBackend entityLockBackend = mock(EntityLockBackend.class);
  private SchedulerXenonRestClient schedulerXenonRestClient = mock(SchedulerXenonRestClient.class);
  private com.vmware.photon.controller.apife.backends.clients.DeployerClient deployerXenonClient =
      mock(com.vmware.photon.controller.apife.backends.clients.DeployerClient.class);
  private com.vmware.photon.controller.apife.backends.clients.HousekeeperClient housekeeperXenonClient =
      mock(com.vmware.photon.controller.apife.backends.clients.HousekeeperClient.class);

  @DataProvider(name = "getSubmitParams")
  public Object[][] getSubmitParams() {
    return new Object[][]{
        new Object[]{1, 1, 1},
        new Object[]{3, 2, 2},
        new Object[]{5, 2, 5},
        new Object[]{5, 3, 10},
        new Object[]{6, 3, 15},
        new Object[]{16, 4, 15}
    };
  }

  @Test(dataProvider = "getSubmitParams")
  public void testSubmit(int poolSize, int poolBufferSize, int extra) throws Exception {
    final TaskCommandExecutorService service = new TaskCommandExecutorService(
        poolSize,
        poolSize,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(poolBufferSize),
        new ThreadFactoryBuilder().setNameFormat("BackendWorker" + "-%d").build()
    );
    CountDownLatch countDownLatch = new CountDownLatch(1);

    // fill thread pool
    TestTaskCommand[] commands = startCommands(service, poolSize + poolBufferSize, countDownLatch);

    // try to submit extra commands - these should all fail
    TestTaskCommand[] extraCommands = startCommands(service, extra, null);

    // release threads
    countDownLatch.countDown();

    assertThat(getFailureCount(commands), is(0));
    assertThat(getFailureCount(extraCommands), is(extra));
  }

  private int getFailureCount(TestTaskCommand[] commands) throws InterruptedException {
    boolean done = false;
    while (!done) {
      done = true;
      for (int i = 0; i < commands.length; i++) {
        done &= commands[i].isDone();
      }
      Thread.sleep(10);
    }

    int failureCount = 0;
    for (int i = 0; i < commands.length; i++) {
      Throwable t = commands[i].getException();
      if (t != null) {
        failureCount++;
        assertThat(t instanceof OutOfThreadPoolWorkerException, is(true));
      }
    }
    return failureCount;
  }

  private TestTaskCommand[] startCommands(
      final TaskCommandExecutorService service,
      int count,
      final CountDownLatch countDownLatch) {
    TestTaskCommand[] commands = new TestTaskCommand[count];
    for (int i = 0; i < count; i++) {
      TaskEntity task = new TaskEntity();
      task.setId("t" + i);
      commands[i] = new TestTaskCommand(dcpClient, schedulerXenonRestClient, hostClient,
          housekeeperClient, housekeeperXenonRestClient, deployerClient, deployerXenonClient,
          housekeeperXenonClient, task, countDownLatch);
      try {
        service.submit(commands[i]);
      } catch (ExternalException ex) {
        assertThat(ex instanceof OutOfThreadPoolWorkerException, is(true));
      }
    }
    return commands;
  }

  /**
   * A test implementation of TaskCommand.
   */
  private class TestTaskCommand extends TaskCommand {
    private final CountDownLatch countDownLatch;
    private volatile Throwable exception = null;
    private volatile boolean done = false;

    public TestTaskCommand(ApiFeXenonRestClient dcpClient,
                           SchedulerXenonRestClient schedulerXenonRestClient,
                           HostClient hostClient,
                           HousekeeperClient housekeeperClient,
                           HousekeeperXenonRestClient housekeeperXenonRestClient,
                           DeployerClient deployerClient,
                           com.vmware.photon.controller.apife.backends.clients.DeployerClient deployerXenonClient,
                           com.vmware.photon.controller.apife.backends.clients.HousekeeperClient housekeeperXenonClient,
                           TaskEntity task,
                           CountDownLatch countDownLatch) {
      super(dcpClient, schedulerXenonRestClient, hostClient, housekeeperClient, housekeeperXenonRestClient,
          deployerClient, deployerXenonClient, housekeeperXenonClient, entityLockBackend, task);
      this.countDownLatch = countDownLatch;
    }

    @Override
    protected void execute() throws ApiFeException, InterruptedException {
      if (null != countDownLatch) {
        countDownLatch.await();
      }
    }

    @Override
    protected void markAsDone() {
      done = true;
    }

    @Override
    protected void markAsStarted() {
    }

    @Override
    public void markAllStepsAsFailed(Throwable t) {
      exception = t;
      done = true;
    }

    @Override
    protected void cleanup() {
    }

    public Throwable getException() {
      return exception;
    }

    public boolean isDone() {
      return done;
    }
  }
}
