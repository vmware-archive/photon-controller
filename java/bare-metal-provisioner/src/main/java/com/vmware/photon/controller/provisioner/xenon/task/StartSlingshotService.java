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

package com.vmware.photon.controller.provisioner.xenon.task;


import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultString;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ProcessFactoryService;
import com.vmware.xenon.services.common.ProcessState;
import com.vmware.xenon.services.common.ServiceHostLogService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * This class implements a Xenon microservice which performs the task of starting go process for Slingshot.
 */
public class StartSlingshotService extends StatefulService {
  public static final int GO_DCP_HOST_PORT = 8082;
  public static final String GO_DCP_HOST_PROCESS_NAME = "go-bmp";

  /**
   * Look in these directories for the Go binary.
   */
  private static final String[] SEARCH_PATHS = new String[]{
      "./bare-metal-provisioner/src/main/resources", // When PWD equals "./"
      "./src/main/resources", // When PWD equals "./bare-metal-provisioner"
      "../go/bmp-adapters/src/main/go"
  };

  /**
   * Relative path to the Go binary.
   */
  public static final Path DEFAULT_DIRECTORY = Paths.get("bin");

  /**
   * This class represent the document state associated with a single {@link StartSlingshotService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * TCP port the Go process should listen on.
     */
    public int httpPort;

    /**
     * Glog v level.
     */
    public int slingshotLogVLevel;

    /**
     * Glog log directory.
     */
    @DefaultString(value = ".")
    public String logDirectory;

    /**
     * Start Slingshot service stage.
     */
    @NotNull
    public TaskState taskInfo;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    @DefaultBoolean(value = false)
    public Boolean isSelfProgressionDisabled;

