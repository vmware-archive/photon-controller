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
      provisionComputeTaskService = new SshCommandTaskService();
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
    private final String hostname = "52.34.82.166";
    private final String username = "ec2-user";
    private final String privateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEogIBAAKCAQEAgw3wfpZ5nPpScOGeJ4S9s6X7Kb84gip6Y6MWIIlQ2h3YVr5WYctb9WfxX6bj" +
        "LfbSCrbIqwZHU1sotE6Y2iidBKgrtckPKk4pA92s0X+A6inunmAo7wWbDurZGv5xf4IiFqWK6Evw" +
        "7wVBqXZOLNaBCAdiDLLJGwSu6/e8pbKSts9kLnzsxtcJfd8RTGTGhfMcGuFAuyuBH3wMUDEsBR5y" +
        "QpSVNgeWGhyS90tSK39hS8fyN4Hy5OOWnr4WQxyNgCKzqk6bxQG0w9i30blUDla0Ci/lwTyk1Ioe" +
        "SFvaOm8Z+5CznJ38CTumj0XjDrfgRw7fKbDYxaHBUbc+jLE3algiiwIDAQABAoIBABVy5JdzPTgp" +
        "5/A9nMrO+NU8Jx0wBKmZFirUeDye/LKWC+A2iqC6zbQebOzAvZG6QaorPDBxeJ713nWUH4Qk/X3T" +
        "oEPCQk8kN6ZAU5Z+DbVFY55cEpb8DeKlIR8/4YeC3t1h/pgCRc54x8RabnT8LmYH/04gvLVFUPOh" +
        "uihMT6LHlpdhFOi/00CaxExvooaUjxiHOiZre10PYoZWp2RPXGanLqeoIaHxk2qGlVkyV4x/NUXE" +
        "Wvf11Xv4At/VMdsxlAOCGdCG/MtE8Lkzi/rqp3gzDvidesLZ/E2gaKO53QfJLFKvKBkI+QtpeRyP" +
        "gcCyYMQ2z+i8/pJ9eYMxcZjmDfECgYEAvnZviJyWhki8z6Hm41YunhfyN1ARar9f88GfwhNLQV/t" +
        "bSPwGnRYq+RveVauJ1PHst5kS3So1Bq8YTXyzEWqGA5Qbl1RyAiFGNYASEXzTVDmg15i0U+rEh67" +
        "lcCGNSjzi075nBMZKteNKrE0UfDpmZ0IGv8ZEMOPdwbS3p8LMN0CgYEAsCZTpkgFUl1r122VinFs" +
        "nuwb5BkyxXPanTe1/CHgKGNnHQYjrBiiHzT9Ky20zn+Yt4UvAwiH9bEUfpcQ/O9ZvhhvFeeYsbnO" +
        "NA3/qjPSzefEdq5bJ2FvOc5XOjXH1/FZnE0tWqiKZjpbxMaD1b4PeEbt257MG3HxdR4F8pYh9ocC" +
        "gYASomvPJeLkSIGQnvqEC46METO1jbPmicrNgogq6NBYGRaVswpuzCtQxgzSBlULq/rB2VheuY87" +
        "EVKVAD49FiPKLrxXz/GMbKj5ARcN+yoOyneDKtzoNa87Gp6nzCpVUShi3Ns3FfdEZcp9/tBro/J3" +
        "ARIl8gd1yGxk7Nn8xfrGUQKBgEfOl/MFEQZOhxO+3GMfccQnRAdsLkJHxCqq90jdFl/ghbxTMF+L" +
        "eyt1km7zpu03Hq5RYKS/6YzCrzQbqRUzDUZs93vaeWiZ8fFOc5aSobDGdlRbJ1WaZpkOAIj+O4VQ" +
        "IdORspdudVzPFXHV6xBK9kt1vqj0xwe5H1E69/m4MggpAoGAEOMf3sqShS5FETdqhJJJRkgN8PrU" +
        "WZZ6NKYBvkgwRV9tIrmSBKNYFUO3u6pFmXCRbi8JA8jnYzYMzXjS2iRMhhlHUNgakp7fFiVkkhyU" +
        "kW+p5nlEwvXnY6n3tauNKWV+Ik6inwdYH8QqWRSg4iGhy+jBaj/gYmB/L2sd/GDr6nI=\n" +
        "-----END RSA PRIVATE KEY-----";

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
