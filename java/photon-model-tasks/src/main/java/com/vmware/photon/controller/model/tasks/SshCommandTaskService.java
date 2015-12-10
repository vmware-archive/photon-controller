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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task to execute commands on remote host via SSH.
 */
public class SshCommandTaskService extends StatefulService {

  private static final long DEFAULT_EXPIRATION_SECONDS = 600;

  private static final int DEFAULT_SSH_PORT = 22;

  private ExecutorService executor;

  /**
   * Represent state of SshCommand task.
   */
  public static class SshCommandTaskState extends ServiceDocument {

    // task state.
    public TaskState taskInfo = new TaskState();

    // Host address.
    public String host;

    // SSH Port.
    public int port = DEFAULT_SSH_PORT;

    // Auth credential link.
    public String authCredentialLink;

    // list of commands to execute on the compute resource.
    public List<String> commands;

    // result of command execution.
    public Map<String, String> commandResponse;

    /**
     * Value indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    public boolean isMockRequest;
  }

  public SshCommandTaskService(ExecutorService executor) {
    super(SshCommandTaskState.class);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
    this.executor = executor;
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      start.fail(new IllegalArgumentException("body is required"));
      return;
    }

    SshCommandTaskState state = start.getBody(SshCommandTaskState.class);

    if (state.host == null) {
      start.fail(new IllegalArgumentException("host is required"));
      return;
    }

    if (state.authCredentialLink == null) {
      start.fail(new IllegalArgumentException("authCredentialLink is required"));
      return;
    }

    if (state.commands == null || state.commands.isEmpty()) {
      start.fail(new IllegalArgumentException("commands must be specified"));
      return;
    }

    if (executor == null) {
      start.fail(new IllegalArgumentException("executor must not be null"));
      return;
    }

    if (state.taskInfo == null) {
      state.taskInfo = new TaskState();
    }
    if (TaskState.isFinished(state.taskInfo)) {
      // task is in the finished state, just return.
      start.complete();
      return;
    }
    if (state.taskInfo.stage != null && state.taskInfo.stage != TaskStage.CREATED) {
      start.fail(new IllegalStateException("SshCommand cannot be restarted."));
      return;
    }
    state.taskInfo.stage = TaskStage.STARTED;

    if (state.documentExpirationTimeMicros == 0) {
      // always set expiration so we do not accumulate tasks.
      state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
          + TimeUnit.SECONDS.toMicros(DEFAULT_EXPIRATION_SECONDS);
    }

    start.setBody(state).complete();

    getAuth(state);
  }

  @Override
  public void handlePatch(Operation patch) {
    SshCommandTaskState currentState = getState(patch);
    SshCommandTaskState patchState = patch
        .getBody(SshCommandTaskState.class);

    if (TaskState.isFinished(currentState.taskInfo) ||
        TaskState.isFailed(currentState.taskInfo) ||
        TaskState.isCancelled(currentState.taskInfo)) {
      logInfo("Task is complete, patch ignored");
      patch.complete();
      return;
    }

    if (patchState.commandResponse != null) {
      currentState.commandResponse = patchState.commandResponse;
    }
    if (patchState.taskInfo != null) {
      currentState.taskInfo = patchState.taskInfo;
    }

    patch.complete();
    return;
  }

  private void getAuth(SshCommandTaskState state) {
    try {
      sendRequest(Operation
          .createGet(this, state.authCredentialLink)
          .setCompletion((o, e) -> {
            if (e != null) {
              fail(state, e);
              return;
            }

            // handle mock requests
            if (state.isMockRequest) {
              Map<String, String> commandResponse = new HashMap<>();
              for (String cmd: state.commands) {
                commandResponse.put(cmd, cmd);
              }
              sendSelfPatch(state, TaskStage.FINISHED, commandResponse, null);
              return;
            }

            // Submit commands to executor.
            try {
              AuthCredentialsServiceState auth = o.getBody(AuthCredentialsServiceState.class);
              executor.submit(new SshCommand(state, auth));
            } catch (Throwable t) {
              fail(state, t);
            }
          }));
    } catch (Throwable t) {
      fail(state, t);
    }
  }

  private void sendSelfPatch(SshCommandTaskState state,
                             TaskStage stage,
                             Map<String, String> commandResponse,
                             Throwable t) {
    SshCommandTaskState patch = new SshCommandTaskState();
    patch.taskInfo = new TaskState();
    patch.taskInfo.stage = stage;
    patch.commandResponse = commandResponse;
    if (t != null) {
      patch.taskInfo.failure = Utils.toServiceErrorResponse(t);
    }

    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(), state.documentSelfLink))
        .setBody(patch);
    sendRequest(patchOperation);
  }

  private void fail(SshCommandTaskState state, Throwable t) {
    sendSelfPatch(state, TaskStage.FAILED, null, t);
  }

  /**
   * Runnable that executes SSH commands on worker thread.
   */
  private class SshCommand implements Runnable {

    SshCommandTaskState state;
    AuthCredentialsServiceState auth;

    public SshCommand(SshCommandTaskState state, AuthCredentialsServiceState auth) {
      this.state = state;
      this.auth = auth;
    }

    @Override
    public void run() {
      TaskStage stage = TaskStage.FINISHED;
      Map<String, String> commandResponse = new HashMap<>();

      Session session = null;
      try {
        JSch jsch = new JSch();
        session = jsch.getSession(auth.userEmail, state.host, state.port);

        jsch.addIdentity("KeyPair", auth.privateKey.getBytes(), null, null);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        session.connect();

        for (String cmd : state.commands) {
          // Create a channel for each command.
          ChannelExec channel = (ChannelExec) session.openChannel("exec");
          channel.setCommand(cmd);

          ByteArrayOutputStream out = new ByteArrayOutputStream();
          channel.setOutputStream(out);
          channel.setErrStream(out);

          channel.connect();

          while (!channel.isClosed()) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
            }
          }

          // add command output/err to response map.
          commandResponse.put(cmd, out.toString());
          int exitStatus = channel.getExitStatus();
          channel.disconnect();

          if (exitStatus != 0) {
            // last command failed, sendSelfPatch the task.
            stage = TaskStage.FAILED;
            break;
          }
        }

        // patch task state
        sendSelfPatch(state, stage, commandResponse, null);

      } catch (Throwable t) {
        fail(state, t);
        return;
      } finally {
        if (session != null) {
          session.disconnect();
        }
      }
    }
  }
}