    /**
     * Link to the ProcessService.
     */
    @WriteOnce
    public String processServiceSelfLink;
  }

  public StartSlingshotService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      int httpPort = 0;
      int gLogVLevel = 0;

      State state = startOperation.getBody(State.class);
      InitializationUtils.initialize(state);

      if (state.taskInfo == null || state.taskInfo.stage == null) {
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskState.TaskStage.CREATED;
      }

      if (state.documentExpirationTimeMicros <= 0) {
        state.documentExpirationTimeMicros =
            ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
      }

      validateState(state);

      httpPort = state.httpPort;
      gLogVLevel = state.slingshotLogVLevel;

      if (httpPort == 0) {
        httpPort = GO_DCP_HOST_PORT;
      }


      this.startProcess(startOperation, state, httpPort, gLogVLevel);
    } catch (IllegalStateException t) {
      ServiceUtils.logSevere(this, t);
      if (!OperationUtils.isCompleted(startOperation)) {
        ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
      }
    } catch (RuntimeException e) {
      logSevere(e);
      if (!OperationUtils.isCompleted(startOperation)) {
        startOperation.fail(e);
      }
    }
  }

  /**
   * Handle service requests.
   *
   * @param patchOperation
   */
  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    URI referer = patchOperation.getReferer();

    try {
      ValidationUtils.validatePatch(currentState, patchState);
      validateStatePatch(currentState, patchState, referer);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();

      switch (currentState.taskInfo.stage) {
        case CREATED:
          break;
        case STARTED:
          handleStartedStage(currentState);
          break;
        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;
        default:
          throw new IllegalStateException(
              String.format("Invalid stage %s", currentState.taskInfo.stage));
      }
    } catch (Throwable e) {
      ServiceUtils.logSevere(this, e);
      if (!OperationUtils.isCompleted(patchOperation)) {
        patchOperation.fail(e);
      }
    }
  }

  /**
   * Processes a patch request to update the execution stage.
   *
   * @param current
   */
  private void handleStartedStage(final State current) {
    this.sendStageProgressPatch(current, TaskState.TaskStage.FINISHED);
  }

  public static String getBinaryBasename() {
    String os = System.getProperty("os.name");
    switch (os) {
      case "Mac OS X":
        os = "-darwin";
        break;
      case "Linux":
        os = "-linux";
        break;
      default:
        throw new RuntimeException("Unknown OS: " + os);
    }

    return String.format("%s%s", GO_DCP_HOST_PROCESS_NAME, os);
  }

  private List<Path> getBinarySearchPath() {
    List<Path> searchPath = new LinkedList<>();
    Path pwd = new File(System.getProperty("user.dir")).toPath();
    for (String path : SEARCH_PATHS) {
      searchPath.add(pwd.resolve(path));
    }
    return searchPath;
  }

  private Path getPath() throws Exception {
    URL url;
    Path relPath;
    Path absPath;

    // Find platform-specific binary (either on disk or in a JAR)
    String basename = getBinaryBasename();
    relPath = DEFAULT_DIRECTORY.resolve(basename);
    url = FileUtils.findResource(this.getBinarySearchPath(), relPath);
    if (url == null) {
      throw new FileNotFoundException("Unable to find " + basename);
    }

    // Copy binary to sandbox if it was found in a JAR
    absPath = this.getHost().copyResourceToSandbox(url, relPath);
    if (relPath == null) {
      throw new Exception("Unable to copy " + basename);
    }

    // Set executable bit if it was copied from a JAR
    File absFile = absPath.toFile();
    if (!absFile.canExecute()) {
      if (!absPath.toFile().setExecutable(true, true)) {
        throw new Exception("Unable to set executable bit on " + absPath.toString());
      }
    }

    return absPath;
  }

  private void startProcess(Operation op, State currentState, int httpPort, int gLogVLevel) {
    String processPath;

    try {
      processPath = getPath().toString();
    } catch (Throwable e) {
      failTask(e);
      return;
    }

    URI hostURI = this.getHost().getUri();
    String[] arguments = new String[]{
        processPath,
        String.format("-log_dir=%s", currentState.logDirectory),
        String.format("--v=%d", gLogVLevel),
        String.format("-dcp=%s:%s", hostURI.getHost(), hostURI.getPort()),
        String.format("-bind=127.0.0.1:%d", httpPort),
        String.format("-auth-token=%s", this.getSystemAuthorizationContext().getToken())
    };

    ProcessState state = new ProcessState();
    state.logFile = ServiceHostLogService.getDefaultGoDcpProcessLogName();
    state.arguments = arguments;
    state.logLink = ServiceUriPaths.GO_PROCESS_LOG;

    sendRequest(Operation
        .createPost(this, ProcessFactoryService.SELF_LINK)
        .setBody(state)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              op.complete();
              ProcessState processState = o.getBody(ProcessState.class);
              StartSlingshotService.State patchState = buildPatch(TaskState.TaskStage.STARTED, null);
              patchState.processServiceSelfLink = processState.documentSelfLink;
              sendStageProgressPatch(currentState, patchState);
            }));
  }

  /**
   * Validate patch correctness.
   *
   * @param current
   * @param patch
   */
  private void validateStatePatch(State current, State patch, URI referer) {
    if (current.taskInfo.stage != TaskState.TaskStage.CREATED &&
        referer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage");
    }

    checkState(current.taskInfo.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal(),
        "Can not patch anymore when in final stage %s", current.taskInfo.stage);
    if (patch.taskInfo != null && patch.taskInfo.stage != null) {
      checkState(patch.taskInfo.stage.ordinal() >= current.taskInfo.stage.ordinal(),
          "Can not revert to %s from %s", patch.taskInfo.stage, current.taskInfo.stage);
    }
  }


  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);
    checkNotNull(current.taskInfo.stage);
    checkState(current.documentExpirationTimeMicros > 0, "documentExpirationTimeMicros needs to be greater than 0");
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    StartSlingshotService.State patchState = buildPatch(TaskState.TaskStage.FAILED, e);
    sendSelfPatch(patchState);
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param s
   */
  private void sendSelfPatch(State s) {
    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(s);
    sendRequest(patch);
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Throwable e) {
    State s = new StartSlingshotService.State();
    s.taskInfo = new TaskState();
    s.taskInfo.stage = stage;

    if (e != null) {
      s.taskInfo.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param stage
   */
  private void sendStageProgressPatch(State current, TaskState.TaskStage stage) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    sendSelfPatch(buildPatch(stage, null));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param current
   * @param patch
   */
  private void sendStageProgressPatch(State current, State patch) {
    if (current.isSelfProgressionDisabled) {
      return;
    }

    sendSelfPatch(patch);
  }
}
