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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.TaskServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.services.common.AuthCredentialsFactoryService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * This class implements tests for the {@link SshCommandTaskService} class.
 */
public class SshCommandTaskServiceTest {

  private static Class[] getFactoryServices() {
    List<Class> services = new ArrayList<>();
    Collections.addAll(services, ModelServices.FACTORIES);
    Collections.addAll(services, TaskServices.FACTORIES);
    return services.toArray(new Class[services.size()]);
  }

  private String createAuth(TestHost host, String username, String privateKey) throws Throwable {
    AuthCredentialsServiceState startState = new AuthCredentialsServiceState();
    startState.userEmail = username;
    startState.privateKey = privateKey;
    AuthCredentialsServiceState returnState = host.postServiceSynchronously(
        AuthCredentialsFactoryService.SELF_LINK,
        startState,
        AuthCredentialsServiceState.class);

    return returnState.documentSelfLink;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private SshCommandTaskService provisionComputeTaskService;

    @BeforeMethod
    public void setUpTest() {
      provisionComputeTaskService = new SshCommandTaskService(null);
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.INSTRUMENTATION);

      assertThat(provisionComputeTaskService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return SshCommandTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testNoHost() throws Throwable {
      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = null;
      startState.authCredentialLink = "authLink";
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");

      host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testNoAuthLink() throws Throwable {
      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = "localhost";
      startState.authCredentialLink = null;
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");

      host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testNoCommands() throws Throwable {
      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = "localhost";
      startState.authCredentialLink = "authLink";
      startState.commands = null;

      host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class,
          IllegalArgumentException.class);
    }

    @Test
    public void testBadStage() throws Throwable {
      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = "localhost";
      startState.authCredentialLink = "authLink";
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");
      startState.taskInfo = new TaskState();
      startState.taskInfo.stage = TaskState.TaskStage.STARTED;

      host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class,
          IllegalStateException.class);
    }
  }

  /**
   * This class implements end-to-end tests.
   */
  public class EndToEndTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return SshCommandTaskServiceTest.getFactoryServices();
    }

    @Test
    public void testSuccess() throws Throwable {
      String authLink = createAuth(host, "username", "privatekey");

      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = "localhost";
      startState.authCredentialLink = authLink;
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");
      startState.commands.add("pwd");

      SshCommandTaskService.SshCommandTaskState returnState = host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class);

      SshCommandTaskService.SshCommandTaskState completeState = host.waitForServiceState(
          SshCommandTaskService.SshCommandTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
      for (String cmd : startState.commands) {
        assertThat(completeState.commandResponse.get(cmd), is(cmd));
      }
    }

    @Test
    public void testBadAuthLink() throws Throwable {
      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.isMockRequest = true;
      startState.host = "localhost";
      startState.authCredentialLink = "http://localhost/badAuthLink";
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");
      startState.commands.add("pwd");

      SshCommandTaskService.SshCommandTaskState returnState = host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class);

      SshCommandTaskService.SshCommandTaskState completeState = host.waitForServiceState(
          SshCommandTaskService.SshCommandTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }

  /**
   * This class implements example tests against real ssh server.
   */
  public class ManualTest extends BaseModelTest {
    private final String hostname = "0.0.0.0";
    private final String username = "ec2-user";
    private final String privateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
        "\n-----END RSA PRIVATE KEY-----";

    @Override
    protected Class[] getFactoryServices() {
      return SshCommandTaskServiceTest.getFactoryServices();
    }

    /**
     * An example for testing against a real ssh server.
     */
    @Test (enabled = false)
    public void testSuccess() throws Throwable {

      String authLink = createAuth(host, username, privateKey);

      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.host = hostname;
      startState.authCredentialLink = authLink;
      startState.commands = new ArrayList<>();
      startState.commands.add("ls");
      startState.commands.add("pwd");

      SshCommandTaskService.SshCommandTaskState returnState = host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class);

      SshCommandTaskService.SshCommandTaskState completeState = host.waitForServiceState(
          SshCommandTaskService.SshCommandTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FINISHED));
    }

    @Test (enabled = false)
    public void testFailedCommand() throws Throwable {

      String authLink = createAuth(host, username, privateKey);

      SshCommandTaskService.SshCommandTaskState startState =
          new SshCommandTaskService.SshCommandTaskState();
      startState.host = hostname;
      startState.authCredentialLink = authLink;
      startState.commands = new ArrayList<>();
      startState.commands.add("test"); // this command fails (return non-zero)
      startState.commands.add("pwd");

      SshCommandTaskService.SshCommandTaskState returnState = host.postServiceSynchronously(
          SshCommandTaskFactoryService.SELF_LINK,
          startState,
          SshCommandTaskService.SshCommandTaskState.class);

      SshCommandTaskService.SshCommandTaskState completeState = host.waitForServiceState(
          SshCommandTaskService.SshCommandTaskState.class,
          returnState.documentSelfLink,
          state -> TaskState.TaskStage.FINISHED.ordinal() <= state.taskInfo.stage.ordinal()
      );

      assertThat(completeState.taskInfo.stage, is(TaskState.TaskStage.FAILED));
    }
  }
}
